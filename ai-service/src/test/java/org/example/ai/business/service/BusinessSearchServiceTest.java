package org.example.ai.business.service;

import org.example.ai.business.dto.BusinessContact;
import org.example.ai.business.dto.BusinessContactLookup;
import org.example.ai.business.dto.BusinessContactStatus;
import org.example.ai.business.dto.BusinessIndexFreshness;
import org.example.ai.business.dto.BusinessResultType;
import org.example.ai.business.dto.BusinessSearchCriteria;
import org.example.ai.business.dto.BusinessSearchResponse;
import org.example.ai.business.index.BusinessLexicalRepository;
import org.example.ai.business.index.CompanyEmbeddingRepository;
import org.example.ai.business.index.CompanySearchHit;
import org.example.ai.business.remote.PublicBusinessClient;
import org.example.ai.business.remote.RemoteBusinessProduct;
import org.example.ai.business.remote.RemoteBusinessProductPage;
import org.example.ai.business.remote.RemoteCompanyPage;
import org.example.ai.business.remote.RemotePublicCompany;
import org.example.ai.embedding.EmbeddingSearchHit;
import org.example.ai.embedding.ProductEmbeddingRepository;
import org.example.ai.tool.ToolExecutionContext;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BusinessSearchServiceTest {

    private final BusinessQueryEmbeddingService embeddings = mock(BusinessQueryEmbeddingService.class);
    private final ProductEmbeddingRepository products = mock(ProductEmbeddingRepository.class);
    private final CompanyEmbeddingRepository companies = mock(CompanyEmbeddingRepository.class);
    private final BusinessLexicalRepository lexical = mock(BusinessLexicalRepository.class);
    private final PublicBusinessClient liveCatalog = mock(PublicBusinessClient.class);
    private final PublicCompanyContactHydrator contacts = mock(PublicCompanyContactHydrator.class);
    private final BusinessIndexFreshnessService freshness = mock(BusinessIndexFreshnessService.class);
    private final BusinessSearchService service = new BusinessSearchService(
            embeddings, products, companies, lexical, liveCatalog, contacts, freshness);
    private final ToolExecutionContext context = new ToolExecutionContext(null, "buyer", "jwt", Set.of("BUYER"), "uz");

    @Test
    void combinesTypedProductsAndCompaniesAndHydratesOnlyPublicCompanyContact() {
        float[] vector = {1f, 0f, 0f};
        when(embeddings.embed("cement supplier")).thenReturn(vector);
        when(products.searchFiltered(vector, 7L, 3L, 80.0, 160.0, "UZS", 120)).thenReturn(List.of(
                new EmbeddingSearchHit(1L, "cement", "Cement", 7L, 3L, 100.0, "UZS", 0.88)));
        when(lexical.searchProducts("cement supplier", 7L, 3L, 80.0, 160.0, "UZS", 120)).thenReturn(List.of(
                new EmbeddingSearchHit(1L, "cement", "Cement", 7L, 3L, 100.0, "UZS", 1.0)));
        when(companies.searchFiltered(vector, 7L, 3L, 120)).thenReturn(List.of(
                new CompanySearchHit(9L, "acme", "Acme Cement", "VERIFIED", List.of(7L),
                        List.of(3L), 12, 90.0, 150.0, 0.92)));
        when(contacts.hydrateBatch(List.of("acme"), context)).thenReturn(
                Map.of("acme", BusinessContactLookup.available(
                        new BusinessContact("+9981", null, "acme.uz", "Tashkent"))));
        when(freshness.snapshot(true, true)).thenReturn(fresh());

        BusinessSearchResponse response = service.search(new BusinessSearchCriteria(
                "cement supplier", Set.of(BusinessResultType.PRODUCT, BusinessResultType.COMPANY),
                7L, 3L, 80.0, 160.0, "uzs", 10), context);

        assertThat(response.items()).hasSize(2);
        assertThat(response.items()).extracting(item -> item.type()).containsExactly(
                BusinessResultType.PRODUCT, BusinessResultType.COMPANY);
        assertThat(response.items().get(1).contact().website()).isEqualTo("acme.uz");
        assertThat(response.items().get(1).contactStatus()).isEqualTo(BusinessContactStatus.AVAILABLE);
        assertThat(response.items().get(1).reasons()).contains(
                "CATEGORY_MATCH", "REGION_MATCH", "INDEXED_AS_VERIFIED");
        assertThat(response.items().get(1).minPrice()).isNull();
        assertThat(response.scoreMeaning()).contains("not a guarantee");
        assertThat(response.indexFreshness().stale()).isFalse();
    }

    @Test
    void appliesRequestedFiltersWithoutReportingNonmatchingCandidates() {
        float[] vector = {1f};
        when(embeddings.embed("rice")).thenReturn(vector);
        when(products.searchFiltered(vector, 99L, null, null, null, null, 120)).thenReturn(List.of());
        when(lexical.searchProducts("rice", 99L, null, null, null, null, 120)).thenReturn(List.of());
        when(freshness.snapshot(true, false)).thenReturn(fresh());

        BusinessSearchResponse response = service.search(new BusinessSearchCriteria(
                "rice", Set.of(BusinessResultType.PRODUCT), 99L, null, null, null, null, 10), context);

        assertThat(response.items()).isEmpty();
        assertThat(response.count()).isZero();
        verify(products).searchFiltered(vector, 99L, null, null, null, null, 120);
    }

    @Test
    void fallsBackToLocalLexicalSearchWhenEmbeddingProviderIsUnavailable() {
        when(embeddings.embed("cement supplier")).thenThrow(new IllegalStateException("provider down"));
        when(lexical.searchProducts("cement supplier", null, null, null, null, null, 120)).thenReturn(List.of(
                new EmbeddingSearchHit(1L, "cement", "Cement", 7L, 3L, 100.0, "UZS", 0.92)));
        when(freshness.snapshot(true, false)).thenReturn(fresh());

        BusinessSearchResponse response = service.search(new BusinessSearchCriteria(
                "cement supplier", Set.of(BusinessResultType.PRODUCT), null, null,
                null, null, null, 10), context);

        assertThat(response.items()).singleElement().satisfies(item -> {
            assertThat(item.name()).isEqualTo("Cement");
            assertThat(item.reasons()).contains("LEXICAL_NAME_OR_SLUG_MATCH");
        });
        verify(products, never()).searchFiltered(any(), any(), any(), any(), any(), any(), anyInt());
    }

    @Test
    void fallsBackToLivePublicCatalogWhileAiIndexIsEmpty() {
        when(embeddings.embed("cement")).thenThrow(new IllegalStateException("provider not configured"));
        when(liveCatalog.searchPublicProducts("cement", 30)).thenReturn(new RemoteBusinessProductPage(
                List.of(new RemoteBusinessProduct(1L, 9L, 7L, "Cement", "cement", null, null,
                        100.0, "UZS", 1L, 3L, null, "APPROVED", true, 0L, 0L, Map.of())),
                1, 30, 1L, 1));
        when(liveCatalog.searchVerifiedCompanies("cement", 30)).thenReturn(new RemoteCompanyPage(
                List.of(new RemotePublicCompany(9L, "Cement Company", "cement-company", null,
                        "VERIFIED", false)), 1, 1L, 0));
        when(contacts.hydrateBatch(List.of("cement-company"), context)).thenReturn(Map.of());
        when(freshness.snapshot(true, true)).thenReturn(new BusinessIndexFreshness(
                null, true, "product=NEVER_RUN;company=NEVER_RUN", "warming up"));

        BusinessSearchResponse response = service.search(new BusinessSearchCriteria(
                "cement", Set.of(BusinessResultType.PRODUCT, BusinessResultType.COMPANY), null, null,
                null, null, null, 10), context);

        assertThat(response.items()).extracting(item -> item.type()).containsExactly(
                BusinessResultType.COMPANY, BusinessResultType.PRODUCT);
        assertThat(response.items()).allSatisfy(item ->
                assertThat(item.reasons()).anyMatch(reason -> reason.startsWith("LIVE_")));
    }

    @Test
    void rejectsNonFinitePriceFilters() {
        assertThatThrownBy(() -> new BusinessSearchCriteria(
                "rice", Set.of(BusinessResultType.PRODUCT), null, null,
                Double.NaN, null, "UZS", 10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("finite");
    }

    @Test
    void rejectsPriceFilterForCompanyOnlySearch() {
        assertThatThrownBy(() -> new BusinessSearchCriteria(
                "supplier", Set.of(BusinessResultType.COMPANY), null, null,
                100.0, 200.0, "UZS", 10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("individual product");
    }

    @Test
    void rejectsPriceFilterWithoutCurrency() {
        assertThatThrownBy(() -> new BusinessSearchCriteria(
                "rice", Set.of(BusinessResultType.PRODUCT), null, null,
                100.0, 200.0, null, 10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("currency is required");
    }

    private BusinessIndexFreshness fresh() {
        return new BusinessIndexFreshness(Instant.parse("2026-08-11T00:00:00Z"), false,
                "product=SUCCESS;company=SUCCESS", "fresh");
    }
}
