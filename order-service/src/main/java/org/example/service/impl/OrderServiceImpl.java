package org.example.service.impl;

import org.example.service.OrderService;
import org.example.enums.OrderStatus;

import feign.FeignException;
import lombok.extern.slf4j.Slf4j;
import org.example.dto.order.*;
import org.example.exp.OrderException;
import org.example.client.*;
import org.example.utils.SpringSecurityUtil;
import org.example.entity.*;
import org.example.repository.OrderRepository;
import org.example.mapper.OrderMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Supplier;
import static org.example.enums.OrderStatus.*;

@Service
@Slf4j
public class OrderServiceImpl implements OrderService {
    private final OrderRepository orders;
    private final ProductClient products;
    private final CompanyClient companies;
    private final ControlClient control;
    private final Clock clock;
    private final Duration ttl;
    private final TransactionTemplate tx;
    private final OrderMapper mapper;

    public OrderServiceImpl(OrderRepository orders, ProductClient products, CompanyClient companies, ControlClient control,
            Clock clock, PlatformTransactionManager manager, OrderMapper mapper, @Value("${order.reservation-ttl}") Duration ttl) {
        this.orders = orders; this.products = products; this.companies = companies; this.control = control;
        this.clock = clock; this.tx = new TransactionTemplate(manager); this.ttl = ttl; this.mapper = mapper;
        if (ttl.isNegative() || ttl.isZero() || ttl.compareTo(Duration.ofDays(30)) > 0)
            throw new IllegalArgumentException("order.reservation-ttl 0 dan katta va 30 kundan oshmasin");
    }

    @Override
    public OrderResponse create(OrderCreateRequest request) {
        Long buyer = SpringSecurityUtil.getProfileId();
        String key = request.idempotencyKey().strip();
        String hash = fingerprint(request);
        var existing = orders.findByBuyerIdAndIdempotencyKey(buyer, key);
        Long id;
        if (existing.isPresent()) {
            if (!hash.equals(existing.get().getRequestHash())) throw conflict("Kalit boshqa buyurtma uchun ishlatilgan");
            id = existing.get().getId();
        } else {
            var prepared = prepare(request, buyer, key, hash);
            try { id = tx.execute(s -> orders.saveAndFlush(prepared).getId()); }
            catch (DataIntegrityViolationException e) {
                var winner = orders.findByBuyerIdAndIdempotencyKey(buyer, key).orElseThrow(() -> e);
                if (!hash.equals(winner.getRequestHash())) throw conflict("Kalit boshqa buyurtma uchun ishlatilgan");
                id = winner.getId();
            }
        }
        var response = resume(id);
        if (response.status() == REJECTED) throw conflict(response.failureReason());
        return response;
    }

    private Order prepare(OrderCreateRequest r, Long buyer, String key, String hash) {
        if (key.isEmpty()) throw new OrderException(400, "idempotencyKey bo'sh bo'lmasin");
        Set<Long> ids = new HashSet<>();
        var order = new Order();
        order.setBuyerId(buyer); order.setCompanyId(r.companyId()); order.setIdempotencyKey(key); order.setRequestHash(hash);
        order.setStatus(RESERVING); order.setDeliveryAddress(normalize(r.deliveryAddress())); order.setNote(normalize(r.note()));
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        order.setCreatedAt(now); order.setUpdatedAt(now); order.setExpiresAt(now.plus(ttl));
        BigDecimal total = BigDecimal.ZERO;
        Long seller = null;
        for (var input : r.items().stream().sorted(Comparator.comparing(OrderCreateRequest.Item::productId)).toList()) {
            if (!ids.add(input.productId())) throw new OrderException(400, "Bitta productId ni takror yubormang");
            var p = remote(() -> products.get(input.productId()));
            if (p == null || !input.productId().equals(p.id()) || p.companyId() == null || p.sellerId() == null
                    || p.name() == null || p.name().isBlank())
                throw OrderException.unavailable();
            if (!r.companyId().equals(p.companyId())) throw new OrderException(400, "Bitta buyurtmada faqat bitta kompaniya mahsulotlari bo'lsin");
            if (!p.orderable() || p.price() == null || p.price().signum() <= 0 || p.currency() == null)
                throw conflict("Mahsulot sotuvda emas yoki qat'iy sotuv narxi belgilanmagan");
            if (p.price().stripTrailingZeros().scale() > 3 || p.price().precision() - p.price().scale() > 16)
                throw conflict("Mahsulot narxi ruxsat etilgan aniqlikdan oshgan");
            if (p.minimumQuantity() != null && input.quantity().compareTo(BigDecimal.valueOf(p.minimumQuantity())) < 0)
                throw new OrderException(400, "Mahsulotning minimal buyurtma miqdoriga rioya qiling");
            if (order.getCurrency() != null && !order.getCurrency().equals(p.currency()))
                throw new OrderException(400, "Bitta buyurtmada valyuta bir xil bo'lsin");
            if (seller != null && !seller.equals(p.sellerId())) throw OrderException.unavailable();
            seller = p.sellerId(); order.setCurrency(p.currency());
            var item = new OrderItem(); item.setOrder(order); item.setProductId(p.id()); item.setProductName(p.name());
            item.setQuantity(input.quantity()); item.setUnitPrice(p.price());
            item.setLineAmount(p.price().multiply(input.quantity())); total = total.add(item.getLineAmount());
            order.getItems().add(item);
        }
        requireOwner(r.companyId(), seller);
        order.setTotalAmount(total);
        return order;
    }

