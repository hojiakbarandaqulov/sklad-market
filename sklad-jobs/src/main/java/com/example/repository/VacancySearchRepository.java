package com.example.repository;

import com.example.document.VacancyDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface VacancySearchRepository extends ElasticsearchRepository<VacancyDocument, String> {
}
