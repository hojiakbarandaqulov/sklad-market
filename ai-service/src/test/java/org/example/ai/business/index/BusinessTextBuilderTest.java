package org.example.ai.business.index;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BusinessTextBuilderTest {

    private final BusinessTextBuilder builder = new BusinessTextBuilder();

    @Test
    void aggregateProjectionChangesInvalidateContentHash() {
        String original = builder.hash("company: Acme", "VERIFIED", List.of(7L), List.of(3L),
                2, 100.0, 200.0);
        String repriced = builder.hash("company: Acme", "VERIFIED", List.of(7L), List.of(3L),
                2, 120.0, 220.0);
        String additionalProduct = builder.hash("company: Acme", "VERIFIED", List.of(7L), List.of(3L),
                3, 100.0, 200.0);

        assertThat(repriced).isNotEqualTo(original);
        assertThat(additionalProduct).isNotEqualTo(original);
    }
}
