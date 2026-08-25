package org.example.ai.gateway.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Mirrors lead-service's {@code LeadResponse} exactly (verified in source, plain camelCase, no
 * {@code @JsonProperty} overrides). {@code neededDate} is left as {@code Object} rather than
 * {@code LocalDate}: Spring Boot's default Jackson config serializes it as an ISO date string, but
 * this DTO deliberately doesn't depend on that default holding to avoid a deserialization failure.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RemoteLeadDto(
        Long id,
        Long buyerId,
        Long sellerId,
        Long companyId,
        String source,
        String status,
        String contactName,
        String contactPhone,
        String contactEmail,
        String deliveryAddress,
        Object neededDate,
        String comment,
        String closeReason,
        List<RemoteLeadItemDto> items) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RemoteLeadItemDto(Long productId, String productNameSnapshot, Double priceSnapshot, Integer quantity) {
    }
}
