package com.example.service;

import com.example.document.ResumeDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface ResumeSearchService {
    void index(ResumeDocument document);

    Optional<ResumeDocument> get(Long id);

    void delete(Long id);

    void update(ResumeDocument document);

    Page<ResumeDocument> search(
            String query,
            Long categoryId,
            Pageable pageable
    );

}
