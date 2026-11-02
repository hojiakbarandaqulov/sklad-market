package com.example.service.impl;

import com.example.config.client.CompanyClient;
import com.example.dto.reservation.ReservationCreateRequest;
import com.example.dto.reservation.ReservationDTO;
import com.example.dto.reservation.OrderReservationRequest;
import com.example.entity.*;
import com.example.enums.*;
import com.example.exp.*;
import com.example.repository.*;
import com.example.service.ResourceBundleService;
import com.example.service.StockReservationService;
import com.example.utils.SpringSecurityUtil;
import feign.FeignException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional
public class StockReservationServiceImpl implements StockReservationService {
    private final StockReservationRepository reservations;
    private final StockReservationItemRepository items;
    private final WarehouseStockRepository stocks;
    private final WarehouseRepository warehouses;
    private final StockOperationRepository operations;
    private final CompanyClient companies;
    private final ResourceBundleService messages;
    private final Clock clock;
    private final EntityManager entityManager;
    private final InventoryProductRepository inventoryProducts;

    // Faqat ishonchli Order service-account uchun; oddiy BUYER/SELLER bu yo'ldan foydalana olmaydi.
    public ReservationDTO reserveOrder(Long orderId, OrderReservationRequest request) {
        requireOrderService();
        var requested = new TreeMap<Long, java.math.BigDecimal>();
        for (var item : request.items())
            if (requested.putIfAbsent(item.productId(), item.quantity()) != null)
                throw bad("reservation.items.duplicate", AppLanguage.UZ);
        var existing = reservations.lockOrder(request.companyId(), orderId);
        if (existing.isPresent()) return replayOrder(existing.get(), request, requested);

        // Ombor -> mahsulot -> qoldiq: arxivlash va parallel buyurtmalar ham shu qulflar bilan muvofiqlashadi.
        Set<Long> activeWarehouses = new HashSet<>();
        for (Long id : warehouses.companyIds(request.companyId())) {
            var warehouse = warehouses.lockPresent(id).orElse(null);
            if (warehouse == null) continue;
            entityManager.refresh(warehouse, LockModeType.PESSIMISTIC_WRITE);
            if (!Boolean.TRUE.equals(warehouse.getDeleted()) && warehouse.getStatus() == WarehouseStatus.ACTIVE)
                activeWarehouses.add(id);
        }
        existing = reservations.lockOrder(request.companyId(), orderId);
        if (existing.isPresent()) return replayOrder(existing.get(), request, requested);
        Instant expires = request.expiresAt().truncatedTo(ChronoUnit.MICROS);
        if (!expires.isAfter(clock.instant())) throw bad("reservation.expiry.invalid", AppLanguage.UZ);
        if (activeWarehouses.isEmpty()) throw conflict("reservation.stock.insufficient", AppLanguage.UZ);

        var tracked = inventoryProducts.findByCompanyIdAndCatalogCardIdInAndDeletedFalse(request.companyId(), requested.keySet())
                .stream().sorted(Comparator.comparing(InventoryProduct::getId)).toList();
        Set<Long> marketIds = new HashSet<>();
        List<Long> inventoryIds = new ArrayList<>();
        for (var product : tracked) {
            var locked = inventoryProducts.lockPresent(product.getId()).orElseThrow(() -> missing("control.product.missing", AppLanguage.UZ));
            entityManager.refresh(locked, LockModeType.PESSIMISTIC_WRITE);
            if (Boolean.TRUE.equals(locked.getDeleted()) || locked.getStatus() != InventoryProductStatus.ACTIVE
                    || !request.companyId().equals(locked.getCompanyId()) || !marketIds.add(locked.getCatalogCardId()))
                throw conflict("reservation.stock.inactive", AppLanguage.UZ);
            inventoryIds.add(locked.getId());
        }
        if (!marketIds.equals(requested.keySet())) throw conflict("reservation.stock.insufficient", AppLanguage.UZ);
        var remaining = new TreeMap<>(requested);
        var allocation = new LinkedHashMap<WarehouseStock, java.math.BigDecimal>();
        // Eng kichik qoldiq ID'sidan boshlab deterministik taqsimlash. Xaridor ombor ID'sini bilmaydi.
        for (var candidate : stocks.forProducts(inventoryIds).stream().sorted(Comparator.comparing(WarehouseStock::getId)).toList()) {
            if (!activeWarehouses.contains(candidate.getWarehouse().getId())) continue;
            var stock = lockStock(candidate.getId(), AppLanguage.UZ);
            Long productId = stock.getInventoryProduct().getCatalogCardId();
            var needed = remaining.get(productId);
            if (needed == null || needed.signum() <= 0 || stock.getAvailable().signum() <= 0) continue;
            var quantity = needed.min(stock.getAvailable());
            allocation.put(stock, quantity); remaining.put(productId, needed.subtract(quantity));
        }
        if (remaining.values().stream().anyMatch(q -> q.signum() > 0)) throw conflict("reservation.stock.insufficient", AppLanguage.UZ);
        var reservation = new StockReservation();
        reservation.setCompanyId(request.companyId()); reservation.setOrderId(orderId);
        reservation.setIdempotencyKey("order:" + orderId); reservation.setExpiresAt(expires);
        reservation.setReason("Buyurtma #" + orderId); reservation.setCreatedBy(request.buyerId()); reservation.setModifiedBy(request.buyerId());
        reservations.saveAndFlush(reservation);
        var allocated = new ArrayList<StockReservationItem>();
        allocation.forEach((stock, quantity) -> {
            stock.setReserved(stock.getReserved().add(quantity)); stock.setModifiedBy(request.buyerId());
            var item = new StockReservationItem(); item.setStock(stock); item.setReservation(reservation); item.setQuantity(quantity);
            item.setCreatedBy(request.buyerId()); item.setModifiedBy(request.buyerId()); allocated.add(items.save(item));
        });
        return dto(reservation, allocated);
    }

