package org.example.controller;

import lombok.RequiredArgsConstructor;
import org.example.dto.internal.CategoryInternalSummaryResponse;
import org.example.dto.internal.CategoryInternalValidationResponse;
import org.example.entity.Category;
import org.example.exp.AppBadException;
import org.example.repository.CategoryRepository;
import org.example.service.CategoryService;
import org.example.service.ResourceBundleService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/categories")
public class CategoryInternalController {

    private final CategoryService categoryService;

    @GetMapping("/{categoryId}/exists")
    public CategoryInternalValidationResponse exists(@PathVariable Long categoryId) {
        Category category = categoryService.findById(categoryId);
        if (category == null) throw new AppBadException("category.not.found");

        return CategoryInternalValidationResponse.builder()
                .categoryId(categoryId)
                .exists(true)
                .active(Boolean.TRUE.equals(category.getIsActive()))
                .build();
    }

    @GetMapping("/{categoryId}/summary")
    public CategoryInternalSummaryResponse summary(@PathVariable Long categoryId) {
        Category category = categoryService.findById(categoryId);
        if (category == null) throw new AppBadException("category.not.found");

        return CategoryInternalSummaryResponse.builder()
                .categoryId(category.getId())
                .name(category.getNameUz())
                .slug(category.getSlug())
                .build();
    }
}
