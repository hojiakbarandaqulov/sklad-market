package com.example.repository;

import com.example.document.RegionDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface RegionSearchRepository extends ElasticsearchRepository<RegionDocument, String> {
    void save();
}
