package org.example.service;

import org.example.dto.LeadCreateRequest;
import org.example.dto.LeadResponse;
import org.example.dto.LeadStatusUpdateRequest;
import org.example.dto.PagedResponse;
import org.example.enums.AppLanguage;
import org.example.enums.LeadSource;
import org.example.enums.LeadStatus;

public interface LeadService {
    LeadResponse create(LeadCreateRequest request, AppLanguage language);

    PagedResponse<LeadResponse> getBuyerLeads(LeadStatus status, LeadSource source, int page, int perPage, AppLanguage language);

    PagedResponse<LeadResponse> getSellerLeads(Long companyId, LeadStatus status, LeadSource source, int page, int perPage, AppLanguage language);

    LeadResponse getById(Long id, AppLanguage language);

    Boolean cancel(Long id, AppLanguage language);

    LeadResponse updateStatus(Long id, LeadStatusUpdateRequest request, AppLanguage language);
}
