package com.example.repository;

import com.example.document.ResumeDocument;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

public interface ResumeSearchRepository extends ElasticsearchRepository<ResumeDocument, String> {

}
