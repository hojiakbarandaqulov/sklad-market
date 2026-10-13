package com.example.service.impl;

import com.example.dto.category.JobCategoryDTO;
import com.example.dto.category.JobCategoryRequest;
import com.example.entity.JobCategory;
import com.example.enums.AppLanguage;
import com.example.exp.AppBadException;
import com.example.exp.AppNotFoundException;
import com.example.repository.JobCategoryRepository;
import com.example.service.JobCategoryService;
import com.example.service.ResourceBundleService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class JobCategoryServiceImpl implements JobCategoryService {
    private final JobCategoryRepository repository;
    private final ResourceBundleService messages;

    @Override
    @Transactional
    public JobCategoryDTO create(JobCategoryRequest request, AppLanguage language) {
        JobCategory category = new JobCategory();
        apply(category, request);
        return toDTO(repository.save(category), language);
    }

    @Override
    @Transactional
    public JobCategoryDTO update(Long id, JobCategoryRequest request, AppLanguage language) {
        JobCategory category = find(id, language);
        apply(category, request);
        return toDTO(repository.save(category), language);
    }

    @Override
    @Transactional
    public void delete(Long id, AppLanguage language) {
        JobCategory category = find(id, language);
        // Preserve references from existing vacancies.
        category.setDeleted(true);
        repository.save(category);
    }

    @Override
    public JobCategoryDTO get(Long id, boolean activeOnly, AppLanguage language) {
        return toDTO(find(id, language), language);
    }

    @Override
    public PageImpl<JobCategoryDTO> list(int page, int perPage, boolean activeOnly, AppLanguage language) {
        if (page < 1 || perPage < 1 || perPage > 100) {
            throw new AppBadException(messages.getMessage("vacancy.filter.invalid", language));
        }
        var pageable = PageRequest.of(page - 1, perPage, Sort.by("id"));
        Page<JobCategory> allByDeletedFalse = repository.findAllByDeletedFalse(pageable);
        return new PageImpl<>(allByDeletedFalse.getContent().stream().map(c -> toDTO(c, language)).toList(),
                pageable, allByDeletedFalse.getTotalElements());
    }

    @Override
    public void validateSelection(Long categoryId, AppLanguage language) {
        // Nullable for existing clients and vacancies created before categories were introduced.
        if (categoryId == null) return;
        if (categoryId <= 0 || repository.findByIdAndDeletedFalse(categoryId).isEmpty()) {
            throw new AppBadException(messages.getMessage("job.category.invalid", language));
        }
    }

    private JobCategory find(Long id, AppLanguage language) {
        if (id == null || id <= 0) {
            throw new AppBadException(messages.getMessage("job.category.invalid", language));
        }
        return (repository.findByIdAndDeletedFalse(id))
                .orElseThrow(() -> new AppNotFoundException(messages.getMessage("job.category.not.found", language)));
    }

    private void apply(JobCategory category, JobCategoryRequest request) {
        category.setNameUz(request.getNameUz().trim());
        category.setNameRu(request.getNameRu().trim());
        category.setNameEn(request.getNameEn().trim());
    }

    private JobCategoryDTO toDTO(JobCategory category, AppLanguage language) {
        String name = switch (language) {
            case UZ -> category.getNameUz();
            case RU -> category.getNameRu();
            case EN -> category.getNameEn();
        };
        return new JobCategoryDTO(category.getId(), name, category.getNameUz(), category.getNameRu(),
                category.getNameEn());
    }
}
