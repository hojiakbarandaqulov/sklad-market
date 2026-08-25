package org.example.ai.business.index;

import org.example.ai.embedding.EmbeddingSearchHit;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Local name/slug lookup used as a no-provider lexical fallback and hybrid-search signal. */
@Repository
public class BusinessLexicalRepository {

    private static final int MAX_TERMS = 6;
    private final JdbcTemplate jdbc;

    public BusinessLexicalRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<EmbeddingSearchHit> searchProducts(
            String query,
            Long categoryId,
            Long regionId,
            Double minPrice,
            Double maxPrice,
            String currency,
            int limit) {
        List<String> terms = terms(query);
        if (terms.isEmpty()) return List.of();
        StringBuilder sql = new StringBuilder(
                "SELECT product_id, slug, name, category_id, region_id, price, currency, image_url "
                        + "FROM product_embedding WHERE "
                        + "(CAST(? AS bigint) IS NULL OR category_id = CAST(? AS bigint)) "
                        + "AND (CAST(? AS bigint) IS NULL OR region_id = CAST(? AS bigint)) "
                        + "AND (CAST(? AS numeric) IS NULL OR price >= CAST(? AS numeric)) "
                        + "AND (CAST(? AS numeric) IS NULL OR price <= CAST(? AS numeric)) "
                        + "AND (CAST(? AS varchar) IS NULL OR upper(currency) = upper(CAST(? AS varchar))) AND (");
        List<Object> args = new ArrayList<>();
        args.add(categoryId);
        args.add(categoryId);
        args.add(regionId);
        args.add(regionId);
        args.add(minPrice);
        args.add(minPrice);
        args.add(maxPrice);
        args.add(maxPrice);
        args.add(currency);
        args.add(currency);
        appendTermPredicates(sql, args, terms);
        sql.append(") ORDER BY lower(name), product_id LIMIT ?");
        args.add(Math.max(1, limit));
        return jdbc.query(sql.toString(), (rs, rowNum) -> mapProduct(rs, query), args.toArray());
    }

    public List<CompanySearchHit> searchCompanies(
            String query,
            Long categoryId,
            Long regionId,
            boolean requirePublicCatalog,
            int limit) {
        List<String> terms = terms(query);
        if (terms.isEmpty()) return List.of();
        StringBuilder sql = new StringBuilder(
                "SELECT company_id, slug, name, logo_url, verification_status, category_ids, region_ids, product_count, "
                        + "min_price, max_price FROM company_embedding WHERE "
                        + "(CAST(? AS bigint) IS NULL OR CAST(? AS bigint) = ANY(category_ids)) "
                        + "AND (CAST(? AS bigint) IS NULL OR CAST(? AS bigint) = ANY(region_ids)) ");
        List<Object> args = new ArrayList<>();
        args.add(categoryId);
        args.add(categoryId);
        args.add(regionId);
        args.add(regionId);
        if (requirePublicCatalog) {
            sql.append("AND product_count > 0 AND verification_status = 'VERIFIED' ");
        }
        sql.append("AND (");
        appendTermPredicates(sql, args, terms);
        sql.append(") ORDER BY lower(name), company_id LIMIT ?");
        args.add(Math.max(1, limit));
        return jdbc.query(sql.toString(), (rs, rowNum) -> mapCompany(rs, query), args.toArray());
    }

    private void appendTermPredicates(StringBuilder sql, List<Object> args, List<String> terms) {
        for (int i = 0; i < terms.size(); i++) {
            if (i > 0) sql.append(" OR ");
            sql.append("(position(? in lower(name)) > 0 OR position(? in lower(slug)) > 0)");
            args.add(terms.get(i));
            args.add(terms.get(i));
        }
    }

    private EmbeddingSearchHit mapProduct(ResultSet rs, String query) throws SQLException {
        BigDecimal price = rs.getBigDecimal("price");
        String slug = rs.getString("slug");
        String name = rs.getString("name");
        return new EmbeddingSearchHit(rs.getLong("product_id"), slug, name,
                (Long) rs.getObject("category_id"), (Long) rs.getObject("region_id"),
                price == null ? null : price.doubleValue(), rs.getString("currency"), rs.getString("image_url"),
                lexicalScore(name, slug, query));
    }

    private CompanySearchHit mapCompany(ResultSet rs, String query) throws SQLException {
        BigDecimal min = rs.getBigDecimal("min_price");
        BigDecimal max = rs.getBigDecimal("max_price");
        String slug = rs.getString("slug");
        String name = rs.getString("name");
        return new CompanySearchHit(rs.getLong("company_id"), slug, name,
                rs.getString("logo_url"), rs.getString("verification_status"), readLongArray(rs.getArray("category_ids")),
                readLongArray(rs.getArray("region_ids")), rs.getInt("product_count"),
                min == null ? null : min.doubleValue(), max == null ? null : max.doubleValue(),
                lexicalScore(name, slug, query));
    }

    static List<String> terms(String query) {
        if (query == null || query.isBlank()) return List.of();
        String normalized = query.trim().toLowerCase(Locale.ROOT);
        Set<String> terms = new LinkedHashSet<>();
        terms.add(normalized);
        for (String term : normalized.split("[\\p{P}\\p{Z}\\s]+")) {
            if (term.length() >= 2) terms.add(term);
            if (terms.size() >= MAX_TERMS) break;
        }
        return List.copyOf(terms);
    }

    static double lexicalScore(String name, String slug, String query) {
        String normalized = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        String normalizedName = name == null ? "" : name.toLowerCase(Locale.ROOT);
        String normalizedSlug = slug == null ? "" : slug.toLowerCase(Locale.ROOT);
        if (normalizedName.equals(normalized) || normalizedSlug.equals(normalized)) return 1.0;
        if (normalizedName.startsWith(normalized) || normalizedSlug.startsWith(normalized)
                || (!normalizedName.isEmpty() && normalized.startsWith(normalizedName))) return 0.92;
        return 0.78;
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
}
