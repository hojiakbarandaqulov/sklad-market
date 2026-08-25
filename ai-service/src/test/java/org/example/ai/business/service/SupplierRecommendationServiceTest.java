package org.example.ai.business.service;

import org.example.ai.business.dto.BusinessContact;
import org.example.ai.business.dto.BusinessContactLookup;
import org.example.ai.business.dto.BusinessContactStatus;
import org.example.ai.business.dto.BusinessIndexFreshness;
import org.example.ai.business.dto.SupplierRecommendationResponse;
import org.example.ai.business.index.BusinessLexicalRepository;
import org.example.ai.business.index.CompanyEmbeddingRepository;
import org.example.ai.business.index.CompanySearchHit;
import org.example.ai.tool.ToolExecutionContext;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SupplierRecommendationServiceTest {

    @Test
    void ranksDeterministicallyAndExplainsGroundedMatches() {
        BuyerPreferenceService preferences = mock(BuyerPreferenceService.class);
        BusinessQueryEmbeddingService embeddings = mock(BusinessQueryEmbeddingService.class);
        CompanyEmbeddingRepository companies = mock(CompanyEmbeddingRepository.class);
        BusinessLexicalRepository lexical = mock(BusinessLexicalRepository.class);
        PublicCompanyContactHydrator contacts = mock(PublicCompanyContactHydrator.class);
        BusinessIndexFreshnessService freshness = mock(BusinessIndexFreshnessService.class);
        SupplierRecommendationService service = new SupplierRecommendationService(
                preferences, embeddings, companies, lexical, contacts, freshness);
        ToolExecutionContext context = new ToolExecutionContext(null, "buyer", "jwt", Set.of("BUYER"), "ru");
        float[] vector = {1f};
        when(preferences.resolve("bulk cement", context)).thenReturn(
                new BuyerPreferenceService.Preference("bulk cement", "EXPLICIT"));
        when(embeddings.embed("bulk cement")).thenReturn(vector);
        when(companies.searchSuppliers(vector, 7L, 3L, 150)).thenReturn(List.of(
                hit(2L, "second", 0.80, 15), hit(1L, "first", 0.90, 10)));
        when(contacts.hydrateBatch(List.of("first", "second"), context)).thenReturn(
                Map.of("first", BusinessContactLookup.available(
                        new BusinessContact("+998", null, null, null))));
        when(freshness.snapshot(false, true)).thenReturn(fresh());

        SupplierRecommendationResponse response = service.recommend(
                "bulk cement", 7L, 3L, null, null, 10, context);

        assertThat(response.personalizationSource()).isEqualTo("EXPLICIT");
        assertThat(response.items()).extracting(item -> item.companyId()).containsExactly(1L, 2L);
        assertThat(response.items().get(0).reasons()).contains(
                "MATCHED_EXPLICIT_NEED", "CATEGORY_MATCH", "REGION_MATCH", "INDEXED_AS_VERIFIED");
        assertThat(response.items().get(0).contact().phonePrimary()).isEqualTo("+998");
        assertThat(response.items().get(0).contactStatus()).isEqualTo(BusinessContactStatus.AVAILABLE);
        assertThat(response.items().get(1).contactStatus()).isEqualTo(BusinessContactStatus.NOT_CHECKED);
        assertThat(response.items().get(0).minPrice()).isNull();
        assertThat(response.disclaimer()).contains("not guaranteed");
        assertThat(response.disclaimer()).contains("different currencies");
    }

    @Test
    void hydratesPublicContactOnlyAfterRankingAndLimit() {
        BuyerPreferenceService preferences = mock(BuyerPreferenceService.class);
        BusinessQueryEmbeddingService embeddings = mock(BusinessQueryEmbeddingService.class);
        CompanyEmbeddingRepository companies = mock(CompanyEmbeddingRepository.class);
        BusinessLexicalRepository lexical = mock(BusinessLexicalRepository.class);
        PublicCompanyContactHydrator contacts = mock(PublicCompanyContactHydrator.class);
        BusinessIndexFreshnessService freshness = mock(BusinessIndexFreshnessService.class);
        SupplierRecommendationService service = new SupplierRecommendationService(
                preferences, embeddings, companies, lexical, contacts, freshness);
        ToolExecutionContext context = new ToolExecutionContext(null, "buyer", "jwt", Set.of("BUYER"), "uz");
        float[] vector = {1f};
        when(preferences.resolve(null, context)).thenReturn(
                new BuyerPreferenceService.Preference("generic", "COLD_START"));
        when(embeddings.embed("generic")).thenReturn(vector);
        when(companies.searchSuppliers(vector, null, null, 150)).thenReturn(List.of(
                hit(1L, "winner", 0.95, 5), hit(2L, "not-selected", 0.50, 5)));
        when(freshness.snapshot(false, true)).thenReturn(fresh());

        SupplierRecommendationResponse response = service.recommend(
                null, null, null, null, null, 1, context);

        assertThat(response.items()).singleElement().satisfies(item ->
                assertThat(item.reasons()).contains("GENERAL_CATALOG_RELEVANCE"));
        verify(contacts).hydrateBatch(List.of("winner"), context);
    }

    @Test
    void rejectsSupplierLevelPriceRangesAcrossCurrencies() {
        SupplierRecommendationService service = new SupplierRecommendationService(
                mock(BuyerPreferenceService.class), mock(BusinessQueryEmbeddingService.class),
                mock(CompanyEmbeddingRepository.class), mock(BusinessLexicalRepository.class),
                mock(PublicCompanyContactHydrator.class), mock(BusinessIndexFreshnessService.class));

        assertThatThrownBy(() -> service.recommend(
                "cement", null, null, 100.0, 200.0, 5, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("multiple currencies");
    }

    private CompanySearchHit hit(long id, String slug, double score, int products) {
        return new CompanySearchHit(id, slug, "Company " + id, "VERIFIED", List.of(7L), List.of(3L),
                products, 90.0, 150.0, score);
    }

    private BusinessIndexFreshness fresh() {
        return new BusinessIndexFreshness(Instant.parse("2026-08-11T00:00:00Z"), false,
                "company=SUCCESS", "fresh");
    }
}
