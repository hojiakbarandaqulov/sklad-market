package com.example.service.impl;

import co.elastic.clients.elasticsearch._types.query_dsl.Operator;
import com.example.document.ResumeDocument;
import com.example.repository.ResumeSearchRepository;
import com.example.service.ResumeSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Service
public class ResumeSearchServiceImpl implements ResumeSearchService {

    private final ResumeSearchRepository resumeSearchRepository;

    private final ElasticsearchOperations elasticsearchOperations;

    @Override
    public void index(ResumeDocument document) {
        resumeSearchRepository.save(document);
    }

    @Override
    public Optional<ResumeDocument> get(Long id) {
        validateId(id);
        return resumeSearchRepository.findById(id.toString());
    }

    @Override
    public void delete(Long id) {
        validateId(id);
        resumeSearchRepository.deleteById(id.toString());
    }

    @Override
    public void update(ResumeDocument document) {
        resumeSearchRepository.save(document);
    }

    @Override
    public Page<ResumeDocument> search(String query, Long categoryId, Pageable pageable) {

        var nativeQueryBuilder = NativeQuery.builder().withQuery(q -> q.bool(b -> {
            b.filter(f -> f.term(t -> t.field("deleted").value(false)));
            if (categoryId != null) {
                b.filter(f -> f.term(t -> t.field("categoryId").value(categoryId)));
            }
            if (query != null && !query.isBlank()) {
                b.must(m -> m.matchBoolPrefix(t -> t.field("title").query(query.trim()).operator(Operator.And)));
            }
            return b;
        })).withPageable(pageable).withTrackTotalHits(true).build();
        SearchHits<ResumeDocument> search = elasticsearchOperations.search(nativeQueryBuilder, ResumeDocument.class);

        List<ResumeDocument> document = search.getSearchHits().stream().map(SearchHit::getContent).toList();
        return new PageImpl<>(document, pageable, search.getTotalHits());
    }


    private void validateId(Long id) {
        Assert.isTrue(id != null && id > 0, "Resume ID must be positive");
    }
}
