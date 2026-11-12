package org.example.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.config.internal.FileClient;
import org.example.config.internal.ProductClient;
import org.example.dto.*;
import org.example.dto.attach.AttachDto;
import org.example.dto.attach.AttachInfoDto;
import org.example.dto.categoryAtribute.CategoryCreateRequest;
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

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@Slf4j
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {
    private final CategoryRepository categoryRepository;
    private final org.example.service.CategorySearchService categorySearchService;
    private final FileClient fileClient;
    private final ProductClient productClient;
    private final ModelMapper modelMapper;
    private final ResourceBundleService messageService;

    @Override
    public CategoryResponse create(CategoryCreateRequest request,
                                   MultipartFile file,
                                   AppLanguage language) {

        Category category = new Category();

        if (request.getParentId() != null && request.getParentId() != 0) {
            Category parent = categoryRepository.findById(request.getParentId())
                    .orElseThrow(() ->
                            new AppBadException(messageService.getMessage("category.not.found", language)));
            category.setParent(parent);
        }

        if (categoryRepository.existsBySlug(request.getSlug())) {
            throw new AppBadException(messageService.getMessage("category.slug.exists", language));
        }
        if (categoryRepository.existsBySortOrder(request.getSortOrder())) {
            throw new AppBadException(messageService.getMessage("category.sort.exists", language));
        }

        if (file != null && !file.isEmpty()) {
            try {
                ApiResponse<AttachDto> upload = fileClient.upload(file, language.name());

                if (upload != null && upload.getData() != null) {
                    category.setIconId(upload.getData().getId());
                    category.setIconUrl(upload.getData().getUrl());
                }

            } catch (Exception e) {
                log.error("File upload failed: {}", e.getMessage(), e);
            }
        }

        category.setNameUz(request.getNameUz());
        category.setNameRu(request.getNameRu());
        category.setNameEn(request.getNameEn());
        category.setSlug(request.getSlug());
        category.setSortOrder(request.getSortOrder());
        category.setIsActive(request.getIsActive());
        Category saved = saveAndIndex(category);

        return modelMapper.map(saved, CategoryResponse.class);
    }

    @Override
    public CategoryResponse update(Long id,
                                   CategoryUpdateRequest request,
                                   MultipartFile file,
                                   AppLanguage language) {

        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new AppBadException(
                        messageService.getMessage("category.not.found", language)
                ));

        if (!category.getSlug().equals(request.getSlug())
                && categoryRepository.existsBySlug(request.getSlug())) {
            throw new AppBadException(
                    messageService.getMessage("category.slug.exists", language)
            );
        }

        if (request.getParentId() != null && request.getParentId() != 0) {
            if (request.getParentId().equals(id)) {
                throw new AppBadException(
                        messageService.getMessage("category.cannot.be.own.parent", language)
                );
            }

            Category parent = categoryRepository.findById(request.getParentId())
                    .orElseThrow(() -> new AppBadException(
                            messageService.getMessage("category.parent.not.found", language)
                    ));

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

        // Faqat foydalanuvchi yangi rasm yuborsa ishlaydi
        if (file != null && !file.isEmpty()) {
            String oldIconId = category.getIconId();

            ApiResponse<AttachDto> upload = fileClient.upload(file, language.name());

            if (upload == null || upload.getData() == null) {
                throw new AppBadException(messageService.getMessage("category.image.upload.failed", language));
            }

            // Yangi rasmning ma'lumotlarini DB ga saqlash shart
            category.setIconId(upload.getData().getId());
            category.setIconUrl(upload.getData().getUrl());

            Category saved = saveAndIndex(category);

            // Eski rasm bo'lsa, keyin o'chiramiz
            if (oldIconId != null && !oldIconId.isBlank()) {
                try {
                    fileClient.delete(oldIconId, language.name());
                } catch (Exception e) {
                    log.warn("Eski category rasmi o'chirilmadi: {}", oldIconId);
                }
            }

            return modelMapper.map(saved, CategoryResponse.class);
        }

        Category saved = saveAndIndex(category);
        return modelMapper.map(saved, CategoryResponse.class);
    }

    @Override
    public Boolean delete(Long id, AppLanguage language) {
        Category category = categoryRepository.findById(id).orElseThrow(() -> new AppBadException(messageService.getMessage("category.not.found", language)));
        category.setIsActive(false);
        saveAndIndex(category);
        return true;
    }

    @Override
    public Page<CategoryResponse> getCategory(Pageable pageable, AppLanguage language) {
        Pageable sortedPageable = PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                Sort.by(Sort.Direction.ASC, "sortOrder")
        );
        return categoryRepository.findAllByIsActiveTrue(sortedPageable)
                .map(category -> toCategoryResponse(category, language));
    }

    @Override
    public Page<CategoryResponse> searchByName(String query, Pageable pageable, AppLanguage language) {
        Pageable sortedPageable = PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                Sort.by(Sort.Direction.ASC, "sortOrder")
        );

        return categorySearchService.search(query == null ? "" : query.trim(), sortedPageable)
                .map(category -> toCategoryResponse(category, language));
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

    @Override
    public List<TopCategoryResponse> topCategory(Long limit, AppLanguage language) {
        if (limit == null || limit < 1) {
            throw new AppBadException("limit kamida 1 bo‘lishi kerak");
        }
        ApiResponse<List<CategoryCountResponse>> categoryCounts = productClient.getCategoryCounts();

        Map<Long, Long> counts = categoryCounts.getData().stream()
                .collect(Collectors.toMap(
                        CategoryCountResponse::getCategoryId,
                        CategoryCountResponse::getCount
                ));


        return getCategoryTree(language).stream()
                .map(category -> {
                    TopCategoryResponse dto = new TopCategoryResponse();
                    dto.setId(category.getId());
                    dto.setName(category.getName());
                    dto.setSlug(category.getSlug());
                    dto.setIconUrl(category.getIconUrl());
                    dto.setProductCount(countProducts(category, counts));
                    return dto;
                })
                .sorted(Comparator.comparingLong(TopCategoryResponse::getProductCount)
                        .reversed())
                .limit(limit)
                .toList();
    }

    private long countProducts(CategoryTreeResponse category, Map<Long, Long> counts) {
        long total = counts.getOrDefault(category.getId(), 0L);

        for (CategoryTreeResponse child : category.getChildren()) {
            total += countProducts(child, counts);
        }

        return total;
    }

    @Override
    public List<CategoryTreeResponse> getCategoryTree(AppLanguage language) {
        List<Category> categories = categoryRepository.findAllByIsActiveTrueOrderBySortOrderAsc();

        Map<Long, List<Category>> byParentId = categories.stream()
                .collect(Collectors.groupingBy(category ->
                        category.getParent() == null ? 0L : category.getParent().getId()
                ));

        return buildTree(0L, byParentId, language);
    }

    private List<CategoryTreeResponse> buildTree(
            Long parentId,
            Map<Long, List<Category>> byParentId,
            AppLanguage language
    ) {
        return byParentId.getOrDefault(parentId, List.of())
                .stream()
                .map(category -> {
                    CategoryTreeResponse response = toTreeResponse(category, language);
                    response.setChildren(buildTree(category.getId(), byParentId, language));
                    return response;
                })
                .toList();
    }

    private CategoryTreeResponse toTreeResponse(Category category, AppLanguage language) {
        CategoryTreeResponse response = new CategoryTreeResponse();
        response.setId(category.getId());
        response.setParentId(category.getParent() == null ? null : category.getParent().getId());
        response.setName(resolveName(category, language));
        response.setSlug(category.getSlug());
        response.setIconId(category.getIconId());
        response.setIconUrl(category.getIconUrl());
        response.setSortOrder(category.getSortOrder());
        return response;
    }

    private String resolveName(Category category, AppLanguage language) {
        String preferred = switch (language == null ? AppLanguage.UZ : language) {
            case EN -> category.getNameEn();
            case RU -> category.getNameRu();
            default -> category.getNameUz();
        };
        if (preferred != null && !preferred.isBlank()) {
            return preferred;
        }
        return Stream.of(category.getNameUz(), category.getNameRu(), category.getNameEn(), category.getSlug())
                .filter(value -> value != null && !value.isBlank()).findFirst().orElse("");
    }

    private CategoryResponse toCategoryResponse(Category category, AppLanguage language) {
        CategoryResponse response = new CategoryResponse();
        response.setId(category.getId());
        response.setSlug(category.getSlug());
        response.setSortOrder(category.getSortOrder());
        response.setIsActive(category.getIsActive());
        response.setIconId(category.getIconId());
        response.setIconUrl(category.getIconUrl());

        response.setNameUz(category.getNameUz());
        response.setNameRu(category.getNameRu());
        response.setNameEn(category.getNameEn());
        response.setName(resolveName(category, language));

        return response;
    }

    private Category saveAndIndex(Category category) {
        Category saved = categoryRepository.save(category);
        categorySearchService.index(saved);
        return saved;
    }
}
