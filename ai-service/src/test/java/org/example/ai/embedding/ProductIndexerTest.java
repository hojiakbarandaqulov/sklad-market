package org.example.ai.embedding;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.example.ai.error.AiChatException;
import org.example.ai.error.AiErrorCode;
import org.example.ai.gateway.dto.RemoteIndexProductDto;
import org.example.ai.provider.EmbeddingProvider;
import org.example.entity.IndexRun;
import org.example.repository.IndexRunRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.ArgumentCaptor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Exercises the indexer orchestration against a WireMock catalog with a mocked embedding provider +
 * repositories: exclusion of non-visible products, hash-skip, removal of disappeared rows, and the
 * failure-isolation guarantees (downstream failure ⇒ FAILURE run, never a thrown exception;
 * per-batch failure ⇒ PARTIAL; concurrent triggers ⇒ SKIPPED).
 */
class ProductIndexerTest {

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance().build();

    private EmbeddingProvider embeddingProvider;
    private ProductEmbeddingRepository embeddingRepository;
    private IndexRunRepository indexRunRepository;
    private ThreadPoolTaskScheduler scheduler;
    private final EmbeddingTextBuilder textBuilder = new EmbeddingTextBuilder();

    @BeforeEach
    void setUp() {
        embeddingProvider = mock(EmbeddingProvider.class);
        embeddingRepository = mock(ProductEmbeddingRepository.class);
        indexRunRepository = mock(IndexRunRepository.class);
        when(embeddingRepository.findAllContentHashes()).thenReturn(new HashMap<>());
        // one 3-dim unit vector per input text
        when(embeddingProvider.embedDocuments(anyList())).thenAnswer(inv -> {
            List<String> texts = inv.getArgument(0);
            return texts.stream().map(t -> new float[] {1f, 0f, 0f}).collect(Collectors.toList());
        });
        scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.initialize();
        stubCategoriesEmpty();
    }

    @AfterEach
    void tearDown() {
        scheduler.shutdown();
    }

    private ProductIndexer indexer(int embedBatchSize) {
        return indexer(embedBatchSize, 1000);
    }

    private ProductIndexer indexer(int embedBatchSize, int maxPages) {
        PublicCatalogClient catalogClient = new PublicCatalogClient(wireMock.baseUrl(), 5);
        return new ProductIndexer(catalogClient, embeddingProvider, textBuilder, embeddingRepository,
                indexRunRepository, scheduler, true, 50, maxPages, embedBatchSize);
    }

    @Test
    void reindex_indexesOnlyPubliclyVisibleProducts() {
        stubProductsPage1(
                item(1, "Цемент", "APPROVED", true),
                item(2, "Кирпич PENDING", "PENDING", true),
                item(3, "Цемент удалённый", "APPROVED", false),  // soft-deleted: APPROVED but inactive
                item(4, "Песок", "APPROVED", true));

        ProductIndexer.IndexOutcome outcome = indexer(32).reindex("test");

        assertThat(outcome).isEqualTo(ProductIndexer.IndexOutcome.SUCCESS);
        ArgumentCaptor<ProductEmbeddingRow> rows = ArgumentCaptor.forClass(ProductEmbeddingRow.class);
        verify(embeddingRepository, org.mockito.Mockito.times(2)).upsert(rows.capture());
        assertThat(rows.getAllValues().stream().map(ProductEmbeddingRow::productId)).containsExactlyInAnyOrder(1L, 4L);
        assertRecordedStatus("SUCCESS");
    }

    @Test
    void reindex_skipsUnchangedByContentHash() {
        RemoteIndexProductDto dto = item(1, "Цемент", "APPROVED", true);
        // categoryName resolves to null (categories endpoint is empty), so the stored hash is computable here.
        String storedHash = textBuilder.contentHash(dto, null);
        when(embeddingRepository.findAllContentHashes()).thenReturn(new HashMap<>(Map.of(1L, storedHash)));
        stubProductsPage1(dto);

        indexer(32).reindex("test");

        verify(embeddingProvider, never()).embedDocuments(anyList());
        verify(embeddingRepository, never()).upsert(org.mockito.ArgumentMatchers.any());
        assertRecordedStatus("SUCCESS");
    }

