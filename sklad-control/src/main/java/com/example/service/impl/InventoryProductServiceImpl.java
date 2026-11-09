package com.example.service.impl;

import com.example.config.client.ProductClient;
import com.example.config.client.CompanyClient;
import com.example.dto.ApiResponse;
import com.example.dto.internal.CompanyOwnershipResponse;
import com.example.dto.warehouse.WarehouseDTO;
import com.example.dto.product.*;
import com.example.entity.*;
import com.example.enums.*;
import com.example.exp.*;
import com.example.repository.*;
import com.example.service.*;
import com.example.utils.SpringSecurityUtil;
import com.fasterxml.jackson.databind.JsonNode;
import feign.FeignException;
import jakarta.persistence.criteria.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Supplier;

/**
 * Mahsulot kartasi product-service'da, qoldiq va kirim esa shu servisda saqlanadi.
 * Ikki servisda bitta transaction yo'q: qayta create bir xil kompaniya va SKU orqali davom ettiriladi.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InventoryProductServiceImpl implements InventoryProductService {
    private final InventoryProductRepository products;
    private final WarehouseStockRepository stocks;
    private final StockOperationRepository operations;
    private final WarehouseRepository warehouses;
    private final WarehouseService warehouseService;
    private final ProductClient client;
    private final CompanyClient companies;
    private final ResourceBundleService messages;

    @Override
    public boolean hasCatalogLink(Long companyId, Long productId, AppLanguage language) {
        if (!SpringSecurityUtil.hasRole("ADMIN") && !SpringSecurityUtil.hasRole("SUPER_ADMIN")) {
            authorizeCompany(companyId, language);
        }
        return !products.findByCard(companyId, productId).isEmpty();
    }

    @Override
    @Transactional
    public InventoryProductDTO create(InventoryProductCreateRequest request, AppLanguage language) {
        Long actor = actor(language);
        // Egalik tekshiruvi mavjud WarehouseService orqali qayta ishlatiladi.
        warehouseService.get(request.getWarehouseId(), language);
        Warehouse warehouse = warehouses.lockPresent(request.getWarehouseId()).orElseThrow(() -> new AppNotFoundException(messages.getMessage("control.product.missing", language)));

        if (!warehouse.getCompanyId().equals(request.getCompanyId()))
            throw new AppBadException(messages.getMessage("control.product.company", language));
        if (warehouse.getStatus() != WarehouseStatus.ACTIVE)
            throw new AppConflictException(messages.getMessage("control.warehouse.inactive", language));
        if ((request.getProductId() == null) == (request.getProduct() == null))
            throw new AppBadException(messages.getMessage("control.product.card.required", language));
        if (request.getProduct() != null && !request.getCompanyId().equals(request.getProduct().getCompanyId()))
            throw new AppBadException(messages.getMessage("control.product.company", language));
        String sku = request.getSku().strip();
        if (sku.isEmpty()) throw new AppBadException(messages.getMessage("control.product.sku", language));
        InventoryProduct product = products.findBySku(request.getCompanyId(), sku).orElse(null);
        JsonNode card;
        if (product != null) {
            product = products.lockPresent(product.getId()).orElseThrow(() -> new AppNotFoundException(messages.getMessage("control.product.missing", language)));
            if (product.getStatus() != InventoryProductStatus.ACTIVE)
                throw new AppConflictException(messages.getMessage("control.product.archived", language));
            if (request.getProductId() != null && !product.getCatalogCardId().equals(request.getProductId()))
                throw new AppConflictException(messages.getMessage("control.product.sku", language));
            card = card(product, language);
        } else {
            if (request.getProductId() != null) {
                card = oneCard(request.getCompanyId(), request.getProductId(), language);
            } else {
                card = remote("create", () -> client.create(request.getProduct(), sku, false, language.name()), language);
                validateCard("create", card, request.getCompanyId(), null, language);
            }
            Long cardId = card.get("id").asLong();
            if (!products.findByCard(request.getCompanyId(), cardId).isEmpty())
                throw new AppConflictException(messages.getMessage("control.product.duplicate", language));
            product = new InventoryProduct();
            product.setCompanyId(request.getCompanyId());
            // Hozir catalogCardId mavjud Product.id ni bildiradi; nusxa karta yaratmaymiz.
            product.setCatalogCardId(cardId);
            product.setSku(sku);
            product.setStatus(InventoryProductStatus.ACTIVE);
            product.setCreatedBy(actor);
            product.setModifiedBy(actor);
            product = products.saveAndFlush(product);
        }
        Long productId = product.getId();
        List<WarehouseStock> current = stocks.forProducts(List.of(productId));
        boolean alreadyAdded = current.stream().anyMatch(s -> s.getWarehouse().getId().equals(warehouse.getId()));
        if (!alreadyAdded) {
            WarehouseStock stock = new WarehouseStock();
            stock.setWarehouse(warehouse);
            stock.setInventoryProduct(product);
            stock.setOnHand(request.getOpeningQuantity());
            stock.setReserved(BigDecimal.ZERO);
            stock.setMinimumStock(request.getMinimumStock());
            stock.setCreatedBy(actor);
            stock.setModifiedBy(actor);
            stock = stocks.saveAndFlush(stock);
            // Takroriy create qoldiqni yana qo'shmaydi va ikkinchi kirim yozmaydi.
            if (request.getOpeningQuantity().signum() > 0) {
                StockOperation operation = new StockOperation();
                operation.setStock(stock);
                operation.setType(StockOperationType.OPENING_BALANCE);
                operation.setQuantityBefore(BigDecimal.ZERO);
                operation.setQuantityChange(request.getOpeningQuantity());
                operation.setQuantityAfter(request.getOpeningQuantity());
                operation.setReason("Boshlang'ich qoldiq");
                operation.setCreatedBy(actor);
                operation.setModifiedBy(actor);
                operations.saveAndFlush(operation);
            }
        }
        Boolean showInMarket = request.getShowInMarket();
        // Qoldiq yozilgach, faqat sotuvchi yuborgan ko'rinishni qo'llaymiz.
        if (showInMarket != null) {
            card = changeVisibility(product, showInMarket, language);
        }
        return dto(product, card, stocks.forProducts(List.of(productId)));
    }

    @Override
    public PageImpl<InventoryProductDTO> list(InventoryProductFilter filter, AppLanguage language) {
        authorizeCompany(filter.getCompanyId(), language);
        if (filter.getWarehouseId() != null) {
            WarehouseDTO warehouse = warehouseService.get(filter.getWarehouseId(), language);
            if (!warehouse.getCompanyId().equals(filter.getCompanyId()))
                throw new AppBadException(messages.getMessage("control.product.company", language));
        }
        Specification<InventoryProduct> spec = (root, query, cb) -> cb.and(cb.equal(root.get("companyId"), filter.getCompanyId()), cb.isFalse(root.get("deleted")));
        if (filter.getStatus() != null)
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), filter.getStatus()));
        if (filter.getSaleStatus() != null) {
            List<Long> ids = remote("matching-sale", () -> client.matchingBySaleStatus(filter.getCompanyId(),
                    filter.getSaleStatus(), language.name()), language);
            // Filtr sahifalashdan oldin qo'llanadi: totalElements/totalPages ham to'g'ri qoladi.
            spec = spec.and((root, query, cb) -> ids.isEmpty()
                    ? cb.disjunction() : root.get("catalogCardId").in(ids));
        }
        if (filter.getCategoryId() != null) {
            List<Long> ids = remote("matching-category", () -> client.matching(filter.getCompanyId(), null, filter.getCategoryId(), language.name()), language);
            spec = spec.and((root, query, cb) -> root.get("catalogCardId").in(ids));
        }
        if (StringUtils.hasText(filter.getQ())) {
            String search = filter.getQ().strip().toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
            List<Long> ids = remote("matching-name", () -> client.matching(filter.getCompanyId(), filter.getQ(), null, language.name()), language);
            spec = spec.and((root, query, cb) -> cb.or(root.get("catalogCardId").in(ids), cb.like(cb.lower(root.get("sku")), "%" + search + "%", '\\')));
        }
        if (filter.getWarehouseId() != null || !"ALL".equals(filter.getStockState())) {
            spec = spec.and((root, query, cb) -> {
                Subquery<Long> sq = query.subquery(Long.class);
                Root<WarehouseStock> stock = sq.from(WarehouseStock.class);
                List<Predicate> conditions = new ArrayList<>();
                conditions.add(cb.equal(stock.get("inventoryProduct").get("id"), root.get("id")));
                conditions.add(cb.isFalse(stock.get("deleted")));
                if (filter.getWarehouseId() != null)
                    conditions.add(cb.equal(stock.get("warehouse").get("id"), filter.getWarehouseId()));
                Expression<BigDecimal> available = cb.diff(stock.<BigDecimal>get("onHand"), stock.<BigDecimal>get("reserved"));
                switch (filter.getStockState()) {
                    case "LOW" -> {
                        conditions.add(cb.greaterThan(available, BigDecimal.ZERO));
                        conditions.add(cb.lessThanOrEqualTo(available, stock.get("minimumStock")));
                    }
                    case "OUT" -> conditions.add(cb.equal(available, BigDecimal.ZERO));
                    case "AVAILABLE" -> conditions.add(cb.greaterThan(available, BigDecimal.ZERO));
                    default -> {
                    }
                }
                sq.select(stock.get("id")).where(conditions.toArray(Predicate[]::new));
                return cb.exists(sq);
            });
        }
        Pageable pageable = PageRequest.of(filter.getPage() - 1, filter.getPerPage(), Sort.by(Sort.Direction.DESC, "id"));
        Page<InventoryProduct> page = products.findAll(spec, pageable);
        if (page.isEmpty()) return new PageImpl<>(List.of(), pageable, page.getTotalElements());
        List<InventoryProduct> rows = page.getContent();
        List<JsonNode> cards = remote("cards", () -> client.cards(filter.getCompanyId(), rows.stream().map(InventoryProduct::getCatalogCardId).distinct().toList(), language.name()), language);
        Map<Long, JsonNode> byId = new HashMap<>();
        for (JsonNode card : cards) {
            validateCard("cards", card, filter.getCompanyId(), null, language);
            byId.put(card.get("id").asLong(), card);
        }
        List<WarehouseStock> balances = stocks.forProducts(rows.stream().map(InventoryProduct::getId).toList());
        List<InventoryProductDTO> result = rows.stream().map(p -> {
            JsonNode card = byId.get(p.getCatalogCardId());
            return readableDto(p, card, balances.stream().filter(s -> s.getInventoryProduct().getId().equals(p.getId())).filter(s -> filter.getWarehouseId() == null || filter.getWarehouseId().equals(s.getWarehouse().getId())).toList(), language);
        }).toList();
        return new PageImpl<>(result, pageable, page.getTotalElements());
    }

    @Override
    public InventoryProductDTO get(Long id, AppLanguage language) {
        InventoryProduct product = owned(id, false, language);
        return readableDto(product, optionalCard(product, language), stocks.forProducts(List.of(id)), language);
    }

    @Override
    @Transactional
    public InventoryProductDTO update(Long id, InventoryProductUpdateRequest request, AppLanguage language) {
        InventoryProduct product = owned(id, true, language);
        if (product.getStatus() != InventoryProductStatus.ACTIVE)
            throw new AppConflictException(messages.getMessage("control.product.archived", language));
        if (request.getProduct() == null && request.getShowInMarket() == null)
            throw new AppBadException(messages.getMessage("warehouse.patch.empty", language));
        JsonNode card = card(product, language);
        if (request.getProduct() != null) {
            if (!product.getCompanyId().equals(request.getProduct().getCompanyId()))
                throw new AppBadException(messages.getMessage("control.product.company", language));
            card = remote("update", () -> client.update(product.getCatalogCardId(), request.getProduct(), language.name()), language);
            validateCard("update", card, product.getCompanyId(), product.getCatalogCardId(), language);
        }
        Boolean showInMarket = request.getShowInMarket();
        if (showInMarket != null) {
            card = changeVisibility(product, showInMarket, language);
        }
        product.setModifiedBy(actor(language));
        products.saveAndFlush(product);
        return dto(product, card, stocks.forProducts(List.of(id)));
    }

    @Override
    @Transactional
    public InventoryProductDTO archive(Long id, AppLanguage language) {
        InventoryProduct product = owned(id, true, language);
        if (stocks.hasBalance(id))
            throw new AppConflictException(messages.getMessage("control.product.balance", language));
        JsonNode card = optionalCard(product, language);
        if (card != null) {
            card = remote("visibility", () -> client.visibility(product.getCatalogCardId(), false, language.name()), language);
            validateCard("visibility", card, product.getCompanyId(), product.getCatalogCardId(), language);
        }
        product.setStatus(InventoryProductStatus.ARCHIVED);
        product.setModifiedBy(actor(language));
        products.saveAndFlush(product);
        return readableDto(product, card, stocks.forProducts(List.of(id)), language);
    }

    @Override
    @Transactional
    public InventoryProductDTO restore(Long id, AppLanguage language) {
        InventoryProduct product = owned(id, true, language);
        // Tiklash faqat ombor hisobini faollashtiradi. Marketga chiqarish alohida PATCH bilan bajariladi.
        JsonNode card = card(product, language);
        product.setStatus(InventoryProductStatus.ACTIVE);
        product.setModifiedBy(actor(language));
        products.saveAndFlush(product);
        return dto(product, card, stocks.forProducts(List.of(id)));
    }

    private InventoryProduct owned(Long id, boolean lock, AppLanguage language) {
        actor(language);
        if (id == null || id <= 0) throw new AppBadException(messages.getMessage("control.product.id", language));
        InventoryProduct product = (lock ? products.lockPresent(id) : products.findPresent(id)).orElseThrow(() -> new AppNotFoundException(messages.getMessage("control.product.missing", language)));
        authorizeCompany(product.getCompanyId(), language);
        return product;
    }

    private void authorizeCompany(Long companyId, AppLanguage language) {
        Long userId = actor(language);
        try {
            CompanyOwnershipResponse company = companies.ownershipCheck(companyId, userId);
            if (company == null || !Objects.equals(company.getCompanyId(), companyId))
                throw new AppServiceUnavailableException(messages.getMessage("warehouse.company.service.unavailable", language));
            if (!company.isExists())
                throw new AppNotFoundException(messages.getMessage("warehouse.company.not.found", language));
            if (!company.isOwner() || !company.isActive())
                throw new AccessDeniedException(messages.getMessage("auth.access.denied", language));
        } catch (FeignException exception) {
            if (exception.status() == 401 || exception.status() == 403)
                throw new AccessDeniedException(messages.getMessage("auth.access.denied", language));
            throw new AppServiceUnavailableException(messages.getMessage("warehouse.company.service.unavailable", language));
        }
    }

    private Long actor(AppLanguage language) {
        Long id = SpringSecurityUtil.getProfileId();
        if (id == null) throw new AccessDeniedException(messages.getMessage("auth.profile.missing", language));
        return id;
    }

    private JsonNode card(InventoryProduct product, AppLanguage language) {
        return oneCard(product.getCompanyId(), product.getCatalogCardId(), language);
    }

    private JsonNode optionalCard(InventoryProduct product, AppLanguage language) {
        List<JsonNode> cards = remote("cards", () -> client.cards(product.getCompanyId(),
                List.of(product.getCatalogCardId()), language.name()), language);
        if (cards.isEmpty()) return null;
        if (cards.size() != 1) throw unavailable("cards", "invalid card count", language);
        validateCard("cards", cards.get(0), product.getCompanyId(), product.getCatalogCardId(), language);
        return cards.get(0);
    }

    private InventoryProductDTO readableDto(InventoryProduct product, JsonNode card,
                                            List<WarehouseStock> balances, AppLanguage language) {
        InventoryProductDTO result = dto(product, card, balances);
        if (card == null) {
            // Retain the inventory ID, SKU and balances; never restore or publish a missing card.
            result.setCatalogCardUnavailableReason(messages.getMessage("control.product.card.unavailable", language));
        }
        return result;
    }

    private JsonNode oneCard(Long companyId, Long id, AppLanguage language) {
        List<JsonNode> result = remote("cards", () -> client.cards(companyId, List.of(id), language.name()), language);
        if (result.size() != 1)
            throw new AppBadException(messages.getMessage("control.product.card.missing", language));
        validateCard("cards", result.get(0), companyId, id, language);
        return result.get(0);
    }

    private JsonNode changeVisibility(InventoryProduct product, boolean visible, AppLanguage language) {
        Long cardId = product.getCatalogCardId();
        JsonNode card = remote("visibility", () -> client.visibility(cardId, visible, language.name()), language);
        validateCard("visibility", card, product.getCompanyId(), cardId, language);

        if (visible) {
            boolean pending = "PENDING".equals(card.path("status").asText());
            JsonNode active = card.path("isActive");
            if (!pending || !active.isBoolean() || !active.booleanValue()) {
                throw unavailable("visibility", "product was not submitted for moderation", language);
            }
        }
        return card;
    }

    private void validateCard(String operation, JsonNode card, Long companyId, Long id, AppLanguage language) {
        if (card == null || !card.path("id").canConvertToLong() || card.path("id").asLong() <= 0
                || !card.path("companyId").canConvertToLong() || card.path("companyId").asLong() != companyId
                || (id != null && card.path("id").asLong() != id)) {
            throw unavailable(operation, "invalid product card identity", language);
        }
    }

    private <T> T remote(String operation, Supplier<ApiResponse<T>> action, AppLanguage language) {
        try {
            ApiResponse<T> response = action.get();
            if (response == null) throw unavailable(operation, "empty response", language);
            if (!Boolean.TRUE.equals(response.getSuccess()))
                throw unavailable(operation, "unsuccessful response", language);
            if (response.getData() == null) throw unavailable(operation, "missing response data", language);
            return response.getData();
        } catch (FeignException e) {
            if (e.status() == 401 || e.status() == 403)
                throw new AccessDeniedException(messages.getMessage("auth.access.denied", language));
            if (e.status() == 400 || e.status() == 404 || e.status() == 422)
                throw new AppBadException(messages.getMessage("control.product.remote.invalid", language));
            if (e.status() == 409)
                throw new AppConflictException(messages.getMessage("control.product.retry", language));
            Throwable cause = e.getCause() == null ? e : e.getCause();
            // Feign xabarida so'rov URL'i yoki javob tanasi bo'lishi mumkin. Faqat status va xato turini yozamiz.
            String status = e.status() < 0 ? "no HTTP response" : String.valueOf(e.status());
            throw unavailable(operation, "upstream_status=" + status + ", error=" + cause.getClass().getSimpleName(), language);
        }
    }

    private InventoryProductDTO dto(InventoryProduct product, JsonNode card, List<WarehouseStock> balances) {
        return InventoryProductDTO.builder().id(product.getId()).companyId(product.getCompanyId()).productId(product.getCatalogCardId()).sku(product.getSku()).status(product.getStatus()).product(card).catalogCardAvailable(card != null).stocks(balances.stream().map(s -> InventoryProductDTO.StockDTO.builder().warehouseId(s.getWarehouse().getId()).warehouseName(s.getWarehouse().getName()).onHand(s.getOnHand()).reserved(s.getReserved()).available(s.getAvailable()).minimumStock(s.getMinimumStock()).build()).toList()).build();
    }


    private AppServiceUnavailableException unavailable(String operation, String reason, AppLanguage language) {
        return new AppServiceUnavailableException(messages.getMessage("control.product.unavailable", language),
                "service=product-service, operation=" + operation + ", " + reason);
    }
}
