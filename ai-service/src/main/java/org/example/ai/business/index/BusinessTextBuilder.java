package org.example.ai.business.index;

import org.example.ai.business.remote.RemoteBusinessProduct;
import org.example.ai.business.remote.RemotePublicCompany;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Component
public class BusinessTextBuilder {

    private static final int MAX_TEXT_CHARS = 14_000;
    private static final int MAX_PRODUCTS = 80;

    public String build(RemotePublicCompany company, List<RemoteBusinessProduct> products) {
        StringBuilder text = new StringBuilder();
        append(text, "company", company.name());
        products.stream().sorted(Comparator.comparing(RemoteBusinessProduct::id)).limit(MAX_PRODUCTS).forEach(product -> {
            append(text, "product", product.name());
            append(text, "shortDescription", product.shortDescription());
            append(text, "description", product.description());
            if (product.attributes() != null && !product.attributes().isEmpty()) {
                Map<String, Object> sorted = new TreeMap<>(product.attributes());
                append(text, "attributes", sorted.toString());
            }
        });
        return text.length() <= MAX_TEXT_CHARS ? text.toString() : text.substring(0, MAX_TEXT_CHARS);
    }

    public String hash(
            String text,
            String status,
            List<Long> categories,
            List<Long> regions,
            int productCount,
            Double minPrice,
            Double maxPrice) {
        try {
            // These aggregates are stored beside the vector and therefore belong to the content
            // hash even when the embedding text itself is unchanged (for example, a price edit).
            String canonical = text + "\nstatus=" + status + "\ncategories=" + categories
                    + "\nregions=" + regions + "\nproductCount=" + productCount
                    + "\nminPrice=" + minPrice + "\nmaxPrice=" + maxPrice;
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Could not hash business index document", e);
        }
    }

    private void append(StringBuilder out, String label, Object value) {
        if (value == null) return;
        String string = String.valueOf(value).trim();
        if (!string.isEmpty()) out.append(label).append(": ").append(string).append('\n');
    }
}
