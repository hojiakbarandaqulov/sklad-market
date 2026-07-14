package org.example.service.impl;

import lombok.RequiredArgsConstructor;
import org.example.config.clent.ChatClient;
import org.example.config.clent.CompanyClient;
import org.example.config.clent.LeadClient;
import org.example.config.clent.ProductClient;
import org.example.dto.dashboard.SellerDashboardProductResponse;
import org.example.dto.dashboard.SellerDashboardResponse;
import org.example.dto.dashboard.SellerDashboardSummaryResponse;
import org.example.dto.dashboard.SellerDashboardTrendResponse;
import org.example.dto.dashboard.SellerStatsFilterRequest;
import org.example.dto.dashboard.internal.ChatSellerStatsResponse;
import org.example.dto.dashboard.internal.LeadSellerStatsResponse;
import org.example.dto.dashboard.internal.MonthlyCountResponse;
import org.example.dto.dashboard.internal.ProductSellerStatsResponse;
import org.example.exp.AppBadException;
import org.example.service.SellerDashboardService;
import org.example.utils.SpringSecurityUtil;
import org.springframework.stereotype.Service;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class SellerDashboardServiceImpl implements SellerDashboardService {

    private static final int DEFAULT_MONTHS = 6;
    private static final int MAX_MONTHS = 12;
    private static final int DEFAULT_RECENT_LIMIT = 5;
    private static final DateTimeFormatter MONTH_KEY_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM", Locale.ROOT);

    private final CompanyClient companyClient;
    private final ProductClient productClient;
    private final LeadClient leadClient;
    private final ChatClient chatClient;

    @Override
    public SellerDashboardResponse getDashboard(Long companyId, Integer months) {
        Long sellerId = SpringSecurityUtil.getProfileId();
        if (sellerId == null) {
            throw new AppBadException("Seller not found");
        }

        int normalizedMonths = normalizeMonths(months);
        List<Long> ownedCompanyIds = safeList(companyClient.getOwnedCompanyIds(sellerId));

        // Sellerda hali kompaniya bo'lmasa, frontend bo'sh dashboard olsa kifoya.
        if (ownedCompanyIds.isEmpty()) {
            return emptyDashboard(normalizedMonths);
        }

        if (companyId != null && !ownedCompanyIds.contains(companyId)) {
            throw new AppBadException("Selected company does not belong to the seller");
        }

        List<Long> targetCompanyIds = companyId == null ? ownedCompanyIds : List.of(companyId);

        SellerStatsFilterRequest statsFilter = new SellerStatsFilterRequest();
        statsFilter.setCompanyIds(targetCompanyIds);
        statsFilter.setMonths(normalizedMonths);

        ProductSellerStatsResponse productStats = productClient.getSellerOverview(statsFilter);
        LeadSellerStatsResponse leadStats = leadClient.getSellerOverview(statsFilter);
        ChatSellerStatsResponse chatStats = chatClient.getSellerOverview(statsFilter);

        SellerStatsFilterRequest recentFilter = new SellerStatsFilterRequest();
        recentFilter.setCompanyIds(targetCompanyIds);
        recentFilter.setLimit(DEFAULT_RECENT_LIMIT);

        List<SellerDashboardProductResponse> recentProducts = safeList(productClient.getSellerRecentProducts(recentFilter));
        List<String> labels = buildMonthKeys(normalizedMonths);

        SellerDashboardSummaryResponse summary = new SellerDashboardSummaryResponse();
        summary.setActiveProducts(defaultLong(productStats == null ? null : productStats.getActiveProducts()));
        summary.setLeads(defaultLong(leadStats == null ? null : leadStats.getTotalLeads()));
        summary.setContacts(defaultLong(chatStats == null ? null : chatStats.getTotalThreads()));
        summary.setTotalViews(defaultLong(productStats == null ? null : productStats.getTotalViews()));
        summary.setTotalFavorites(defaultLong(productStats == null ? null : productStats.getTotalFavorites()));

        SellerDashboardTrendResponse trend = new SellerDashboardTrendResponse();
        trend.setLabels(labels);
        trend.setViewsSeries(buildSeries(labels, productStats == null ? null : productStats.getMonthlyViews()));
        trend.setLeadsSeries(buildSeries(labels, leadStats == null ? null : leadStats.getMonthlyLeads()));
        trend.setChatsSeries(buildSeries(labels, chatStats == null ? null : chatStats.getMonthlyChats()));
        trend.setTotalViews(defaultLong(productStats == null ? null : productStats.getTotalViews()));
        trend.setTotalLeads(defaultLong(leadStats == null ? null : leadStats.getTotalLeads()));
        trend.setTotalChats(defaultLong(chatStats == null ? null : chatStats.getTotalThreads()));

        SellerDashboardResponse response = new SellerDashboardResponse();
        response.setSummary(summary);
        response.setTrend(trend);
        response.setRecentProducts(recentProducts);
        return response;
    }

    private SellerDashboardResponse emptyDashboard(int months) {
        SellerDashboardSummaryResponse summary = new SellerDashboardSummaryResponse();
        summary.setActiveProducts(0L);
        summary.setLeads(0L);
        summary.setContacts(0L);
        summary.setTotalViews(0L);
        summary.setTotalFavorites(0L);

        List<String> labels = buildMonthKeys(months);
        List<Long> emptySeries = new ArrayList<>(Collections.nCopies(labels.size(), 0L));

        SellerDashboardTrendResponse trend = new SellerDashboardTrendResponse();
        trend.setLabels(labels);
        trend.setViewsSeries(emptySeries);
        trend.setLeadsSeries(new ArrayList<>(emptySeries));
        trend.setChatsSeries(new ArrayList<>(emptySeries));
        trend.setTotalViews(0L);
        trend.setTotalLeads(0L);
        trend.setTotalChats(0L);

        SellerDashboardResponse response = new SellerDashboardResponse();
        response.setSummary(summary);
        response.setTrend(trend);
        response.setRecentProducts(List.of());
        return response;
    }

    // Har service o'zining oyma-oy statistikani qaytaradi, biz esa ularni bitta chartga tekislaymiz.
    private List<Long> buildSeries(List<String> labels, List<MonthlyCountResponse> rows) {
        Map<String, Long> valuesByMonth = new LinkedHashMap<>();
        if (rows != null) {
            for (MonthlyCountResponse row : rows) {
                if (row == null || row.getMonth() == null) {
                    continue;
                }
                valuesByMonth.put(row.getMonth(), defaultLong(row.getCount()));
            }
        }

        List<Long> series = new ArrayList<>(labels.size());
        for (String label : labels) {
            series.add(valuesByMonth.getOrDefault(label, 0L));
        }
        return series;
    }

    private List<String> buildMonthKeys(int months) {
        List<String> labels = new ArrayList<>(months);
        YearMonth current = YearMonth.now();
        YearMonth start = current.minusMonths(months - 1L);

        for (int i = 0; i < months; i++) {
            labels.add(start.plusMonths(i).format(MONTH_KEY_FORMATTER));
        }
        return labels;
    }

    private int normalizeMonths(Integer months) {
        int value = months == null ? DEFAULT_MONTHS : months;
        if (value < 1 || value > MAX_MONTHS) {
            throw new AppBadException("months must be between 1 and 12");
        }
        return value;
    }

    private long defaultLong(Long value) {
        return value == null ? 0L : value;
    }

    private <T> List<T> safeList(List<T> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream().filter(Objects::nonNull).toList();
    }
}
