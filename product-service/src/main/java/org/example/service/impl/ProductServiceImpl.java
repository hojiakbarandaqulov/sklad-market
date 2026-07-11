package org.example.service.impl;

import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.example.client.CategoryClient;
import org.example.client.CompanyClient;
import org.example.client.FileClient;
import org.example.client.dto.*;
import org.example.document.ProductDocument;
import org.example.dto.ApiResponse;
import org.example.dto.product.*;
import org.example.entity.Product;
import org.example.entity.ProductImage;
import org.example.enums.*;
import org.example.enums.Currency;
import org.example.event.ProductCreatedEvent;
import org.example.exp.AppBadException;
import org.example.repository.ProductImageRepository;
import org.example.repository.ProductRepository;
import org.example.service.*;
import org.example.utils.SpringSecurityUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {
    private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of("image/jpeg", "image/png", "image/webp", "image/jpg");
    private static final long MAX_FILE_SIZE = 5L * 1024 * 1024;
    private static final int MAX_IMAGES = 12;

    private final ProductRepository productRepository;
    private final ProductImageRepository productImageRepository;
    private final ProductSearchService productSearchService;
    private final CompanyClient companyClient;
    private final CategoryClient categoryClient;
    private final KafkaProducerService kafkaProducerService;
    private final ViewAsyncService viewAsyncService;
    private final ResourceBundleService messageService;
    private final FileClient fileClient;

    @Value("${app.media.base-url}")
    private String mediaBaseUrl;


    @Transactional
    @Override
    public ProductResponse create(CreateProductRequest request, AppLanguage language) {
        Long sellerId = requiredSellerId(language);
        validateCompanyOwnership(request.getCompanyId(), sellerId, language);
        validateCategory(request.getCategoryId(), language);

        Product product = new Product();
        applyCommonFields(product, request, language);
        product.setSellerId(sellerId);
        product.setSlug(generateUniqueSlug(request.getName()));
        product.setModerationStatus(ProductModerationStatus.PENDING);
        product.setMinProduct(request.getMinProduct());
        product.setSaleType(request.getSaleType());

        Product saved = productRepository.save(product);

        kafkaProducerService.sendProductCreated(ProductCreatedEvent.builder()
                .productId(saved.getId())
                .companyId(saved.getCompanyId())
                .categoryId(saved.getCategoryId())
                .sellerId(saved.getSellerId())
                .name(saved.getName())
                .slug(saved.getSlug())
                .price(saved.getPrice())
                .currency(saved.getCurrency().name())
                .moderationStatus(saved.getModerationStatus().name())
                .createdAt(saved.getCreatedAt())
                .build());
        productSearchService.index(toDocument(saved));

        return toResponse(saved);
    }

    @Transactional
    @Override
    public List<ProductImageResponse> uploadImages(Long productId, List<MultipartFile> files, AppLanguage language) {
        if (files == null || files.isEmpty()) {
            throw new AppBadException(messageService.getMessage("product.image.required", language));
        }

        Product product = getOwnedProduct(productId, language);
        long existingCount = productImageRepository.countByProduct_Id(productId);
        if (existingCount + files.size() > MAX_IMAGES) {
            throw new AppBadException(messageService.getMessage("product.images.limit.exceeded", language));
        }

        int nextSortOrder = productImageRepository.findByProduct_IdOrderBySortOrderAscIdAsc(productId)
                .stream()
                .map(ProductImage::getSortOrder)
                .filter(Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(0) + 1;

        boolean hasPrimary = existingCount > 0;

        for (MultipartFile file : files) {
//            ImageMeta imageMeta = validateAndReadImage(file, language);

            ApiResponse<AttachDto> upload = fileClient.upload(file, language.name());
            AttachInfoDto attachInfo = fileClient.getById(upload.getData().getId());
            if (attachInfo == null || !StringUtils.hasText(attachInfo.getPath())) {
                throw new AppBadException(messageService.getMessage("product.image.not.found", language));
            }
            String path = attachInfo.getPath().trim();
            String objectKey= path.substring(path.lastIndexOf('/') + 1);

            ProductImage image = new ProductImage();
            image.setId(upload.getData().getId());
            image.setProduct(product);
            image.setStorageKey(objectKey);
            image.setSortOrder(nextSortOrder++);
            image.setIsPrimary(!hasPrimary);
            image.setMimeType(file.getContentType());
            image.setFileSize(file.getSize());
//            image.setWidth(imageMeta.width());
//            image.setHeight(imageMeta.height());
            productImageRepository.save(image);
            hasPrimary = true;
        }
        return getImages(productId);
    }

    @Transactional(rollbackOn = Exception.class)
    @Override
    public void setPrimaryImage(Long productId, String imageId, AppLanguage language) {
        getOwnedProduct(productId, language);
        productImageRepository.clearPrimaryByProductId(productId);
        productImageRepository.isPrimary(imageId, productId);
    }

    @Transactional(rollbackOn = Exception.class)
    @Override
    public ApiResponse<Boolean> deleteImage(Long productId, String imageId, AppLanguage language) {
        getOwnedProduct(productId, language);
        ProductImage image = productImageRepository.findByIdAndProduct_Id(imageId, productId)
                .orElseThrow(() -> new AppBadException(messageService.getMessage("product.image.not.found", language)));
        try {
          /*  minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucketName)
                            .object(imageId)
                            .build()
            );*/
            ApiResponse<Boolean> delete = fileClient.delete(imageId, language.name());
            productImageRepository.delete(image);
            return delete;
        } catch (Exception e) {
            throw new AppBadException(messageService.getMessage("file.delete.failed", language));
        }
    }

    @Override
    public ProductListResponse getMyProducts(Long companyId, ProductModerationStatus status, int page, int perPage, AppLanguage language) {
        Long sellerId = requiredSellerId(language);
        int resolvedPage = normalizePage(page, language);
        int resolvedPerPage = normalizePerPage(perPage, language);
        List<Long> ownedCompanyIds = companyClient.getOwnedCompanyIds(sellerId);

        if (ownedCompanyIds.isEmpty()) {
            return ProductListResponse.builder()
                    .items(List.of())
                    .page(resolvedPage)
                    .perPage(resolvedPerPage)
                    .totalElements(0L)
                    .totalPages(0)
                    .build();
        }

        if (companyId != null && !ownedCompanyIds.contains(companyId)) {
            throw new AppBadException(messageService.getMessage("company.not.owned", language));
        }

        Pageable pageable = PageRequest.of(resolvedPage - 1, resolvedPerPage, Sort.by(Sort.Direction.DESC, "createdAt"));
        Specification<Product> specification = notDeleted();
        specification = specification.and((root, query, cb) -> root.get("companyId").in(companyId != null ? List.of(companyId) : ownedCompanyIds));

        if (status != null) {
            specification = specification.and((root, query, cb) -> cb.equal(root.get("moderationStatus"), status));
        }

        Page<Product> productPage = productRepository.findAll(specification, pageable);
        return ProductListResponse.builder()
                .items(productPage.getContent().stream().map(this::toResponse).collect(Collectors.toList()))
                .page(resolvedPage)
                .perPage(resolvedPerPage)
                .totalElements(productPage.getTotalElements())
                .totalPages(productPage.getTotalPages())
                .build();
    }

    @Override
    public ProductDetailResponse getPublicDetail(String slug, String sessionId, AppLanguage language) {
        Product product = productRepository.findBySlugAndModerationStatusAndDeletedAtIsNull(slug, ProductModerationStatus.APPROVED)
                .orElseThrow(() -> new AppBadException(messageService.getMessage("product.not.found", language)));

        CompanySummaryResponse company = companyClient.getSummary(product.getCompanyId());
        CategorySummaryResponse category = categoryClient.getSummary(product.getCategoryId());

        viewAsyncService.logView(product.getId(), SpringSecurityUtil.getProfileId(), sessionId);

        return ProductDetailResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .slug(product.getSlug())
                .shortDescription(product.getShortDescription())
                .description(product.getDescription())
                .priceType(product.getPriceType())
                .price(product.getPrice())
                .currency(product.getCurrency())
                .images(getImages(product.getId()))
                .attributes(product.getAttributesJsonb())
                .status(product.getModerationStatus())
                .isPromoted(product.getIsPromoted())
                .viewsCountCache(product.getViewsCountCache())
                .favoritesCountCache(product.getFavoritesCountCache())
                .createdAt(product.getCreatedAt())
                .company(CompanySummary.builder()
                        .id(company.getId())
                        .name(company.getName())
                        .slug(company.getSlug())
                        .logoPath(company.getLogoPath())
                        .build())
                .category(CategorySummary.builder()
                        .id(category.getId())
                        .name(category.getName())
                        .slug(category.getSlug())
                        .build())
                .regionId(product.getRegionId())
                .districtId(product.getDistrictId())
                .similarProducts(productRepository
                        .findTop8ByCategoryIdAndIdNotAndModerationStatusAndIsActiveTrueAndDeletedAtIsNullOrderByCreatedAtDesc(
                                product.getCategoryId(),
                                product.getId(),
                                ProductModerationStatus.APPROVED
                        )
                        .stream()
                        .map(this::toSimilarProduct)
                        .collect(Collectors.toList()))
                .build();
    }

    @Transactional
    @Override
    public ProductResponse update(Long id, UpdateProductRequest request, AppLanguage language) {
        Product product = getOwnedProduct(id, language);
        validateCompanyOwnership(request.getCompanyId(), requiredSellerId(language), language);
        validateCategory(request.getCategoryId(), language);

        String previousName = product.getName();
        applyCommonFields(product, request, language);
        if (!previousName.equalsIgnoreCase(request.getName())) {
            product.setSlug(generateUniqueSlug(request.getName()));
        }
        if (product.getModerationStatus() == ProductModerationStatus.APPROVED) {
            product.setModerationStatus(ProductModerationStatus.PENDING);
        }
        Product save = productRepository.save(product);
        productSearchService.index(toDocument(save));
        return toResponse(save);
    }

    @Transactional
    @Override
    public void publish(Long id, AppLanguage language) {
        Product product = getOwnedProduct(id, language);
        if (product.getModerationStatus() != ProductModerationStatus.PENDING) {
            throw new AppBadException(messageService.getMessage("product.publish.only.pending", language));
        }
        product.setModerationStatus(ProductModerationStatus.PENDING);
        productRepository.save(product);
    }

    @Transactional
    @Override
    public void archive(Long id, AppLanguage language) {
        Product product = getOwnedProduct(id, language);
        if (product.getModerationStatus() != ProductModerationStatus.APPROVED) {
            throw new AppBadException(messageService.getMessage("product.archive.only.approved", language));
        }
        product.setModerationStatus(ProductModerationStatus.ARCHIVED);
        product.setIsActive(Boolean.FALSE);
        productRepository.save(product);
    }

    @Transactional
    @Override
    public void delete(Long id, AppLanguage language) {
        Product product = productRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new AppBadException(messageService.getMessage("product.not.found", language)));

        if (!SpringSecurityUtil.hasRole("ADMIN") && !SpringSecurityUtil.hasRole("SUPER_ADMIN")) {
            validateCompanyOwnership(product.getCompanyId(), requiredSellerId(language), language);
        }

        product.setDeletedAt(LocalDateTime.now());
        product.setIsActive(Boolean.FALSE);
        productRepository.save(product);
    }

    @Override
    public ProductListResponse getAllProducts(int page, int perPage, AppLanguage language) {
        int resolvedPage = normalizePage(page, language);
        int resolvedPerPage = normalizePerPage(perPage, language);
        Pageable pageable = PageRequest.of(resolvedPage - 1, resolvedPerPage, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Product> all = productRepository.findAll(pageable);

        return ProductListResponse.builder()
                .items(all.getContent().stream().map(this::toResponse).collect(Collectors.toList()))
                .page(resolvedPage)
                .perPage(resolvedPerPage)
                .totalElements(all.getTotalElements())
                .totalPages(all.getTotalPages())
                .build();
    }

    @Override
    public Optional<Product> findByIdAndDeletedAtIsNull(Long productId) {
        return productRepository.findByIdAndDeletedAtIsNull(productId);
    }

    @Override
    public Optional<ProductImage> findFirstByProduct_IdAndIsPrimaryTrueOrderByCreatedDateDesc(Long productId) {
        return productImageRepository.findFirstByProduct_IdAndIsPrimaryTrueOrderByCreatedDateDesc(productId);
    }

    @Override
    public Page<Product> findByCompanyIdAndModerationStatusAndIsActiveTrueAndDeletedAtIsNullOrderByCreatedAtDesc(Long companyId, ProductModerationStatus productModerationStatus, PageRequest createdAt) {
        return productRepository.findByCompanyIdAndModerationStatusAndIsActiveTrueAndDeletedAtIsNullOrderByCreatedAtDesc(companyId, productModerationStatus, createdAt);
    }

    @Override
    public Long countByModerationStatusAndDeletedAtIsNull(ProductModerationStatus productModerationStatus) {
        return productRepository.countByModerationStatusAndDeletedAtIsNull(productModerationStatus);
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

    private Double normalizePrice(PriceType priceType, Double price, AppLanguage language) {
        if (priceType == PriceType.NEGOTIABLE) {
            return null;
        }
        if (price <= 0) {
            throw new AppBadException(messageService.getMessage("product.price.required", language));
        }
        return price;
    }

    private void validateCompanyOwnership(Long companyId, Long sellerId, AppLanguage language) {
        CompanyOwnershipResponse response = companyClient.checkOwnership(companyId, sellerId);
        if (!response.isExists()) {
            throw new AppBadException(messageService.getMessage("company.not.found", language));
        }
        if (!response.isOwner()) {
            throw new AppBadException(messageService.getMessage("company.not.owned", language));
        }
        if (!response.isActive()) {
            throw new AppBadException(messageService.getMessage("company.not.active", language));
        }

    }

    private void validateCategory(Long categoryId, AppLanguage language) {
        CategoryValidationResponse response = categoryClient.validate(categoryId);
        if (!response.isExists()) {
            throw new AppBadException(messageService.getMessage("category.not.found", language));
        }
        if (!response.isActive()) {
            throw new AppBadException(messageService.getMessage("category.inactive", language));
        }
    }

    private Product getOwnedProduct(Long productId, AppLanguage language) {
        Product product = productRepository.findByIdAndDeletedAtIsNull(productId)
                .orElseThrow(() -> new AppBadException(messageService.getMessage("product.not.found", language)));
        validateCompanyOwnership(product.getCompanyId(), requiredSellerId(language), language);
        return product;
    }

    private Long requiredSellerId(AppLanguage language) {
        Long sellerId = SpringSecurityUtil.getProfileId();
        if (sellerId == null) {
            throw new AppBadException(messageService.getMessage("seller.profile.not.found", language));
        }
        return sellerId;
    }

    private void applyCommonFields(Product product, CreateProductRequest request, AppLanguage language) {
        product.setCompanyId(request.getCompanyId());
        product.setCategoryId(request.getCategoryId());
        product.setName(request.getName());
        product.setShortDescription(request.getShortDescription());
        product.setDescription(request.getDescription());
        product.setPriceType(request.getPriceType());
        product.setPrice(normalizePrice(request.getPriceType(), request.getPrice(), language));
        product.setCurrency(request.getCurrency());
        product.setAttributesJsonb(normalizeAttributes(request.getAttributes()));
    }

    private void applyCommonFields(Product product, UpdateProductRequest request, AppLanguage language) {
        product.setCompanyId(request.getCompanyId());
        product.setCategoryId(request.getCategoryId());
        product.setName(request.getName());
        product.setShortDescription(request.getShortDescription());
        product.setDescription(request.getDescription());
        product.setPriceType(request.getPriceType());
        product.setPrice(normalizePrice(request.getPriceType(), request.getPrice(), language));
        product.setCurrency(request.getCurrency());
        product.setRegionId(request.getRegionId());
        product.setDistrictId(request.getDistrictId());
        product.setAttributesJsonb(normalizeAttributes(request.getAttributes()));
    }

    private String generateUniqueSlug(String name) {
        String base = slugify(name);
        String candidate = base;
        int counter = 1;
        while (productRepository.existsBySlug(candidate)) {
            candidate = base + "-" + counter++;
        }
        return candidate;
    }

    private String slugify(String value) {
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        String slug = normalized.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        return StringUtils.hasText(slug) ? slug : "product";
    }

    private ImageMeta validateAndReadImage(MultipartFile file, AppLanguage language) {
        if (file.isEmpty()) {
            throw new AppBadException(messageService.getMessage("file.empty", language));
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new AppBadException(messageService.getMessage("image.size.exceeded", language));
        }
        if (!ALLOWED_IMAGE_TYPES.contains(file.getContentType())) {
            throw new AppBadException(messageService.getMessage("image.type.invalid", language));
        }
        try {
            BufferedImage bufferedImage = ImageIO.read(file.getInputStream());
            if (bufferedImage == null) {
                throw new AppBadException(messageService.getMessage("image.invalid", language));
            }

            return new ImageMeta(bufferedImage.getWidth(), bufferedImage.getHeight());
        } catch (IOException e) {
            throw new AppBadException(messageService.getMessage("image.read.failed", language));
        }
    }

    private record ImageMeta(int width, int height) {

    }

/*
    private String generateStorageKey(Long productId, String originalFilename) {
        String cleanName = StringUtils.hasText(originalFilename) ? originalFilename.replaceAll("\\s+", "-") : "image";
        return "products/" + productId + "/" + UUID.randomUUID() + "-" + cleanName;
    }
*/

    private List<ProductImageResponse> getImages(Long productId) {
        return productImageRepository.findByProduct_IdOrderBySortOrderAscIdAsc(productId)
                .stream()
                .map(this::toImageResponse)
                .collect(Collectors.toList());
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

    private SimilarProductResponse toSimilarProduct(Product product) {
        List<ProductImage> images = productImageRepository
                .findByProduct_IdOrderBySortOrderAscIdAsc(product.getId());

        String primaryImage = null;
        for (ProductImage image : images) {
            if (Boolean.TRUE.equals(image.getIsPrimary())) {
                primaryImage = mediaBaseUrl + image.getStorageKey();
                break;
            }
        }
        return SimilarProductResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .slug(product.getSlug())
                .price(product.getPrice())
                .currency(product.getCurrency())
                .isPromoted(product.getIsPromoted())
                .primaryImage(primaryImage)
                .build();
    }

    private Specification<Product> notDeleted() {
        return (root, query, cb) -> cb.isNull(root.get("deletedAt"));
    }

    private Map<String, Object> normalizeAttributes(Map<String, Object> attributes) {
        return attributes == null ? new HashMap<>() : new HashMap<>(attributes);
    }

    private int normalizePage(int page, AppLanguage language) {
        if (page < 1) {
            throw new AppBadException(messageService.getMessage("page.invalid", language));
        }
        return page;
    }

    private int normalizePerPage(int perPage, AppLanguage language) {
        if (perPage < 1 || perPage > 100) {
            throw new AppBadException(messageService.getMessage("per.page.invalid", language));
        }
        return perPage;
    }

    @Override
    public ProductResponse toResponse(Product product) {
        ProductResponse response = new ProductResponse();
        response.setId(product.getId());
        response.setSellerId(product.getSellerId());
        response.setCompanyId(product.getCompanyId());
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
        response.setStatus(resolveStatus(product));
        response.setAttributes(product.getAttributesJsonb());
        response.setIsActive(product.getIsActive());
        response.setIsPromoted(product.getIsPromoted());
        response.setPromotedUntil(product.getPromotedUntil());
        response.setRejectReason(product.getRejectReason());
        response.setViewsCountCache(product.getViewsCountCache());
        response.setFavoritesCountCache(product.getFavoritesCountCache());
        response.setCreatedAt(product.getCreatedAt());
        response.setImages(getImages(product.getId()));
        return response;
    }

    @Override
    public ProductResponse getById(Long id,AppLanguage language) {
        Optional<Product> byId = productRepository.findById(id);
        if (byId.isEmpty()) {
            throw new AppBadException(messageService.getMessage("product.notFound", language));
        }
        return toResponse(byId.get());
    }

    private ProductModerationStatus resolveStatus(Product product) {
        return product.getModerationStatus() == null ? ProductModerationStatus.PENDING : product.getModerationStatus();
    }


    /*private void fillProduct(Product product, org.example.dto.CreateProductRequest request) {
        product.setCompanyId(request.getCompanyId());
        product.setCategoryId(request.getCategoryId());
        product.setName(request.getName());
        product.setShortDescription(request.getShortDescription());
        product.setDescription(request.getDescription());
        product.setPriceType(request.getPriceType());
        product.setPrice(request.getPrice());
        product.setCurrency(Currency.valueOf(request.getCurrency().name()));
        product.setRegionId(request.getRegionId());
        product.setDistrictId(request.getDistrictId());
        product.setAttributesJsonb(ServiceHelper.writeAttributes(request.getAttributes()));
    }*/
}
