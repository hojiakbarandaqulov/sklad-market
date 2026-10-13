package com.example.service;

import com.example.dto.category.JobCategoryDTO;
import com.example.dto.category.JobCategoryRequest;
import com.example.enums.AppLanguage;
import org.springframework.data.domain.PageImpl;

public interface JobCategoryService {
    JobCategoryDTO create(JobCategoryRequest request, AppLanguage language);

    JobCategoryDTO update(Long id, JobCategoryRequest request, AppLanguage language);

    void delete(Long id, AppLanguage language);

    JobCategoryDTO get(Long id, boolean activeOnly, AppLanguage language);

    PageImpl<JobCategoryDTO> list(int page, int perPage, boolean activeOnly, AppLanguage language);

    void validateSelection(Long categoryId, AppLanguage language);
}
