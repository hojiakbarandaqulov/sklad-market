package org.example.service.impl;

import lombok.RequiredArgsConstructor;
import org.example.dto.CategoryAttributeCreateRequest;
import org.example.dto.CategoryResponse;
import org.example.dto.CategoryUpdateRequest;
import org.example.dto.categoryAtribute.CategoryAttributeResponse;
import org.example.dto.categoryAtribute.CategoryCreateRequest;
import org.example.entity.Category;
import org.example.entity.CategoryAttribute;
import org.example.enums.AppLanguage;
import org.example.exp.AppBadException;
import org.example.repository.CategoryAttributeRepository;
import org.example.repository.CategoryRepository;
import org.example.service.AdminCategoryService;
import org.example.service.ResourceBundleService;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminCategoryServiceImpl implements AdminCategoryService {

    private final CategoryRepository categoryRepository;
    private final CategoryAttributeRepository categoryAttributeRepository;
    private final ResourceBundleService messageService;

    @Override
    public List<CategoryResponse> getCategories() {
        return categoryRepository.findAll(Sort.by(Sort.Direction.ASC, "sortOrder"))
                .stream()
                .map(this::toCategoryResponse)
                .toList();
    }

    @Override
    @Transactional
    public CategoryResponse createCategory(CategoryCreateRequest request, AppLanguage language) {
        if (categoryRepository.existsBySlug(request.getSlug())) {
            throw new AppBadException(messageService.getMessage("category.slug.exists", language));
        }

        Category category = new Category();
        applyCategoryFields(category, request, language);
        return toCategoryResponse(categoryRepository.save(category));
    }

    @Override
    @Transactional
    public CategoryResponse updateCategory(Long id, CategoryUpdateRequest request, AppLanguage language) {
        Category category = getCategory(id, language);
        if (!category.getSlug().equalsIgnoreCase(request.getSlug()) && categoryRepository.existsBySlug(request.getSlug())) {
            throw new AppBadException(messageService.getMessage("category.slug.exists", language));
        }

        applyCategoryFields(category, request, language);
        return toCategoryResponse(categoryRepository.save(category));
    }

    @Override
    @Transactional
    public void archiveCategory(Long id, AppLanguage language) {
        Category category = getCategory(id, language);
        category.setIsActive(Boolean.FALSE);
        categoryRepository.save(category);
    }

    @Override
    @Transactional
    public CategoryAttributeResponse addAttribute(Long categoryId, CategoryAttributeCreateRequest request, AppLanguage language) {
        Category category = getCategory(categoryId, language);
        if (categoryAttributeRepository.existsByCategory_IdAndCodeIgnoreCase(categoryId, request.getCode())) {
            throw new AppBadException(messageService.getMessage("category.attribute.code.exists", language));
        }

        CategoryAttribute attribute = new CategoryAttribute();
        attribute.setCategory(category);
        applyAttributeFields(attribute, request);
        return toAttributeResponse(categoryAttributeRepository.save(attribute));
    }

    @Override
    @Transactional
    public CategoryAttributeResponse updateAttribute(Long categoryId, Long attrId, CategoryAttributeCreateRequest request, AppLanguage language) {
        CategoryAttribute attribute = categoryAttributeRepository.findByIdAndCategory_Id(attrId, categoryId)
                .orElseThrow(() -> new AppBadException(messageService.getMessage("category.attribute.not.found", language)));

        if (!attribute.getCode().equalsIgnoreCase(request.getCode())
                && categoryAttributeRepository.existsByCategory_IdAndCodeIgnoreCase(categoryId, request.getCode())) {
            throw new AppBadException(messageService.getMessage("category.attribute.code.exists", language));
        }

        applyAttributeFields(attribute, request);
        return toAttributeResponse(categoryAttributeRepository.save(attribute));
    }

    @Override
    @Transactional
    public void deleteAttribute(Long categoryId, Long attrId, AppLanguage language) {
        CategoryAttribute attribute = categoryAttributeRepository.findByIdAndCategory_Id(attrId, categoryId)
                .orElseThrow(() -> new AppBadException(messageService.getMessage("category.attribute.not.found", language)));
        categoryAttributeRepository.delete(attribute);
    }

    private Category getCategory(Long id, AppLanguage language) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new AppBadException(messageService.getMessage("category.not.found", language)));
    }

    private void applyCategoryFields(Category category, CategoryCreateRequest request, AppLanguage language) {
        category.setParent(resolveParent(request.getParentId(), category.getId(), language));
        category.setNameUz(request.getNameUz());
        category.setNameRu(request.getNameRu());
        category.setNameEn(request.getNameEn());
        category.setSlug(request.getSlug());
        category.setSortOrder(request.getSortOrder());
        category.setIsActive(request.getIsActive());
    }

    private void applyCategoryFields(Category category, CategoryUpdateRequest request, AppLanguage language) {
        category.setParent(resolveParent(request.getParentId(), category.getId(), language));
        category.setNameUz(request.getNameUz());
        category.setNameRu(request.getNameRu());
        category.setNameEn(request.getNameEn());
        category.setSlug(request.getSlug());
        category.setSortOrder(request.getSortOrder());
        category.setIsActive(request.getIsActive());
    }

    private Category resolveParent(Long parentId, Long currentId, AppLanguage language) {
        if (parentId == null || parentId == 0) {
            return null;
        }
        if (currentId != null && currentId.equals(parentId)) {
            throw new AppBadException(messageService.getMessage("category.cannot.be.own.parent", language));
        }
        return categoryRepository.findById(parentId)
                .orElseThrow(() -> new AppBadException(messageService.getMessage("category.parent.not.found", language)));
    }

    private void applyAttributeFields(CategoryAttribute attribute, CategoryAttributeCreateRequest request) {
        attribute.setCode(request.getCode());
        attribute.setLabel(request.getLabel());
        attribute.setDataType(request.getDataType());
        attribute.setIsRequired(request.getIsRequired());
        attribute.setIsFilterable(request.getIsFilterable());
        attribute.setOptionsJson(request.getOptionsJson());
        attribute.setSortOrder(request.getSortOrder());
    }

    private CategoryResponse toCategoryResponse(Category category) {
        CategoryResponse response = new CategoryResponse();
        response.setId(category.getId());
        response.setNameUz(category.getNameUz());
        response.setNameRu(category.getNameRu());
        response.setNameEn(category.getNameEn());
        response.setSlug(category.getSlug());
        response.setSortOrder(category.getSortOrder());
        response.setIsActive(category.getIsActive());
        return response;
    }

    private CategoryAttributeResponse toAttributeResponse(CategoryAttribute attribute) {
        CategoryAttributeResponse response = new CategoryAttributeResponse();
        response.setId(attribute.getId());
        response.setCode(attribute.getCode());
        response.setLabel(attribute.getLabel());
        response.setDataType(attribute.getDataType());
        response.setIsRequired(attribute.getIsRequired());
        response.setIsFilterable(attribute.getIsFilterable());
        response.setOptionsJson(attribute.getOptionsJson());
        response.setSortOrder(attribute.getSortOrder());
        return response;
    }
}
