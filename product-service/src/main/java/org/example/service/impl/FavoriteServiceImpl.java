package org.example.service.impl;

import lombok.RequiredArgsConstructor;
import org.example.dto.PagedResponse;
import org.example.dto.favorite.FavoriteCountResponse;
import org.example.dto.favorite.FavoriteResponse;
import org.example.dto.product.ProductImageResponse;
import org.example.dto.product.ProductResponse;
import org.example.entity.Favorite;
import org.example.entity.Product;
import org.example.entity.ProductImage;
import org.example.enums.AppLanguage;
import org.example.exp.AppBadException;
import org.example.repository.FavoriteRepository;
import org.example.repository.ProductImageRepository;
import org.example.repository.ProductRepository;
import org.example.service.FavoriteService;
import org.example.service.ProductService;
import org.example.service.ResourceBundleService;
import org.example.utils.SpringSecurityUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FavoriteServiceImpl implements FavoriteService {
    private final FavoriteRepository favoriteRepository;
    private final ProductRepository productRepository;
    private final ProductImageRepository productImageRepository;
    private final ProductService productService;
    private final ResourceBundleService messageService;

    @Value("${app.media.base-url}")
    private String mediaBaseUrl;


    @Override
    public PageImpl<ProductResponse> getFavorites(int page, int perPage, AppLanguage language) {
        Long userId = requireProfileId(language);

        Page<Product> productPage = productRepository
                .findActiveFavoriteProducts(userId, PageRequest.of(page-1, perPage));

        List<ProductResponse> items = productPage.getContent()
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());

        return new PageImpl<>(items,PageRequest.of(page-1, perPage), productPage.getTotalElements());
    }
    @Override
    public FavoriteCountResponse getCount(AppLanguage language) {
        Long userId = requireProfileId(language);
        return new FavoriteCountResponse(favoriteRepository.countByUserIdAndIsActiveTrue(userId));
    }

    @Override
    public FavoriteResponse add(Long productId, AppLanguage language) {
        Long userId = requireProfileId(language);
        productRepository.findByIdAndIsActiveTrue(productId)
                .orElseThrow(() -> new AppBadException(messageService.getMessage("product.not.found", language)));
        if (favoriteRepository.findByUserIdAndProductIdAndIsActiveTrue(userId, productId).isPresent()) {
            throw new AppBadException(messageService.getMessage("favorite.exists", language));
        }
        Favorite favorite = new Favorite();
        favorite.setUserId(userId);
        favorite.setProductId(productId);
        favoriteRepository.save(favorite);
        return new FavoriteResponse(true);
    }

    @Override
    public FavoriteResponse remove(Long productId, AppLanguage language) {
        Long userId = requireProfileId(language);
        Favorite favorite = favoriteRepository.findByUserIdAndProductIdAndIsActiveTrue(userId, productId)
                .orElseThrow(() -> new AppBadException(messageService.getMessage("favorite.not.found", language)));
        favorite.setIsActive(Boolean.FALSE);
        favoriteRepository.save(favorite);
        return new FavoriteResponse(false);
    }

    private Long requireProfileId(AppLanguage language) {
        Long profileId = SpringSecurityUtil.getProfileId();
        if (profileId == null) {
            throw new AppBadException(messageService.getMessage("user.not.found", language));
        }
        return profileId;
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

        response.setImages(getImages(product.getId()));

        return response;
    }

    private List<ProductImageResponse> getImages(Long productId) {
        return productImageRepository.findByProduct_IdOrderBySortOrderAscIdAsc(productId)
                .stream()
                .map(this::toImageResponse)
                .collect(Collectors.toList());
    }
    private ProductImageResponse toImageResponse(ProductImage image) {
        String originalUrl =mediaBaseUrl + image.getStorageKey();
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


}
