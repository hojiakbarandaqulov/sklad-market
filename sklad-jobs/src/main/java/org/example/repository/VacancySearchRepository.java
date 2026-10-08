package org.example.repository;

import com.example.document.VacancyDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface VacancySearchRepository extends ElasticsearchRepository<VacancyDocument,String> {

}
