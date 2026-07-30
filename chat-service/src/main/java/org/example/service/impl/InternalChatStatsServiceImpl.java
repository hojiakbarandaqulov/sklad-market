package org.example.service.impl;

import lombok.RequiredArgsConstructor;
import org.example.dto.internal.dashboard.SellerChatStatsResponse;
import org.example.dto.internal.dashboard.SellerStatsFilterRequest;
import org.example.exp.AppBadException;
import org.example.mapper.ChatStatsMapper;
import org.example.repository.ChatThreadRepository;
import org.example.service.InternalChatStatsService;
import org.example.service.ResourceBundleService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class InternalChatStatsServiceImpl implements InternalChatStatsService {

    private static final int DEFAULT_MONTHS = 6;

    private final ChatThreadRepository chatThreadRepository;
    private final ResourceBundleService messageService;
    private final ChatStatsMapper chatStatsMapper;

    @Override
    public SellerChatStatsResponse getSellerOverview(SellerStatsFilterRequest request) {
        // Dashboard faqat sellerga tegishli kompaniyalar bo'yicha hisoblanadi.
        List<Long> companyIds = requireCompanyIds(request);
        int months = normalizeMonths(request == null ? null : request.getMonths());
        LocalDateTime from = YearMonth.now().minusMonths(months - 1L).atDay(1).atStartOfDay();

        // Repository Object[] qaytaradi, toMonthlyCount esa uni tushunarli DTO'ga qo'lda o'giradi.
        SellerChatStatsResponse response = new SellerChatStatsResponse();
        response.setTotalThreads(chatThreadRepository.countBySellerCompanyIdInAndDeletedFalse(companyIds));
        response.setMonthlyChats(
                chatThreadRepository.countMonthlyByCompanyIds(companyIds, from)
                        .stream()
                        .map(chatStatsMapper::toMonthlyCount)
                        .filter(Objects::nonNull)
                        .toList()
        );
        return response;
    }

    private List<Long> requireCompanyIds(SellerStatsFilterRequest request) {
        if (request == null || request.getCompanyIds() == null || request.getCompanyIds().isEmpty()) {
            throw new AppBadException(messageService.getMessage("validation.company.ids.required"));
        }
        return request.getCompanyIds();
    }

    private int normalizeMonths(Integer months) {
        int value = months == null ? DEFAULT_MONTHS : months;
        if (value < 1 || value > 12) {
            throw new AppBadException(messageService.getMessage("validation.months.range"));
        }
        return value;
    }
}