    private ReservationDTO replayOrder(StockReservation reservation, OrderReservationRequest request,
            Map<Long, java.math.BigDecimal> requested) {
        var allocated = items.forReservation(reservation.getId());
        var original = new TreeMap<Long, java.math.BigDecimal>();
        allocated.forEach(i -> original.merge(i.getStock().getInventoryProduct().getCatalogCardId(), i.getQuantity(), java.math.BigDecimal::add));
        boolean same = Objects.equals(reservation.getCreatedBy(), request.buyerId())
                && reservation.getExpiresAt().equals(request.expiresAt().truncatedTo(ChronoUnit.MICROS))
                && original.keySet().equals(requested.keySet())
                && original.entrySet().stream().allMatch(e -> e.getValue().compareTo(requested.get(e.getKey())) == 0);
        if (!same) throw conflict("reservation.key.conflict", AppLanguage.UZ);
        expireIfDue(reservation, allocated, null, AppLanguage.UZ);
        return dto(reservation, allocated);
    }

    public ReservationDTO orderReservation(Long companyId, Long orderId, String action) {
        requireOrderService();
        var reservation = reservations.lockOrder(companyId, orderId).orElseThrow(() -> missing("reservation.missing", AppLanguage.UZ));
        entityManager.refresh(reservation, LockModeType.PESSIMISTIC_WRITE);
        var allocated = items.forReservation(reservation.getId());
        expireIfDue(reservation, allocated, null, AppLanguage.UZ);
        if ("COMMIT".equals(action)) {
            if (reservation.getStatus() == ReservationStatus.ACTIVE) finish(reservation, allocated, ReservationStatus.COMMITTED, null, AppLanguage.UZ);
            // Yakuniy haqiqiy holat qaytariladi: Order ham shu holat bilan sinxronlashadi.
        } else if ("RELEASE".equals(action)) {
            if (reservation.getStatus() == ReservationStatus.ACTIVE) finish(reservation, allocated, ReservationStatus.RELEASED, null, AppLanguage.UZ);
        } else if (!"GET".equals(action)) throw bad("reservation.request.invalid", AppLanguage.UZ);
        return dto(reservation, allocated);
    }

    private void requireOrderService() {
        if (!SpringSecurityUtil.hasRole("CONTROL_ORDER_WRITE")) throw new AccessDeniedException("Order service-account talab qilinadi");
    }

