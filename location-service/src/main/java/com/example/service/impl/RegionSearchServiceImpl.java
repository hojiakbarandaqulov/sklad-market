package com.example.service.impl;

import com.example.document.RegionDocument;
import com.example.entity.Region;
import com.example.repository.RegionSearchRepository;
import com.example.service.RegionSearchService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.*;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@AllArgsConstructor
@Service
public class RegionSearchServiceImpl implements RegionSearchService {
    private final ElasticsearchOperations operations;
    private final RegionSearchRepository regionSearchRepository;

    @Override
    public Page<Region> search(String query, Pageable pageable) {
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
        var hits = operations.search(builder.build(), RegionDocument.class);
        List<Region> regions = new ArrayList<>(hits.getSearchHits().size());
        for (SearchHit<RegionDocument> hit : hits) {
            RegionDocument document = hit.getContent();
            Region region = new Region();
            region.setId(Long.valueOf(document.getId()));
            region.setCode(document.getCode());
            region.setNameUz(document.getNameUz());
            region.setNameRu(document.getNameRu());
            region.setNameEn(document.getNameEn());
            region.setType(document.getType());
            region.setSortOrder(document.getSortOrder());
            regions.add(region);
        }
        return new PageImpl<>(regions, pageable, hits.getTotalHits());
    }

    @Override
    public void index(RegionDocument regionDocument) {
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    saveDocument(regionDocument);
                }
            });
        } else {
            saveDocument(regionDocument);
        }
    }

    private void saveDocument(RegionDocument document) {
        try {
            regionSearchRepository.save(document);
        } catch (RuntimeException ex) {
            log.error("Region {} search index update failed", document.getId(), ex);
        }
    }
}
