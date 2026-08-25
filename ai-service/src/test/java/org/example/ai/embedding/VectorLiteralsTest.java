package org.example.ai.embedding;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VectorLiteralsTest {

    @Test
    void toLiteral_wrapsInBracketsAndCommaSeparatesWithDotDecimal() {
        String literal = VectorLiterals.toLiteral(new float[] {0.5f, -0.25f, 1.0f});
        assertThat(literal).isEqualTo("[0.5,-0.25,1.0]");
    }

    @Test
    void toLiteral_emptyVector() {
        assertThat(VectorLiterals.toLiteral(new float[] {})).isEqualTo("[]");
    }

    @Test
    void toLiteral_neverUsesCommaAsDecimalSeparator() {
        // The formatting must be locale-independent (Float.toString always emits a '.'), otherwise a
        // comma decimal separator would corrupt the pgvector literal on some default locales.
        String literal = VectorLiterals.toLiteral(new float[] {0.123456f, 0.654321f});
        assertThat(literal).contains(".").doesNotContain(",,");
        assertThat(literal.chars().filter(c -> c == ',').count()).isEqualTo(1);
    }
}
