package org.example.ai.gateway.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/** Mirrors lead-service's {@code CartResponse}/{@code CartItemResponse} (plain camelCase, verified in source). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RemoteCartDto(Long itemCount, Long totalQuantity, List<RemoteCartItemDto> items) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RemoteCartItemDto(
            Long id,
            Long productId,
            Long sellerId,
            Long companyId,
            String productName,
            String productSlug,
            Double price,
            String currency,
            String companyName,
            String companySlug,
            Integer quantity) {
    }
}
