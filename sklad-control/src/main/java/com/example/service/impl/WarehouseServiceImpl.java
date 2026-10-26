package com.example.service.impl;

import com.example.config.client.CompanyClient;
import com.example.dto.internal.CompanyBranchResponse;
import com.example.dto.internal.CompanyOwnershipResponse;
import com.example.dto.warehouse.*;
import com.example.entity.Warehouse;
import com.example.enums.AppLanguage;
import com.example.enums.WarehouseStatus;
import com.example.exp.AppBadException;
import com.example.exp.AppConflictException;
import com.example.exp.AppNotFoundException;
import com.example.exp.AppServiceUnavailableException;
import com.example.repository.WarehouseRepository;
import com.example.repository.WarehouseStockRepository;
import com.example.service.ResourceBundleService;
import com.example.service.WarehouseService;
import com.example.utils.SpringSecurityUtil;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WarehouseServiceImpl implements WarehouseService {
    private final WarehouseRepository warehouseRepository;
    private final WarehouseStockRepository warehouseStockRepository;
    private final CompanyClient companyClient;
    private final ResourceBundleService messages;

    @Override
    @Transactional
    public WarehouseDTO create(WarehouseCreateRequest request, AppLanguage language) {
        Long profileId = requiredProfileId(language);
        validateCompanyAccess(request.getCompanyId(), profileId, language);
        validateBranch(request.getCompanyId(), request.getCompanyBranchId(), language);

        Warehouse warehouse = new Warehouse();
        warehouse.setCompanyId(request.getCompanyId());
        warehouse.setCompanyBranchId(request.getCompanyBranchId());
        warehouse.setName(requiredName(request.getName(), language));
        warehouse.setRegionId(request.getRegionId());
        warehouse.setAddress(blankToNull(request.getAddress()));
        warehouse.setStatus(WarehouseStatus.ACTIVE);
        warehouse.setCreatedBy(profileId);
        warehouse.setModifiedBy(profileId);
        return toDTO(warehouseRepository.saveAndFlush(warehouse));
    }

    @Override
    public PageImpl<WarehouseDTO> list(WarehouseFilter filter, AppLanguage language) {
        Long profileId = requiredProfileId(language);
        if (filter.getPage() < 1 || filter.getPerPage() < 1 || filter.getPerPage() > 100) {
            throw new AppBadException(messages.getMessage("warehouse.pagination.invalid", language));
        }
        validateCompanyAccess(filter.getCompanyId(), profileId, language);

        Specification<Warehouse> specification = (root, query, cb) -> cb.and(
                cb.equal(root.get("companyId"), filter.getCompanyId()),
                cb.isFalse(root.get("deleted")));
        if (filter.getCompanyBranchId() != null) {
            specification = specification.and((root, query, cb) ->
                    cb.equal(root.get("companyBranchId"), filter.getCompanyBranchId()));
        }
        if (filter.getStatus() != null) {
            specification = specification.and((root, query, cb) ->
                    cb.equal(root.get("status"), filter.getStatus()));
        }
        if (StringUtils.hasText(filter.getQ())) {
            String search = filter.getQ().strip().toLowerCase(Locale.ROOT)
                    .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
            specification = specification.and((root, query, cb) ->
                    cb.like(cb.lower(root.get("name")), "%" + search + "%", '\\'));
        }

        var pageable = PageRequest.of(filter.getPage() - 1, filter.getPerPage(), Sort.by(Sort.Direction.DESC, "id"));
        Page<Warehouse> result = warehouseRepository.findAll(specification, pageable);
        return new PageImpl<>(result.getContent().stream().map(this::toDTO).toList(),
                pageable, result.getTotalElements());
    }

    @Override
    public WarehouseDTO get(Long id, AppLanguage language) {
        Long profileId = requiredProfileId(language);
        Warehouse warehouse = find(id, language);
        validateCompanyAccess(warehouse.getCompanyId(), profileId, language);
        return toDTO(warehouse);
    }

    @Override
    @Transactional
    public WarehouseDTO update(Long id, WarehouseUpdateRequest request, AppLanguage language) {
        Long profileId = requiredProfileId(language);
        Warehouse warehouse = find(id, language);
        // Kompaniya requestdan olinmaydi: tahrirlanayotgan omborning o'z kompaniyasi tekshiriladi.
        validateCompanyAccess(warehouse.getCompanyId(), profileId, language);
        if (!request.hasChanges()) {
            throw new AppBadException(messages.getMessage("warehouse.patch.empty", language));
        }
        if (request.isNameProvided()) {
            warehouse.setName(requiredName(request.getName(), language));
        }
        if (request.isCompanyBranchIdProvided()) {
            validateBranch(warehouse.getCompanyId(), request.getCompanyBranchId(), language);
            warehouse.setCompanyBranchId(request.getCompanyBranchId());
        }
        if (request.isRegionIdProvided()) {
            warehouse.setRegionId(request.getRegionId());
        }
        if (request.isAddressProvided()) {
            warehouse.setAddress(blankToNull(request.getAddress()));
        }
        warehouse.setModifiedBy(profileId);
        return toDTO(warehouseRepository.saveAndFlush(warehouse));
    }

    @Override
    @Transactional
    public WarehouseDTO archive(Long id, AppLanguage language) {
        Long profileId = requiredProfileId(language);
        Warehouse warehouse = warehouseRepository.lockPresent(id)
                .orElseThrow(() -> new AppNotFoundException(messages.getMessage("warehouse.not.found", language)));
        validateCompanyAccess(warehouse.getCompanyId(), profileId, language);
        if (warehouseStockRepository.hasReservedStock(id)) {
            throw new AppConflictException(
                    messages.getMessage("warehouse.archive.reserved", language));
        }

        if (warehouseStockRepository.hasOnHandStock(id)) {
            throw new AppConflictException(
                    messages.getMessage("warehouse.archive.stock", language));
        }
        warehouse.setStatus(WarehouseStatus.ARCHIVED);
        warehouse.setModifiedBy(profileId);
        return toDTO(warehouseRepository.saveAndFlush(warehouse));
    }

    @Override
    @Transactional
    public WarehouseDTO restore(Long id, AppLanguage language) {
        Long profileId = requiredProfileId(language);
        Warehouse warehouse = find(id, language);
        validateCompanyAccess(warehouse.getCompanyId(), profileId, language);
        warehouse.setStatus(WarehouseStatus.ACTIVE);
        warehouse.setModifiedBy(profileId);
        return toDTO(warehouseRepository.saveAndFlush(warehouse));
    }

    private Warehouse find(Long id, AppLanguage language) {
        if (id == null || id <= 0) {
            throw new AppBadException(messages.getMessage("warehouse.id.invalid", language));
        }
        return warehouseRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new AppNotFoundException(messages.getMessage("warehouse.not.found", language)));
    }

    private Long requiredProfileId(AppLanguage language) {
        Long profileId = SpringSecurityUtil.getProfileId();
        if (profileId == null) {
            throw new AccessDeniedException(messages.getMessage("auth.profile.missing", language));
        }
        return profileId;
    }

    private void validateCompanyAccess(Long companyId, Long profileId, AppLanguage language) {
        if (companyId == null || companyId <= 0) {
            throw new AppBadException(messages.getMessage("warehouse.company.invalid", language));
        }
        CompanyOwnershipResponse company;
        try {
            company = companyClient.ownershipCheck(companyId, profileId);
        } catch (FeignException exception) {
            if (exception.status() == 404) {
                throw new AppNotFoundException(messages.getMessage("warehouse.company.not.found", language));
            }
            if (exception.status() == 401 || exception.status() == 403) {
                throw new AccessDeniedException(messages.getMessage("auth.access.denied", language));
            }
            throw companyServiceUnavailable(exception.status(), language);
        }
        if (company == null) throw companyServiceUnavailable(null, language);
        if (!company.isExists()) {
            throw new AppNotFoundException(messages.getMessage("warehouse.company.not.found", language));
        }
        if (!Objects.equals(company.getCompanyId(), companyId)) {
            throw companyServiceUnavailable(null, language);
        }
        if (!company.isOwner()) {
            throw new AccessDeniedException(messages.getMessage("auth.access.denied", language));
        }
        if (!company.isActive()) {
            throw new AccessDeniedException(messages.getMessage("warehouse.company.inactive", language));
        }
    }

    private void validateBranch(Long companyId, Long branchId, AppLanguage language) {
        if (branchId == null) return;
        if (branchId <= 0) {
            throw new AppBadException(messages.getMessage("warehouse.branch.invalid", language));
        }
        CompanyBranchResponse branch;
        try {
            branch = companyClient.getBranch(companyId, branchId);
        } catch (FeignException exception) {
            if (exception.status() == 400 || exception.status() == 404) {
                throw new AppBadException(messages.getMessage("warehouse.branch.invalid", language));
            }
            if (exception.status() == 401 || exception.status() == 403) {
                throw new AccessDeniedException(messages.getMessage("auth.access.denied", language));
            }
            throw companyServiceUnavailable(exception.status(), language);
        }
        if (branch == null) throw companyServiceUnavailable(null, language);
        if (!branch.isExists() || !Objects.equals(branch.getId(), branchId)
                || !Objects.equals(branch.getCompanyId(), companyId)) {
            throw new AppBadException(messages.getMessage("warehouse.branch.invalid", language));
        }
    }

    private AppServiceUnavailableException companyServiceUnavailable(Integer status, AppLanguage language) {
        log.warn("Company service response unavailable or invalid, status={}", status);
        return new AppServiceUnavailableException(messages.getMessage("warehouse.company.service.unavailable", language));
    }

    private String requiredName(String name, AppLanguage language) {
        if (!StringUtils.hasText(name) || name.length() > 160) {
            throw new AppBadException(messages.getMessage("warehouse.name.invalid", language));
        }
        return name.strip();
    }

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.strip() : null;
    }

    private WarehouseDTO toDTO(Warehouse warehouse) {
        return WarehouseDTO.builder()
                .id(warehouse.getId())
                .companyId(warehouse.getCompanyId())
                .companyBranchId(warehouse.getCompanyBranchId())
                .name(warehouse.getName())
                .regionId(warehouse.getRegionId())
                .address(warehouse.getAddress())
                .status(warehouse.getStatus())
                .createdDate(warehouse.getCreatedDate())
                .modifiedDate(warehouse.getModifiedDate())
                .build();
    }
}
