package org.example.ai.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.ai.business.dto.BusinessContact;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ToolAuditSanitizerTest {

    @Test
    void redactsFreeTextAndPiiAtEveryNestingLevelButKeepsStructuralArguments() {
        Map<String, Object> sanitized = ToolAuditSanitizer.sanitize(Map.of(
                "needText", "call +998901234567",
                "category", "cement",
                "quantity", 100,
                "items", List.of(Map.of(
                        "productSlug", "cement-m500",
                        "contactPhone", "+998901234567",
                        "comment", "private"))));

        assertThat(sanitized).containsEntry("needText", "[REDACTED]")
                .containsEntry("category", "[REDACTED]")
                .containsEntry("quantity", 100);
        @SuppressWarnings("unchecked")
        Map<String, Object> item = (Map<String, Object>) ((List<?>) sanitized.get("items")).get(0);
        assertThat(item).containsEntry("productSlug", "[REDACTED]")
                .containsEntry("contactPhone", "[REDACTED]")
                .containsEntry("comment", "[REDACTED]");
    }

    @Test
    void buyingIntentAuditAlsoRedactsCategoryAndRegionFreeText() {
        Map<String, Object> sanitized = ToolAuditSanitizer.sanitize("draft_buying_intent", Map.of(
                "category", "Cement from John Doe",
                "region", "Street 12, Tashkent",
                "quantityUnit", "@private_buyer",
                "quantity", 50,
                "currency", "UZS"));

        assertThat(sanitized).containsEntry("category", "[REDACTED]")
                .containsEntry("region", "[REDACTED]")
                .containsEntry("quantityUnit", "[REDACTED]")
                .containsEntry("quantity", 50)
                .containsEntry("currency", "[REDACTED]");
    }

    @Test
    void rejectedOrUnknownArgumentsDiscardAllModelControlledKeysAndValues() {
        Map<String, Object> sanitized = ToolAuditSanitizer.sanitizeRejected("unknown_tool", Map.of(
                "limit", "alice@example.com",
                "unknown", "+998 90 123 45 67",
                "page", 3,
                "enabled", true,
                "nested", Map.of("safeLookingKey", "https://private.example"),
                "items", List.of("@private_handle", 7)));

        assertThat(sanitized).hasSize(2)
                .containsEntry("redacted", true)
                .containsEntry("argumentCount", 6);
    }

    @Test
    void durableStructuredResultsDropHydratedPublicContactsButKeepCardData() {
        Map<String, Object> resultSet = Map.of(
                "kind", "business_search",
                "items", List.of(Map.of(
                        "slug", "acme",
                        "name", "Acme",
                        "contactStatus", "AVAILABLE",
                        "contactAvailable", true,
                        "contact", new BusinessContact("+9981", null, "https://acme.uz", "Tashkent"))));

        Map<String, Object> sanitized = ToolAuditSanitizer.sanitizeResultSetForPersistence(
                new ObjectMapper().findAndRegisterModules(), resultSet);

        @SuppressWarnings("unchecked")
        Map<String, Object> item = (Map<String, Object>) ((List<?>) sanitized.get("items")).get(0);
        assertThat(item).containsEntry("slug", "acme")
                .containsEntry("name", "Acme")
                .containsEntry("contactStatus", "NOT_CHECKED")
                .containsEntry("contactAvailable", false)
                .doesNotContainKeys("contact", "phonePrimary", "website", "address");
    }
}
