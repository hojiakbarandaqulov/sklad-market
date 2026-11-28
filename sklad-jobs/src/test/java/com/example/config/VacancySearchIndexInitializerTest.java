package com.example.config;

import com.example.document.VacancyDocument;
import com.example.entity.Vacancy;
import com.example.enums.VacancyStatus;
import com.example.repository.VacancyRepository;
import com.example.repository.VacancySearchRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class VacancySearchIndexInitializerTest {
    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void rebuildsEveryPageFromDatabaseIncludingPublicationAndDeletionState() {
        var repository = mock(VacancyRepository.class);
        var searchRepository = mock(VacancySearchRepository.class);
        var operations = mock(ElasticsearchOperations.class);
        var index = mock(IndexOperations.class);
        when(operations.indexOps(VacancyDocument.class)).thenReturn(index);
        var firstPage = PageRequest.of(0, 200, Sort.by("id"));
        var secondPage = PageRequest.of(1, 200, Sort.by("id"));
        var published = vacancy(1L, VacancyStatus.PUBLISHED, false);
        published.setCategoryId(3L);
        published.setJobType(com.example.enums.JobType.TEMPORARY);
        var archived = vacancy(201L, VacancyStatus.ARCHIVE, true);
        when(repository.findAll(firstPage)).thenReturn(new PageImpl<>(List.of(published), firstPage, 201));
        when(repository.findAll(secondPage)).thenReturn(new PageImpl<>(List.of(archived), secondPage, 201));

        new VacancySearchIndexInitializer(repository, searchRepository, operations).run(null);

        ArgumentCaptor<Iterable<VacancyDocument>> batches = ArgumentCaptor.forClass((Class) Iterable.class);
        var order = inOrder(searchRepository, index);
        order.verify(index).putMapping();
        order.verify(searchRepository, times(2)).saveAll(batches.capture());
        order.verify(index).refresh();
        var first = batches.getAllValues().get(0).iterator().next();
        var second = batches.getAllValues().get(1).iterator().next();
        assertEquals("1", first.getId());
        assertEquals(3L, first.getCategoryId());
        assertEquals(com.example.enums.JobType.TEMPORARY, first.getJobType());
        assertNull(second.getJobType());
        assertEquals(VacancyStatus.PUBLISHED, first.getVacancyStatus());
        assertFalse(first.getDeleted());
        assertEquals(69.24, first.getLng());
        assertEquals("201", second.getId());
        assertEquals(VacancyStatus.ARCHIVE, second.getVacancyStatus());
        assertTrue(second.getDeleted());
        verify(repository).findAll(secondPage);
    }

    @Test
    void failedBulkDoesNotReportAReadyIndex() {
        var repository = mock(VacancyRepository.class);
        var searchRepository = mock(VacancySearchRepository.class);
        var operations = mock(ElasticsearchOperations.class);
        var index = mock(IndexOperations.class);
        when(operations.indexOps(VacancyDocument.class)).thenReturn(index);
        when(repository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(
                List.of(vacancy(1L, VacancyStatus.PUBLISHED, false))));
        when(searchRepository.saveAll(any())).thenThrow(new IllegalStateException("bulk failed"));
        assertThrows(IllegalStateException.class,
                () -> new VacancySearchIndexInitializer(repository, searchRepository, operations).run(null));
        verify(index, never()).refresh();
    }

    private Vacancy vacancy(long id, VacancyStatus status, boolean deleted) {
        var vacancy = new Vacancy();
        vacancy.setId(id);
        vacancy.setPositionName("Armatura");
        vacancy.setVacancyStatus(status);
        vacancy.setDeleted(deleted);
        vacancy.setLng("69.24");
        return vacancy;
    }
}