    @Override
    public ReservationDTO create(ReservationCreateRequest request, AppLanguage language) {
        Long actor = authorize(request.companyId(), language);
        String key = request.idempotencyKey().strip();
        if (key.isEmpty()) throw bad("reservation.request.invalid", language);
        var requestedItems = request.items().stream()
                .sorted(Comparator.comparing(ReservationCreateRequest.Item::warehouseId)
                        .thenComparing(ReservationCreateRequest.Item::inventoryProductId)).toList();
        Set<String> positions = new HashSet<>();
        for (var item : requestedItems) {
            if (!positions.add(item.warehouseId() + ":" + item.inventoryProductId()))
                throw bad("reservation.items.duplicate", language);
        }

        var existing = reservations.findByCompanyIdAndIdempotencyKey(request.companyId(), key);
        if (existing.isPresent()) return replay(existing.get().getId(), request, requestedItems, language);

        // Bir xil so'rovlar va omborni arxivlash bir vaqtda qoldiqni buzmaydi.
        for (Long warehouseId : requestedItems.stream().map(ReservationCreateRequest.Item::warehouseId).distinct().toList()) {
            Warehouse warehouse = warehouses.lockPresent(warehouseId)
                    .orElseThrow(() -> missing("warehouse.not.found", language));
            if (!Objects.equals(warehouse.getCompanyId(), request.companyId()))
                throw new AccessDeniedException(messages.getMessage("auth.access.denied", language));
            if (warehouse.getStatus() != WarehouseStatus.ACTIVE)
                throw conflict("reservation.stock.inactive", language);
        }
        // Parallel kelgan aynan bir xil so'rov ombor qulfini kutgan bo'lishi mumkin.
        existing = reservations.findByCompanyIdAndIdempotencyKey(request.companyId(), key);
        if (existing.isPresent()) return replay(existing.get().getId(), request, requestedItems, language);
        Instant expiresAt = request.expiresAt().truncatedTo(ChronoUnit.MICROS);
        if (!expiresAt.isAfter(clock.instant())) throw bad("reservation.expiry.invalid", language);

        StockReservation reservation = new StockReservation();
        reservation.setCompanyId(request.companyId());
        reservation.setIdempotencyKey(key);
        reservation.setExpiresAt(expiresAt);
        reservation.setReason(normalizeReason(request.reason()));
        reservation.setCreatedBy(actor);
        reservation.setModifiedBy(actor);
        // orderId majburiy emas: hozir rezerv buyurtmasiz ham ishlaydi.
        reservations.saveAndFlush(reservation);

        List<StockReservationItem> allocated = new ArrayList<>();
        // Qoldiqlarni barcha amallarda bir xil ID tartibida qulflaymiz.
        Map<Long, ReservationCreateRequest.Item> byStockId = new TreeMap<>();
        for (var requested : requestedItems) {
            Long stockId = stocks.positionId(requested.warehouseId(), requested.inventoryProductId())
                    .orElseThrow(() -> missing("reservation.stock.missing", language));
            byStockId.put(stockId, requested);
        }
        List<StockReservationItem> pending = new ArrayList<>();
        for (var position : byStockId.entrySet()) {
            var requested = position.getValue();
            WarehouseStock stock = lockStock(position.getKey(), language);
            if (!Objects.equals(stock.getInventoryProduct().getCompanyId(), request.companyId())
                    || Boolean.TRUE.equals(stock.getInventoryProduct().getDeleted())
                    || stock.getInventoryProduct().getStatus() != InventoryProductStatus.ACTIVE)
                throw conflict("reservation.stock.inactive", language);
            if (stock.getAvailable().compareTo(requested.quantity()) < 0)
                throw conflict("reservation.stock.insufficient", language);
            StockReservationItem item = new StockReservationItem();
            item.setReservation(reservation);
            item.setStock(stock);
            item.setQuantity(requested.quantity());
            item.setCreatedBy(actor);
            item.setModifiedBy(actor);
            pending.add(item);
        }
        for (var item : pending) {
            WarehouseStock stock = item.getStock();
            stock.setReserved(stock.getReserved().add(item.getQuantity()));
            stock.setModifiedBy(actor);
            allocated.add(items.save(item));
        }
        return dto(reservation, allocated);
    }

    @Override
    public ReservationDTO get(Long id, AppLanguage language) {
        StockReservation reservation = owned(id, language);
        var allocated = items.forReservation(id);
        expireIfDue(reservation, allocated, SpringSecurityUtil.getProfileId(), language);
        return dto(reservation, allocated);
    }