    @Override
    public OrderResponse get(Long id) {
        checkAccess(id, false);
        return resume(id);
    }

    @Override
    public OrderResponse ship(Long id) {
        checkAccess(id, true);
        resume(id);
        tx.executeWithoutResult(s -> {
            var o = locked(id);
            if (o.getStatus() == SHIPPED || o.getStatus() == SHIPPING) return;
            if (o.getStatus() != RESERVED) throw conflict("Bu holatdagi buyurtmani jo'natib bo'lmaydi");
            o.setStatus(SHIPPING); o.setUpdatedAt(clock.instant());
        });
        var result = resume(id);
        if (result.status() != SHIPPED) throw conflict("Rezerv muddati tugagan yoki buyurtma bekor qilingan");
        return result;
    }

    @Override
    public OrderResponse cancel(Long id) {
        checkAccess(id, false);
        resume(id);
        tx.executeWithoutResult(s -> {
            var o = locked(id);
            if (Set.of(CANCELLED, CANCELLING, EXPIRED, REJECTED).contains(o.getStatus())) return;
            if (o.getStatus() != RESERVED) throw conflict("Jo'natilgan yoki jo'natilayotgan buyurtmani bekor qilib bo'lmaydi");
            o.setStatus(CANCELLING); o.setUpdatedAt(clock.instant());
        });
        return resume(id);
    }

    private void checkAccess(Long id, boolean sellerOnly) {
        Long actor = SpringSecurityUtil.getProfileId();
        tx.executeWithoutResult(s -> {
            var o = orders.findById(id).orElseThrow(() -> new OrderException(404, "Buyurtma topilmadi"));
            if (!sellerOnly && actor.equals(o.getBuyerId())) return;
            if (!SpringSecurityUtil.isSeller()) throw new OrderException(403, "Buyurtma sizga tegishli emas");
            requireOwner(o.getCompanyId(), actor);
        });
    }

    @Override
    public Page<OrderResponse> list(OrderFilter f, boolean seller) {
        Long actor = SpringSecurityUtil.getProfileId();
        if (seller) {
            if (f.getCompanyId() == null) throw new OrderException(400, "companyId majburiy");
            requireOwner(f.getCompanyId(), actor);
        }
        return tx.execute(s -> {
            Specification<Order> spec = (root, query, cb) -> seller
                    ? cb.equal(root.get("companyId"), f.getCompanyId()) : cb.equal(root.get("buyerId"), actor);
            if (!seller && f.getCompanyId() != null) spec = spec.and((r,q,c) -> c.equal(r.get("companyId"), f.getCompanyId()));
            if (f.getStatus() != null) spec = spec.and((r,q,c) -> c.equal(r.get("status"), f.getStatus()));
            if (f.getDateFrom() != null) spec = spec.and((r,q,c) -> c.greaterThanOrEqualTo(r.get("createdAt"),
                    f.getDateFrom().atStartOfDay(ZoneId.of("Asia/Tashkent")).toInstant()));
            if (f.getDateTo() != null && !f.getDateTo().equals(LocalDate.MAX)) spec = spec.and((r,q,c) -> c.lessThan(r.get("createdAt"),
                    f.getDateTo().plusDays(1).atStartOfDay(ZoneId.of("Asia/Tashkent")).toInstant()));
            return orders.findAll(spec, PageRequest.of(f.getPage()-1, f.getPerPage(), Sort.by(Sort.Direction.DESC, "createdAt", "id")))
                    .map(mapper::toResponse);
        });
    }

