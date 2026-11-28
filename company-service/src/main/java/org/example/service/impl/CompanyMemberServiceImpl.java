package org.example.service.impl;

import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.example.config.internal.UserProfileClient;
import org.example.dto.member.CompanyMemberResponse;
import org.example.entity.Company;
import org.example.entity.CompanyMember;
import org.example.enums.AppLanguage;
import org.example.enums.CompanyMemberRole;
import org.example.exp.CompanyMemberException;
import org.example.mapper.CompanyMemberMapper;
import org.example.repository.CompanyMemberRepository;
import org.example.repository.CompanyRepository;
import org.example.service.CompanyMemberService;
import org.example.service.ResourceBundleService;
import org.example.utils.SpringSecurityUtil;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class CompanyMemberServiceImpl implements CompanyMemberService {
    private final CompanyRepository companies;
    private final CompanyMemberRepository members;
    private final CompanyMemberMapper mapper;
    private final UserProfileClient profiles;
    private final ResourceBundleService messages;
    private final JdbcTemplate jdbc;

    @Override
    public List<CompanyMemberResponse> list(Long companyId, AppLanguage language) {
        Long actor = currentProfile(language);
        requireAvailableCompany(companies.findByIdAndDeletedFalse(companyId), language);
        requireActiveMember(companyId, actor, language);

        List<CompanyMemberResponse> response = members.findAllByCompanyIdAndDeletedFalseOrderByIdAsc(companyId)
                .stream().map(mapper::toResponse).toList();
        audit(companyId, actor, null, "MEMBERS_LISTED");
        return response;
    }

    @Override
    public CompanyMemberResponse add(Long companyId, Long profileId, AppLanguage language) {
        Long actor = currentProfile(language);
        requireAdminForUpdate(companyId, actor, language);
        Optional<CompanyMember> existing = members.findByCompanyIdAndProfileId(companyId, profileId);
        if (existing.filter(member -> Boolean.FALSE.equals(member.getDeleted())).isPresent()) {
            throw error(HttpStatus.CONFLICT, "company.member.already.active", language);
        }
        requireExistingProfile(profileId, language);

        // Qayta qo'shilganda eski a'zolikni faollashtiramiz. Bir juftlikka bitta yozuv saqlanadi.
        CompanyMember member = existing.orElseGet(() -> mapper.toEntity(companyId, profileId, CompanyMemberRole.MEMBER, actor));
        member.setDeleted(false);
        member.setRole(CompanyMemberRole.MEMBER);
        member.setModifiedBy(actor);
        CompanyMember saved = members.save(member);
        audit(companyId, actor, profileId, "MEMBER_ADDED");
        return mapper.toResponse(saved);
    }

    @Override
    public void remove(Long companyId, Long profileId, AppLanguage language) {
        Long actor = currentProfile(language);
        requireAdminForUpdate(companyId, actor, language);
        CompanyMember member = members.findByCompanyIdAndProfileId(companyId, profileId)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "company.member.not.found", language));
        if (member.getRole() == CompanyMemberRole.ADMIN) {
            throw error(HttpStatus.CONFLICT, "company.member.admin.protected", language);
        }
        if (Boolean.TRUE.equals(member.getDeleted())) {
            return;
        }

        // A'zolik yozuvi tarixda qoladi. Faol a'zolar so'roviga bu profil endi kirmaydi.
        member.setDeleted(true);
        member.setModifiedBy(actor);
        audit(companyId, actor, profileId, "MEMBER_REMOVED");
    }

    private Long currentProfile(AppLanguage language) {
        Long profileId = SpringSecurityUtil.getProfileId();
        if (profileId == null) {
            throw error(HttpStatus.UNAUTHORIZED, "company.member.profile.required", language);
        }
        return profileId;
    }

    private void requireAvailableCompany(Optional<Company> found, AppLanguage language) {
        Company company = found.filter(value -> value.getDeletedAt() == null)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "company.not.found", language));
        if (Boolean.TRUE.equals(company.getIsBlocked())) {
            throw error(HttpStatus.FORBIDDEN, "company.member.forbidden", language);
        }
    }

    private CompanyMember requireActiveMember(Long companyId, Long actor, AppLanguage language) {
        return members.findByCompanyIdAndProfileId(companyId, actor)
                .filter(member -> Boolean.FALSE.equals(member.getDeleted()))
                .orElseThrow(() -> error(HttpStatus.FORBIDDEN, "company.member.forbidden", language));
    }

    private void requireAdminForUpdate(Long companyId, Long actor, AppLanguage language) {
        // Bir kompaniyadagi parallel o'zgarishlar navbat bilan bajariladi.
        requireAvailableCompany(companies.findForMembershipUpdate(companyId), language);
        if (requireActiveMember(companyId, actor, language).getRole() != CompanyMemberRole.ADMIN) {
            throw error(HttpStatus.FORBIDDEN, "company.member.admin.required", language);
        }
    }

    private void requireExistingProfile(Long profileId, AppLanguage language) {
        try {
            UserProfileClient.ProfileSummary profile = profiles.summary(profileId);
            if (profile == null || !profileId.equals(profile.id())) {
                throw error(HttpStatus.SERVICE_UNAVAILABLE, "company.member.profile.unavailable", language);
            }
        } catch (FeignException e) {
            // user-service mavjud bo'lmagan profil uchun 400 qaytaradi.
            if (e.status() == 400 || e.status() == 404) {
                throw error(HttpStatus.NOT_FOUND, "company.member.profile.not.found", language);
            }
            throw error(HttpStatus.SERVICE_UNAVAILABLE, "company.member.profile.unavailable", language);
        }
    }

    private void audit(Long companyId, Long actor, Long target, String action) {
        // Jurnal va a'zolik o'zgarishi birga saqlanadi yoki birga bekor qilinadi.
        jdbc.update("""
                INSERT INTO company_member_audit (company_id, actor_profile_id, target_profile_id, action)
                VALUES (?, ?, ?, ?)
                """, companyId, actor, target, action);
    }

    private CompanyMemberException error(HttpStatus status, String key, AppLanguage language) {
        return new CompanyMemberException(status, messages.getMessage(key, language));
    }
}
