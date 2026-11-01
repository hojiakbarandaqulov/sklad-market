package com.example.service;

import com.example.config.client.CompanyClient;
import com.example.enums.AppLanguage;
import com.example.exp.AppBadException;
import com.example.exp.AppNotFoundException;
import com.example.exp.AppServiceUnavailableException;
import com.example.utils.SpringSecurityUtil;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.Objects;

/** Xususiy ombor ro'yxatlari token egasining faol kompaniyasigagina ochiladi. */
@Service
@RequiredArgsConstructor
public class CompanyAccessService {
    private final CompanyClient companies;
    private final ResourceBundleService messages;

    public void requireOwner(Long companyId, AppLanguage language) {
        Long profileId = SpringSecurityUtil.getProfileId();
        if (profileId == null)
            throw new AccessDeniedException(messages.getMessage("auth.profile.missing", language));
        if (companyId == null || companyId <= 0)
            throw new AppBadException(messages.getMessage("warehouse.company.invalid", language));
        try {
            var company = companies.ownershipCheck(companyId, profileId);
            if (company == null) throw unavailable(language);
            if (!company.isExists())
                throw new AppNotFoundException(messages.getMessage("warehouse.company.not.found", language));
            if (!Objects.equals(company.getCompanyId(), companyId)) throw unavailable(language);
            if (!company.isOwner())
                throw new AccessDeniedException(messages.getMessage("auth.access.denied", language));
            if (!company.isActive())
                throw new AccessDeniedException(messages.getMessage("warehouse.company.inactive", language));
        } catch (FeignException exception) {
            if (exception.status() == 404)
                throw new AppNotFoundException(messages.getMessage("warehouse.company.not.found", language));
            if (exception.status() == 401 || exception.status() == 403)
                throw new AccessDeniedException(messages.getMessage("auth.access.denied", language));
            throw unavailable(language);
        }
    }

    private AppServiceUnavailableException unavailable(AppLanguage language) {
        return new AppServiceUnavailableException(messages.getMessage("warehouse.company.service.unavailable", language));
    }
}