    // Intent bazaga oldin yoziladi. Masofaviy javob yo'qolsa, shu orderId bilan amal qayta bajariladi.
    @Override
    public OrderResponse resume(Long id) {
        return tx.execute(s -> {
            var o = locked(id);
            if (Set.of(SHIPPED, CANCELLED, EXPIRED, REJECTED).contains(o.getStatus())) return mapper.toResponse(o);
            ControlClient.Envelope result;
            try {
                result = switch (o.getStatus()) {
                    case RESERVING -> control.reserve(id, new ControlClient.ReserveRequest(o.getCompanyId(), o.getBuyerId(), o.getExpiresAt(),
                            o.getItems().stream().map(i -> new ControlClient.Item(i.getProductId(), i.getQuantity())).toList()));
                    case SHIPPING -> control.commit(id, o.getCompanyId());
                    case CANCELLING -> control.release(id, o.getCompanyId());
                    case RESERVED -> control.get(id, o.getCompanyId());
                    default -> throw new IllegalStateException("Kutilmagan order holati");
                };
            } catch (FeignException e) {
                if (o.getStatus() == RESERVING && (e.status() == 400 || e.status() == 404 || e.status() == 409)) {
                    o.setStatus(o.getExpiresAt().isAfter(clock.instant()) ? REJECTED : EXPIRED);
                    o.setFailureReason("Mahsulot qoldig'i yetarli emas yoki rezerv yaratib bo'lmadi");
                    o.setUpdatedAt(clock.instant());
                    return mapper.toResponse(o);
                }
                throw OrderException.unavailable();
            }
            if (result == null || !result.success() || result.data() == null) throw OrderException.unavailable();
            var r = result.data();
            if (r.id() == null || r.id() <= 0 || !o.getCompanyId().equals(r.companyId()) || !id.equals(r.orderId()))
                throw OrderException.unavailable();
            OrderStatus next = switch (String.valueOf(r.status())) {
                case "ACTIVE" -> RESERVED;
                case "COMMITTED" -> SHIPPED;
                case "RELEASED" -> CANCELLED;
                case "EXPIRED" -> EXPIRED;
                default -> throw OrderException.unavailable();
            };
            if (o.getStatus() == SHIPPING && next == RESERVED || o.getStatus() == CANCELLING && next == RESERVED)
                throw OrderException.unavailable();
            o.setReservationId(r.id()); o.setStatus(next); o.setUpdatedAt(clock.instant());
            if (next == SHIPPED && o.getShippedAt() == null) o.setShippedAt(clock.instant());
            if (next == CANCELLED && o.getCancelledAt() == null) o.setCancelledAt(clock.instant());
            return mapper.toResponse(o);
        });
    }

    @Override
    public void recover() {
        for (Long id : orders.pending(List.of(RESERVING, RESERVED, SHIPPING, CANCELLING), PageRequest.of(0, 100))) {
            try { resume(id); }
            catch (RuntimeException e) {
                log.warn("Buyurtma sinxronlash kechiktirildi, orderId={}", id);
                tx.executeWithoutResult(s -> { var o = locked(id); o.setUpdatedAt(clock.instant()); });
            }
        }
    }

    private Order locked(Long id) { return orders.lock(id).orElseThrow(() -> new OrderException(404, "Buyurtma topilmadi")); }
    private void requireOwner(Long companyId, Long actor) {
        var c = remote(() -> companies.ownership(companyId, actor));
        if (c == null || !companyId.equals(c.companyId())) throw OrderException.unavailable();
        if (!c.exists()) throw new OrderException(404, "Kompaniya topilmadi");
        if (!c.owner() || !c.active()) throw new OrderException(403, "Faol kompaniya egasigagina ruxsat beriladi");
    }
    private <T> T remote(Supplier<T> call) {
        try { return call.get(); }
        catch (FeignException e) {
            if (e.status() == 404) throw new OrderException(404, "Mahsulot yoki kompaniya topilmadi");
            throw OrderException.unavailable();
        }
    }
    private static OrderException conflict(String message) { return new OrderException(409, message); }
    private static String normalize(String value) { return value == null || value.isBlank() ? null : value.strip(); }
    private static String fingerprint(OrderCreateRequest r) {
        // Uzunlik prefiksi matn ichidagi separatorlardan keladigan to'qnashuvlarni yo'q qiladi.
        var canonical = new StringBuilder().append(r.companyId()).append(';');
        for (String text : new String[]{normalize(r.deliveryAddress()), normalize(r.note())})
            canonical.append(text == null ? -1 : text.length()).append(':').append(text).append(';');
        r.items().stream().sorted(Comparator.comparing(OrderCreateRequest.Item::productId)).forEach(i ->
                canonical.append(i.productId()).append(':').append(i.quantity().stripTrailingZeros().toPlainString()).append(';'));
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.toString().getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
