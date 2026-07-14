package org.example.service.impl;

import lombok.RequiredArgsConstructor;
import org.example.dto.internal.SellerMonthlyCountResponse;
import org.example.dto.internal.SellerProductCardResponse;
import org.example.dto.internal.SellerProductStatsFilterRequest;
import org.example.dto.internal.SellerProductStatsResponse;
import org.example.entity.Product;
import org.example.entity.ProductImage;
import org.example.enums.ProductModerationStatus;
import org.example.exp.AppBadException;
import org.example.repository.ProductImageRepository;
import org.example.repository.ProductRepository;
import org.example.repository.ProductViewRepository;
import org.example.service.InternalProductStatsService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class InternalProductStatsServiceImpl implements InternalProductStatsService {

    private static final int DEFAULT_MONTHS = 6;
    private static final int DEFAULT_LIMIT = 5;
    private static final int MAX_LIMIT = 20;
    private static final DateTimeFormatter MONTH_KEY_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM", Locale.ROOT);

    private final ProductRepository productRepository;
    private final ProductViewRepository productViewRepository;
    private final ProductImageRepository productImageRepository;

    @Value("${app.media.base-url}")
    private String mediaBaseUrl;

    @Override
    public SellerProductStatsResponse getSellerOverview(SellerProductStatsFilterRequest request) {
        List<Long> companyIds = requireCompanyIds(request);
        int months = normalizeMonths(request == null ? null : request.getMonths());
        LocalDateTime from = YearMonth.now().minusMonths(months - 1L).atDay(1).atStartOfDay();

        SellerProductStatsResponse response = new SellerProductStatsResponse();
        response.setActiveProducts(productRepository.countByCompanyIdInAndModerationStatusAndIsActiveTrueAndDeletedAtIsNull(
                companyIds,
                ProductModerationStatus.APPROVED
        ));
        response.setTotalViews(defaultLong(productRepository.sumViewsCountByCompanyIds(companyIds, ProductModerationStatus.APPROVED)));
        response.setTotalFavorites(defaultLong(productRepository.sumFavoritesCountByCompanyIds(companyIds, ProductModerationStatus.APPROVED)));

        // Views loglari alohida jadvalda turadi, shu sabab trend shu yerdan yig'iladi.
        List<SellerMonthlyCountResponse> monthlyViews = productViewRepository.countMonthlyViews(companyIds, ProductModerationStatus.APPROVED, from)
                .stream()
                .map(this::toMonthlyCount)
                .filter(Objects::nonNull)
                .toList();
        response.setMonthlyViews(monthlyViews);
        return response;
    }

    @Override
    public List<SellerProductCardResponse> getRecentProducts(SellerProductStatsFilterRequest request) {
        List<Long> companyIds = requireCompanyIds(request);
        int limit = normalizeLimit(request == null ? null : request.getLimit());

        return productRepository.findByCompanyIdInAndModerationStatusAndIsActiveTrueAndDeletedAtIsNull(
                        companyIds,
                        ProductModerationStatus.APPROVED,
                        PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "createdAt"))
                )
                .getContent()
                .stream()
                .map(this::toCardResponse)
                .toList();
    }

    private SellerProductCardResponse toCardResponse(Product product) {
        ProductImage primaryImage = productImageRepository
                .findFirstByProduct_IdAndIsPrimaryTrueOrderByCreatedDateDesc(product.getId())
                .orElse(null);

        SellerProductCardResponse response = new SellerProductCardResponse();
        response.setProductId(product.getId());
        response.setName(product.getName());
        response.setImageUrl(primaryImage == null ? null : mediaBaseUrl + primaryImage.getStorageKey());
        response.setPrice(product.getPrice());
        response.setCurrency(product.getCurrency() == null ? null : product.getCurrency().name());
        response.setStatus(product.getModerationStatus() == null ? null : product.getModerationStatus().name());
        response.setViewsCount(defaultLong(product.getViewsCountCache()));
        response.setFavoritesCount(defaultLong(product.getFavoritesCountCache()));
        response.setCreatedAt(product.getCreatedAt());
        return response;
    }

    private SellerMonthlyCountResponse toMonthlyCount(Object[] row) {
        if (row == null || row.length < 2) {
            return null;
        }

        LocalDateTime bucket = toLocalDateTime(row[0]);
        if (bucket == null) {
            return null;
        }

        SellerMonthlyCountResponse response = new SellerMonthlyCountResponse();
        response.setMonth(YearMonth.from(bucket).format(MONTH_KEY_FORMATTER));
        response.setCount(row[1] instanceof Number number ? number.longValue() : 0L);
        return response;
    }

    private LocalDateTime toLocalDateTime(Object value) {
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        return null;
    }

    private List<Long> requireCompanyIds(SellerProductStatsFilterRequest request) {
        if (request == null || request.getCompanyIds() == null || request.getCompanyIds().isEmpty()) {
            throw new AppBadException("companyIds is required");
        }
        return request.getCompanyIds();
    }

    private int normalizeMonths(Integer months) {
        int value = months == null ? DEFAULT_MONTHS : months;
        if (value < 1 || value > 12) {
            throw new AppBadException("months must be between 1 and 12");
        }
        return value;
    }

    private int normalizeLimit(Integer limit) {
        int value = limit == null ? DEFAULT_LIMIT : limit;
        if (value < 1 || value > MAX_LIMIT) {
            throw new AppBadException("limit must be between 1 and 20");
        }
        return value;
    }

    private long defaultLong(Long value) {
        return value == null ? 0L : value;
    }
}
