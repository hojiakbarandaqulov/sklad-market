package org.example.service.impl;

import lombok.RequiredArgsConstructor;
import org.example.dto.CompanyResponseDTO;
import org.example.dto.admin.ModerationDecisionRequest;
import org.example.dto.admin.ReasonRequest;
import org.example.entity.Company;
import org.example.enums.AppLanguage;
import org.example.enums.VerificationStatus;
import org.example.exp.AppBadException;
import org.example.repository.CompanyRepository;
import org.example.service.AdminCompanyService;
import org.example.service.ResourceBundleService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminCompanyServiceImpl implements AdminCompanyService {

    private final CompanyRepository companyRepository;
    private final ResourceBundleService messageService;

    @Override
    public PageImpl<CompanyResponseDTO> getCompanies(VerificationStatus status, String q, int page, int perPage, AppLanguage language) {
        int resolvedPage = normalizePage(page, language);
        int resolvedPerPage = normalizePerPage(perPage, language);

        Specification<Company> specification = notDeleted();
        if (status != null) {
            specification = specification.and((root, query, cb) -> cb.equal(root.get("verificationStatus"), status));
        }
        if (StringUtils.hasText(q)) {
            String like = "%" + q.trim().toLowerCase() + "%";
            specification = specification.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("name")), like),
                    cb.like(cb.lower(root.get("slug")), like),
                    cb.like(cb.lower(root.get("stir")), like),
                    cb.like(cb.lower(root.get("phonePrimary")), like)
            ));
        }

        PageRequest pageRequest = PageRequest.of(resolvedPage - 1, resolvedPerPage, Sort.by(Sort.Direction.DESC, "createdDate"));
        Page<Company> result = companyRepository.findAll(specification, pageRequest);
        List<CompanyResponseDTO> items = result.getContent().stream()
                .map(this::toResponse)
                .toList();
        return new PageImpl<>(items, pageRequest, result.getTotalElements());
    }

    @Override
    public List<CompanyResponseDTO> getModerationQueue() {
        Specification<Company> specification = notDeleted()
                .and((root, query, cb) -> cb.equal(root.get("verificationStatus"), VerificationStatus.PENDING_VERIFICATION));

        return companyRepository.findAll(specification, Sort.by(Sort.Direction.ASC, "createdDate"))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public void verify(Long id, AppLanguage language) {
        Company company = getCompany(id, language);
        company.setVerificationStatus(VerificationStatus.VERIFIED);
        company.setVerifiedAt(LocalDateTime.now());
        company.setRejectReason(null);
        company.setIsBlocked(Boolean.FALSE);
        companyRepository.save(company);
    }

    @Override
    @Transactional
    public void reject(Long id, ModerationDecisionRequest request, AppLanguage language) {
        Company company = getCompany(id, language);
        company.setVerificationStatus(VerificationStatus.REJECTED);
        company.setVerifiedAt(null);
        company.setRejectReason(resolveReason(request));
        companyRepository.save(company);
    }

    @Override
    public void block(Long id, ReasonRequest request, AppLanguage language) {
        Company company = getCompany(id, language);
        company.setIsBlocked(Boolean.TRUE);
        if (StringUtils.hasText(request == null ? null : request.getReason())) {
            company.setRejectReason(request.getReason().trim());
        }
        companyRepository.save(company);
    }

    private Company getCompany(Long id, AppLanguage language) {
        return companyRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new AppBadException(messageService.getMessage("company.not.found", language)));
    }

    private CompanyResponseDTO toResponse(Company company) {
        CompanyResponseDTO response = new CompanyResponseDTO();
        response.setId(company.getId());
        response.setName(company.getName());
        response.setSlug(company.getSlug());
        response.setShortDescription(company.getShortDescription());
        response.setDescription(company.getDescription());
        response.setLogoUrl(company.getLogoPath());
        response.setStir(company.getStir());
        response.setPhonePrimary(company.getPhonePrimary());
        response.setPhoneSecondary(company.getPhoneSecondary());
        response.setWebsite(company.getWebsite());
        response.setAddress(company.getAddress());
        response.setVerificationStatus(company.getVerificationStatus());
        response.setVerifiedAt(company.getVerifiedAt());
        response.setCreatedAt(company.getCreatedDate());
        return response;
    }

    private Specification<Company> notDeleted() {
        return (root, query, cb) -> cb.isNull(root.get("deletedAt"));
    }

    private String resolveReason(ModerationDecisionRequest request) {
        if (request == null) {
            return null;
        }
        boolean hasReasonCode = StringUtils.hasText(request.getReasonCode());
        boolean hasComment = StringUtils.hasText(request.getComment());
        if (hasReasonCode && hasComment) {
            return request.getReasonCode().trim() + ": " + request.getComment().trim();
        }
        if (hasReasonCode) {
            return request.getReasonCode().trim();
        }
        if (hasComment) {
            return request.getComment().trim();
        }
        return null;
    }

    private int normalizePage(int page, AppLanguage language) {
        if (page < 1) {
            throw new AppBadException(messageService.getMessage("page.invalid", language));
        }
        return page;
    }

    private int normalizePerPage(int perPage, AppLanguage language) {
        if (perPage < 1 || perPage > 100) {
            throw new AppBadException(messageService.getMessage("per.page.invalid", language));
        }
        return perPage;
    }
}
