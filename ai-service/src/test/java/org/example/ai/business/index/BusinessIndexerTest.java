package org.example.ai.business.index;

import org.example.ai.business.remote.PublicBusinessClient;
import org.example.ai.business.remote.RemoteBusinessProduct;
import org.example.ai.business.remote.RemoteBusinessProductPage;
import org.example.ai.business.remote.RemoteCompanyPage;
import org.example.ai.business.remote.RemotePublicCompany;
import org.example.ai.provider.EmbeddingProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BusinessIndexerTest {

    private PublicBusinessClient client;
    private EmbeddingProvider embeddings;
    private BusinessTextBuilder textBuilder;
    private CompanyEmbeddingRepository repository;
    private JdbcTemplate jdbc;
    private ThreadPoolTaskScheduler scheduler;

    @BeforeEach
    void setUp() {
        client = mock(PublicBusinessClient.class);
        embeddings = mock(EmbeddingProvider.class);
        textBuilder = mock(BusinessTextBuilder.class);
        repository = mock(CompanyEmbeddingRepository.class);
        jdbc = mock(JdbcTemplate.class);
        scheduler = mock(ThreadPoolTaskScheduler.class);
        when(repository.findAllContentHashes()).thenReturn(Map.of());
        when(repository.count()).thenReturn(1L);
        when(textBuilder.build(any(), anyList())).thenReturn("company capability text");
        when(textBuilder.hash(anyString(), any(), anyList(), anyList(), anyInt(), any(), any()))
                .thenReturn("hash");
        when(embeddings.embedDocuments(anyList())).thenReturn(List.of(new float[] {1f, 0f}));
    }

    @Test
    void incompleteCrawlRetainsUnseenRowsAndReportsPartial() {
        when(repository.findAllContentHashes()).thenReturn(Map.of(99L, "stale"));
        when(client.fetchVerifiedCompanies(1, 100)).thenReturn(
                new RemoteCompanyPage(List.of(company()), 2, 2L));
        when(client.fetchProducts(1, 100)).thenReturn(
                new RemoteBusinessProductPage(List.of(product()), 1, 100, 2L, 2));

        BusinessIndexer.Outcome outcome = indexer(1).reindex("test");

        assertThat(outcome).isEqualTo(BusinessIndexer.Outcome.PARTIAL);
        verify(repository, never()).upsert(any());
        verify(repository, never()).deleteByCompanyIds(any());
    }

    @Test
    void changingPagingMetadataCannotAuthorizeStaleDeletion() {
        when(repository.findAllContentHashes()).thenReturn(Map.of(99L, "stale"));
        when(client.fetchVerifiedCompanies(1, 100)).thenReturn(
                new RemoteCompanyPage(List.of(company()), 2, 2L, 0));
        when(client.fetchVerifiedCompanies(2, 100)).thenReturn(
                new RemoteCompanyPage(List.of(company(2L)), 2, 3L, 1));
        when(client.fetchProducts(1, 100)).thenReturn(
                new RemoteBusinessProductPage(List.of(), 1, 100, 0L, 0));
        when(embeddings.embedDocuments(anyList())).thenReturn(
                List.of(new float[] {1f, 0f}, new float[] {0f, 1f}));

        BusinessIndexer.Outcome outcome = indexer(10).reindex("test");

        assertThat(outcome).isEqualTo(BusinessIndexer.Outcome.PARTIAL);
        verify(repository, never()).deleteByCompanyIds(any());
    }

    @Test
    void completeCrawlDeletesOnlyTrulyStaleCompaniesAndIndexesCompaniesWithoutProducts() {
        when(repository.findAllContentHashes()).thenReturn(Map.of(99L, "stale"));
        when(client.fetchVerifiedCompanies(1, 100)).thenReturn(
                new RemoteCompanyPage(List.of(company()), 1, 1L));
        when(client.fetchProducts(1, 100)).thenReturn(
                new RemoteBusinessProductPage(List.of(), 1, 100, 0L, 0));

        BusinessIndexer.Outcome outcome = indexer(10).reindex("test");

        assertThat(outcome).isEqualTo(BusinessIndexer.Outcome.SUCCESS);
        ArgumentCaptor<CompanyEmbeddingRow> row = ArgumentCaptor.forClass(CompanyEmbeddingRow.class);
        verify(repository).upsert(row.capture());
        assertThat(row.getValue().companyId()).isEqualTo(1L);
        assertThat(row.getValue().productCount()).isZero();
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Set<Long>> deleted = ArgumentCaptor.forClass(Set.class);
        verify(repository).deleteByCompanyIds(deleted.capture());
        assertThat(deleted.getValue()).containsExactly(99L);
    }

    @Test
    void cardinalityMismatchPerformsNoPartialBatchUpsert() {
        when(client.fetchVerifiedCompanies(1, 100)).thenReturn(
                new RemoteCompanyPage(List.of(company()), 1, 1L));
        when(client.fetchProducts(1, 100)).thenReturn(
                new RemoteBusinessProductPage(List.of(product()), 1, 100, 1L, 1));
        when(embeddings.embedDocuments(anyList())).thenReturn(List.of());

        BusinessIndexer.Outcome outcome = indexer(10).reindex("test");

        assertThat(outcome).isEqualTo(BusinessIndexer.Outcome.FAILURE);
        verify(repository, never()).upsert(any());
    }

    @Test
    void nullVectorPerformsNoBatchUpsert() {
        when(client.fetchVerifiedCompanies(1, 100)).thenReturn(
                new RemoteCompanyPage(List.of(company()), 1, 1L));
        when(client.fetchProducts(1, 100)).thenReturn(
                new RemoteBusinessProductPage(List.of(product()), 1, 100, 1L, 1));
        when(embeddings.embedDocuments(anyList())).thenReturn(java.util.Collections.singletonList(null));

        BusinessIndexer.Outcome outcome = indexer(10).reindex("test");

        assertThat(outcome).isEqualTo(BusinessIndexer.Outcome.FAILURE);
        verify(repository, never()).upsert(any());
    }

    @Test
    void schedulerRejectionReleasesReservation() {
        BusinessIndexer indexer = indexer(10);
        doThrow(new IllegalStateException("scheduler stopped")).when(scheduler).execute(any(Runnable.class));

        assertThat(indexer.triggerAsyncReindex()).isFalse();
        assertThat(indexer.isRunning()).isFalse();
    }

    private BusinessIndexer indexer(int maxPages) {
        return new BusinessIndexer(client, embeddings, textBuilder, repository, jdbc, scheduler,
                true, 100, maxPages, 32);
    }

    private RemotePublicCompany company() {
        return company(1L);
    }

    private RemotePublicCompany company(long id) {
        return new RemotePublicCompany(id, "Acme " + id, "acme-" + id, null, "VERIFIED", false);
    }

    private RemoteBusinessProduct product() {
        return new RemoteBusinessProduct(11L, 1L, 7L, "Cement", "cement", "short", "description",
                100.0, "UZS", 1L, 3L, null, "APPROVED", true, 10L, 2L, Map.of());
    }
}
