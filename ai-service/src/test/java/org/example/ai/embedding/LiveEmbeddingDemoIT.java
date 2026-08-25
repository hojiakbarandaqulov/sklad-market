package org.example.ai.embedding;

import org.example.ai.provider.gemini.GeminiEmbeddingProvider;
import org.example.dto.SearchResultItem;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * LIVE, opt-in end-to-end demo of the ML value: real {@code gemini-embedding-001} (768-dim,
 * MRL-truncated + renormalized) over realistic multilingual fixtures, real pgvector cosine ranking,
 * proving (a) a cross-lingual hit (Russian query retrieves an Uzbek-titled product) and (b)
 * content-based "similar products". Env-gated: runs ONLY when {@code GEMINI_API_KEY} is set, so the
 * normal suite skips it (no key, no cost). Prints the ranked results for the Phase-5 report.
 *
 * <p>Run with: {@code GEMINI_API_KEY=... ./gradlew test --tests '*LiveEmbeddingDemoIT'}.
 * The live skladmarket.uz catalog holds only placeholder products, so realistic fixtures are seeded
 * here (the user opted for this over reindexing junk data).
 */
@EnabledIfEnvironmentVariable(named = "GEMINI_API_KEY", matches = ".+")
class LiveEmbeddingDemoIT {

    private static PostgreSQLContainer<?> postgres;
    private static EmbeddingSearchService searchService;
    private static ProductEmbeddingRepository repository;
    private static GeminiEmbeddingProvider provider;

    private record Fixture(long id, String slug, String name, long categoryId, String text) {
    }

    // Realistic wholesale fixtures across ru/uz/en; ids/categories mirror the real wire shapes.
    private static final List<Fixture> FIXTURES = List.of(
            new Fixture(1, "cement-m500-ru", "Цемент М500", 10,
                    "Цемент М500 портландцемент, мешок 50 кг, для бетона, стяжки и фундамента. Оптом со склада."),
            new Fixture(2, "sement-m400-uz", "Sement M400", 10,
                    "Sement M400 portlandsement, 50 kg qop, beton, poydevor va qurilish ishlari uchun. Ulgurji."),
            new Fixture(3, "rice-basmati-ru", "Рис басмати", 20,
                    "Рис басмати длиннозёрный, высший сорт, оптом, мешок 25 кг. Доставка по Узбекистану."),
            new Fixture(4, "guruch-basmati-uz", "Basmati guruch", 20,
                    "Basmati guruch uzun donli, oliy nav, ulgurji narxda, 25 kg qop. Butun O'zbekiston bo'ylab yetkazib berish."),
            new Fixture(5, "rebar-12mm-en", "Steel rebar 12mm", 30,
                    "Steel rebar 12mm ribbed reinforcement bar for reinforced concrete construction, wholesale, per ton."),
            new Fixture(6, "sugar-white-ru", "Сахар белый", 40,
                    "Сахар белый кристаллический, оптом, мешок 50 кг. Пищевой, для производства и торговли."));

    @BeforeAll
    static void setUp() {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable(), "Docker required for the live demo");
        postgres = new PostgreSQLContainer<>(
                DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));
        postgres.start();
        DriverManagerDataSource ds = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        ds.setDriverClassName("org.postgresql.Driver");
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        jdbc.execute("CREATE EXTENSION IF NOT EXISTS vector");
        jdbc.execute("CREATE TABLE product_embedding ("
                + "product_id bigint PRIMARY KEY, slug varchar(255) NOT NULL, name varchar(512) NOT NULL, "
                + "category_id bigint, region_id bigint, price numeric, currency varchar(16), image_url text, "
                + "content_hash varchar(64) NOT NULL, embedding vector(768) NOT NULL, indexed_at timestamptz DEFAULT now())");
        jdbc.execute("CREATE INDEX ON product_embedding USING hnsw (embedding vector_cosine_ops)");

        repository = new ProductEmbeddingRepository(jdbc);
        provider = new GeminiEmbeddingProvider(System.getenv("GEMINI_API_KEY"), "", 60, "gemini-embedding-001", 768, 32);
        searchService = new EmbeddingSearchService(provider, repository, 0.05);

        long t0 = System.currentTimeMillis();
        List<float[]> vectors = provider.embedDocuments(FIXTURES.stream().map(Fixture::text).toList());
        long embedMs = System.currentTimeMillis() - t0;
        for (int i = 0; i < FIXTURES.size(); i++) {
            Fixture f = FIXTURES.get(i);
            repository.upsert(new ProductEmbeddingRow(f.id(), f.slug(), f.name(), f.categoryId(), 1L, 1000.0 + f.id(), "UZS",
                    "hash-" + f.id(), vectors.get(i)));
        }
        System.out.println("\n[LIVE DEMO] Embedded " + FIXTURES.size() + " fixtures (768-dim) in " + embedMs + " ms ("
                + (embedMs / FIXTURES.size()) + " ms/doc), index size = " + repository.count());
    }

    @Test
    void crossLingualSearch_retrievesOppositeLanguageProduct() {
        // Uzbek query must rank a rice product first AND surface the Russian-titled rice (cross-lingual).
        String uzQuery = "arzon guruch ulgurji"; // "cheap rice wholesale"
        long t0 = System.currentTimeMillis();
        List<SearchResultItem> uzHits = searchService.search(uzQuery, 5);
        print("SEARCH q='" + uzQuery + "' (" + (System.currentTimeMillis() - t0) + " ms, embed+query+rank)", uzHits);
        assertThat(uzHits).isNotEmpty();
        assertThat(uzHits.get(0).categoryId()).isEqualTo(20L); // top hit is a rice product (category 20)
        assertThat(topSlugs(uzHits, 3)).contains("rice-basmati-ru"); // Russian rice reached via an Uzbek query

        // Russian query must surface the Uzbek-titled rice (the plan's headline cross-lingual case).
        String ruQuery = "Рис оптом со склада"; // "rice wholesale from the warehouse"
        List<SearchResultItem> ruHits = searchService.search(ruQuery, 5);
        print("SEARCH q='" + ruQuery + "'", ruHits);
        assertThat(ruHits.get(0).categoryId()).isEqualTo(20L);
        assertThat(topSlugs(ruHits, 3)).contains("guruch-basmati-uz"); // Uzbek rice reached via a Russian query
    }

    private List<String> topSlugs(List<SearchResultItem> hits, int n) {
        return hits.stream().limit(n).map(SearchResultItem::slug).toList();
    }

    @Test
    void similarProducts_findsCrossLingualNeighbour() {
        List<SearchResultItem> similar = searchService.similarBySlug("cement-m500-ru", 5);
        print("SIMILAR to 'cement-m500-ru' (Цемент М500)", similar);
        // The nearest neighbour of the Russian cement listing should be the Uzbek cement listing.
        assertThat(similar).isNotEmpty();
        assertThat(similar.get(0).slug()).isEqualTo("sement-m400-uz");
        assertThat(similar).noneSatisfy(h -> assertThat(h.slug()).isEqualTo("cement-m500-ru")); // self excluded
    }

    private void print(String header, List<SearchResultItem> hits) {
        System.out.println("\n[LIVE DEMO] " + header);
        for (int i = 0; i < hits.size(); i++) {
            SearchResultItem h = hits.get(i);
            System.out.printf("  #%d  %-20s  %-16s  score=%.4f  cat=%d%n",
                    i + 1, h.slug(), h.name(), h.score(), h.categoryId());
        }
    }
}
