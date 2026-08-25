package org.example.ai.embedding;

import org.example.ai.gateway.dto.RemoteIndexProductDto;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.TreeMap;

/**
 * Builds the embedding source text and its content hash from a product's PUBLIC list fields only
 * (PLAN.md Phase 5). The hash covers every field that feeds either the embedded text or a stored
 * projection column, so any change (name/description/attributes/category/price/region/slug/currency/
 * public image) re-embeds, while an unchanged product is skipped — the whole point of the
 * content-hash pass.
 */
@Component
public class EmbeddingTextBuilder {

    private static final int MAX_TEXT_CHARS = 8000;

    /** Source text for {@code RETRIEVAL_DOCUMENT}: name + category + short/long description + flattened attributes. */
    public String buildText(RemoteIndexProductDto product, String categoryName) {
        StringBuilder sb = new StringBuilder(512);
        appendLine(sb, "Название", product.name());
        if (categoryName != null && !categoryName.isBlank()) {
            appendLine(sb, "Категория", categoryName);
        }
        appendLine(sb, null, product.shortDescription());
        appendLine(sb, null, product.description());
        String attributes = flattenAttributes(product.attributes());
        if (!attributes.isEmpty()) {
            appendLine(sb, "Характеристики", attributes);
        }
        String text = sb.toString().strip();
        return text.length() > MAX_TEXT_CHARS ? text.substring(0, MAX_TEXT_CHARS) : text;
    }

    /** Stable SHA-256 hex over the canonical field set; identical hash ⇒ no re-embed. */
    public String contentHash(RemoteIndexProductDto product, String categoryName) {
        String canonical = String.join("",
                nullSafe(product.name()),
                nullSafe(product.slug()),
                nullSafe(product.shortDescription()),
                nullSafe(product.description()),
                String.valueOf(product.categoryId()),
                nullSafe(categoryName),
                String.valueOf(product.regionId()),
                String.valueOf(product.price()),
                nullSafe(product.currency()),
                nullSafe(product.primaryImageUrl()),
                flattenAttributes(product.attributes()));
        return sha256Hex(canonical);
    }

    private void appendLine(StringBuilder sb, String label, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        if (label != null) {
            sb.append(label).append(": ");
        }
        sb.append(value.strip()).append('\n');
    }

    /** Deterministic {@code key=value; ...} rendering (keys sorted) so the hash is order-independent. */
    private String flattenAttributes(Map<String, Object> attributes) {
        if (attributes == null || attributes.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Object> entry : new TreeMap<>(attributes).entrySet()) {
            Object value = entry.getValue();
            if (value == null || String.valueOf(value).isBlank() || "{}".equals(String.valueOf(value))) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append("; ");
            }
            sb.append(entry.getKey()).append('=').append(value);
        }
        return sb.toString();
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }

    private String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
