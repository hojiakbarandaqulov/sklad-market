package org.example.ai.gateway.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Public product image fields used only to render grounded AI result thumbnails. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RemoteProductImageDto(
        String id,
        String url,
        @JsonProperty("thumbnail_urls") Map<String, String> thumbnailUrls,
        @JsonProperty("is_primary") Boolean primary) {

    public String cardUrl() {
        if (thumbnailUrls != null) {
            for (String key : new String[]{"medium", "md", "small", "sm", "large", "lg"}) {
                String value = thumbnailUrls.get(key);
                if (value != null && !value.isBlank()) return value;
            }
            for (String value : thumbnailUrls.values()) {
                if (value != null && !value.isBlank()) return value;
            }
        }
        return url;
    }

    public static String primaryCardUrl(List<RemoteProductImageDto> images) {
        if (images == null || images.isEmpty()) return null;
        RemoteProductImageDto selected = images.stream()
                .filter(Objects::nonNull)
                .filter(image -> Boolean.TRUE.equals(image.primary()))
                .findFirst()
                .orElseGet(() -> images.stream().filter(Objects::nonNull).findFirst().orElse(null));
        return selected == null ? null : selected.cardUrl();
    }
}