    @Test
    void reindex_removesRowsForDisappearedProducts() {
        when(embeddingRepository.findAllContentHashes()).thenReturn(new HashMap<>(Map.of(1L, "old", 99L, "stale")));
        stubProductsPage1(item(1, "Цемент", "APPROVED", true));

        indexer(32).reindex("test");

        ArgumentCaptor<Set<Long>> deleted = ArgumentCaptor.forClass(Set.class);
        verify(embeddingRepository).deleteByProductIds(deleted.capture());
        assertThat(deleted.getValue()).containsExactly(99L);
    }

    @Test
    void reindex_downstreamFailureIsContainedAsFailureRun_neverThrows() {
        wireMock.stubFor(get(urlPathEqualTo("/api/v1/products/all"))
                .willReturn(aResponse().withStatus(500)));

        ProductIndexer.IndexOutcome outcome = indexer(32).reindex("test"); // must not throw

        assertThat(outcome).isEqualTo(ProductIndexer.IndexOutcome.FAILURE);
        verify(embeddingRepository, never()).upsert(org.mockito.ArgumentMatchers.any());
        assertRecordedStatus("FAILURE");
    }

    @Test
    void reindex_perBatchFailureIsIsolated_othersStillIndexed() {
        when(embeddingProvider.embedDocuments(anyList()))
                .thenReturn(List.of(new float[] {1f, 0f, 0f}))       // first batch ok
                .thenThrow(new AiChatException(AiErrorCode.PROVIDER_ERROR, "boom")); // second batch fails
        stubProductsPage1(item(1, "Цемент", "APPROVED", true), item(2, "Песок", "APPROVED", true));

        ProductIndexer.IndexOutcome outcome = indexer(1).reindex("test"); // batch size 1 -> one call per product

        assertThat(outcome).isEqualTo(ProductIndexer.IndexOutcome.PARTIAL);
        verify(embeddingRepository, org.mockito.Mockito.times(1)).upsert(org.mockito.ArgumentMatchers.any());
        assertRecordedStatus("PARTIAL");
    }

