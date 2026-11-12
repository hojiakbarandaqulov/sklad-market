package com.example.service;

import com.example.document.RegionDocument;
import com.example.entity.Region;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface RegionSearchService {
    Page<Region> search(String query, Pageable pageable);
    void index(RegionDocument category);
}
