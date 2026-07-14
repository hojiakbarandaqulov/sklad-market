package org.example.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.config.internal.FileClient;
import org.example.dto.ApiResponse;
import org.example.dto.attach.AttachDto;
import org.example.dto.attach.AttachInfoDto;
import org.example.dto.categoryAtribute.CategoryCreateRequest;
import org.example.dto.CategoryResponse;
import org.example.dto.CategoryUpdateRequest;
import org.example.dto.internal.CategoryInternalSummaryResponse;
import org.example.dto.internal.CategoryInternalValidationResponse;
import org.example.entity.Category;
import org.example.enums.AppLanguage;
import org.example.exp.AppBadException;
import org.example.repository.CategoryRepository;
import org.example.service.CategoryService;
import org.example.service.ResourceBundleService;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.stream.Stream;

@Service
@Slf4j
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {
    private final CategoryRepository categoryRepository;
    private final FileClient fileClient;
    private final ModelMapper modelMapper;
    private final ResourceBundleService messageService;

    @Override
    public CategoryResponse create(CategoryCreateRequest request, MultipartFile file, AppLanguage language) {
        Category category = new Category();
        ApiResponse<AttachDto> upload = fileClient.upload(file, language.name());
        if (upload==null){
            throw new IllegalArgumentException(messageService.getMessage("file.upload.don't.success",language));
        }
        if (request.getParentId() != null && request.getParentId() != 0) {
            Category parent = categoryRepository.findById(request.getParentId())
                    .orElseThrow(() -> new AppBadException(messageService.getMessage("category.not.found", language)));
            category.setParent(parent);
        }
        if (categoryRepository.existsBySlug(request.getSlug())) {
            throw new AppBadException(messageService.getMessage("category.slug.exists", language));
        }
        category.setNameUz(request.getNameUz());
        category.setNameRu(request.getNameRu());
        category.setNameEn(request.getNameEn());
        category.setSlug(request.getSlug()); // unique tekshiruv ham kerak
        category.setIconId(upload.getData().getId());
        category.setIconUrl(upload.getData().getUrl());
        category.setSortOrder(request.getSortOrder());
        category.setIsActive(request.getIsActive());
        Category save = categoryRepository.save(category);
        return modelMapper.map(save, CategoryResponse.class);
    }

    @Override
    public CategoryResponse update(Long id, CategoryUpdateRequest request, AppLanguage language) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new AppBadException(messageService.getMessage("category.not.found", language)));

        if (!category.getSlug().equals(request.getSlug()) &&
                categoryRepository.existsBySlug(request.getSlug())) {
            throw new AppBadException(messageService.getMessage("category.slug.exists", language));
        }

        if (request.getParentId() != null) {
            if (request.getParentId().equals(id)) {
                throw new AppBadException(messageService.getMessage("category.cannot.be.own.parent", language));
            }
            Category parent = categoryRepository.findById(request.getParentId())
                    .orElseThrow(() -> new AppBadException(messageService.getMessage("category.parent.not.found", language)));
            category.setParent(parent);
        } else {
            category.setParent(null);
        }
        category.setNameUz(request.getNameUz());
        category.setNameRu(request.getNameRu());
        category.setNameEn(request.getNameEn());
        category.setSlug(request.getSlug());
        category.setSortOrder(request.getSortOrder());
        category.setIsActive(request.getIsActive());

        Category saved = categoryRepository.save(category);
        return modelMapper.map(saved, CategoryResponse.class);  // ← null emas!
    }

    @Override
    public Boolean delete(Long id, AppLanguage language) {
        Category category = categoryRepository.findById(id).orElseThrow(() -> new AppBadException(messageService.getMessage("category.not.found", language)));
        category.setIsActive(false);
        fileClient.delete(category.getIconId(), language.name());
        categoryRepository.save(category);
        return true;
    }

    @Override
    public Page<CategoryResponse> getCategory(Pageable pageable, AppLanguage language) {
        Pageable sortedPagable= PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                Sort.by(Sort.Direction.ASC, "sortOrder")
        );
        Page<Category> all = categoryRepository.findAll(sortedPagable);
        return all.map(category -> {
            CategoryResponse response = new CategoryResponse();
            response.setId(category.getId());
            response.setSlug(category.getSlug());
            response.setSortOrder(category.getSortOrder());
            response.setIsActive(category.getIsActive());
            response.setIconId(category.getIconId());
            response.setIconUrl(category.getIconUrl());

            switch (language) {
                case EN -> response.setNameEn(category.getNameEn());
                case RU -> response.setNameRu(category.getNameRu());
                case UZ -> response.setNameUz(category.getNameUz());
            }
            return response;
        });
    }

    @Override
    public CategoryResponse getCategoryBySlug(String slug, AppLanguage language) {
        Category category = categoryRepository.findBySlugAndIsActiveTrue(slug);
        return modelMapper.map(category, CategoryResponse.class);
    }

    @Override
    public Category findById(Long categoryId) {
       return categoryRepository.findByIdAndIsActiveTrue(categoryId);
    }

}
