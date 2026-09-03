package org.example.mapper;

import lombok.RequiredArgsConstructor;
import org.example.dto.LeadItemResponse;
import org.example.dto.LeadResponse;
import org.example.entity.Lead;
import org.example.repository.LeadItemRepository;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LeadMapper {

    private final LeadItemRepository leadItemRepository;

    public LeadResponse toResponse(Lead lead) {
        return LeadResponse.builder()
                .id(lead.getId())
                .buyerId(lead.getBuyerId())
                .sellerId(lead.getSellerId())
                .companyId(lead.getCompanyId())
                .source(lead.getSource())
                .status(lead.getStatus())
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
}
