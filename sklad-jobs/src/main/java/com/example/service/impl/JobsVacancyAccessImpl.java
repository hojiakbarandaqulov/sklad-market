package com.example.service.impl;

import com.example.service.JobsVacancyAccess;

import com.example.config.clent.CompanyClient;
import com.example.entity.Vacancy;
import com.example.enums.VacancyStatus;
import com.example.exp.AppConflictException;
import com.example.exp.AppNotFoundException;
import com.example.repository.VacancyRepository;
import com.example.utils.SpringSecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.stereotype.Service;
import java.util.List;

@Service @RequiredArgsConstructor
public class JobsVacancyAccessImpl implements JobsVacancyAccess {
    private final VacancyRepository vacancies;
    private final CompanyClient companies;
    @Override
    public long viewer() {
        Long id = SpringSecurityUtil.getProfileId();
        if (id == null || id <= 0) throw new AuthenticationCredentialsNotFoundException("Authentication required");
        return id;
    }
    @Override
    public List<Long> ownedCompanies(long viewer) {
        var ids = companies.getOwnedCompanyIds(viewer);
        return ids == null ? List.of() : ids;
    }
    @Override
    public Vacancy owned(Long id, boolean lock, boolean published) {
        if (id == null || id <= 0) throw new AppNotFoundException("Vacancy not found");
        long viewer = viewer();
        Vacancy vacancy = (lock ? vacancies.findForApplication(id) : vacancies.findByIdAndDeletedFalse(id))
                .orElseThrow(() -> new AppNotFoundException("Vacancy not found"));
        if (!isOwner(vacancy,viewer))
            throw new AppNotFoundException("Vacancy not found");
        if (published && vacancy.getVacancyStatus() != VacancyStatus.PUBLISHED)
            throw new AppConflictException("Vacancy is not open");
        return vacancy;
    }
    @Override
    public boolean isOwner(Vacancy vacancy,long profileId) {
        return Long.valueOf(profileId).equals(vacancy.getBuyerId())
                || vacancy.getCompanyId()!=null && ownedCompanies(profileId).contains(vacancy.getCompanyId());
    }
}
