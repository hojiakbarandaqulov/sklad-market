package org.example.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.example.clent.OrderAcceptClient;
import org.example.dto.*;
import org.example.entity.Lead;
import org.example.enums.*;
import org.example.exp.AppConflictException;
import org.example.exp.AppNotFoundException;
import org.example.exp.AppServiceUnavailableException;
import org.example.exp.LeadAcceptanceException;
import org.springframework.security.access.AccessDeniedException;
import feign.FeignException;
import java.util.Objects;
import java.util.Set;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
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
    private final ObjectMapper json;

    @Transactional
    public JsonNode accept(Long id, LeadAcceptRequest request, String authorization, AppLanguage language) {
        Lead lead = repository.lockPresent(id).orElseThrow(() -> new AppNotFoundException(messages.getMessage("lead.not.found", language)));
        if (!Objects.equals(lead.getSellerId(), SpringSecurityUtil.getProfileId()))
            throw new AccessDeniedException(messages.getMessage("lead.forbidden", language));
        if (!Set.of(LeadStatus.NEW, LeadStatus.VIEWED, LeadStatus.CONTACTED, LeadStatus.ACCEPTED).contains(lead.getStatus()))
            throw new AppConflictException(messages.getMessage("lead.accept.invalid", language));
        ApiResponse<JsonNode> result;
        try {
            result = orders.accept(id, request, authorization, language.name());
        } catch (FeignException ex) {
            throw remoteFailure(ex, language);
        }
        if (result == null || !Boolean.TRUE.equals(result.getSuccess()) || result.getData() == null
                || !result.getData().path("id").canConvertToLong()
                || result.getData().path("companyId").asLong() != lead.getCompanyId()
                || result.getData().path("buyerId").asLong() != lead.getBuyerId()
                || !Set.of("RESERVED","FINALIZING","SHIPPED").contains(result.getData().path("status").asText()))
            throw new AppServiceUnavailableException(messages.getMessage("lead.accept.failed", language));
        lead.setAcceptedOrderId(result.getData().path("id").asLong());
        lead.setStatus(LeadStatus.ACCEPTED);
        repository.save(lead);
        return result.getData();
    }

    private LeadAcceptanceException remoteFailure(FeignException ex, AppLanguage language) {
        int status = switch (ex.status()) {
            case 400, 403, 404, 409, 422 -> ex.status();
            default -> 503;
        };
        String message = messages.getMessage("lead.accept.failed", language);
        String upstreamTraceId = null;
        Map<String, String> errors = new LinkedHashMap<>();
        // Only the Order API error envelope is public. Never expose HTML, raw bodies or 5xx details.
        try {
            String body = ex.contentUTF8();
            if (body.length() <= 16384) {
                JsonNode error = json.readTree(body);
                if (error != null && error.path("success").isBoolean() && !error.path("success").asBoolean()) {
                    String trace = error.path("trace_id").asText();
                    if (trace.matches("[0-9a-fA-F-]{36}")) upstreamTraceId = UUID.fromString(trace).toString();
                    if (status < 500 && publicText(error.path("message"))) {
                        message = error.path("message").asText();
                        error.path("errors").fields().forEachRemaining(field -> {
                            if (field.getKey().matches("[a-zA-Z][a-zA-Z0-9.\\[\\]]{0,99}") && publicText(field.getValue()))
                                errors.put(field.getKey(), field.getValue().asText());
                        });
                    }
                }
            }
        } catch (java.io.IOException | IllegalArgumentException ignored) {
            // Non-JSON responses use the localized fallback.
        }
        return new LeadAcceptanceException(status, message, errors, upstreamTraceId);
    }

    private boolean publicText(JsonNode value) {
        return value.isTextual() && !value.asText().isBlank() && value.asText().length() <= 1000
                && value.asText().chars().noneMatch(Character::isISOControl);
    }
}
