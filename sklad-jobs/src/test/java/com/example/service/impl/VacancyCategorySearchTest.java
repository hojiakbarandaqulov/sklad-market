package com.example.service.impl;

import com.example.document.VacancyDocument;
import com.example.dto.vacancy.VacancyFilter;
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

class VacancyCategorySearchTest {
    @Test void categoryTermCombinesWithExistingPublicAndRegionFiltersAndPagination() {
        var operations = mock(ElasticsearchOperations.class);
        @SuppressWarnings("unchecked") SearchHits<VacancyDocument> hits = mock(SearchHits.class);
        when(hits.getSearchHits()).thenReturn(List.of());
        when(operations.search(any(Query.class), eq(VacancyDocument.class))).thenReturn(hits);
        var service = new VacancySearchServiceImpl(mock(VacancySearchRepository.class), operations);
        var filter = new VacancyFilter();
        filter.setCategoryId(3L);
        filter.setRegionId(1L);
        var pageable = PageRequest.of(1, 20);
        service.search(filter, pageable);
        var capture = ArgumentCaptor.forClass(Query.class);
        verify(operations).search(capture.capture(), eq(VacancyDocument.class));
        var query = (NativeQuery) capture.getValue();
        var terms = query.getQuery().bool().filter();
        assertTrue(terms.stream().anyMatch(q -> q.term().field().equals("categoryId") && q.term().value().longValue() == 3));
        assertTrue(terms.stream().anyMatch(q -> q.term().field().equals("regionId") && q.term().value().longValue() == 1));
        assertTrue(terms.stream().anyMatch(q -> q.term().field().equals("vacancyStatus") && q.term().value().stringValue().equals("PUBLISHED")));
        assertTrue(terms.stream().anyMatch(q -> q.term().field().equals("deleted") && !q.term().value().booleanValue()));
        assertEquals(pageable, query.getPageable());
    }
}
