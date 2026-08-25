package org.example.ai.gateway.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Mirrors category-service's {@code CategoryResponse}. The list endpoint ({@code GET
 * /api/v1/categories}) only populates the one {@code nameXx} field matching the request's
 * {@code Accept-Language}; the by-slug endpoint populates all three (unconditional entity mapping)
 * — re-verified live on {@code main} for Phase 2 (see the tool javadoc for the full drift note).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RemoteCategoryDto(
        Long id,
        String nameUz,
        String nameRu,
        String nameEn,
        String slug,
        Integer sortOrder,
        Boolean isActive) {

    /** Picks the name matching {@code locale} ("uz"/"ru"/"en"), falling back to whichever is present. */
    public String displayName(String locale) {
        String preferred = switch (locale == null ? "" : locale) {
            case "uz" -> nameUz;
            case "en" -> nameEn;
            default -> nameRu;
        };
        if (preferred != null) return preferred;
        if (nameRu != null) return nameRu;
        if (nameUz != null) return nameUz;
        if (nameEn != null) return nameEn;
        return slug;
    }
}
