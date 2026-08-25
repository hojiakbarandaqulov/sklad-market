package org.example.ai.business.index;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BusinessLexicalRepositoryTest {

    @Test
    void extractsPhraseAndMultilingualTermsForFallback() {
        String cyrillicCement = "\u0446\u0435\u043c\u0435\u043d\u0442";
        assertThat(BusinessLexicalRepository.terms("  " + cyrillicCement + " cement / supplier  "))
                .contains(cyrillicCement + " cement / supplier", cyrillicCement, "cement", "supplier");
    }

    @Test
    void exactAndPrefixMatchesScoreAboveContainsMatches() {
        assertThat(BusinessLexicalRepository.lexicalScore("Cement", "cement", "cement")).isEqualTo(1.0);
        assertThat(BusinessLexicalRepository.lexicalScore("Cement House", "cement-house", "cement"))
                .isGreaterThan(BusinessLexicalRepository.lexicalScore("Acme Cement House", "acme", "cement"));
    }
}
