package org.example.ai.business.remote;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.example.ai.gateway.dto.RemoteProductImageDto;

import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RemoteBusinessProduct(
        Long id,
        Long companyId,
        Long categoryId,
        String name,
        String slug,
        String shortDescription,
        String description,
        Double price,
        String currency,
        Long min,
        Long regionId,
        Long districtId,
        String status,
        Boolean isActive,
        Long viewsCountCache,
        Long favoritesCountCache,
        Map<String, Object> attributes,
        List<RemoteProductImageDto> images) {

    /** Compatibility constructor for existing indexer/service tests and callers. */
    public RemoteBusinessProduct(
            Long id,
            Long companyId,
            Long categoryId,
            String name,
            String slug,
            String shortDescription,
            String description,
            Double price,
            String currency,
            Long min,
            Long regionId,
            Long districtId,
            String status,
            Boolean isActive,
            Long viewsCountCache,
            Long favoritesCountCache,
            Map<String, Object> attributes) {
        this(id, companyId, categoryId, name, slug, shortDescription, description, price, currency,
                min, regionId, districtId, status, isActive, viewsCountCache, favoritesCountCache,
                attributes, null);
    }

    public boolean publiclyVisible() {
        return id != null && companyId != null && "APPROVED".equals(status) && Boolean.TRUE.equals(isActive);
    }

    public String primaryImageUrl() {
        return RemoteProductImageDto.primaryCardUrl(images);
    }
}
