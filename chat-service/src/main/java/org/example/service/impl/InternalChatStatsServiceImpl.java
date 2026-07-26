package org.example.service.impl;

import lombok.RequiredArgsConstructor;
import org.example.dto.internal.dashboard.MonthlyCountResponse;
import org.example.dto.internal.dashboard.SellerChatStatsResponse;
import org.example.dto.internal.dashboard.SellerStatsFilterRequest;
import org.example.exp.AppBadException;
import org.example.repository.ChatThreadRepository;
import org.example.service.InternalChatStatsService;
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
public class InternalChatStatsServiceImpl implements InternalChatStatsService {

    private static final int DEFAULT_MONTHS = 6;
    private static final DateTimeFormatter MONTH_KEY_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM", Locale.ROOT);

    private final ChatThreadRepository chatThreadRepository;

    @Override
    public SellerChatStatsResponse getSellerOverview(SellerStatsFilterRequest request) {
        List<Long> companyIds = requireCompanyIds(request);
        int months = normalizeMonths(request == null ? null : request.getMonths());
        LocalDateTime from = YearMonth.now().minusMonths(months - 1L).atDay(1).atStartOfDay();

        SellerChatStatsResponse response = new SellerChatStatsResponse();
        response.setTotalThreads(chatThreadRepository.countBySellerCompanyIdInAndDeletedFalse(companyIds));
        response.setMonthlyChats(
                chatThreadRepository.countMonthlyByCompanyIds(companyIds, from)
                        .stream()
                        .map(this::toMonthlyCount)
                        .filter(Objects::nonNull)
                        .toList()
        );
        return response;
    }

    private MonthlyCountResponse toMonthlyCount(Object[] row) {
        if (row == null || row.length < 2) {
            return null;
        }

        LocalDateTime bucket = toLocalDateTime(row[0]);
        if (bucket == null) {
            return null;
        }

        MonthlyCountResponse response = new MonthlyCountResponse();
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

    private List<Long> requireCompanyIds(SellerStatsFilterRequest request) {
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
}
