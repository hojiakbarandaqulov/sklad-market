package com.example.service.impl;

import com.example.config.client.CompanyClient;
import com.example.dto.reservation.ReservationCreateRequest;
import com.example.dto.reservation.ReservationDTO;
import com.example.dto.reservation.OrderReservationRequest;
import com.example.dto.reservation.OrderSaleRequest;
import com.example.dto.internal.CompanyOwnershipResponse;
import java.math.BigDecimal;
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
    public ReservationDTO reserveOrder(Long orderId, OrderReservationRequest request, AppLanguage language) {
        requireOrderService(language);
        Map<Long, BigDecimal> requested = new TreeMap<Long, BigDecimal>();
        for (OrderReservationRequest.Item item : request.items())
            if (requested.putIfAbsent(item.productId(), item.quantity()) != null)
                throw new AppBadException(messages.getMessage("reservation.items.duplicate", language));
        Optional<StockReservation> existing = reservations.lockOrder(request.companyId(), orderId);
        if (existing.isPresent()) return replayOrder(existing.get(), request, requested, language);

        // Ombor -> mahsulot -> qoldiq: arxivlash va parallel buyurtmalar ham shu qulflar bilan muvofiqlashadi.
        Set<Long> activeWarehouses = new HashSet<>();
        for (Long id : warehouses.companyIds(request.companyId())) {
            Warehouse warehouse = warehouses.lockPresent(id).orElse(null);
            if (warehouse == null) continue;
            entityManager.refresh(warehouse, LockModeType.PESSIMISTIC_WRITE);
            if (!Boolean.TRUE.equals(warehouse.getDeleted()) && warehouse.getStatus() == WarehouseStatus.ACTIVE)
                activeWarehouses.add(id);
        }
        existing = reservations.lockOrder(request.companyId(), orderId);
        if (existing.isPresent()) return replayOrder(existing.get(), request, requested, language);
        Instant expires = request.expiresAt().truncatedTo(ChronoUnit.MICROS);
        if (!expires.isAfter(clock.instant())) throw new AppBadException(messages.getMessage("reservation.expiry.invalid", language));
        if (activeWarehouses.isEmpty()) throw new AppConflictException(messages.getMessage("reservation.stock.insufficient", language));

        List<InventoryProduct> tracked = inventoryProducts.findByCompanyIdAndCatalogCardIdInAndDeletedFalse(request.companyId(), requested.keySet())
                .stream().sorted(Comparator.comparing(InventoryProduct::getId)).toList();
        Set<Long> marketIds = new HashSet<>();
        List<Long> inventoryIds = new ArrayList<>();
        for (InventoryProduct product : tracked) {
            InventoryProduct locked = inventoryProducts.lockPresent(product.getId()).orElseThrow(() -> new AppNotFoundException(messages.getMessage("control.product.missing", language)));
            entityManager.refresh(locked, LockModeType.PESSIMISTIC_WRITE);
            if (Boolean.TRUE.equals(locked.getDeleted()) || locked.getStatus() != InventoryProductStatus.ACTIVE
                    || !request.companyId().equals(locked.getCompanyId()) || !marketIds.add(locked.getCatalogCardId()))
                throw new AppConflictException(messages.getMessage("reservation.stock.inactive", language));
            inventoryIds.add(locked.getId());
        }
        if (!marketIds.equals(requested.keySet())) throw new AppConflictException(messages.getMessage("reservation.stock.insufficient", language));
        Map<Long, BigDecimal> remaining = new TreeMap<>(requested);
        Map<WarehouseStock, BigDecimal> allocation = new LinkedHashMap<WarehouseStock, BigDecimal>();
        // Eng kichik qoldiq ID'sidan boshlab deterministik taqsimlash. Xaridor ombor ID'sini bilmaydi.
        for (WarehouseStock candidate : stocks.forProducts(inventoryIds).stream().sorted(Comparator.comparing(WarehouseStock::getId)).toList()) {
            if (!activeWarehouses.contains(candidate.getWarehouse().getId())) continue;
            WarehouseStock stock = lockStock(candidate.getId(), language);
            Long productId = stock.getInventoryProduct().getCatalogCardId();
            BigDecimal needed = remaining.get(productId);
            if (needed == null || needed.signum() <= 0 || stock.getAvailable().signum() <= 0) continue;
            BigDecimal quantity = needed.min(stock.getAvailable());
            allocation.put(stock, quantity); remaining.put(productId, needed.subtract(quantity));
        }
        if (remaining.values().stream().anyMatch(q -> q.signum() > 0)) throw new AppConflictException(messages.getMessage("reservation.stock.insufficient", language));
        StockReservation reservation = new StockReservation();
        reservation.setCompanyId(request.companyId()); reservation.setOrderId(orderId);
        reservation.setIdempotencyKey("order:" + orderId); reservation.setExpiresAt(expires);
        reservation.setReason("Buyurtma #" + orderId); reservation.setCreatedBy(request.buyerId()); reservation.setModifiedBy(request.buyerId());
        reservations.saveAndFlush(reservation);
        List<StockReservationItem> allocated = new ArrayList<StockReservationItem>();
        allocation.forEach((stock, quantity) -> {
            stock.setReserved(stock.getReserved().add(quantity)); stock.setModifiedBy(request.buyerId());
            StockReservationItem item = new StockReservationItem(); item.setStock(stock); item.setReservation(reservation); item.setQuantity(quantity);
            item.setCreatedBy(request.buyerId()); item.setModifiedBy(request.buyerId()); allocated.add(items.save(item));
        });
        return dto(reservation, allocated);
    }

    private ReservationDTO replayOrder(StockReservation reservation, OrderReservationRequest request,
            Map<Long, BigDecimal> requested, AppLanguage language) {
        List<StockReservationItem> allocated = items.forReservation(reservation.getId());
        Map<Long, BigDecimal> original = new TreeMap<Long, BigDecimal>();
        allocated.forEach(i -> original.merge(i.getStock().getInventoryProduct().getCatalogCardId(), i.getQuantity(), BigDecimal::add));
        boolean same = Objects.equals(reservation.getCreatedBy(), request.buyerId())
                && reservation.getExpiresAt().equals(request.expiresAt().truncatedTo(ChronoUnit.MICROS))
                && original.keySet().equals(requested.keySet())
                && original.entrySet().stream().allMatch(e -> e.getValue().compareTo(requested.get(e.getKey())) == 0);
        if (!same) throw new AppConflictException(messages.getMessage("reservation.key.conflict", language));
        expireIfDue(reservation, allocated, null, language);
        return dto(reservation, allocated);
    }

    public ReservationDTO orderReservation(Long companyId, Long orderId, String action, AppLanguage language) {
        requireOrderService(language);
        StockReservation reservation = reservations.lockOrder(companyId, orderId).orElseThrow(() -> new AppNotFoundException(messages.getMessage("reservation.missing", language)));
        entityManager.refresh(reservation, LockModeType.PESSIMISTIC_WRITE);
        List<StockReservationItem> allocated = items.forReservation(reservation.getId());
        expireIfDue(reservation, allocated, null, language);
        if ("COMMIT".equals(action)) {
            if (reservation.getStatus() == ReservationStatus.ACTIVE) finish(reservation, allocated, ReservationStatus.COMMITTED, null, language);
            // Yakuniy haqiqiy holat qaytariladi: Order ham shu holat bilan sinxronlashadi.
        } else if ("RELEASE".equals(action)) {
            if (reservation.getStatus() == ReservationStatus.ACTIVE) finish(reservation, allocated, ReservationStatus.RELEASED, null, language);
        } else if (!"GET".equals(action)) throw new AppBadException(messages.getMessage("reservation.request.invalid", language));
        return dto(reservation, allocated);
    }

    private void requireOrderService(AppLanguage language) {
        if (!SpringSecurityUtil.hasRole("CONTROL_ORDER_WRITE")) throw new AccessDeniedException(messages.getMessage("auth.access.denied", language));
    }

    public java.util.List<java.util.Map<String,Object>> orderAvailability(Long companyId, Long orderId, AppLanguage language) {
        requireOrderService(language);
        StockReservation reservation = reservations.lockOrder(companyId, orderId)
                .orElseThrow(() -> new AppNotFoundException(messages.getMessage("reservation.missing", language)));
        List<StockReservationItem> allocated = items.forReservation(reservation.getId());
        Map<Long, BigDecimal> own = new TreeMap<Long, BigDecimal>();
        allocated.forEach(i -> own.merge(i.getStock().getInventoryProduct().getCatalogCardId(),
                reservation.getStatus() == ReservationStatus.ACTIVE ? i.getQuantity() : BigDecimal.ZERO,
                BigDecimal::add));
        List<InventoryProduct> tracked = inventoryProducts.findByCompanyIdAndCatalogCardIdInAndDeletedFalse(companyId, own.keySet());
        Map<Long, BigDecimal> free = new TreeMap<Long, BigDecimal>();
        for (WarehouseStock stock : stocks.forProducts(tracked.stream().map(InventoryProduct::getId).toList())) {
            if (stock.getWarehouse().getStatus() == WarehouseStatus.ACTIVE
                    && !Boolean.TRUE.equals(stock.getWarehouse().getDeleted())
                    && stock.getInventoryProduct().getStatus() == InventoryProductStatus.ACTIVE) {
                free.merge(stock.getInventoryProduct().getCatalogCardId(), stock.getAvailable(), BigDecimal::add);
            }
        }
        return own.entrySet().stream().map(e -> java.util.Map.<String,Object>of("productId", e.getKey(),
                "reservedQuantity", e.getValue(), "availableQuantity", free.getOrDefault(e.getKey(), BigDecimal.ZERO),
                "maxSaleQuantity", free.getOrDefault(e.getKey(), BigDecimal.ZERO).add(e.getValue()))).toList();
    }

    public ReservationDTO completeOrder(Long orderId, OrderSaleRequest request, AppLanguage language) {
        requireOrderService(language);
        Set<Long> active = new HashSet<Long>();
        for (Long id : warehouses.companyIds(request.companyId())) {
            Warehouse warehouse = warehouses.lockPresent(id).orElse(null);
            if (warehouse != null) {
                entityManager.refresh(warehouse, LockModeType.PESSIMISTIC_WRITE);
                if (warehouse.getStatus() == WarehouseStatus.ACTIVE) active.add(id);
            }
        }
        Map<Long, BigDecimal> requested = new TreeMap<Long, BigDecimal>();
        for (OrderSaleRequest.Item item : request.items()) {
            if (requested.putIfAbsent(item.productId(), item.quantity()) != null)
                throw new AppBadException(messages.getMessage("reservation.items.duplicate", language));
        }
        List<InventoryProduct> tracked = inventoryProducts.findByCompanyIdAndCatalogCardIdInAndDeletedFalse(request.companyId(), requested.keySet());
        StockReservation reservation = reservations.lockOrder(request.companyId(), orderId)
                .orElseThrow(() -> new AppNotFoundException(messages.getMessage("reservation.missing", language)));
        entityManager.refresh(reservation, LockModeType.PESSIMISTIC_WRITE);
        List<StockReservationItem> allocated = items.forReservation(reservation.getId());
        Map<Long, BigDecimal> original = new TreeMap<Long, BigDecimal>();
        allocated.forEach(i -> original.merge(i.getStock().getInventoryProduct().getCatalogCardId(), i.getQuantity(), BigDecimal::add));
        if (!original.keySet().equals(requested.keySet())) throw new AppConflictException(messages.getMessage("reservation.key.conflict", language));
        if (reservation.getStatus() == ReservationStatus.COMMITTED) {
            if (original.entrySet().stream().anyMatch(e -> e.getValue().compareTo(requested.get(e.getKey())) != 0))
                throw new AppConflictException(messages.getMessage("reservation.key.conflict", language));
            return dto(reservation, allocated);
        }
        expireIfDue(reservation, allocated, null, language);
        if (reservation.getStatus() != ReservationStatus.ACTIVE) return dto(reservation, allocated);
        for (InventoryProduct row : tracked.stream().sorted(Comparator.comparing(InventoryProduct::getId)).toList()) {
            InventoryProduct locked = inventoryProducts.lockPresent(row.getId()).orElseThrow(() -> new AppNotFoundException(messages.getMessage("control.product.missing", language)));
            entityManager.refresh(locked, LockModeType.PESSIMISTIC_WRITE);
            if (locked.getStatus() != InventoryProductStatus.ACTIVE) throw new AppConflictException(messages.getMessage("reservation.stock.inactive", language));
        }
        // Release this order's old allocation and allocate the final quantities in ONE transaction.
        // Any shortage rolls back the release as well; other orders' reserves are never consumed.
        for (StockReservationItem item : allocated) {
            WarehouseStock stock = lockStock(item.getStock().getId(), language);
            if (stock.getReserved().compareTo(item.getQuantity()) < 0) throw new AppConflictException(messages.getMessage("reservation.balance.invalid", language));
            stock.setReserved(stock.getReserved().subtract(item.getQuantity()));
        }
        items.deleteAll(allocated);
        items.flush();
        Map<Long, BigDecimal> remaining = new TreeMap<>(requested);
        List<StockReservationItem> finalItems = new ArrayList<StockReservationItem>();
        for (WarehouseStock candidate : stocks.forProducts(tracked.stream().map(InventoryProduct::getId).toList())) {
            if (!active.contains(candidate.getWarehouse().getId())) continue;
            WarehouseStock stock = lockStock(candidate.getId(), language);
            Long productId = stock.getInventoryProduct().getCatalogCardId();
            BigDecimal needed = remaining.get(productId);
            if (needed == null || needed.signum() <= 0 || stock.getAvailable().signum() <= 0) continue;
            BigDecimal quantity = needed.min(stock.getAvailable());
            stock.setReserved(stock.getReserved().add(quantity));
            StockReservationItem item = new StockReservationItem();
            item.setReservation(reservation); item.setStock(stock); item.setQuantity(quantity);
            item.setCreatedBy(reservation.getCreatedBy()); item.setModifiedBy(reservation.getCreatedBy());
            finalItems.add(items.save(item)); remaining.put(productId, needed.subtract(quantity));
        }
        if (remaining.values().stream().anyMatch(q -> q.signum() > 0)) throw new AppConflictException(messages.getMessage("reservation.stock.insufficient", language));
        stocks.flush();
        finish(reservation, finalItems, ReservationStatus.COMMITTED, reservation.getCreatedBy(), language);
        return dto(reservation, finalItems);
    }

    @Override
    public ReservationDTO create(ReservationCreateRequest request, AppLanguage language) {
        Long actor = authorize(request.companyId(), language);
        String key = request.idempotencyKey().strip();
        if (key.isEmpty()) throw new AppBadException(messages.getMessage("reservation.request.invalid", language));
        List<ReservationCreateRequest.Item> requestedItems = request.items().stream()
                .sorted(Comparator.comparing(ReservationCreateRequest.Item::warehouseId)
                        .thenComparing(ReservationCreateRequest.Item::inventoryProductId)).toList();
        Set<String> positions = new HashSet<>();
        for (ReservationCreateRequest.Item item : requestedItems) {
            if (!positions.add(item.warehouseId() + ":" + item.inventoryProductId()))
                throw new AppBadException(messages.getMessage("reservation.items.duplicate", language));
        }

        Optional<StockReservation> existing = reservations.findByCompanyIdAndIdempotencyKey(request.companyId(), key);
        if (existing.isPresent()) return replay(existing.get().getId(), request, requestedItems, language);

        // Bir xil so'rovlar va omborni arxivlash bir vaqtda qoldiqni buzmaydi.
        for (Long warehouseId : requestedItems.stream().map(ReservationCreateRequest.Item::warehouseId).distinct().toList()) {
            Warehouse warehouse = warehouses.lockPresent(warehouseId)
                    .orElseThrow(() -> new AppNotFoundException(messages.getMessage("warehouse.not.found", language)));
            if (!Objects.equals(warehouse.getCompanyId(), request.companyId()))
                throw new AccessDeniedException(messages.getMessage("auth.access.denied", language));
            if (warehouse.getStatus() != WarehouseStatus.ACTIVE)
                throw new AppConflictException(messages.getMessage("reservation.stock.inactive", language));
        }
        // Parallel kelgan aynan bir xil so'rov ombor qulfini kutgan bo'lishi mumkin.
        existing = reservations.findByCompanyIdAndIdempotencyKey(request.companyId(), key);
        if (existing.isPresent()) return replay(existing.get().getId(), request, requestedItems, language);
        Instant expiresAt = request.expiresAt().truncatedTo(ChronoUnit.MICROS);
        if (!expiresAt.isAfter(clock.instant())) throw new AppBadException(messages.getMessage("reservation.expiry.invalid", language));

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
        for (ReservationCreateRequest.Item requested : requestedItems) {
            Long stockId = stocks.positionId(requested.warehouseId(), requested.inventoryProductId())
                    .orElseThrow(() -> new AppNotFoundException(messages.getMessage("reservation.stock.missing", language)));
            byStockId.put(stockId, requested);
        }
        List<StockReservationItem> pending = new ArrayList<>();
        for (Map.Entry<Long, ReservationCreateRequest.Item> position : byStockId.entrySet()) {
            ReservationCreateRequest.Item requested = position.getValue();
            WarehouseStock stock = lockStock(position.getKey(), language);
            if (!Objects.equals(stock.getInventoryProduct().getCompanyId(), request.companyId())
                    || Boolean.TRUE.equals(stock.getInventoryProduct().getDeleted())
                    || stock.getInventoryProduct().getStatus() != InventoryProductStatus.ACTIVE)
                throw new AppConflictException(messages.getMessage("reservation.stock.inactive", language));
            if (stock.getAvailable().compareTo(requested.quantity()) < 0)
                throw new AppConflictException(messages.getMessage("reservation.stock.insufficient", language));
            StockReservationItem item = new StockReservationItem();
            item.setReservation(reservation);
            item.setStock(stock);
            item.setQuantity(requested.quantity());
            item.setCreatedBy(actor);
            item.setModifiedBy(actor);
            pending.add(item);
        }
        for (StockReservationItem item : pending) {
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
        List<StockReservationItem> allocated = items.forReservation(id);
        expireIfDue(reservation, allocated, SpringSecurityUtil.getProfileId(), language);
        return dto(reservation, allocated);
    }

    @Override
    public ReservationDTO release(Long id, AppLanguage language) {
        StockReservation reservation = owned(id, language);
        List<StockReservationItem> allocated = items.forReservation(id);
        Long actor = SpringSecurityUtil.getProfileId();
        expireIfDue(reservation, allocated, actor, language);
        if (reservation.getStatus() == ReservationStatus.RELEASED || reservation.getStatus() == ReservationStatus.EXPIRED)
            return dto(reservation, allocated);
        if (reservation.getStatus() != ReservationStatus.ACTIVE)
            throw new AppConflictException(messages.getMessage("reservation.state.conflict", language));
        finish(reservation, allocated, ReservationStatus.RELEASED, actor, language);
        return dto(reservation, allocated);
    }

    @Override
    public ReservationDTO commit(Long id, AppLanguage language) {
        StockReservation reservation = owned(id, language);
        List<StockReservationItem> allocated = items.forReservation(id);
        Long actor = SpringSecurityUtil.getProfileId();
        // EXPIRED holatini commit qilib bo'lmaydi; bo'shatish tranzaksiyasi saqlanishi uchun holatni qaytaramiz.
        expireIfDue(reservation, allocated, actor, language);
        if (reservation.getStatus() == ReservationStatus.COMMITTED || reservation.getStatus() == ReservationStatus.EXPIRED)
            return dto(reservation, allocated);
        if (reservation.getStatus() != ReservationStatus.ACTIVE)
            throw new AppConflictException(messages.getMessage("reservation.state.conflict", language));
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
                .orElseThrow(() -> new AppNotFoundException(messages.getMessage("reservation.missing", language)));
        entityManager.refresh(reservation, LockModeType.PESSIMISTIC_WRITE);
        List<StockReservationItem> allocated = items.forReservation(id);
        List<StockReservationItem> original = allocated.stream().sorted(Comparator
                .comparing((StockReservationItem i) -> i.getStock().getWarehouse().getId())
                .thenComparing(i -> i.getStock().getInventoryProduct().getId())).toList();
        boolean same = !Boolean.TRUE.equals(reservation.getDeleted())
                && reservation.getExpiresAt().equals(request.expiresAt().truncatedTo(ChronoUnit.MICROS))
                && Objects.equals(reservation.getReason(), normalizeReason(request.reason()))
                && original.size() == requested.size();
        for (int index = 0; same && index < original.size(); index++) {
            StockReservationItem old = original.get(index);
            ReservationCreateRequest.Item next = requested.get(index);
            same = old.getStock().getWarehouse().getId().equals(next.warehouseId())
                    && old.getStock().getInventoryProduct().getId().equals(next.inventoryProductId())
                    && old.getQuantity().compareTo(next.quantity()) == 0;
        }
        if (!same) throw new AppConflictException(messages.getMessage("reservation.key.conflict", language));
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
        if (allocated.isEmpty()) throw new AppConflictException(messages.getMessage("reservation.balance.invalid", language));
        for (StockReservationItem item : allocated) {
            WarehouseStock stock = lockStock(item.getStock().getId(), language);
            item.setStock(stock);
            if (stock.getReserved().compareTo(item.getQuantity()) < 0 || stock.getOnHand().compareTo(item.getQuantity()) < 0)
                throw new AppConflictException(messages.getMessage("reservation.balance.invalid", language));
            BigDecimal before = stock.getOnHand();
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
        if (id == null || id <= 0) throw new AppBadException(messages.getMessage("reservation.request.invalid", language));
        StockReservation reservation = reservations.lockPresent(id)
                .orElseThrow(() -> new AppNotFoundException(messages.getMessage("reservation.missing", language)));
        authorize(reservation.getCompanyId(), language);
        return reservation;
    }

    private Long authorize(Long companyId, AppLanguage language) {
        Long actor = SpringSecurityUtil.getProfileId();
        if (actor == null) throw new AccessDeniedException(messages.getMessage("auth.profile.missing", language));
        try {
            CompanyOwnershipResponse company = companies.ownershipCheck(companyId, actor);
            if (company == null || !Objects.equals(company.getCompanyId(), companyId))
                throw new AppServiceUnavailableException(messages.getMessage("warehouse.company.service.unavailable", language));
            if (!company.isExists()) throw new AppNotFoundException(messages.getMessage("warehouse.company.not.found", language));
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
                    WarehouseStock stock = item.getStock();
                    InventoryProduct product = stock.getInventoryProduct();
                    return new ReservationDTO.Item(stock.getId(), stock.getWarehouse().getId(), product.getId(),
                            product.getCatalogCardId(), product.getSku(), item.getQuantity(), stock.getOnHand(),
                            stock.getReserved(), stock.getAvailable());
                }).toList());
    }

    private String normalizeReason(String reason) { return reason == null || reason.isBlank() ? null : reason.strip(); }
    private WarehouseStock lockStock(Long id, AppLanguage language) {
        WarehouseStock stock = stocks.lockPresent(id)
                .orElseThrow(() -> new AppNotFoundException(messages.getMessage("reservation.stock.missing", language)));
        // Idempotent qaytishda entity oldin yuklangan bo'lishi mumkin: qulfdan keyingi haqiqiy qoldiq olinadi.
        entityManager.refresh(stock, LockModeType.PESSIMISTIC_WRITE);
        return stock;
    }
}
