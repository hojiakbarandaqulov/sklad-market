package com.example.service.impl;

import com.example.config.clent.AiCompanySummaryClient;
import com.example.dto.vacancy.CompanySummaryDTO;
import com.example.dto.vacancy.VacancyFilter;
import com.example.entity.Vacancy;
import com.example.repository.VacancyRepository;
import com.example.repository.specification.VacancySpecifications;
import com.example.service.AiVacancyCandidatesService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.*;

/** Fresh database snapshots: no index staleness, view increments, contacts or applications. */
@Service
@RequiredArgsConstructor
public class AiVacancyCandidatesServiceImpl implements AiVacancyCandidatesService {
    private final VacancyRepository repository;
    private final AiCompanySummaryClient companies;

    @Override
    public List<Candidate> find(VacancyFilter filter, List<String> terms, List<Long> ids, int limit) {
        if (limit < 1 || limit > 40 || (ids != null && (ids.size() > 6 || ids.stream().anyMatch(id -> id == null || id <= 0))))
            throw new IllegalArgumentException("Invalid AI candidate bounds");
        List<String> safeTerms = terms == null ? List.of() : terms.stream().map(String::trim)
                .filter(s -> s.length() >= 2).distinct().toList();
        if (safeTerms.size() > 12 || safeTerms.stream().anyMatch(s -> s.length() > 60))
            throw new IllegalArgumentException("Invalid AI search terms");
        // A missing target must never become an unfiltered catalog dump.
        if ((ids == null || ids.isEmpty()) && safeTerms.isEmpty()
                && (filter.getQ() == null || filter.getQ().isBlank()) && filter.getCategoryId() == null)
            return List.of();
        Specification<Vacancy> spec = VacancySpecifications.published(filter);
        if (ids != null && !ids.isEmpty()) spec = spec.and((root, query, cb) -> root.get("id").in(ids));
        if (!safeTerms.isEmpty()) spec = spec.and((root, query, cb) -> {
            jakarta.persistence.criteria.Expression<String> title = cb.lower(root.get("positionName"));
            for (String apostrophe : List.of("'", "’", "‘", "ʻ", "`", "ʼ"))
                title = cb.function("replace", String.class, title, cb.literal(apostrophe), cb.literal(""));
            final var normalizedTitle = title;
            return cb.or(safeTerms.stream().map(term -> cb.like(normalizedTitle, "%" + escape(term) + "%", '!'))
                    .toArray(jakarta.persistence.criteria.Predicate[]::new));
        });
        var page = repository.findAll(spec, PageRequest.of(0, limit,
                Sort.by(Sort.Order.desc("publishedAt"), Sort.Order.desc("id"))));
        // Only the final <=6 live shortlisted jobs need company hydration, never all candidates.
        // Repository's read transaction ends before any optional downstream HTTP calls.
        Map<Long, CompanySummaryDTO> summaries = new HashMap<>();
        long enrichmentDeadline = System.nanoTime() + java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(1200);
        var enrichmentUnavailable = new java.util.concurrent.atomic.AtomicBoolean(false);
        return page.getContent().stream().map(v -> {
            CompanySummaryDTO company = null;
            if (ids != null && !ids.isEmpty() && v.getCompanyId() != null) {
                if (!summaries.containsKey(v.getCompanyId()) && !enrichmentUnavailable.get() && System.nanoTime() < enrichmentDeadline) {
                    try { summaries.put(v.getCompanyId(), companies.getSummary(v.getCompanyId())); }
                    catch (RuntimeException unavailable) { summaries.put(v.getCompanyId(), null); enrichmentUnavailable.set(true); }
                }
                company = summaries.get(v.getCompanyId());
            }
            return new Candidate(v.getId(), bounded(v.getPositionName(), 200), v.getCategoryId(), v.getPrice(),
                    v.getRegionId(), bounded(v.getAddress(), 200), bounded(v.getEmploymentType(), 80),
                    bounded(v.getWorkSchedule(), 80), v.getExperienceLevel() == null ? null : v.getExperienceLevel().name(),
                    v.getPublishedAt(), bounded(v.getShortDescription(), 1000), bounded(v.getRequirements(), 2000),
                    company == null ? null : bounded(company.getName(), 200), company == null ? null : company.getLogoPath());
        }).toList();
    }

    private static String bounded(String text, int max) { return text == null ? null : text.substring(0, Math.min(text.length(), max)); }
    private static String escape(String text) { return text.toLowerCase(Locale.ROOT).replace("!", "!!").replace("%", "!%").replace("_", "!_"); }
}
