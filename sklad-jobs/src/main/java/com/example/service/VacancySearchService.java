package com.example.service;

import com.example.document.VacancyDocument;
import com.example.dto.vacancy.VacancyFilter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface VacancySearchService {
    void index(VacancyDocument document);

    Optional<VacancyDocument> get(Long id);

    void delete(Long id);

    void update(VacancyDocument document);

    Page<VacancyDocument> search(
            VacancyFilter filter,
            Pageable pageable
    );

}
