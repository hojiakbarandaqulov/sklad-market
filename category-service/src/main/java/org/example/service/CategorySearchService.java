package org.example.service;

import org.example.entity.Category;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface CategorySearchService {
    Page<Category> search(String query, Pageable pageable);
    void index(Category category);
    void reindex();
}
