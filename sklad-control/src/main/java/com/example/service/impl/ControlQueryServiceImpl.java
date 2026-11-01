package com.example.service.impl;

import com.example.dto.availability.StockAvailabilityRequest;
import com.example.dto.availability.StockAvailabilityResponse;
import com.example.dto.filter.ControlListFilter;
import com.example.dto.reservation.ReservationDTO;
import com.example.dto.reservation.ReservationFilter;
import com.example.dto.stockOperation.StockOperationDTO;
import com.example.dto.stockOperation.StockOperationFilter;
import com.example.entity.StockOperation;
import com.example.entity.StockReservation;
import com.example.entity.StockReservationItem;
import com.example.entity.InventoryProduct;
import com.example.enums.AppLanguage;
import com.example.exp.AppNotFoundException;
import com.example.repository.*;
import com.example.service.CompanyAccessService;
import com.example.service.ControlQueryService;
import com.example.service.ResourceBundleService;
import com.example.utils.SpringSecurityUtil;
import jakarta.persistence.criteria.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ControlQueryServiceImpl implements ControlQueryService {
    private final StockOperationRepository operations;
    private final StockReservationRepository reservations;
    private final StockReservationItemRepository reservationItems;
    private final WarehouseStockRepository stocks;
    private final InventoryProductRepository products;
    private final WarehouseRepository warehouses;
    private final CompanyAccessService access;
    private final ResourceBundleService messages;

    @Override
    public PageImpl<StockOperationDTO> operations(StockOperationFilter filter, AppLanguage language) {
        authorizeFilter(filter, language);
        Specification<StockOperation> specification = (root, query, cb) -> {
            var stock = root.get("stock");
            var warehouse = stock.get("warehouse");
            var product = stock.get("inventoryProduct");
            List<Predicate> predicates = commonPredicates(root, cb, filter);
            // Eski operatsiyalarda companyId null: egasi doim ombor orqali aniqlanadi.
            predicates.add(cb.equal(warehouse.get("companyId"), filter.getCompanyId()));
            predicates.add(cb.equal(product.get("companyId"), filter.getCompanyId()));
            if (filter.getWarehouseId() != null)
                predicates.add(cb.equal(warehouse.get("id"), filter.getWarehouseId()));
            if (filter.getInventoryProductId() != null)
                predicates.add(cb.equal(product.get("id"), filter.getInventoryProductId()));
            if (filter.getProductId() != null)
                predicates.add(cb.equal(product.get("catalogCardId"), filter.getProductId()));
            if (filter.getType() != null)
                predicates.add(cb.equal(root.get("type"), filter.getType()));
            if (filter.getReservationId() != null)
                predicates.add(cb.equal(root.get("reservationId"), filter.getReservationId()));
            if (StringUtils.hasText(filter.getQ())) {
                String pattern = containsPattern(filter.getQ());
                predicates.add(cb.or(cb.like(cb.lower(product.get("sku")), pattern, '\\'),
                        cb.like(cb.lower(root.get("reason")), pattern, '\\')));
            }
            // Tarix saqlanadi: arxivlangan ombor va mahsulot amallari ham ko'rinadi.
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        var page = operations.findAll(specification, pageRequest(filter));
        return new PageImpl<>(page.getContent().stream().map(this::operationDTO).toList(),
                page.getPageable(), page.getTotalElements());
    }

    @Override
    public PageImpl<ReservationDTO> reservations(ReservationFilter filter, AppLanguage language) {
        authorizeFilter(filter, language);
        Specification<StockReservation> specification = (root, query, cb) -> {
            List<Predicate> predicates = commonPredicates(root, cb, filter);
            predicates.add(cb.equal(root.get("companyId"), filter.getCompanyId()));
            if (filter.getStatus() != null)
                predicates.add(cb.equal(root.get("status"), filter.getStatus()));
            if (filter.getExpiresFrom() != null)
                predicates.add(cb.greaterThanOrEqualTo(root.get("expiresAt"), filter.getExpiresFrom()));
            if (filter.getExpiresTo() != null)
                predicates.add(cb.lessThanOrEqualTo(root.get("expiresAt"), filter.getExpiresTo()));
            if (filter.getWarehouseId() != null || filter.getInventoryProductId() != null || filter.getProductId() != null)
                predicates.add(matchingItem(root, query, cb, filter, null));
            if (StringUtils.hasText(filter.getQ())) {
                String pattern = containsPattern(filter.getQ());
                predicates.add(cb.or(cb.like(cb.lower(root.get("reason")), pattern, '\\'),
                        cb.like(cb.lower(root.get("idempotencyKey")), pattern, '\\'),
                        matchingItem(root, query, cb, filter, pattern)));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        var page = reservations.findAll(specification, pageRequest(filter));
        if (page.isEmpty()) return new PageImpl<>(List.of(), page.getPageable(), page.getTotalElements());

        // Bitta rezervda ko'p pozitsiya bo'lsa ham, pagination uni bir marta sanaydi.
        List<Long> ids = page.getContent().stream().map(StockReservation::getId).toList();
        Map<Long, List<StockReservationItem>> byReservation = reservationItems.forReservations(ids).stream()
                .collect(Collectors.groupingBy(item -> item.getReservation().getId()));
        var result = page.getContent().stream()
                .map(reservation -> reservationDTO(reservation,
                        byReservation.getOrDefault(reservation.getId(), List.of())))
                .toList();
        return new PageImpl<>(result, page.getPageable(), page.getTotalElements());
    }

    @Override
    public List<StockAvailabilityResponse> availability(StockAvailabilityRequest request) {
        // Kompaniyalararo o'qish faqat maxsus servis roliga beriladi; oddiy seller egaligi tekshiriladi.
        if (!SpringSecurityUtil.hasRole("CONTROL_AVAILABILITY_READ"))
            access.requireOwner(request.companyId(), AppLanguage.UZ);
        List<Long> productIds = request.productIds().stream().distinct().toList();
        Set<Long> tracked = products.findByCompanyIdAndCatalogCardIdInAndDeletedFalse(request.companyId(), productIds)
                .stream().map(InventoryProduct::getCatalogCardId).collect(Collectors.toSet());
        Map<Long, BigDecimal> available = stocks.availabilityBalances(request.companyId(), productIds).stream()
                .collect(Collectors.toMap(WarehouseStockRepository.AvailabilityBalance::getProductId,
                        WarehouseStockRepository.AvailabilityBalance::getAvailableQuantity));
        // Natija so'rov tartibida; topilmagan, arxivlangan yoki nol qoldiqli karta inStock=false.
        return productIds.stream().map(productId -> {
            BigDecimal quantity = available.getOrDefault(productId, BigDecimal.ZERO);
            // Ikki SELECT orasida yangi pozitsiya yaratilsa ham musbat miqdor tracked=false bo'lmasin.
            return new StockAvailabilityResponse(request.companyId(), productId,
                    tracked.contains(productId) || available.containsKey(productId),
                    quantity, quantity.signum() > 0);
        }).toList();
    }

    private void authorizeFilter(ControlListFilter filter, AppLanguage language) {
        access.requireOwner(filter.getCompanyId(), language);
        if (filter.getWarehouseId() != null) {
            var warehouse = warehouses.findByIdAndDeletedFalse(filter.getWarehouseId())
                    .orElseThrow(() -> new AppNotFoundException(messages.getMessage("warehouse.not.found", language)));
            if (!Objects.equals(warehouse.getCompanyId(), filter.getCompanyId()))
                throw new AccessDeniedException(messages.getMessage("auth.access.denied", language));
        }
        if (filter.getInventoryProductId() != null) {
            var product = products.findPresent(filter.getInventoryProductId())
                    .orElseThrow(() -> new AppNotFoundException(messages.getMessage("control.product.missing", language)));
            if (!Objects.equals(product.getCompanyId(), filter.getCompanyId()))
                throw new AccessDeniedException(messages.getMessage("auth.access.denied", language));
        }
    }

    private List<Predicate> commonPredicates(Root<?> root, CriteriaBuilder cb, ControlListFilter filter) {
        List<Predicate> predicates = new ArrayList<>();
        predicates.add(cb.isFalse(root.get("deleted")));
        if (filter.getOrderId() != null)
            predicates.add(cb.equal(root.get("orderId"), filter.getOrderId()));
        if (filter.getCreatedBy() != null)
            predicates.add(cb.equal(root.get("createdBy"), filter.getCreatedBy()));
        if (filter.getDateFrom() != null)
            predicates.add(cb.greaterThanOrEqualTo(root.<LocalDateTime>get("createdDate"),
                    filter.getDateFrom().atStartOfDay()));
        if (filter.getDateTo() != null && !filter.getDateTo().equals(LocalDate.MAX))
            predicates.add(cb.lessThan(root.<LocalDateTime>get("createdDate"),
                    filter.getDateTo().plusDays(1).atStartOfDay()));
        return predicates;
    }

    private Predicate matchingItem(Root<StockReservation> root, CriteriaQuery<?> query, CriteriaBuilder cb,
                                   ReservationFilter filter, String skuPattern) {
        Subquery<Long> matching = query.subquery(Long.class);
        Root<StockReservationItem> item = matching.from(StockReservationItem.class);
        var stock = item.get("stock");
        var product = stock.get("inventoryProduct");
        var warehouse = stock.get("warehouse");
        List<Predicate> predicates = new ArrayList<>();
        predicates.add(cb.equal(item.get("reservation").get("id"), root.get("id")));
        predicates.add(cb.isFalse(item.get("deleted")));
        predicates.add(cb.equal(warehouse.get("companyId"), filter.getCompanyId()));
        predicates.add(cb.equal(product.get("companyId"), filter.getCompanyId()));
        if (filter.getWarehouseId() != null)
            predicates.add(cb.equal(warehouse.get("id"), filter.getWarehouseId()));
        if (filter.getInventoryProductId() != null)
            predicates.add(cb.equal(product.get("id"), filter.getInventoryProductId()));
        if (filter.getProductId() != null)
            predicates.add(cb.equal(product.get("catalogCardId"), filter.getProductId()));
        if (skuPattern != null) predicates.add(cb.like(cb.lower(product.get("sku")), skuPattern, '\\'));
        matching.select(item.get("id")).where(predicates.toArray(Predicate[]::new));
        return cb.exists(matching);
    }

    private PageRequest pageRequest(ControlListFilter filter) {
        return PageRequest.of(filter.getPage() - 1, filter.getPerPage(),
                Sort.by(Sort.Direction.DESC, "createdDate", "id"));
    }

    private String containsPattern(String text) {
        return "%" + text.strip().toLowerCase(Locale.ROOT).replace("\\", "\\\\")
                .replace("%", "\\%").replace("_", "\\_") + "%";
    }

    private StockOperationDTO operationDTO(StockOperation operation) {
        var stock = operation.getStock();
        var warehouse = stock.getWarehouse();
        var product = stock.getInventoryProduct();
        BigDecimal reserved = operation.getReservedSnapshot();
        return new StockOperationDTO(operation.getId(), warehouse.getCompanyId(), warehouse.getId(), warehouse.getName(),
                product.getId(), product.getCatalogCardId(), product.getSku(), operation.getType(),
                operation.getQuantityBefore(), operation.getQuantityChange(), operation.getQuantityAfter(),
                reserved, reserved == null ? null : operation.getQuantityAfter().subtract(reserved),
                operation.getReason(), operation.getOrderId(), operation.getReservationId(),
                operation.getCreatedBy(), operation.getCreatedDate());
    }

    private ReservationDTO reservationDTO(StockReservation reservation, List<StockReservationItem> items) {
        return new ReservationDTO(reservation.getId(), reservation.getCompanyId(), reservation.getOrderId(),
                reservation.getIdempotencyKey(), reservation.getStatus(), reservation.getReason(), reservation.getExpiresAt(),
                reservation.getCommittedAt(), reservation.getReleasedAt(), reservation.getCreatedDate(),
                items.stream().map(item -> {
                    var stock = item.getStock();
                    var product = stock.getInventoryProduct();
                    return new ReservationDTO.Item(stock.getId(), stock.getWarehouse().getId(), product.getId(),
                            product.getCatalogCardId(), product.getSku(), item.getQuantity(), stock.getOnHand(),
                            stock.getReserved(), stock.getAvailable());
                }).toList());
    }
}
