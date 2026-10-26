package com.example.service.impl;

import com.example.config.client.ProductClient;
import com.example.config.client.CompanyClient;
import com.example.dto.ApiResponse;
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
    @Transactional
    public InventoryProductDTO create(InventoryProductCreateRequest request, AppLanguage language) {
        Long actor = actor(language);
        // Egalik tekshiruvi mavjud WarehouseService orqali qayta ishlatiladi.
        warehouseService.get(request.getWarehouseId(), language);
        Warehouse warehouse = warehouses.lockPresent(request.getWarehouseId()).orElseThrow(() -> missing(language));
        if (!warehouse.getCompanyId().equals(request.getCompanyId())) throw bad("control.product.company", language);
        if (warehouse.getStatus() != WarehouseStatus.ACTIVE) throw conflict("control.warehouse.inactive", language);
        if ((request.getProductId() == null) == (request.getProduct() == null))
            throw bad("control.product.card.required", language);
        if (request.getProduct() != null && !request.getCompanyId().equals(request.getProduct().getCompanyId()))
            throw bad("control.product.company", language);
        String sku = request.getSku().strip();
        if (sku.isEmpty()) throw bad("control.product.sku", language);
        InventoryProduct product = products.findBySku(request.getCompanyId(), sku).orElse(null);
        JsonNode card;
        if (product != null) {
            product = products.lockPresent(product.getId()).orElseThrow(() -> missing(language));
            if (product.getStatus() != InventoryProductStatus.ACTIVE)
                throw conflict("control.product.archived", language);
            if (request.getProductId() != null && !product.getCatalogCardId().equals(request.getProductId()))
                throw conflict("control.product.sku", language);
            card = card(product, language);
        } else {
            if (request.getProductId() != null) {
                card = oneCard(request.getCompanyId(), request.getProductId(), language);
            } else {
                card = remote(() -> client.create(request.getProduct(), sku, false, language.name()), language);
                validateCard(card, request.getCompanyId(), null, language);
            }
            Long cardId = card.get("id").asLong();
            if (!products.findByCard(request.getCompanyId(), cardId).isEmpty())
                throw conflict("control.product.duplicate", language);
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
        Long remoteId = product.getCatalogCardId();
        // Yakuniy ko'rinishni faqat lokal yozuvlar tayyor bo'lgandan keyin o'zgartiramiz.
        card = remote(() -> client.visibility(remoteId, request.getShowInMarket(), language.name()), language);
        validateCard(card, product.getCompanyId(), remoteId, language);
        return dto(product, card, stocks.forProducts(List.of(productId)));
    }

    @Override
    public PageImpl<InventoryProductDTO> list(InventoryProductFilter filter, AppLanguage language) {
        authorizeCompany(filter.getCompanyId(), language);
        if (filter.getWarehouseId() != null) {
            var warehouse = warehouseService.get(filter.getWarehouseId(), language);
            if (!warehouse.getCompanyId().equals(filter.getCompanyId())) throw bad("control.product.company", language);
        }
        Specification<InventoryProduct> spec = (root, query, cb) -> cb.and(cb.equal(root.get("companyId"), filter.getCompanyId()), cb.isFalse(root.get("deleted")));
        if (filter.getStatus() != null)
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), filter.getStatus()));
        if (filter.getCategoryId() != null) {
            List<Long> ids = remote(() -> client.matching(filter.getCompanyId(), null, filter.getCategoryId(), language.name()), language);
            spec = spec.and((root, query, cb) -> root.get("catalogCardId").in(ids));
        }
        if (StringUtils.hasText(filter.getQ())) {
            String search = filter.getQ().strip().toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
            List<Long> ids = remote(() -> client.matching(filter.getCompanyId(), filter.getQ(), null, language.name()), language);
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
        List<JsonNode> cards = remote(() -> client.cards(filter.getCompanyId(), rows.stream().map(InventoryProduct::getCatalogCardId).distinct().toList(), language.name()), language);
        Map<Long, JsonNode> byId = new HashMap<>();
        for (JsonNode card : cards) {
            validateCard(card, filter.getCompanyId(), null, language);
            byId.put(card.get("id").asLong(), card);
        }
        List<WarehouseStock> balances = stocks.forProducts(rows.stream().map(InventoryProduct::getId).toList());
        List<InventoryProductDTO> result = rows.stream().map(p -> {
            JsonNode card = byId.get(p.getCatalogCardId());
            if (card == null) throw unavailable(language);
            return dto(p, card, balances.stream().filter(s -> s.getInventoryProduct().getId().equals(p.getId())).filter(s -> filter.getWarehouseId() == null || filter.getWarehouseId().equals(s.getWarehouse().getId())).toList());
        }).toList();
        return new PageImpl<>(result, pageable, page.getTotalElements());
    }

    @Override
    public InventoryProductDTO get(Long id, AppLanguage language) {
        InventoryProduct product = owned(id, false, language);
        return dto(product, card(product, language), stocks.forProducts(List.of(id)));
    }

    @Override
    @Transactional
    public InventoryProductDTO update(Long id, InventoryProductUpdateRequest request, AppLanguage language) {
        InventoryProduct product = owned(id, true, language);
        if (product.getStatus() != InventoryProductStatus.ACTIVE) throw conflict("control.product.archived", language);
        if (request.getProduct() == null && request.getShowInMarket() == null)
            throw bad("warehouse.patch.empty", language);
        JsonNode card = card(product, language);
        if (request.getProduct() != null) {
            if (!product.getCompanyId().equals(request.getProduct().getCompanyId()))
                throw bad("control.product.company", language);
            card = remote(() -> client.update(product.getCatalogCardId(), request.getProduct(), language.name()), language);
        }
        if (request.getShowInMarket() != null)
            card = remote(() -> client.visibility(product.getCatalogCardId(), request.getShowInMarket(), language.name()), language);
        validateCard(card, product.getCompanyId(), product.getCatalogCardId(), language);
        product.setModifiedBy(actor(language));
        products.saveAndFlush(product);
        return dto(product, card, stocks.forProducts(List.of(id)));
    }

    @Override
    @Transactional
    public InventoryProductDTO archive(Long id, AppLanguage language) {
        InventoryProduct product = owned(id, true, language);
        if (stocks.hasBalance(id)) throw conflict("control.product.balance", language);
        JsonNode card = remote(() -> client.visibility(product.getCatalogCardId(), false, language.name()), language);
        validateCard(card, product.getCompanyId(), product.getCatalogCardId(), language);
        product.setStatus(InventoryProductStatus.ARCHIVED);
        product.setModifiedBy(actor(language));
        products.saveAndFlush(product);
        return dto(product, card, stocks.forProducts(List.of(id)));
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
        if (id == null || id <= 0) throw bad("control.product.id", language);
        InventoryProduct product = (lock ? products.lockPresent(id) : products.findPresent(id)).orElseThrow(() -> missing(language));
        authorizeCompany(product.getCompanyId(), language);
        return product;
    }

    private void authorizeCompany(Long companyId, AppLanguage language) {
        Long userId = actor(language);
        try {
            var company = companies.ownershipCheck(companyId, userId);
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

    private JsonNode oneCard(Long companyId, Long id, AppLanguage language) {
        List<JsonNode> result = remote(() -> client.cards(companyId, List.of(id), language.name()), language);
        if (result.size() != 1) throw bad("control.product.card.missing", language);
        validateCard(result.get(0), companyId, id, language);
        return result.get(0);
    }

    private void validateCard(JsonNode card, Long companyId, Long id, AppLanguage language) {
        if (card == null || !card.path("id").canConvertToLong() || card.path("id").asLong() <= 0 || !card.path("companyId").canConvertToLong() || card.path("companyId").asLong() != companyId || (id != null && card.path("id").asLong() != id))
            throw unavailable(language);
    }

    private <T> T remote(Supplier<ApiResponse<T>> action, AppLanguage language) {
        try {
            ApiResponse<T> response = action.get();
            if (response == null || !Boolean.TRUE.equals(response.getSuccess()) || response.getData() == null)
                throw unavailable(language);
            return response.getData();
        } catch (FeignException e) {
            if (e.status() == 401 || e.status() == 403)
                throw new AccessDeniedException(messages.getMessage("auth.access.denied", language));
            if (e.status() == 400 || e.status() == 404 || e.status() == 422)
                throw bad("control.product.remote.invalid", language);
            if (e.status() == 409) throw conflict("control.product.retry", language);
            throw unavailable(language);
        }
    }

    private InventoryProductDTO dto(InventoryProduct product, JsonNode card, List<WarehouseStock> balances) {
        return InventoryProductDTO.builder().id(product.getId()).companyId(product.getCompanyId()).productId(product.getCatalogCardId()).sku(product.getSku()).status(product.getStatus()).product(card).stocks(balances.stream().map(s -> InventoryProductDTO.StockDTO.builder().warehouseId(s.getWarehouse().getId()).warehouseName(s.getWarehouse().getName()).onHand(s.getOnHand()).reserved(s.getReserved()).available(s.getAvailable()).minimumStock(s.getMinimumStock()).build()).toList()).build();
    }

    private AppBadException bad(String key, AppLanguage language) {
        return new AppBadException(messages.getMessage(key, language));
    }

    private AppConflictException conflict(String key, AppLanguage language) {
        return new AppConflictException(messages.getMessage(key, language));
    }

    private AppNotFoundException missing(AppLanguage language) {
        return new AppNotFoundException(messages.getMessage("control.product.missing", language));
    }

    private AppServiceUnavailableException unavailable(AppLanguage language) {
        return new AppServiceUnavailableException(messages.getMessage("control.product.unavailable", language));
    }
}
