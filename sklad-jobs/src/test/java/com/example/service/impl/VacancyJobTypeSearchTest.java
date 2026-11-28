package com.example.service.impl;

import com.example.document.VacancyDocument;
import com.example.dto.vacancy.VacancyFilter;
import com.example.enums.JobType;
import com.example.repository.VacancySearchRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.Query;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class VacancyJobTypeSearchTest {
    @Test void typeFilterCombinesWithPublicStatusRegionAndExistingFilters() {
        var operations = mock(ElasticsearchOperations.class);
        @SuppressWarnings("unchecked") SearchHits<VacancyDocument> hits = mock(SearchHits.class);
        when(hits.getSearchHits()).thenReturn(List.of());
        when(operations.search(any(Query.class), eq(VacancyDocument.class))).thenReturn(hits);
        var service = new VacancySearchServiceImpl(mock(VacancySearchRepository.class), operations);
        for (JobType type : new JobType[]{null, JobType.PERMANENT, JobType.TEMPORARY}) {
            var filter = new VacancyFilter();
            filter.setJobType(type);
            filter.setRegionId(1L);
            var pageable = PageRequest.of(1, 12);
            service.search(filter, pageable);
            var capture = ArgumentCaptor.forClass(Query.class);
            verify(operations, atLeastOnce()).search(capture.capture(), eq(VacancyDocument.class));
            var query = (NativeQuery) capture.getValue();
            var terms = query.getQuery().bool().filter();
            var typeTerms = terms.stream().filter(q -> q.term().field().equals("jobType")).toList();
            assertEquals(type == null ? 0 : 1, typeTerms.size());
            if (type != null) assertEquals(type.name(), typeTerms.get(0).term().value().stringValue());
            assertTrue(terms.stream().anyMatch(q -> q.term().field().equals("vacancyStatus") && q.term().value().stringValue().equals("PUBLISHED")));
            assertTrue(terms.stream().anyMatch(q -> q.term().field().equals("deleted") && !q.term().value().booleanValue()));
            assertTrue(terms.stream().anyMatch(q -> q.term().field().equals("regionId")));
            assertEquals(pageable, query.getPageable());
        }
    }
}
