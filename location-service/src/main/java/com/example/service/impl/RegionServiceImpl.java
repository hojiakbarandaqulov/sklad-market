package com.example.service.impl;

import com.example.dto.RegionDTO;
import com.example.dto.RegionResponse;
import com.example.entity.Region;
import com.example.enums.AppLanguage;
import com.example.exp.AppBadException;
import com.example.repository.RegionRepository;
import com.example.service.RegionService;
import com.example.service.ResourceBundleService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class RegionServiceImpl implements RegionService {

    private final RegionRepository regionRepository;
    private final ResourceBundleService messageService;

    @Override
    @Transactional
    public RegionResponse create(RegionDTO request, AppLanguage language) {
        String code = normalizeCode(request.getCode());
        if (regionRepository.existsByCodeIgnoreCase(code)) {
            throw new AppBadException(messageService.getMessage("region.code.exists", language));
        }

        Region region = new Region();
        apply(region, request, code);
        return toResponse(regionRepository.save(region), language);
    }

    @Override
    @Transactional
    public RegionResponse update(Long id, RegionDTO request, AppLanguage language) {
        Region region = getActiveRegion(id, language);
        String code = normalizeCode(request.getCode());
        if (regionRepository.existsByCodeIgnoreCaseAndIdNot(code, id)) {
            throw new AppBadException(messageService.getMessage("region.code.exists", language));
        }

        apply(region, request, code);
        return toResponse(regionRepository.save(region), language);
    }

    @Override
    @Transactional(readOnly = true)
    public PageImpl<RegionResponse> getAll(Pageable pageable, AppLanguage language) {
        Page<Region> regionPage = regionRepository.findAllByDeletedFalse(pageable);
        List<RegionResponse> content = regionPage.getContent()
                .stream()
                .map(region -> toResponse(region, language))
                .toList();
        return new PageImpl<>(content, pageable, regionPage.getTotalElements());
    }

    @Override
    @Transactional(readOnly = true)
    public RegionResponse getById(Long id, AppLanguage language) {
        return toResponse(getActiveRegion(id, language), language);
    }

    @Override
    @Transactional
    public void delete(Long id, AppLanguage language) {
        Region region = getActiveRegion(id, language);
        region.setDeleted(Boolean.TRUE);
        regionRepository.save(region);
    }

    private Region getActiveRegion(Long id, AppLanguage language) {
        return regionRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new AppBadException(
                        messageService.getMessage("region.not.found", language)));
    }

    private void apply(Region region, RegionDTO request, String code) {
        region.setCode(code);
        region.setNameUz(request.getNameUz().trim());
        region.setNameRu(request.getNameRu().trim());
        region.setNameEn(request.getNameEn().trim());
        region.setType(request.getType());
        region.setSortOrder(request.getSortOrder());
    }

    private String normalizeCode(String code) {
        return code.trim().toUpperCase(Locale.ROOT);
    }

    private RegionResponse toResponse(Region region, AppLanguage language) {
        return new RegionResponse(
                region.getId(),
                region.getCode(),
                resolveName(region, language),
                region.getType(),
                region.getSortOrder()
        );
    }

    private String resolveName(Region region, AppLanguage language) {
        AppLanguage resolvedLanguage = language == null ? AppLanguage.UZ : language;
        return switch (resolvedLanguage) {
            case RU -> region.getNameRu();
            case EN -> region.getNameEn();
            case UZ -> region.getNameUz();
        };
    }
}