    @Test
    void reindex_incompleteCrawlNeverDeletesStaleRows() {
        when(embeddingRepository.findAllContentHashes()).thenReturn(new HashMap<>(Map.of(99L, "keep-me")));
        RemoteIndexProductDto product = item(1, "Cement", "APPROVED", true);
        String body = "{\"success\":true,\"data\":{\"items\":[" + itemJson(product)
                + "],\"page\":1,\"per_page\":50,\"total_elements\":2,\"total_pages\":2}}";
        wireMock.stubFor(get(urlPathEqualTo("/api/v1/products/all"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(body)));

        ProductIndexer.IndexOutcome outcome = indexer(32, 1).reindex("test");

        assertThat(outcome).isEqualTo(ProductIndexer.IndexOutcome.PARTIAL);
        verify(embeddingRepository, never()).deleteByProductIds(any());
        ArgumentCaptor<IndexRun> run = ArgumentCaptor.forClass(IndexRun.class);
        verify(indexRunRepository).save(run.capture());
        assertThat(run.getValue().getNotes()).contains("crawlComplete=false", "staleDeletion=skipped_incomplete_crawl");
    }

    @Test
    void reindex_inconsistentEmptyMetadataNeverDeletesExistingIndex() {
        when(embeddingRepository.findAllContentHashes()).thenReturn(new HashMap<>(Map.of(99L, "keep-me")));
        wireMock.stubFor(get(urlPathEqualTo("/api/v1/products/all"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{\"success\":true,\"data\":{\"items\":[],\"page\":1,"
                                + "\"per_page\":50,\"total_elements\":0,\"total_pages\":5}}")));

        ProductIndexer.IndexOutcome outcome = indexer(32).reindex("test");

        assertThat(outcome).isEqualTo(ProductIndexer.IndexOutcome.FAILURE);
        verify(embeddingRepository, never()).deleteByProductIds(any());
        assertRecordedStatus("FAILURE");
    }

    @Test
    void reindex_embeddingCardinalityMismatchRejectsWholeBatchBeforeAnyUpsert() {
        when(embeddingProvider.embedDocuments(anyList())).thenReturn(List.of(new float[] {1f, 0f, 0f}));
        stubProductsPage1(item(1, "Cement", "APPROVED", true), item(2, "Brick", "APPROVED", true));

        ProductIndexer.IndexOutcome outcome = indexer(32).reindex("test");

        assertThat(outcome).isEqualTo(ProductIndexer.IndexOutcome.FAILURE);
        verify(embeddingRepository, never()).upsert(any());
        assertRecordedStatus("FAILURE");
    }

    @Test
    void triggerAsyncReindex_reservesBeforeEnqueueSoRapidSecondTriggerIsRejected() {
        ThreadPoolTaskScheduler deferredScheduler = mock(ThreadPoolTaskScheduler.class);
        AtomicReference<Runnable> queued = new AtomicReference<>();
        doAnswer(invocation -> {
            queued.set(invocation.getArgument(0));
            return null;
        }).when(deferredScheduler).execute(any(Runnable.class));
        ProductIndexer indexer = new ProductIndexer(
                new PublicCatalogClient(wireMock.baseUrl(), 5), embeddingProvider, textBuilder,
                embeddingRepository, indexRunRepository, deferredScheduler, true, 50, 1000, 32);

        assertThat(indexer.triggerAsyncReindex()).isTrue();
        assertThat(indexer.isRunning()).isTrue();
        assertThat(indexer.triggerAsyncReindex()).isFalse();
        assertThat(queued.get()).isNotNull();
        verify(deferredScheduler, org.mockito.Mockito.times(1)).execute(any(Runnable.class));
    }

    @Test
    void concurrentReindex_isSkippedByTheOverlapGuard() throws Exception {
        CountDownLatch inEmbed = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(embeddingProvider.embedDocuments(anyList())).thenAnswer(inv -> {
            inEmbed.countDown();
            release.await(5, TimeUnit.SECONDS);
            return List.of(new float[] {1f, 0f, 0f});
        });
        stubProductsPage1(item(1, "Цемент", "APPROVED", true));
        ProductIndexer indexer = indexer(32);

        Thread background = new Thread(() -> indexer.reindex("bg"));
        background.start();
        assertThat(inEmbed.await(5, TimeUnit.SECONDS)).isTrue(); // first run is now mid-flight

        assertThat(indexer.isRunning()).isTrue();
        assertThat(indexer.reindex("second")).isEqualTo(ProductIndexer.IndexOutcome.SKIPPED);
        assertThat(indexer.triggerAsyncReindex()).isFalse();

        release.countDown();
        background.join(5000);
        assertThat(indexer.isRunning()).isFalse();
    }

    // --- helpers ---

    private void assertRecordedStatus(String expected) {
        ArgumentCaptor<IndexRun> run = ArgumentCaptor.forClass(IndexRun.class);
        verify(indexRunRepository).save(run.capture());
        assertThat(run.getValue().getLastStatus()).isEqualTo(expected);
    }

    private RemoteIndexProductDto item(long id, String name, String status, boolean active) {
        return new RemoteIndexProductDto(
                id, name, "slug-" + id, "short-" + id, "desc-" + id, 100.0 + id, "UZS", 5L, 3L, status, active, Map.of());
    }

    private void stubProductsPage1(RemoteIndexProductDto... items) {
        String itemsJson = java.util.Arrays.stream(items).map(this::itemJson).collect(Collectors.joining(","));
        String body = "{\"success\":true,\"data\":{\"items\":[" + itemsJson
                + "],\"page\":1,\"per_page\":50,\"total_elements\":" + items.length + ",\"total_pages\":1}}";
        wireMock.stubFor(get(urlPathEqualTo("/api/v1/products/all"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(body)));
    }

    private String itemJson(RemoteIndexProductDto p) {
        return String.format(
                "{\"id\":%d,\"name\":\"%s\",\"slug\":\"%s\",\"shortDescription\":\"%s\",\"description\":\"%s\","
                        + "\"price\":%s,\"currency\":\"%s\",\"regionId\":%d,\"categoryId\":%d,\"status\":\"%s\","
                        + "\"isActive\":%s,\"attributes\":{}}",
                p.id(), p.name(), p.slug(), p.shortDescription(), p.description(), p.price(), p.currency(),
                p.regionId(), p.categoryId(), p.status(), p.isActive());
    }

    private void stubCategoriesEmpty() {
        wireMock.stubFor(get(urlPathEqualTo("/api/v1/categories"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{\"success\":true,\"data\":{\"content\":[]}}")));
    }
}
