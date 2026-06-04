package org.example.service.impl;

import lombok.RequiredArgsConstructor;
import org.example.client.CompanyClient;
import org.example.client.dto.CompanyMapSummaryRequest;
import org.example.client.dto.CompanyMapSummaryResponse;
import org.example.dto.*;
import org.example.dto.banner.BannerResponse;
import org.example.dto.product.ProductDto;
import org.example.dto.product.ProductResponse;
import org.example.entity.Product;
import org.example.enums.AppLanguage;
import org.example.enums.Currency;
import org.example.enums.ProductModerationStatus;
import org.example.enums.SaleType;
import org.example.repository.ProductRepository;
import org.example.service.BannerService;
import org.example.service.CatalogService;
import org.example.service.ProductService;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CatalogServiceImpl implements CatalogService {
    private final ProductRepository productRepository;
    private final ProductServiceImpl productService;
    private final BannerService bannerService;
    private final ModelMapper modelMapper;
    private final CompanyClient companyClient;

    @Override
    public PagedResponse<ProductResponse> getCatalog(String q, String category, Long regionId, String currency, int page, int perPage, AppLanguage language) {
        return queryProducts(q, category, regionId, currency, page, perPage);
    }

    @Override
    public PagedResponse<ProductResponse> search(String q, String category, Long regionId, int page, int perPage, AppLanguage language) {
        return queryProducts(q, category, regionId, null, page, perPage);
    }

    @Override
    public SuggestionResponse suggestions(String q, AppLanguage language) {
        if (q == null || q.trim().length() < 2) {
            return new SuggestionResponse(List.of());
        }
        List<String> suggestions = productRepository.findAll().stream()
                .filter(this::isVisible)
                .map(Product::getName)
                .filter(Objects::nonNull)
                .filter(name -> name.toLowerCase().contains(q.toLowerCase()))
                .distinct()
                .limit(10)
                .toList();
        return new SuggestionResponse(suggestions);
    }

    @Override
    public CatalogFilterResponse filters(String category, AppLanguage language) {
        // 1) category ni bir marta parse qilamiz (noto'g'ri bo'lsa bo'sh javob)
        Long categoryId = null;
        if (category != null && !category.isBlank()) {
            try {
                categoryId = Long.valueOf(category);
            } catch (NumberFormatException e) {
                return CatalogFilterResponse.builder()
                        .minPrice(0.0)
                        .maxPrice(0.0)
                        .regionIds(List.of())
                        .attributes(Map.of())
                        .build();
            }
        }

        // 2) visible productlar + category filtri
        Long finalCategoryId = categoryId;
        List<Product> products = productRepository.findAll().stream()
                .filter(this::isVisible)
                .filter(p -> finalCategoryId == null || finalCategoryId.equals(p.getCategoryId()))
                .toList();

        // 3) min/max price (sizda price Double)
        Double minPrice = products.stream()
                .map(Product::getPrice)
                .filter(Objects::nonNull)
                .min(Double::compareTo)
                .orElse(0.0);

        Double maxPrice = products.stream()
                .map(Product::getPrice)
                .filter(Objects::nonNull)
                .max(Double::compareTo)
                .orElse(0.0);

        // 4) regionlar
        List<Long> regionIds = products.stream()
                .map(Product::getRegionId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        // 5) attributes yig'ish (toString() parse qilmaymiz, mapning o'zini ishlatamiz)
        Map<String, List<String>> attributes = new LinkedHashMap<>();

        for (Product product : products) {
            Map<String, Object> attrs = product.getAttributesJsonb();
            if (attrs == null || attrs.isEmpty()) continue;

            attrs.forEach((key, value) -> {
                if (value == null) return;

                if (value instanceof Collection<?> collection) {
                    for (Object item : collection) {
                        if (item != null) {
                            attributes.computeIfAbsent(key, k -> new ArrayList<>())
                                    .add(String.valueOf(item));
                        }
                    }
                } else {
                    attributes.computeIfAbsent(key, k -> new ArrayList<>())
                            .add(String.valueOf(value));
                }
            });
        }

        // 6) attribute value larni unique qilamiz
        attributes.replaceAll((k, v) -> v.stream().distinct().toList());

        return CatalogFilterResponse.builder()
                .minPrice(minPrice)
                .maxPrice(maxPrice)
                .regionIds(regionIds)
                .attributes(attributes)
                .build();
    }

    @Override
    public List<CategoryCountResponse> categoryCounts(AppLanguage language) {
        return productRepository.countVisibleProductsByCategory(ProductModerationStatus.APPROVED);
    }

    @Override
    public CatalogHomepageResponse homepage(AppLanguage language) {
        List<ProductResponse> featured = productRepository.findTop8ByModerationStatusAndIsActiveTrueAndIsPromotedTrueAndDeletedAtIsNullOrderByCreatedAtDesc(ProductModerationStatus.APPROVED)
                .stream().map(productService::toResponse).toList();
        List<ProductResponse> latest = productRepository.findTop8ByModerationStatusAndIsActiveTrueAndDeletedAtIsNullOrderByCreatedAtDesc(ProductModerationStatus.APPROVED)
                .stream().map(productService::toResponse).toList();
        List<BannerResponse> bannerUrl = bannerService.getAllBanners(language);
        return CatalogHomepageResponse.builder()
                .featuredProducts(featured)
                .newProducts(latest)
                .banners(bannerUrl)
                .topCategories(latest.stream().map(ProductResponse::getCategoryId).filter(Objects::nonNull).distinct().toList())
                .verifiedCompanies(latest.stream().map(ProductResponse::getCompanyId).filter(Objects::nonNull).distinct().toList())
                .build();
    }

    @Override
    public PageImpl<ProductResponse> getSaleTypeFilterProduct(int page, int perPage, SaleType saleType, AppLanguage language) {
        PageRequest pagable = PageRequest.of(page - 1, perPage);
        Page<Product> product = productRepository.findBySaleType(saleType, pagable);

        List<ProductResponse> list = product.getContent().stream()
                .map(this::toProductResponse)
                .collect(Collectors.toList());

        return new PageImpl<>(list, pagable, product.getTotalElements());
    }

    @Override
    public PageImpl<ProductDto> getPopularProduct(int page, int size) {
        Pageable pageable = PageRequest.of(page - 1, size);
        Page<Product> products = productRepository.findByModerationStatusAndDeletedAtIsNullOrderByIsPromotedDescViewsCountCacheDesc(ProductModerationStatus.APPROVED,pageable);
        List<ProductDto> list =products.getContent().stream()
                .map(this::toPopularProductResponse)
                .collect(Collectors.toList());
        return new PageImpl<>(list, pageable, products.getTotalElements());
    }

    @Override
    public PagedResponse<CatalogMapItemResponse> getCatalogMap(String q, String category, Long regionId, Long districtId, int page, int perPage, AppLanguage language) {
        int safePage = Math.max(page, 1);
        int safePerPage = Math.min(Math.max(perPage, 1), 100);

        Specification<Product> spec = (root, query, cb) -> cb.and(
                cb.equal(root.get("moderationStatus"), ProductModerationStatus.APPROVED),
                cb.isTrue(root.get("isActive")),
                cb.isNull(root.get("deletedAt"))
        );

        if (q != null && !q.isBlank()) {
            String keyword = "%" + q.trim().toLowerCase() + "%";
            spec = spec.and((root, query, cb) -> cb.like(cb.lower(root.get("name")), keyword));
        }

        if (category != null && !category.isBlank()) {
            try {
                Long categoryId = Long.valueOf(category);
                spec = spec.and((root, query, cb) -> cb.equal(root.get("categoryId"), categoryId));
            } catch (NumberFormatException ignored) {
            }
        }

        if (regionId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("regionId"), regionId));
        }

        if (districtId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("districtId"), districtId));
        }

        PageRequest pageable = PageRequest.of(safePage - 1, safePerPage);
        Page<Product> productPage = productRepository.findAll(spec, pageable);

        List<Long> companyIds = productPage.getContent().stream()
                .map(Product::getCompanyId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        if (companyIds.isEmpty()) {
            return new PagedResponse<>(List.of(), new PageMeta(0, safePage, safePerPage, 0));
        }

        CompanyMapSummaryRequest request = new CompanyMapSummaryRequest();
        request.setCompanyIds(companyIds);

        List<CompanyMapSummaryResponse> summaries = companyClient.getMapSummaries(request);
        Map<Long, CompanyMapSummaryResponse> summaryMap = summaries.stream()
                .collect(Collectors.toMap(CompanyMapSummaryResponse::getCompanyId, c -> c, (a, b) -> a));

        List<CatalogMapItemResponse> items = productPage.getContent().stream()
                .map(product -> {
                    CompanyMapSummaryResponse company = summaryMap.get(product.getCompanyId());
                    if (company == null) {
                        return null;
                    }

                    CatalogMapItemResponse item = new CatalogMapItemResponse();
                    item.setProductId(product.getId());
                    item.setProductName(product.getName());
                    item.setProductSlug(product.getSlug());
                    item.setCompanyId(product.getCompanyId());
                    item.setCompanyName(company.getCompanyName());
                    item.setCompanySlug(company.getSlug());
                    item.setCompanyAddress(company.getCompanyAddress());
                    item.setLat(company.getLat());
                    item.setLng(company.getLng());
                    item.setPrice(product.getPrice());
                    item.setCurrency(product.getCurrency());
                    item.setVerifiedCompany(company.getVerificationStatus());
                    return item;
                })
                .filter(Objects::nonNull)
                .toList();

        return new PagedResponse<>(
                items,
                new PageMeta(productPage.getTotalElements(), safePage, safePerPage, productPage.getTotalPages())
        );
    }

    private ProductDto toPopularProductResponse(Product p) {
        ProductDto res = new ProductDto();
        res.setId(p.getId());
        res.setName(p.getName());
        res.setSlug(p.getSlug());
        res.setPrice(p.getPrice());
        res.setPriceType(p.getPriceType());
        res.setSaleType(p.getSaleType());
        res.setCurrency(p.getCurrency());
//        res.setImages(p.getImages());           // images
        res.setViewsCountCache(p.getViewsCountCache());
        res.setFavoritesCountCache(p.getFavoritesCountCache());
        res.setIsPromoted(p.getIsPromoted());
        return res;
    }

    private ProductResponse toProductResponse(Product p) {
        ProductResponse res = new ProductResponse();
        res.setId(p.getId());
        res.setCompanyId(p.getCompanyId());
        res.setSellerId(p.getSellerId());
        res.setCategoryId(p.getCategoryId());
        res.setName(p.getName());
        res.setSlug(p.getSlug());
        res.setShortDescription(p.getShortDescription());
        res.setDescription(p.getDescription());
        res.setPriceType(p.getPriceType());
        res.setPrice(p.getPrice());
        res.setCurrency(p.getCurrency());
        res.setRegionId(p.getRegionId());
        res.setDistrictId(p.getDistrictId());
        res.setAttributes(p.getAttributesJsonb());
        res.setStatus(p.getModerationStatus());           // PENDING
        res.setIsActive(p.getIsActive());
        res.setIsPromoted(p.getIsPromoted());
        res.setPromotedUntil(p.getPromotedUntil());
        res.setRejectReason(p.getRejectReason());
        res.setViewsCountCache(p.getViewsCountCache());
        res.setFavoritesCountCache(p.getFavoritesCountCache());
        res.setCreatedAt(p.getCreatedAt());
//        res.setImages(p.getImages());           // images
        return res;
    }

    private PagedResponse<ProductResponse> queryProducts(String q, String category, Long regionId, String currency, int page, int perPage) {
        Specification<Product> spec = (root, query, cb) -> cb.and(
                cb.equal(root.get("moderationStatus"), ProductModerationStatus.APPROVED),
                cb.isTrue(root.get("isActive"))
        );
        if (q != null && !q.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.like(cb.lower(root.get("name")), "%" + q.toLowerCase() + "%"));
        }
        if (category != null && !category.isBlank()) {
            try {
                Long categoryId = Long.valueOf(category);
                spec = spec.and((root, query, cb) -> cb.equal(root.get("categoryId"), categoryId));
            } catch (NumberFormatException ignored) {
            }
        }
        if (regionId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("regionId"), regionId));
        }
        if (currency != null && !currency.isBlank()) {
            String normalized = Currency.valueOf(currency.toUpperCase()).name();
            spec = spec.and((root, query, cb) -> cb.equal(root.get("currency"), normalized));
        }
        Page<Product> result = productRepository.findAll(spec, PageRequest.of(Math.max(page - 1, 0), perPage));
        return ServiceHelper.toPagedResponse(result.map(productService::toResponse));
    }

    private boolean isVisible(Product product) {
        return product.getModerationStatus() == ProductModerationStatus.APPROVED && Boolean.TRUE.equals(product.getIsActive());
    }

}
