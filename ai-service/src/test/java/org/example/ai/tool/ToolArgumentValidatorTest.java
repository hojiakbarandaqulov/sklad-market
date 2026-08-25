package org.example.ai.tool;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ToolArgumentValidatorTest {

    private static final Map<String, Object> SCHEMA = Map.of(
            "type", "OBJECT",
            "properties", Map.of(
                    "slug", Map.of("type", "STRING"),
                    "page", Map.of("type", "INTEGER"),
                    "price", Map.of("type", "NUMBER"),
                    "status", Map.of("type", "STRING", "enum", List.of("ACTIVE", "ARCHIVED")),
                    "slugs", Map.of("type", "ARRAY", "items", Map.of("type", "STRING")),
                    "filter", Map.of("type", "OBJECT", "properties", Map.of(
                            "regionId", Map.of("type", "INTEGER")), "required", List.of("regionId"))),
            "required", List.of("slug"));

    @Test
    void validate_allValidArgs_doesNotThrow() {
        Map<String, Object> args = Map.of("slug", "cement-m500", "page", 1, "price", 19.5, "status", "ACTIVE");

        assertThatCode(() -> ToolArgumentValidator.validate(SCHEMA, args)).doesNotThrowAnyException();
    }

    @Test
    void validate_missingRequiredArg_throwsToolArgumentException() {
        Map<String, Object> args = Map.of("page", 1);

        assertThatThrownBy(() -> ToolArgumentValidator.validate(SCHEMA, args))
                .isInstanceOf(ToolArgumentException.class)
                .hasMessageContaining("slug");
    }

    @Test
    void validate_unknownArg_throwsToolArgumentException() {
        Map<String, Object> args = Map.of("slug", "x", "bogus", "value");

        assertThatThrownBy(() -> ToolArgumentValidator.validate(SCHEMA, args))
                .isInstanceOf(ToolArgumentException.class)
                .hasMessageContaining("bogus");
    }

    @Test
    void validate_wrongType_throwsToolArgumentException() {
        Map<String, Object> args = Map.of("slug", "x", "page", "not-a-number");

        assertThatThrownBy(() -> ToolArgumentValidator.validate(SCHEMA, args))
                .isInstanceOf(ToolArgumentException.class)
                .hasMessageContaining("page");
    }

    @Test
    void validate_badEnumValue_throwsToolArgumentException_notAnUncheckedException() {
        Map<String, Object> args = Map.of("slug", "x", "status", "DELETED");

        assertThatThrownBy(() -> ToolArgumentValidator.validate(SCHEMA, args))
                .isInstanceOf(ToolArgumentException.class)
                .hasMessageContaining("status");
    }

    @Test
    void validate_nullArgsMap_throwsToolArgumentException() {
        assertThatThrownBy(() -> ToolArgumentValidator.validate(SCHEMA, null))
                .isInstanceOf(ToolArgumentException.class);
    }

    @Test
    void validate_arrayItemsRecursivelyRejectsObjectHiddenInsideStringArray() {
        Map<String, Object> args = Map.of(
                "slug", "x",
                "slugs", List.of(Map.of("x", "alice@example.com")));

        assertThatThrownBy(() -> ToolArgumentValidator.validate(SCHEMA, args))
                .isInstanceOf(ToolArgumentException.class)
                .hasMessageContaining("slugs[0]");
    }

    @Test
    void validate_nestedObjectRejectsUnknownFieldsAndRequiresDeclaredFields() {
        assertThatThrownBy(() -> ToolArgumentValidator.validate(
                SCHEMA, Map.of("slug", "x", "filter", Map.of("contact", "+998901234567"))))
                .isInstanceOf(ToolArgumentException.class)
                .hasMessageContaining("regionId");

        assertThatCode(() -> ToolArgumentValidator.validate(
                SCHEMA, Map.of("slug", "x", "filter", Map.of("regionId", 12))))
                .doesNotThrowAnyException();
    }
}
