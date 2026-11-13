package org.example.service.impl;

import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.clent.OrderAcceptClient;
import org.example.dto.ApiResponse;
import org.example.dto.LeadResponse;
import org.example.enums.AppLanguage;
import org.example.enums.LeadDisplayStatus;
import org.example.enums.LeadStatus;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class LeadOrderDisplayStatusService {
    private static final int ORDER_BATCH_SIZE = 100;
    private final OrderAcceptClient orders;

    public void applyOrderStatuses(List<LeadResponse> leads, String authorization, AppLanguage language) {
        List<Long> orderIds = leads.stream()
                .filter(lead -> lead.getStatus() == LeadStatus.ACCEPTED && lead.getAcceptedOrderId() != null)
                .map(LeadResponse::getAcceptedOrderId)
                .distinct()
                .toList();

        Map<Long, String> statuses = new HashMap<>();
        for (int start = 0; start < orderIds.size(); start += ORDER_BATCH_SIZE) {
            List<Long> batch = orderIds.subList(start, Math.min(start + ORDER_BATCH_SIZE, orderIds.size()));
            try {
                ApiResponse<Map<Long, String>> response = orders.leadOrderStatuses(batch, authorization, language.name());
                if (response != null && Boolean.TRUE.equals(response.getSuccess()) && response.getData() != null) {
                    statuses.putAll(response.getData());
                }
            } catch (FeignException exception) {
                log.warn("Could not load linked order statuses, HTTP status={}", exception.status());
            }
        }
        for (LeadResponse lead : leads) {
            if (lead.getStatus() == LeadStatus.ACCEPTED && lead.getAcceptedOrderId() != null) {
                applyOrderStatus(lead, statuses.get(lead.getAcceptedOrderId()));
            }
        }
    }

    private void applyOrderStatus(LeadResponse lead, String orderStatus) {
        if (orderStatus == null) {
            return;
        }
        switch (orderStatus) {
            case "SHIPPED" -> lead.setDisplayStatus(LeadDisplayStatus.COMPLETED);
            case "CANCELLED", "EXPIRED", "REJECTED" -> lead.setDisplayStatus(LeadDisplayStatus.CANCELED);
            default -> lead.setDisplayStatus(LeadDisplayStatus.IN_PROGRESS);
        }
    }
}
