package org.example.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.example.clent.OrderAcceptClient;
import org.example.dto.*;
import org.example.enums.*;
import org.example.exp.LeadAcceptanceException;
import org.example.repository.LeadRepository;
import org.example.service.ResourceBundleService;
import org.example.utils.SpringSecurityUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LeadAcceptanceService {
    private final LeadRepository repository;
    private final OrderAcceptClient orders;
    private final ResourceBundleService messages;

    @Transactional
    public JsonNode accept(Long id, LeadAcceptRequest request, String authorization, AppLanguage language) {
        var lead = repository.lockPresent(id).orElseThrow(() -> error(404, "lead.not.found", language));
        if (!java.util.Objects.equals(lead.getSellerId(), SpringSecurityUtil.getProfileId()))
            throw error(403, "lead.forbidden", language);
        if (!java.util.Set.of(LeadStatus.NEW, LeadStatus.VIEWED, LeadStatus.CONTACTED, LeadStatus.ACCEPTED).contains(lead.getStatus()))
            throw error(409, "lead.accept.invalid", language);
        ApiResponse<JsonNode> result;
        try {
            result = orders.accept(id, request, authorization, language.name());
        } catch (feign.FeignException ex) {
            int status = java.util.Set.of(400, 403, 404, 409).contains(ex.status()) ? ex.status() : 503;
            throw error(status, "lead.accept.failed", language);
        }
        if (result == null || !Boolean.TRUE.equals(result.getSuccess()) || result.getData() == null
                || !result.getData().path("id").canConvertToLong()
                || result.getData().path("companyId").asLong() != lead.getCompanyId()
                || result.getData().path("buyerId").asLong() != lead.getBuyerId()
                || !java.util.Set.of("RESERVED","FINALIZING","SHIPPED").contains(result.getData().path("status").asText()))
            throw error(503, "lead.accept.failed", language);
        lead.setAcceptedOrderId(result.getData().path("id").asLong());
        lead.setStatus(LeadStatus.ACCEPTED);
        repository.save(lead);
        return result.getData();
    }

    private LeadAcceptanceException error(int status, String key, AppLanguage language) {
        return new LeadAcceptanceException(status, messages.getMessage(key, language));
    }
}