    @Override
    public ReservationDTO release(Long id, AppLanguage language) {
        StockReservation reservation = owned(id, language);
        var allocated = items.forReservation(id);
        Long actor = SpringSecurityUtil.getProfileId();
        expireIfDue(reservation, allocated, actor, language);
        if (reservation.getStatus() == ReservationStatus.RELEASED || reservation.getStatus() == ReservationStatus.EXPIRED)
            return dto(reservation, allocated);
        if (reservation.getStatus() != ReservationStatus.ACTIVE)
            throw conflict("reservation.state.conflict", language);
        finish(reservation, allocated, ReservationStatus.RELEASED, actor, language);
        return dto(reservation, allocated);
    }

    @Override
    public ReservationDTO commit(Long id, AppLanguage language) {
        StockReservation reservation = owned(id, language);
        var allocated = items.forReservation(id);
        Long actor = SpringSecurityUtil.getProfileId();
        // EXPIRED holatini commit qilib bo'lmaydi; bo'shatish tranzaksiyasi saqlanishi uchun holatni qaytaramiz.
        expireIfDue(reservation, allocated, actor, language);
        if (reservation.getStatus() == ReservationStatus.COMMITTED || reservation.getStatus() == ReservationStatus.EXPIRED)
            return dto(reservation, allocated);
        if (reservation.getStatus() != ReservationStatus.ACTIVE)
            throw conflict("reservation.state.conflict", language);
        finish(reservation, allocated, ReservationStatus.COMMITTED, actor, language);
        return dto(reservation, allocated);
    }

    @Override
    public void expire(Long id) {
        reservations.lockPresent(id).ifPresent(reservation -> {
            // Fon vazifasi token talab qilmaydi; yangi foydalanuvchi nomidan amal yozilmaydi.
            expireIfDue(reservation, items.forReservation(id), null, AppLanguage.UZ);
        });
    }

    private ReservationDTO replay(Long id, ReservationCreateRequest request,
                                  List<ReservationCreateRequest.Item> requested, AppLanguage language) {
        StockReservation reservation = reservations.lockPresent(id)
                .orElseThrow(() -> missing("reservation.missing", language));
        entityManager.refresh(reservation, LockModeType.PESSIMISTIC_WRITE);
        var allocated = items.forReservation(id);
        var original = allocated.stream().sorted(Comparator
                .comparing((StockReservationItem i) -> i.getStock().getWarehouse().getId())
                .thenComparing(i -> i.getStock().getInventoryProduct().getId())).toList();
        boolean same = !Boolean.TRUE.equals(reservation.getDeleted())
                && reservation.getExpiresAt().equals(request.expiresAt().truncatedTo(ChronoUnit.MICROS))
                && Objects.equals(reservation.getReason(), normalizeReason(request.reason()))
                && original.size() == requested.size();
        for (int index = 0; same && index < original.size(); index++) {
            var old = original.get(index);
            var next = requested.get(index);
            same = old.getStock().getWarehouse().getId().equals(next.warehouseId())
                    && old.getStock().getInventoryProduct().getId().equals(next.inventoryProductId())
                    && old.getQuantity().compareTo(next.quantity()) == 0;
        }
        if (!same) throw conflict("reservation.key.conflict", language);
        expireIfDue(reservation, allocated, SpringSecurityUtil.getProfileId(), language);
        return dto(reservation, allocated);
    }

    private void expireIfDue(StockReservation reservation, List<StockReservationItem> allocated,
                             Long actor, AppLanguage language) {
        if (reservation.getStatus() == ReservationStatus.ACTIVE && !reservation.getExpiresAt().isAfter(clock.instant()))
            finish(reservation, allocated, ReservationStatus.EXPIRED, actor, language);
    }

