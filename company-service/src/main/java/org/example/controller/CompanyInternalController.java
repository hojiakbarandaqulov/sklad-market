package org.example.controller;

import lombok.RequiredArgsConstructor;
import org.example.dto.admin.ReasonRequest;
import org.example.dto.internal.CompanyIds;
import org.example.dto.internal.CompanyInternalOwnershipResponse;
import org.example.dto.internal.CompanyInternalSummaryResponse;
import org.example.dto.map.CompanyMapResponse;
import org.example.entity.Company;
import org.example.enums.AppLanguage;
import org.example.enums.VerificationStatus;
import org.example.exp.AppBadException;
import org.example.repository.CompanyRepository;
import org.example.service.AdminCompanyService;
import org.example.service.CompanyService;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/companies")
public class CompanyInternalController {

    private final CompanyService companyService;
    private final AdminCompanyService adminCompanyService;


    @GetMapping("/{companyId}/ownership-check")
    public CompanyInternalOwnershipResponse ownershipCheck(@PathVariable Long companyId, @RequestParam Long buyerId) {
        Company company = companyService.findByIdAndDeletedAtIsNull(companyId).orElse(null);
        if (company == null) {
            return CompanyInternalOwnershipResponse.builder()
                    .companyId(companyId)
                    .exists(false)
                    .owner(false)
                    .active(false)
                    .build();
        }

        boolean active = !Boolean.TRUE.equals(company.getIsBlocked())
                && company.getDeletedAt() == null
                && (company.getVerificationStatus() == VerificationStatus.VERIFIED || company.getVerificationStatus() == VerificationStatus.PENDING_VERIFICATION);

        return CompanyInternalOwnershipResponse.builder()
                .companyId(companyId)
                .exists(true)
                .owner(company.getOwnerUserId().equals(buyerId))
                .active(active)
                .build();
    }

    @GetMapping("/owned")
    public List<Long> ownedCompanies(@RequestParam Long sellerId) {
       return companyService.findAllByOwnerUserIdAndDeletedAtIsNull(sellerId).stream()
                .map(Company::getId)
                .toList();
    }

    @GetMapping("/{companyId}/summary")
    public CompanyInternalSummaryResponse summary(@PathVariable Long companyId) {
        Company company = companyService.findByIdAndDeletedAtIsNull(companyId)
                .orElseThrow(() -> new AppBadException("company.not.found"));

        return CompanyInternalSummaryResponse.builder()
                .id(company.getId())
                .ownerUserId(company.getOwnerUserId())
                .name(company.getName())
                .slug(company.getSlug())
                .logoPath(company.getLogoPath())
                .build();
    }

    @GetMapping("/stats/pending-count")
    public Map<String, Long> pendingCount() {
        return Map.of("count", companyService.countByVerificationStatusAndDeletedAtIsNull(VerificationStatus.PENDING_VERIFICATION));
    }

    @PutMapping("/{companyId}/block")
    public void block(@PathVariable Long companyId, @RequestBody(required = false) ReasonRequest request) {
        adminCompanyService.block(companyId, request, AppLanguage.UZ);
    }

    @PostMapping("/map-summary")
    public List<CompanyMapResponse> mapSummary(@RequestBody CompanyIds request) {
        return companyService.findAllByIdInAndDeletedAtIsNullAndIsBlockedFalseAndLatNotNullAndLngNotNull(request.getCompanyIds());
    }
}
