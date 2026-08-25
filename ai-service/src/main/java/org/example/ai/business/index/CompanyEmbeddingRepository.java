package org.example.ai.business.index;

import org.example.ai.embedding.VectorLiterals;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class CompanyEmbeddingRepository {

    private final JdbcTemplate jdbc;

    public CompanyEmbeddingRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Map<Long, String> findAllContentHashes() {
        Map<Long, String> hashes = new HashMap<>();
        jdbc.query("SELECT company_id, content_hash FROM company_embedding",
                rs -> { hashes.put(rs.getLong("company_id"), rs.getString("content_hash")); });
        return hashes;
    }

    public long count() {
        Long count = jdbc.queryForObject("SELECT count(*) FROM company_embedding", Long.class);
        return count == null ? 0 : count;
    }

    public void upsert(CompanyEmbeddingRow row) {
        String vector = VectorLiterals.toLiteral(row.embedding());
        jdbc.update(
                "INSERT INTO company_embedding "
                        + "(company_id, slug, name, logo_url, verification_status, category_ids, region_ids, product_count, "
                        + "min_price, max_price, content_hash, embedding, indexed_at) "
                        + "VALUES (?, ?, ?, ?, ?, CAST(? AS bigint[]), CAST(? AS bigint[]), ?, ?, ?, ?, CAST(? AS vector), now()) "
                        + "ON CONFLICT (company_id) DO UPDATE SET slug=EXCLUDED.slug, name=EXCLUDED.name, "
                        + "logo_url=EXCLUDED.logo_url, verification_status=EXCLUDED.verification_status, "
                        + "category_ids=EXCLUDED.category_ids, "
                        + "region_ids=EXCLUDED.region_ids, product_count=EXCLUDED.product_count, min_price=EXCLUDED.min_price, "
                        + "max_price=EXCLUDED.max_price, content_hash=EXCLUDED.content_hash, embedding=EXCLUDED.embedding, indexed_at=now()",
                row.companyId(), row.slug(), row.name(), row.logoUrl(), row.verificationStatus(), arrayLiteral(row.categoryIds()),
                arrayLiteral(row.regionIds()), row.productCount(), row.minPrice(), row.maxPrice(), row.contentHash(), vector);
    }

    public List<CompanySearchHit> search(float[] queryVector, int candidateLimit) {
        String vector = VectorLiterals.toLiteral(queryVector);
        return jdbc.query(
                "SELECT company_id, slug, name, logo_url, verification_status, category_ids, region_ids, product_count, "
                        + "min_price, max_price, 1 - (embedding <=> CAST(? AS vector)) AS score "
                        + "FROM company_embedding ORDER BY embedding <=> CAST(? AS vector) LIMIT ?",
                CompanyEmbeddingRepository::mapHit, vector, vector, candidateLimit);
    }

    /** Capability filters are applied before LIMIT, avoiding misleading in-memory page filtering. */
    public List<CompanySearchHit> searchFiltered(
            float[] queryVector,
            Long categoryId,
            Long regionId,
            int candidateLimit) {
        String vector = VectorLiterals.toLiteral(queryVector);
        return jdbc.query(
                "SELECT company_id, slug, name, logo_url, verification_status, category_ids, region_ids, product_count, "
                        + "min_price, max_price, 1 - (embedding <=> CAST(? AS vector)) AS score "
                        + "FROM company_embedding "
                        + "WHERE (CAST(? AS bigint) IS NULL OR CAST(? AS bigint) = ANY(category_ids)) "
                        + "AND (CAST(? AS bigint) IS NULL OR CAST(? AS bigint) = ANY(region_ids)) "
                        + "ORDER BY embedding <=> CAST(? AS vector) LIMIT ?",
                CompanyEmbeddingRepository::mapHit, vector, categoryId, categoryId, regionId, regionId,
                vector, candidateLimit);
    }

    /**
     * Supplier candidates must own at least one currently public catalog item. Keeping that
     * predicate in SQL is important: filtering zero-catalog companies after the vector LIMIT can
     * hide valid suppliers that rank just beyond a page of otherwise ineligible companies.
     */
    public List<CompanySearchHit> searchSuppliers(
            float[] queryVector,
            Long categoryId,
            Long regionId,
            int candidateLimit) {
        String vector = VectorLiterals.toLiteral(queryVector);
        return jdbc.query(
                "SELECT company_id, slug, name, logo_url, verification_status, category_ids, region_ids, product_count, "
                        + "min_price, max_price, 1 - (embedding <=> CAST(? AS vector)) AS score "
                        + "FROM company_embedding "
                        + "WHERE product_count > 0 AND verification_status = 'VERIFIED' "
                        + "AND (CAST(? AS bigint) IS NULL OR CAST(? AS bigint) = ANY(category_ids)) "
                        + "AND (CAST(? AS bigint) IS NULL OR CAST(? AS bigint) = ANY(region_ids)) "
                        + "ORDER BY embedding <=> CAST(? AS vector) LIMIT ?",
                CompanyEmbeddingRepository::mapHit, vector, categoryId, categoryId, regionId, regionId,
                vector, candidateLimit);
    }

    /** Best-effort local logo lookup for a single detail card. */
    public Optional<String> findLogoUrlBySlug(String slug) {
        if (slug == null || slug.isBlank()) return Optional.empty();
        List<String> values = jdbc.query(
                "SELECT logo_url FROM company_embedding WHERE slug = ? LIMIT 1",
                (rs, rowNum) -> rs.getString("logo_url"), slug);
        return values.stream().filter(value -> value != null && !value.isBlank()).findFirst();
    }

    public int deleteByCompanyIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) return 0;
        List<Long> safeIds = List.copyOf(ids);
        String placeholders = String.join(",", safeIds.stream().map(id -> "?").toList());
        return jdbc.update("DELETE FROM company_embedding WHERE company_id IN (" + placeholders + ")", safeIds.toArray());
    }

    private static CompanySearchHit mapHit(ResultSet rs, int rowNum) throws SQLException {
        BigDecimal min = rs.getBigDecimal("min_price");
        BigDecimal max = rs.getBigDecimal("max_price");
        return new CompanySearchHit(
                rs.getLong("company_id"), rs.getString("slug"), rs.getString("name"),
                rs.getString("logo_url"), rs.getString("verification_status"), readLongArray(rs.getArray("category_ids")),
                readLongArray(rs.getArray("region_ids")), rs.getInt("product_count"),
                min == null ? null : min.doubleValue(), max == null ? null : max.doubleValue(), rs.getDouble("score"));
    }

    private static List<Long> readLongArray(Array sqlArray) throws SQLException {
        if (sqlArray == null) return List.of();
        Object raw = sqlArray.getArray();
        if (!(raw instanceof Object[] values)) return List.of();
        List<Long> result = new ArrayList<>(values.length);
        for (Object value : values) {
            if (value instanceof Number number) result.add(number.longValue());
        }
        return List.copyOf(result);
    }

    private static String arrayLiteral(List<Long> values) {
        if (values == null || values.isEmpty()) return "{}";
        return "{" + String.join(",", values.stream().distinct().sorted().map(String::valueOf).toList()) + "}";
    }
}
