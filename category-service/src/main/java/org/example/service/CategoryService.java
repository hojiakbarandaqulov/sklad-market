package org.example.service;

import org.example.dto.categoryAtribute.CategoryCreateRequest;
import org.example.dto.CategoryResponse;
import org.example.dto.CategoryUpdateRequest;
import org.example.dto.internal.CategoryInternalSummaryResponse;
import org.example.dto.internal.CategoryInternalValidationResponse;
import org.example.entity.Category;
import org.example.enums.AppLanguage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

public interface CategoryService {


    CategoryResponse create(CategoryCreateRequest request, MultipartFile file, AppLanguage language);

    CategoryResponse update(Long id, CategoryUpdateRequest request,AppLanguage language);

    Boolean delete(Long id,AppLanguage language);

    Page<CategoryResponse> getCategory(Pageable pageable,AppLanguage language);

    CategoryResponse getCategoryBySlug(String slug, AppLanguage language);



    Category findById(Long categoryId);
}
