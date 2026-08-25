package org.example.ai.embedding;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Plain-JDBC access to {@code product_embedding} — there is no pgvector Hibernate type on this
 * stack (PLAN.md §7 item 5), so vectors are bound as {@code CAST(? AS vector)} text-literal
 * parameters (never concatenated) and ranked with pgvector's {@code <=>} cosine-distance operator.
 */
@Repository
public class ProductEmbeddingRepository {

    private static final RowMapper<EmbeddingSearchHit> HIT_MAPPER = ProductEmbeddingRepository::mapHit;

    private final JdbcTemplate jdbc;

    public ProductEmbeddingRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** product_id -> content_hash for every indexed row, used by the indexer's skip-unchanged pass. */
    public Map<Long, String> findAllContentHashes() {
        Map<Long, String> hashes = new HashMap<>();
        jdbc.query("SELECT product_id, content_hash FROM product_embedding",
                rs -> { hashes.put(rs.getLong("product_id"), rs.getString("content_hash")); });
        return hashes;
    }

    public long count() {
        Long count = jdbc.queryForObject("SELECT count(*) FROM product_embedding", Long.class);
        return count == null ? 0 : count;
    }

    /**
     * Distinct regions represented by approved/active products in the last successful index crawl.
     * The public catalog-filter DTO does not expose regions, so agent filter discovery uses this
     * local, visibility-filtered projection instead of claiming that an absent wire field is empty.
     */
    public List<Long> findDistinctRegionIds(Long categoryId, int limit) {
        int boundedLimit = Math.max(limit, 1);
        if (categoryId == null) {
            return jdbc.queryForList(
                    "SELECT DISTINCT region_id FROM product_embedding "
                            + "WHERE region_id IS NOT NULL ORDER BY region_id LIMIT ?",
                    Long.class, boundedLimit);
        }
        return jdbc.queryForList(
                "SELECT DISTINCT region_id FROM product_embedding "
                        + "WHERE region_id IS NOT NULL AND category_id = ? ORDER BY region_id LIMIT ?",
                Long.class, categoryId, boundedLimit);
    }

    /** Resolve an indexed product's id from its slug (for the slug-based agent tool). */
    public Optional<Long> findProductIdBySlug(String slug) {
        List<Long> ids = jdbc.queryForList(
                "SELECT product_id FROM product_embedding WHERE slug = ? LIMIT 1", Long.class, slug);
        return ids.isEmpty() ? Optional.empty() : Optional.of(ids.get(0));
    }

    /** Upsert a single product's embedding + projection fields. */
    public void upsert(ProductEmbeddingRow row) {
        String vector = VectorLiterals.toLiteral(row.embedding());
        jdbc.update(
                "INSERT INTO product_embedding "
                        + "(product_id, slug, name, category_id, region_id, price, currency, image_url, "
                        + "content_hash, embedding, indexed_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS vector), now()) "
                        + "ON CONFLICT (product_id) DO UPDATE SET "
                        + "slug = EXCLUDED.slug, name = EXCLUDED.name, category_id = EXCLUDED.category_id, "
                        + "region_id = EXCLUDED.region_id, price = EXCLUDED.price, currency = EXCLUDED.currency, "
                        + "image_url = EXCLUDED.image_url, content_hash = EXCLUDED.content_hash, "
                        + "embedding = EXCLUDED.embedding, indexed_at = now()",
                row.productId(), row.slug(), row.name(), row.categoryId(), row.regionId(),
                row.price(), row.currency(), row.imageUrl(), row.contentHash(), vector);
    }

