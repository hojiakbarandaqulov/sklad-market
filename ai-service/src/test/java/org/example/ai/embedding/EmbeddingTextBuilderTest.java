package org.example.ai.embedding;

import org.example.ai.gateway.dto.RemoteIndexProductDto;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EmbeddingTextBuilderTest {

    private final EmbeddingTextBuilder builder = new EmbeddingTextBuilder();

    private RemoteIndexProductDto product(String name, String desc, Map<String, Object> attributes) {
        return new RemoteIndexProductDto(
                1L, name, "slug-1", "short " + name, desc, 100.0, "UZS", 5L, 3L, "APPROVED", true, attributes);
    }

    @Test
    void buildText_includesNameCategoryDescriptionAndAttributes() {
        String text = builder.buildText(product("Цемент", "Портландцемент М500", Map.of("вес", "50кг")), "Стройматериалы");
        assertThat(text)
                .contains("Цемент")
                .contains("Стройматериалы")
                .contains("Портландцемент М500")
                .contains("вес=50кг");
    }

    @Test
    void contentHash_isStableForUnchangedInput() {
        RemoteIndexProductDto p = product("Цемент", "desc", Map.of("a", "1"));
        assertThat(builder.contentHash(p, "Cat")).isEqualTo(builder.contentHash(p, "Cat"));
    }

    @Test
    void contentHash_isOrderIndependentForAttributes() {
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("x", "1");
        a.put("y", "2");
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("y", "2");
        b.put("x", "1");
        assertThat(builder.contentHash(product("N", "d", a), "C"))
                .isEqualTo(builder.contentHash(product("N", "d", b), "C"));
    }

    @Test
    void contentHash_changesWhenAnyEmbeddedFieldChanges() {
        RemoteIndexProductDto base = product("Цемент", "desc", Map.of("a", "1"));
        String baseHash = builder.contentHash(base, "Cat");

        assertThat(builder.contentHash(product("Цемент-2", "desc", Map.of("a", "1")), "Cat")).isNotEqualTo(baseHash);
        assertThat(builder.contentHash(product("Цемент", "desc-2", Map.of("a", "1")), "Cat")).isNotEqualTo(baseHash);
        assertThat(builder.contentHash(product("Цемент", "desc", Map.of("a", "2")), "Cat")).isNotEqualTo(baseHash);
        assertThat(builder.contentHash(base, "Cat-renamed")).isNotEqualTo(baseHash);
    }

    @Test
    void flattenAttributes_ignoresEmptyObjectPlaceholders() {
        // The live catalog exposes junk like {"additionalProp1":{}} — those must not pollute the text.
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("additionalProp1", Map.of());
        attributes.put("цвет", "серый");
        String text = builder.buildText(product("N", "d", attributes), "C");
        assertThat(text).contains("цвет=").doesNotContain("additionalProp1");
    }
}
