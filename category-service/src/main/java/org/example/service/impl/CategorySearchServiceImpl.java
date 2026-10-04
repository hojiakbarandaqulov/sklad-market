package org.example.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.document.CategoryDocument;
import org.example.entity.Category;
import org.example.repository.CategoryRepository;
import org.example.service.CategorySearchService;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.*;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Slf4j
@Service
@RequiredArgsConstructor
public class CategorySearchServiceImpl implements CategorySearchService {
    private final ElasticsearchOperations operations;
    private final CategoryRepository repository;
    private volatile boolean ready;

    @Override
    public Page<Category> search(String query, Pageable pageable) {
        if (!ready) {
            reindex();
        }
        var builder = NativeQuery.builder()
                .withQuery(q -> q.bool(b -> {
                    b.filter(f -> f.term(t -> t.field("isActive").value(true)));
                    if (query != null && !query.isBlank()) {
                        b.must(m -> m.multiMatch(mm -> mm
                                .fields("nameUz", "nameRu", "nameEn")
                                .query(query.trim())
                                .type(co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType.PhrasePrefix)));
                    }
                    return b;
                }))
                .withPageable(PageRequest.of(pageable.getPageNumber(), pageable.getPageSize()))
                .withSort(Sort.by("sortOrder", "categoryId"))
                .withTrackTotalHits(true);
        var hits = operations.search(builder.build(), CategoryDocument.class);
        return new PageImpl<>(hits.stream().map(SearchHit::getContent)
                .map(CategoryDocument::toCategory).toList(), pageable, hits.getTotalHits());
    }

    @Override
    public void index(Category category) {
        CategoryDocument document = CategoryDocument.from(category);
        // Transactional admin writes must be committed before publishing to Elasticsearch.
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    saveDocument(document);
                }
            });
        } else {
            saveDocument(document);
        }
    }

    private synchronized void saveDocument(CategoryDocument document) {
        try {
            ensureIndex();
            operations.save(document);
            operations.indexOps(CategoryDocument.class).refresh();
        } catch (RuntimeException ex) {
            // The DB write succeeded. Rebuild from DB on the next search instead of serving stale results.
            ready = false;
            log.error("Category {} search index update failed; reindex required", document.getId(), ex);
        }
    }

    private void ensureIndex() {
        var index = operations.indexOps(CategoryDocument.class);
        if (!index.exists()) {
            index.createWithMapping();
        }
    }

    @Override
    public synchronized void reindex() {
        ready = false;
        ensureIndex();
        int page = 0;
        Page<Category> categories;
        do {
            categories = repository.findAll(PageRequest.of(page++, 200, Sort.by("id")));
            if (categories.hasContent()) {
                operations.save(categories.getContent().stream().map(CategoryDocument::from).toList());
            }
        } while (categories.hasNext());
        operations.indexOps(CategoryDocument.class).refresh();
        ready = true;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void initialize() {
        try {
            reindex();
        } catch (RuntimeException ex) {
            log.error("Category index initialization failed; next search will retry", ex);
        }
    }
}
