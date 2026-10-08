package com.example.service.impl;

import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType;
import co.elastic.clients.json.JsonData;
import com.example.document.VacancyDocument;
import com.example.dto.vacancy.VacancyFilter;
import com.example.enums.VacancyStatus;
import com.example.service.VacancySearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.util.Locale;
import java.util.Optional;

@RequiredArgsConstructor
@Service
public class VacancySearchServiceImpl implements VacancySearchService {

    private final ElasticsearchOperations elasticsearchOperations;

    @Override
    public void index(VacancyDocument document) {
        validateDocument(document);
        elasticsearchOperations.save(document);
    }

    @Override
    public Optional<VacancyDocument> get(Long id) {
        validateId(id);
        return Optional.ofNullable(elasticsearchOperations.get(id.toString(), VacancyDocument.class));
    }

    @Override
    public void delete(Long id) {
        validateId(id);
        elasticsearchOperations.delete(id.toString(), VacancyDocument.class);
    }

    @Override
    public void update(VacancyDocument document) {
        validateDocument(document);
        // Replace the full document; a missing ID in Elasticsearch is inserted.
        elasticsearchOperations.save(document);
    }

    @Override
    public Page<VacancyDocument> search(
            VacancyFilter filter,
            Pageable pageable
    ) {
        var query = NativeQuery.builder()
                .withQuery(q -> q.bool(b -> {

                    // Faqat public vakansiyalar.
                    b.filter(f -> f.term(t -> t
                            .field("vacancyStatus")
                            .value(VacancyStatus.PUBLISHED.name())));

                    b.filter(f -> f.term(t -> t
                            .field("deleted")
                            .value(false)));

                    if (filter.getCompanyId() != null) {
                        b.filter(f -> f.term(t -> t
                                .field("companyId")
                                .value(filter.getCompanyId())));
                    }

                    if (filter.getRegionId() != null) {
                        b.filter(f -> f.term(t -> t
                                .field("regionId")
                                .value(filter.getRegionId())));
                    }

                    if (filter.getSalaryMin() != null
                            || filter.getSalaryMax() != null) {

                        b.filter(f -> f.range(r -> r.untyped(range -> {
                            range.field("price");

                            if (filter.getSalaryMin() != null) {
                                range.gte(JsonData.of(filter.getSalaryMin()));
                            }

                            if (filter.getSalaryMax() != null) {
                                range.lte(JsonData.of(filter.getSalaryMax()));
                            }

                            return range;
                        })));
                    }

                    if (filter.getExperienceLevel() != null) {
                        b.filter(f -> f.term(t -> t
                                .field("experienceLevel")
                                .value(filter.getExperienceLevel().name())));
                    }

                    if (filter.getEmploymentType() != null
                            && !filter.getEmploymentType().isBlank()) {

                        String value = filter.getEmploymentType()
                                .trim()
                                .toLowerCase(Locale.ROOT);

                        b.filter(f -> f.term(t -> t
                                .field("employmentType")
                                .value(value)));
                    }

                    if (filter.getWorkSchedule() != null
                            && !filter.getWorkSchedule().isBlank()) {

                        String value = filter.getWorkSchedule()
                                .trim()
                                .toLowerCase(Locale.ROOT);

                        b.filter(f -> f.term(t -> t
                                .field("workSchedule")
                                .value(value)));
                    }

                    if (filter.getQ() != null && !filter.getQ().isBlank()) {
                        b.must(m -> m.multiMatch(mm -> mm
                                .fields(
                                        "positionName",
                                        "shortDescription",
                                        "requirements",
                                        "workingConditions"
                                )
                                .query(filter.getQ().trim())
                                .type(TextQueryType.PhrasePrefix)));
                    }

                    return b;
                }))
                .withPageable(pageable)
                .withTrackTotalHits(true)
                .build();

        var hits = elasticsearchOperations.search(
                query,
                VacancyDocument.class
        );

        var documents = hits.stream()
                .map(SearchHit::getContent)
                .toList();

        return new PageImpl<>(
                documents,
                pageable,
                hits.getTotalHits()
        );
    }

    private void validateDocument(VacancyDocument document) {
        Assert.notNull(document, "Vacancy document must not be null");
        Assert.hasText(document.getId(), "Vacancy document must have the database vacancy ID");
        Long id;
        try {
            id = Long.valueOf(document.getId());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Vacancy document ID must be a positive Long", exception);
        }
        validateId(id);
        Assert.isTrue(document.getId().equals(id.toString()),
                "Vacancy document ID must use the database vacancy ID without extra characters");
    }

    private void validateId(Long id) {
        Assert.isTrue(id != null && id > 0, "Vacancy ID must be positive");
    }
}
