package org.example.service.impl;

import lombok.RequiredArgsConstructor;
import org.example.document.ProductDocument;
import org.example.dto.admin.ModerationDecisionRequest;
import org.example.dto.admin.PromotionRequest;
import org.example.dto.admin.ReasonRequest;
import org.example.dto.product.ProductImageResponse;
import org.example.dto.product.ProductListResponse;
import org.example.dto.product.ProductResponse;
import org.example.entity.Product;
import org.example.entity.ProductImage;
import org.example.enums.AppLanguage;
import org.example.enums.ProductModerationStatus;
import org.example.exp.AppBadException;
import org.example.repository.ProductImageRepository;
import org.example.repository.ProductRepository;
import org.example.service.AdminProductService;
import org.example.service.ProductSearchService;
import org.example.service.ResourceBundleService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AdminProductServiceImpl implements AdminProductService {

    private final ProductRepository productRepository;
    private final ProductImageRepository productImageRepository;
    private final ResourceBundleService messageService;
    private final ProductSearchService productSearchService;

    @Value("${app.media.base-url}")
    private String mediaBaseUrl;

    @Override
    public ProductListResponse getProducts(ProductModerationStatus status, Long companyId, String q, int page, int perPage, AppLanguage language) {
        int resolvedPage = normalizePage(page, language);
        int resolvedPerPage = normalizePerPage(perPage, language);

        Specification<Product> specification = Specification.where(null);
        if (status != null) {
            specification = specification.and((root, query, cb) -> cb.equal(root.get("moderationStatus"), status));
        }
        if (companyId != null) {
            specification = specification.and((root, query, cb) -> cb.equal(root.get("companyId"), companyId));
        }
        if (StringUtils.hasText(q)) {
            String like = "%" + q.trim().toLowerCase() + "%";
            specification = specification.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("name")), like),
                    cb.like(cb.lower(root.get("slug")), like),
                    cb.like(cb.lower(root.get("description")), like)
            ));
        }

        PageRequest pageRequest = PageRequest.of(resolvedPage - 1, resolvedPerPage, Sort.by(Sort.Direction.DESC, "createdDate"));
        Page<Product> result = productRepository.findAll(specification, pageRequest);
        return ProductListResponse.builder()
                .items(result.getContent().stream().map(this::toResponse).toList())
                .page(resolvedPage)
                .perPage(resolvedPerPage)
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .build();
    }

    @Override
    public List<ProductResponse> getModerationQueue() {
        Specification<Product> specification = Specification.where(notDeleted())
                .and((root, query, cb) -> cb.equal(root.get("moderationStatus"), ProductModerationStatus.PENDING));

        return productRepository.findAll(specification, Pageable.unpaged(Sort.by(Sort.Direction.ASC, "createdAt")))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public void approve(Long id, AppLanguage language) {
        Product product = getProduct(id, language);
        if (product.getDeletedAt() != null) {
            throw new AppBadException(messageService.getMessage("product.deleted", language));
        }
        product.setModerationStatus(ProductModerationStatus.APPROVED);
        product.setRejectReason(null);
        product.setIsActive(Boolean.TRUE);
        productRepository.save(product);
        productSearchService.update(toDocument(product));
    }

    @Override
    @Transactional
    public void reject(Long id, ModerationDecisionRequest request, AppLanguage language) {
        Product product = getProduct(id, language);
        product.setModerationStatus(ProductModerationStatus.REJECTED);
        product.setRejectReason(resolveReason(request));
        productRepository.save(product);
        productSearchService.update(toDocument(product));
    }

    @Override
    @Transactional
    public void block(Long id, ReasonRequest request, AppLanguage language) {
        Product product = getProduct(id, language);
        product.setIsActive(Boolean.FALSE);
        if (StringUtils.hasText(request == null ? null : request.getReason())) {
            product.setRejectReason(request.getReason().trim());
        }
        productRepository.save(product);
        productSearchService.update(toDocument(product));
    }

    @Override
    @Transactional
    public void promote(Long id, PromotionRequest request, AppLanguage language) {
        Product product = getProduct(id, language);
        product.setIsPromoted(Boolean.TRUE);
        product.setPromotedUntil(request == null ? null : request.getEndsAt());
        productRepository.save(product);
        productSearchService.update(toDocument(product));
    }

    @Override
    public ProductResponse getByProduct(Long id, AppLanguage language) {
        Optional<Product> result = productRepository.findByIdAndDeletedAtIsNull(id);
        if (result.isEmpty()) {
            throw new AppBadException(messageService.getMessage("product.not.found", language));
        }
        return toResponse(result.get());
    }

    private Product getProduct(Long id, AppLanguage language) {
        return productRepository.findById(id)
                .orElseThrow(() -> new AppBadException(messageService.getMessage("product.not.found", language)));
    }

    private ProductDocument toDocument(Product product) {
        String primaryImageUrl = productImageRepository
                .findByProduct_IdOrderBySortOrderAscIdAsc(product.getId())
                .stream()
                .filter(img -> Boolean.TRUE.equals(img.getIsPrimary()))
                .findFirst()
                .map(img -> mediaBaseUrl + img.getStorageKey())
                .orElse(null);

        return ProductDocument.builder()
                .id(product.getId().toString())
                .name(product.getName())
                .shortDescription(product.getShortDescription())
                .slug(product.getSlug())
                .price(product.getPrice())
                .currency(product.getCurrency().name())
                .moderationStatus(product.getModerationStatus().name())
                .isActive(product.getIsActive())
                .primaryImageUrl(primaryImageUrl)
                .build();
    }

    private ProductResponse toResponse(Product product) {
        ProductResponse response = new ProductResponse();
        response.setId(product.getId());
        response.setCompanyId(product.getCompanyId());
        response.setSellerId(product.getSellerId());
        response.setCategoryId(product.getCategoryId());
        response.setName(product.getName());
        response.setSlug(product.getSlug());
        response.setShortDescription(product.getShortDescription());
        response.setDescription(product.getDescription());
        response.setPriceType(product.getPriceType());
        response.setPrice(product.getPrice());
        response.setCurrency(product.getCurrency());
        response.setRegionId(product.getRegionId());
        response.setDistrictId(product.getDistrictId());
        response.setAttributes(product.getAttributesJsonb());
        response.setStatus(product.getModerationStatus());
        response.setIsActive(product.getIsActive());
        response.setIsPromoted(product.getIsPromoted());
        response.setPromotedUntil(product.getPromotedUntil());
        response.setRejectReason(product.getRejectReason());
        response.setViewsCountCache(product.getViewsCountCache());
        response.setFavoritesCountCache(product.getFavoritesCountCache());
        response.setCreatedAt(product.getCreatedAt());
//        response.setUpdatedAt(product.getModifiedDate());
        response.setImages(getImages(product.getId()));
        return response;
    }

    private List<ProductImageResponse> getImages(Long productId) {
        return productImageRepository.findByProduct_IdOrderBySortOrderAscIdAsc(productId)
                .stream()
                .map(this::toImageResponse)
                .toList();
    }

    private ProductImageResponse toImageResponse(ProductImage image) {
        String originalUrl = mediaBaseUrl + image.getStorageKey();
        return ProductImageResponse.builder()
                .id(image.getId())
                .url(originalUrl)
                .thumbnailUrls(Map.of(
                        "320", originalUrl + "?w=320",
                        "640", originalUrl + "?w=640",
                        "960", originalUrl + "?w=960"
                ))
                .isPrimary(image.getIsPrimary())
                .build();
    }

    private Specification<Product> notDeleted() {
        return (root, query, cb) -> cb.isNull(root.get("deletedAt"));
    }

    private String resolveReason(ModerationDecisionRequest request) {
        if (request == null) {
            return null;
        }
        boolean hasReasonCode = StringUtils.hasText(request.getReasonCode());
        boolean hasComment = StringUtils.hasText(request.getComment());
        if (hasReasonCode && hasComment) {
            return request.getReasonCode().trim() + ": " + request.getComment().trim();
        }
        if (hasReasonCode) {
            return request.getReasonCode().trim();
        }
        if (hasComment) {
            return request.getComment().trim();
        }
        return null;
    }

    private int normalizePage(int page, AppLanguage language) {
        if (page < 0) {
            throw new AppBadException(messageService.getMessage("page.invalid", language));
        }
        return page == 0 ? 1 : page;
    }

    private int normalizePerPage(int perPage, AppLanguage language) {
        if (perPage < 1 || perPage > 100) {
            throw new AppBadException(messageService.getMessage("per.page.invalid", language));
        }
        return perPage;
    }
}
