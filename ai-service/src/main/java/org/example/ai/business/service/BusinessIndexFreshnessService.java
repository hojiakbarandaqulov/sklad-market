package org.example.ai.business.service;

import org.example.ai.business.dto.BusinessIndexFreshness;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class BusinessIndexFreshnessService {

    private final JdbcTemplate jdbc;
    private final Duration maxAge;

    public BusinessIndexFreshnessService(
            JdbcTemplate jdbc,
            @Value("${ai.business-index.max-age-minutes:90}") long maxAgeMinutes) {
        this.jdbc = jdbc;
        this.maxAge = Duration.ofMinutes(Math.max(1, maxAgeMinutes));
    }

    public BusinessIndexFreshness snapshot(boolean includeProducts, boolean includeCompanies) {
        List<SourceState> sources = new ArrayList<>(2);
        if (includeProducts) sources.add(read("product", "index_state"));
        if (includeCompanies) sources.add(read("company", "business_index_state"));
        return evaluate(sources, Instant.now(), maxAge);
    }

    private SourceState read(String name, String table) {
        // Table names are fixed constants selected above, never request data.
        try {
            List<Run> latest = jdbc.query(
                    "SELECT last_run_at, last_status FROM " + table
                            + " ORDER BY last_run_at DESC, id DESC LIMIT 1",
                    (rs, rowNum) -> new Run(toInstant(rs.getTimestamp("last_run_at")), rs.getString("last_status")));
            List<Run> successful = jdbc.query(
                    "SELECT last_run_at, last_status FROM " + table
                            + " WHERE last_status = 'SUCCESS' ORDER BY last_run_at DESC, id DESC LIMIT 1",
                    (rs, rowNum) -> new Run(toInstant(rs.getTimestamp("last_run_at")), rs.getString("last_status")));
            return new SourceState(name, latest.isEmpty() ? null : latest.get(0).status(),
                    successful.isEmpty() ? null : successful.get(0).at(), false);
        } catch (DataAccessException unavailable) {
            return new SourceState(name, "UNAVAILABLE", null, true);
        }
    }

    static BusinessIndexFreshness evaluate(List<SourceState> sources, Instant now, Duration maxAge) {
        if (sources == null || sources.isEmpty()) {
            return new BusinessIndexFreshness(null, true, "none=UNAVAILABLE",
                    "Index freshness could not be established.");
        }
        Instant effectiveAsOf = null;
        boolean stale = false;
        boolean allSourcesHaveSuccessfulRun = true;
        List<String> statuses = new ArrayList<>(sources.size());
        for (SourceState source : sources) {
            statuses.add(source.name() + "=" + (source.latestStatus() == null ? "NEVER_RUN" : source.latestStatus()));
            if (source.unavailable() || source.lastSuccessfulAt() == null
                    || !"SUCCESS".equals(source.latestStatus())) {
                stale = true;
            }
            if (source.lastSuccessfulAt() == null) allSourcesHaveSuccessfulRun = false;
            if (source.lastSuccessfulAt() != null) {
                effectiveAsOf = effectiveAsOf == null || source.lastSuccessfulAt().isBefore(effectiveAsOf)
                        ? source.lastSuccessfulAt() : effectiveAsOf;
                if (source.lastSuccessfulAt().plus(maxAge).isBefore(now)) stale = true;
            }
        }
        if (!allSourcesHaveSuccessfulRun) effectiveAsOf = null;
        String note = stale
                ? "Results come from a local index that may be incomplete or out of date; verify current company and product details."
                : "Results were checked by all required indexers as of the reported time; availability and verification can still change.";
        return new BusinessIndexFreshness(effectiveAsOf, stale, String.join(";", statuses), note);
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    record SourceState(String name, String latestStatus, Instant lastSuccessfulAt, boolean unavailable) {}
    private record Run(Instant at, String status) {}
}
