package org.example.mapper;

import lombok.RequiredArgsConstructor;
import org.example.dto.LeadItemResponse;
import org.example.dto.LeadResponse;
import org.example.entity.Lead;
import org.example.enums.LeadDisplayStatus;
import org.example.enums.LeadStatus;
import org.example.repository.LeadItemRepository;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class LeadMapper {

    private final LeadItemRepository leadItemRepository;

    public LeadResponse toResponse(Lead lead) {
        return LeadResponse.builder()
                .id(lead.getId())
                .orderNumber("RQ-" + lead.getId())
                .buyerId(lead.getBuyerId())
                .acceptedOrderId(lead.getAcceptedOrderId())
                .sellerId(lead.getSellerId())
                .companyId(lead.getCompanyId())
                .source(lead.getSource())
                .status(lead.getStatus())
                .displayStatus(displayStatus(lead))
                .contactName(lead.getContactName())
                .contactPhone(lead.getContactPhone())
                .contactEmail(lead.getContactEmail())
                .deliveryAddress(lead.getDeliveryAddress())
                .neededDate(lead.getNeededDate())
                .comment(lead.getComment())
                .createdDate(lead.getCreatedDate())
                .closeReason(lead.getCloseReason())
                .items(leadItemRepository.findByLeadIdAndDeletedFalse(lead.getId()).stream()
                        .map(item -> LeadItemResponse.builder()
                                .productId(item.getProductId())
                                .productNameSnapshot(item.getProductNameSnapshot())
                                .priceSnapshot(item.getPriceSnapshot())
                                .quantity(item.getQuantity())
                                .build())
                        .toList())
                .build();
    }

    private LeadDisplayStatus displayStatus(Lead lead) {
        if (lead.getStatus() == LeadStatus.CANCELED || lead.getStatus() == LeadStatus.CLOSED) {
            return LeadDisplayStatus.CANCELED;
        }
        if (lead.getStatus() == LeadStatus.ACCEPTED) {
            return LeadDisplayStatus.IN_PROGRESS;
        }
        if (lead.getCreatedDate() != null
                && !lead.getCreatedDate().isAfter(LocalDateTime.now().minusHours(24))) {
            return LeadDisplayStatus.WAITING;
        }
        return LeadDisplayStatus.NEW;
    }
}