    private void finish(StockReservation reservation, List<StockReservationItem> allocated,
                        ReservationStatus status, Long actor, AppLanguage language) {
        if (allocated.isEmpty()) throw conflict("reservation.balance.invalid", language);
        for (var item : allocated) {
            WarehouseStock stock = lockStock(item.getStock().getId(), language);
            item.setStock(stock);
            if (stock.getReserved().compareTo(item.getQuantity()) < 0 || stock.getOnHand().compareTo(item.getQuantity()) < 0)
                throw conflict("reservation.balance.invalid", language);
            var before = stock.getOnHand();
            stock.setReserved(stock.getReserved().subtract(item.getQuantity()));
            stock.setModifiedBy(actor);
            if (status == ReservationStatus.COMMITTED) {
                stock.setOnHand(before.subtract(item.getQuantity()));
                StockOperation operation = new StockOperation();
                operation.setStock(stock);
                operation.setCompanyId(reservation.getCompanyId());
                operation.setReservedSnapshot(stock.getReserved());
                operation.setType(StockOperationType.SALE);
                operation.setQuantityBefore(before);
                operation.setQuantityChange(item.getQuantity().negate());
                operation.setQuantityAfter(stock.getOnHand());
                operation.setOrderId(reservation.getOrderId());
                operation.setReservationId(reservation.getId());
                operation.setReason(reservation.getReason() == null ? "Rezerv bo'yicha chiqim" : reservation.getReason());
                operation.setCreatedBy(actor);
                operation.setModifiedBy(actor);
                operations.save(operation);
            }
        }
        reservation.setStatus(status);
        reservation.setModifiedBy(actor);
        if (status == ReservationStatus.COMMITTED) reservation.setCommittedAt(clock.instant());
        else reservation.setReleasedAt(clock.instant());
    }

    private StockReservation owned(Long id, AppLanguage language) {
        if (id == null || id <= 0) throw bad("reservation.request.invalid", language);
        StockReservation reservation = reservations.lockPresent(id)
                .orElseThrow(() -> missing("reservation.missing", language));
        authorize(reservation.getCompanyId(), language);
        return reservation;
    }

    private Long authorize(Long companyId, AppLanguage language) {
        Long actor = SpringSecurityUtil.getProfileId();
        if (actor == null) throw new AccessDeniedException(messages.getMessage("auth.profile.missing", language));
        try {
            var company = companies.ownershipCheck(companyId, actor);
            if (company == null || !Objects.equals(company.getCompanyId(), companyId))
                throw new AppServiceUnavailableException(messages.getMessage("warehouse.company.service.unavailable", language));
            if (!company.isExists()) throw missing("warehouse.company.not.found", language);
            if (!company.isOwner() || !company.isActive())
                throw new AccessDeniedException(messages.getMessage("auth.access.denied", language));
        } catch (FeignException exception) {
            if (exception.status() == 401 || exception.status() == 403)
                throw new AccessDeniedException(messages.getMessage("auth.access.denied", language));
            throw new AppServiceUnavailableException(messages.getMessage("warehouse.company.service.unavailable", language));
        }
        return actor;
    }

    private ReservationDTO dto(StockReservation reservation, List<StockReservationItem> allocated) {
        return new ReservationDTO(reservation.getId(), reservation.getCompanyId(), reservation.getOrderId(),
                reservation.getIdempotencyKey(), reservation.getStatus(), reservation.getReason(), reservation.getExpiresAt(),
                reservation.getCommittedAt(), reservation.getReleasedAt(), reservation.getCreatedDate(),
                allocated.stream().map(item -> {
                    var stock = item.getStock();
                    var product = stock.getInventoryProduct();
                    return new ReservationDTO.Item(stock.getId(), stock.getWarehouse().getId(), product.getId(),
                            product.getCatalogCardId(), product.getSku(), item.getQuantity(), stock.getOnHand(),
                            stock.getReserved(), stock.getAvailable());
                }).toList());
    }

    private String normalizeReason(String reason) { return reason == null || reason.isBlank() ? null : reason.strip(); }
    private WarehouseStock lockStock(Long id, AppLanguage language) {
        WarehouseStock stock = stocks.lockPresent(id)
                .orElseThrow(() -> missing("reservation.stock.missing", language));
        // Idempotent qaytishda entity oldin yuklangan bo'lishi mumkin: qulfdan keyingi haqiqiy qoldiq olinadi.
        entityManager.refresh(stock, LockModeType.PESSIMISTIC_WRITE);
        return stock;
    }
    private AppBadException bad(String key, AppLanguage language) { return new AppBadException(messages.getMessage(key, language)); }
    private AppConflictException conflict(String key, AppLanguage language) { return new AppConflictException(messages.getMessage(key, language)); }
    private AppNotFoundException missing(String key, AppLanguage language) { return new AppNotFoundException(messages.getMessage(key, language)); }
}
