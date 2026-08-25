package org.example.ai.seller;

import org.example.ai.gateway.dto.RemoteCategoryAttributeDto;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PLAN.md Phase 6, C8: strict server-side validation of model-proposed attribute values against
 * the REAL {@code CategoryAttribute} schema (verified in category-service source: {@code DataType}
 * is exactly {@code TEXT|NUMBER|BOOLEAN|SELECT}; {@code optionsJson} has no enforced shape anywhere
 * on the platform). Nothing here may ever accept a value the real schema wouldn't.
 */
class CategoryAttributeSchemaTest {

    private static RemoteCategoryAttributeDto attribute(String code, String dataType, Boolean required, String optionsJson) {
        return new RemoteCategoryAttributeDto(1L, code, code + " label", dataType, required, false, optionsJson, 1);
    }

    @Test
    void parseOptions_plainStringArray_parsesEachEntry() {
        List<String> options = CategoryAttributeSchema.parseOptions("[\"25kg\",\"50kg\",\"1t\"]");
        assertThat(options).containsExactly("25kg", "50kg", "1t");
    }

    @Test
    void parseOptions_objectArrayWithValueField_extractsValue() {
        List<String> options = CategoryAttributeSchema.parseOptions(
                "[{\"value\":\"m400\",\"label\":\"M400\"},{\"value\":\"m500\",\"label\":\"M500\"}]");
        assertThat(options).containsExactly("m400", "m500");
    }

    @Test
    void parseOptions_objectArrayWithOnlyLabelField_fallsBackToLabel() {
        List<String> options = CategoryAttributeSchema.parseOptions("[{\"label\":\"Red\"},{\"label\":\"Blue\"}]");
        assertThat(options).containsExactly("Red", "Blue");
    }

    @Test
    void parseOptions_malformedOrNonArrayJson_degradesToEmptyList() {
        assertThat(CategoryAttributeSchema.parseOptions("not json at all")).isEmpty();
        assertThat(CategoryAttributeSchema.parseOptions("{\"not\":\"an array\"}")).isEmpty();
        assertThat(CategoryAttributeSchema.parseOptions(null)).isEmpty();
        assertThat(CategoryAttributeSchema.parseOptions("")).isEmpty();
    }

    @Test
    void isValidValue_text_requiresNonBlankString() {
        RemoteCategoryAttributeDto attr = attribute("material", "TEXT", true, null);
        assertThat(CategoryAttributeSchema.isValidValue(attr, "Portland cement", List.of())).isTrue();
        assertThat(CategoryAttributeSchema.isValidValue(attr, "  ", List.of())).isFalse();
        assertThat(CategoryAttributeSchema.isValidValue(attr, 42, List.of())).isFalse();
        assertThat(CategoryAttributeSchema.isValidValue(attr, null, List.of())).isFalse();
    }

    @Test
    void isValidValue_number_acceptsNumericStringsAndRejectsGarbage() {
        RemoteCategoryAttributeDto attr = attribute("weightKg", "NUMBER", true, null);
        assertThat(CategoryAttributeSchema.isValidValue(attr, 50, List.of())).isTrue();
        assertThat(CategoryAttributeSchema.isValidValue(attr, "50.5", List.of())).isTrue();
        assertThat(CategoryAttributeSchema.isValidValue(attr, "fifty", List.of())).isFalse();
        assertThat(CategoryAttributeSchema.isValidValue(attr, true, List.of())).isFalse();
    }

    @Test
    void isValidValue_boolean_acceptsBooleanAndBooleanLikeStrings() {
        RemoteCategoryAttributeDto attr = attribute("isOrganic", "BOOLEAN", false, null);
        assertThat(CategoryAttributeSchema.isValidValue(attr, true, List.of())).isTrue();
        assertThat(CategoryAttributeSchema.isValidValue(attr, "false", List.of())).isTrue();
        assertThat(CategoryAttributeSchema.isValidValue(attr, "maybe", List.of())).isFalse();
    }

    @Test
    void isValidValue_select_onlyAcceptsRealOptionsJsonMembers() {
        RemoteCategoryAttributeDto attr = attribute("grade", "SELECT", true, "[\"M400\",\"M500\"]");
        List<String> options = CategoryAttributeSchema.parseOptions(attr.optionsJson());

        assertThat(CategoryAttributeSchema.isValidValue(attr, "M500", options)).isTrue();
        assertThat(CategoryAttributeSchema.isValidValue(attr, "m500", options)).isTrue(); // case-insensitive match
        assertThat(CategoryAttributeSchema.isValidValue(attr, "M600", options)).isFalse(); // hallucinated value
        assertThat(CategoryAttributeSchema.isValidValue(attr, "M600", List.of())).isFalse(); // no options at all -> never valid
    }

    @Test
    void jsonSchemaProperty_select_includesRealOptionsAsEnum() {
        RemoteCategoryAttributeDto attr = attribute("grade", "SELECT", true, "[\"M400\",\"M500\"]");

        Map<String, Object> property = CategoryAttributeSchema.jsonSchemaProperty(attr);

        assertThat(property.get("type")).isEqualTo("STRING");
        assertThat(property.get("enum")).isEqualTo(List.of("M400", "M500"));
    }

    @Test
    void jsonSchemaProperty_numberAndBoolean_mapToRealJsonSchemaTypes() {
        assertThat(CategoryAttributeSchema.jsonSchemaProperty(attribute("weightKg", "NUMBER", true, null)).get("type"))
                .isEqualTo("NUMBER");
        assertThat(CategoryAttributeSchema.jsonSchemaProperty(attribute("isOrganic", "BOOLEAN", false, null)).get("type"))
                .isEqualTo("BOOLEAN");
        assertThat(CategoryAttributeSchema.jsonSchemaProperty(attribute("material", "TEXT", false, null)).get("type"))
                .isEqualTo("STRING");
    }

    @Test
    void jsonSchemaProperty_selectWithUnparsableOptions_omitsEnumConstraint() {
        Map<String, Object> property = CategoryAttributeSchema.jsonSchemaProperty(attribute("grade", "SELECT", true, "garbage"));

        assertThat(property.get("type")).isEqualTo("STRING");
        assertThat(property).doesNotContainKey("enum");
    }
}