    /** Remove rows whose products are no longer approved/active or have disappeared. No-op on empty. */
    public int deleteByProductIds(Collection<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return 0;
        }
        List<Long> ids = List.copyOf(productIds);
        String placeholders = String.join(",", ids.stream().map(id -> "?").toList());
        return jdbc.update("DELETE FROM product_embedding WHERE product_id IN (" + placeholders + ")", ids.toArray());
    }

    /**
     * Cosine top-K for a query vector. Orders by the raw {@code <=>} distance expression (not the
     * derived score alias) so the HNSW index can serve the ordering.
     */
    public List<EmbeddingSearchHit> search(float[] queryVector, int limit) {
        String vector = VectorLiterals.toLiteral(queryVector);
        return jdbc.query(
                "SELECT product_id, slug, name, category_id, region_id, price, currency, image_url, "
                        + "1 - (embedding <=> CAST(? AS vector)) AS score "
                        + "FROM product_embedding "
                        + "ORDER BY embedding <=> CAST(? AS vector) "
                        + "LIMIT ?",
                HIT_MAPPER, vector, vector, limit);
    }

    /** Filter-before-limit search used by business discovery so totals/results are never page-filtered. */
    public List<EmbeddingSearchHit> searchFiltered(
            float[] queryVector,
            Long categoryId,
            Long regionId,
            Double minPrice,
            Double maxPrice,
            String currency,
            int limit) {
        String vector = VectorLiterals.toLiteral(queryVector);
        return jdbc.query(
                "SELECT product_id, slug, name, category_id, region_id, price, currency, image_url, "
                        + "1 - (embedding <=> CAST(? AS vector)) AS score FROM product_embedding "
                        + "WHERE (CAST(? AS bigint) IS NULL OR category_id = CAST(? AS bigint)) "
                        + "AND (CAST(? AS bigint) IS NULL OR region_id = CAST(? AS bigint)) "
                        + "AND (CAST(? AS numeric) IS NULL OR price >= CAST(? AS numeric)) "
                        + "AND (CAST(? AS numeric) IS NULL OR price <= CAST(? AS numeric)) "
                        + "AND (CAST(? AS varchar) IS NULL OR upper(currency) = upper(CAST(? AS varchar))) "
                        + "ORDER BY embedding <=> CAST(? AS vector) LIMIT ?",
                HIT_MAPPER, vector, categoryId, categoryId, regionId, regionId,
                minPrice, minPrice, maxPrice, maxPrice, currency, currency, vector, limit);
    }

    /**
     * Vector neighbours of {@code productId}, excluding itself, with a same-category boost.
     * Returns {@link Optional#empty()} when the product is not in the index (so the endpoint can
     * 404 rather than return an empty neighbour list), otherwise the (possibly empty) neighbour list.
     */
    public Optional<List<EmbeddingSearchHit>> findSimilar(long productId, int limit, double sameCategoryBoost) {
        List<Target> targets = jdbc.query(
                "SELECT category_id, embedding::text AS vec FROM product_embedding WHERE product_id = ?",
                (rs, rowNum) -> new Target((Long) rs.getObject("category_id"), rs.getString("vec")),
                productId);
        if (targets.isEmpty()) {
            return Optional.empty();
        }
        Target target = targets.get(0);
        // Boost lowers the effective distance for same-category rows so they rank higher. For a
        // demo-scale catalog this exact re-rank is fine; a large catalog would instead re-rank an
        // index-served candidate set.
        List<EmbeddingSearchHit> hits = jdbc.query(
                "SELECT product_id, slug, name, category_id, region_id, price, currency, image_url, "
                        + "1 - (embedding <=> CAST(? AS vector)) AS score "
                        + "FROM product_embedding "
                        + "WHERE product_id <> ? "
                        + "ORDER BY (embedding <=> CAST(? AS vector)) "
                        + "         - (CASE WHEN category_id IS NOT DISTINCT FROM ? THEN ? ELSE 0 END) "
                        + "LIMIT ?",
                HIT_MAPPER, target.vector(), productId, target.vector(), target.categoryId(), sameCategoryBoost, limit);
        return Optional.of(hits);
    }

    private static EmbeddingSearchHit mapHit(ResultSet rs, int rowNum) throws SQLException {
        BigDecimal price = rs.getBigDecimal("price");
        return new EmbeddingSearchHit(
                rs.getLong("product_id"),
                rs.getString("slug"),
                rs.getString("name"),
                (Long) rs.getObject("category_id"),
                (Long) rs.getObject("region_id"),
                price == null ? null : price.doubleValue(),
                rs.getString("currency"),
                rs.getString("image_url"),
                rs.getDouble("score"));
    }

    private record Target(Long categoryId, String vector) {
    }
}
