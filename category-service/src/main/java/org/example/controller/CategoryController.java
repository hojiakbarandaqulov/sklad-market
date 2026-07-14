package org.example.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.dto.ApiResponse;
import org.example.dto.categoryAtribute.CategoryCreateRequest;
import org.example.dto.CategoryResponse;
import org.example.dto.CategoryUpdateRequest;
import org.example.enums.AppLanguage;
import org.example.service.CategoryService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/categories")
public class CategoryController {

    private final CategoryService categoryService;

    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @PostMapping(value = "/create", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<CategoryResponse> createCategory(@RequestBody @Valid CategoryCreateRequest request,
                                                        @RequestParam("file") MultipartFile file,
                                                        @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        CategoryResponse categoryResponse = categoryService.create(request,file, language);
        return ApiResponse.successResponse(categoryResponse);
    }

    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @PutMapping("/update/{id}")
    public ApiResponse<CategoryResponse> updateCategory(
            @PathVariable Long id,
            @RequestBody @Valid CategoryUpdateRequest request,
            @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        CategoryResponse categoryResponse = categoryService.update(id, request, language);
        return ApiResponse.successResponse(categoryResponse);
    }

    @PreAuthorize("permitAll()")
    @GetMapping
    public ApiResponse<Page<CategoryResponse>> getCategory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        Pageable pageable= PageRequest.of(page,size, Sort.by(Sort.Direction.ASC,"sortOrder"));
        return ApiResponse.successResponse(categoryService.getCategory(pageable, language));
    }


    @PreAuthorize("permitAll()")
    @GetMapping("/{slug}")
    public ApiResponse<CategoryResponse> getCategoryBySlug(@PathVariable String slug,
                                                           @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        CategoryResponse categoryResponse = categoryService.getCategoryBySlug(slug, language);
        return ApiResponse.successResponse(categoryResponse);
    }

    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @DeleteMapping("/delete/{id}")
    public ApiResponse<Boolean> deleteCategory(@PathVariable Long id,
                                               @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        Boolean categoryResponse = categoryService.delete(id, language);
        return ApiResponse.successResponse(categoryResponse);
    }
}
