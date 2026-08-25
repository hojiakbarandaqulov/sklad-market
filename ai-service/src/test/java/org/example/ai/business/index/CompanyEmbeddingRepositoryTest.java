package org.example.ai.business.index;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class CompanyEmbeddingRepositoryTest {

    private static PostgreSQLContainer<?> postgres;
    private static JdbcTemplate jdbc;
    private static CompanyEmbeddingRepository repository;
    private static BusinessLexicalRepository lexical;

    @BeforeAll
    static void startContainer() {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable(), "Docker is unavailable");
        postgres = new PostgreSQLContainer<>(
                DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));
        postgres.start();
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        dataSource.setDriverClassName("org.postgresql.Driver");
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE EXTENSION IF NOT EXISTS vector");
        jdbc.execute("CREATE TABLE company_embedding (company_id bigint PRIMARY KEY, slug varchar(255) NOT NULL, "
                + "name varchar(512) NOT NULL, logo_url text, verification_status varchar(64), category_ids bigint[] NOT NULL, "
                + "region_ids bigint[] NOT NULL, product_count int NOT NULL, min_price numeric, max_price numeric, "
                + "content_hash varchar(64) NOT NULL, embedding vector(3) NOT NULL, indexed_at timestamptz DEFAULT now())");
        repository = new CompanyEmbeddingRepository(jdbc);
        lexical = new BusinessLexicalRepository(jdbc);
    }

    @AfterAll
    static void stopContainer() {
        if (postgres != null) postgres.stop();
    }

    @BeforeEach
    void clean() {
        if (jdbc != null) jdbc.execute("TRUNCATE company_embedding");
    }

    @Test
    void filteredSearchAppliesCapabilityRegionAndPriceBeforeLimit() {
        repository.upsert(row(1, List.of(7L), List.of(3L), 90.0, 150.0, 1f, 0f, 0f));
        repository.upsert(row(2, List.of(8L), List.of(3L), 90.0, 150.0, 1f, 0f, 0f));
        repository.upsert(row(3, List.of(7L), List.of(4L), 90.0, 150.0, 1f, 0f, 0f));

        List<CompanySearchHit> hits = repository.searchFiltered(
                new float[] {1f, 0f, 0f}, 7L, 3L, 1);

        assertThat(hits).extracting(CompanySearchHit::companyId).containsExactly(1L);
        assertThat(hits.get(0).categoryIds()).containsExactly(7L);
        assertThat(hits.get(0).regionIds()).containsExactly(3L);
    }

    @Test
    void hashAndStaleDeletionAreCompanyScoped() {
        repository.upsert(row(1, List.of(), List.of(), null, null, 1f, 0f, 0f));
        repository.upsert(row(2, List.of(), List.of(), null, null, 0f, 1f, 0f));

        assertThat(repository.findAllContentHashes()).containsOnly(Map.entry(1L, "hash-1"), Map.entry(2L, "hash-2"));
        assertThat(repository.deleteByCompanyIds(Set.of(2L))).isEqualTo(1);
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void supplierSearchExcludesEmptyAndUnverifiedCatalogsBeforeLimit() {
        repository.upsert(row(1, List.of(7L), List.of(3L), 90.0, 150.0, 0, 1f, 0f, 0f));
        repository.upsert(row(2, List.of(7L), List.of(3L), 90.0, 150.0, 5, 1f, 0f, 0f));
        jdbc.update("UPDATE company_embedding SET verification_status = 'PENDING_VERIFICATION' WHERE company_id = 2");
        repository.upsert(row(3, List.of(7L), List.of(3L), 90.0, 150.0, 5, 0.9f, 0.1f, 0f));

        List<CompanySearchHit> hits = repository.searchSuppliers(
                new float[] {1f, 0f, 0f}, 7L, 3L, 1);

        assertThat(hits).extracting(CompanySearchHit::companyId).containsExactly(3L);
    }

    @Test
    void lexicalCompanySearchMatchesNameTermsAndKeepsCapabilityFilters() {
        repository.upsert(row(1, List.of(7L), List.of(3L), null, null, 5, 1f, 0f, 0f));
        repository.upsert(row(2, List.of(7L), List.of(3L), null, null, 5, 1f, 0f, 0f));
        jdbc.update("UPDATE company_embedding SET name = ? WHERE company_id = ?", "Zed Cement", 1L);
        jdbc.update("UPDATE company_embedding SET name = ?, verification_status = 'PENDING_VERIFICATION' "
                + "WHERE company_id = ?", "Acme Cement", 2L);

        List<CompanySearchHit> hits = lexical.searchCompanies(
                "cement supplier", 7L, 3L, true, 10);

        assertThat(hits).singleElement().satisfies(hit -> {
            assertThat(hit.companyId()).isEqualTo(1L);
            assertThat(hit.score()).isGreaterThan(0.7);
        });
    }

    private CompanyEmbeddingRow row(long id, List<Long> categories, List<Long> regions,
                                    Double min, Double max, float... vector) {
        return row(id, categories, regions, min, max, 5, vector);
    }

    private CompanyEmbeddingRow row(long id, List<Long> categories, List<Long> regions,
                                    Double min, Double max, int productCount, float... vector) {
        return new CompanyEmbeddingRow(id, "company-" + id, "Company " + id, "VERIFIED", categories,
                regions, productCount, min, max, "hash-" + id, vector);
    }
}
