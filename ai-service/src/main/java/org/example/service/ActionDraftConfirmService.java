package org.example.service;

import lombok.extern.slf4j.Slf4j;
import org.example.ai.gateway.GatewayUnavailableException;
import org.example.ai.gateway.dto.GatewayEnvelope;
import org.example.ai.gateway.dto.RemoteLeadDto;
import org.example.ai.tool.PlatformLanguage;
import org.example.dto.DraftActionResponse;
import org.example.dto.DraftConfirmRequest;
import org.example.entity.ActionDraft;
import org.example.entity.DraftStatus;
import org.example.exception.ActionDraftStateException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * <b>The only class in ai-service that calls {@code POST /api/v1/leads}</b> (PLAN.md Phase 4
 * invariant, grep-verified in tests): every confirm executes a fresh authenticated request with
 * the confirming request's own Bearer token, after re-checking ownership, {@code DRAFT} status,
 * and TTL. Deliberately owns a private {@link RestClient} instead of reusing the tool-facing
 * {@link org.example.ai.gateway.GatewayClient} (GET-only by construction, PLAN.md §4.2 item 3:
 * "tools NEVER write to the platform") — that separation is what makes the single-call-site
 * invariant structurally true rather than just a convention.
 *
 * <p>The owned draft row is loaded with a database pessimistic-write lock and that lock is held
 * across the downstream POST, so simultaneous confirms in this AI database serialize and only one
 * can create a lead. The downstream lead API has no idempotency-key contract; a process/database
 * crash after the POST succeeds but before this transaction commits can therefore still require
 * manual reconciliation and cannot be made exactly-once from ai-service alone.
 */
@Slf4j
@Service
public class ActionDraftConfirmService {

    private final ActionDraftService actionDraftService;
    private final RestClient restClient;

    public ActionDraftConfirmService(
            ActionDraftService actionDraftService,
            @Value("${ai.gateway.base-url}") String baseUrl,
            @Value("${ai.limits.request-timeout-seconds:60}") int timeoutSeconds) {
        this.actionDraftService = actionDraftService;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        int timeoutMillis = (int) Duration.ofSeconds(timeoutSeconds).toMillis();
        requestFactory.setConnectTimeout(timeoutMillis);
        requestFactory.setReadTimeout(timeoutMillis);
        this.restClient = RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
    }

    @Transactional
    public DraftActionResponse confirm(String userSub, UUID draftId, DraftConfirmRequest overrides, String bearerToken, String acceptLanguage) {
        ActionDraft draft = actionDraftService.loadForTransition(userSub, draftId);
        return switch (draft.getStatus()) {
            case DraftStatus.CONFIRMED -> DraftActionResponse.builder()
                    .draftId(draft.getId()).status(draft.getStatus()).leadId(draft.getLeadId()).build();
            case DraftStatus.CANCELLED -> throw new ActionDraftStateException(draft.getStatus(), "This request was already cancelled.");
            case DraftStatus.EXPIRED -> throw new ActionDraftStateException(draft.getStatus(), "This draft has expired. Please ask the assistant to prepare a new request.");
            case DraftStatus.DRAFT -> executeConfirm(draft, overrides, bearerToken, acceptLanguage);
            default -> throw new IllegalStateException("Unknown draft status: " + draft.getStatus());
        };
    }

    @Transactional
    public DraftActionResponse cancel(String userSub, UUID draftId) {
        ActionDraft draft = actionDraftService.cancel(userSub, draftId);
        return DraftActionResponse.builder().draftId(draft.getId()).status(draft.getStatus()).build();
    }

    private DraftActionResponse executeConfirm(ActionDraft draft, DraftConfirmRequest overrides, String bearerToken, String acceptLanguage) {
        Map<String, Object> payload = actionDraftService.readPayload(draft);
        Map<String, Object> body = buildLeadCreateRequestBody(payload, overrides);

        RemoteLeadDto lead;
        try {
            GatewayEnvelope<RemoteLeadDto> envelope = restClient.post()
                    .uri("/api/v1/leads")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken)
                    .header(HttpHeaders.ACCEPT_LANGUAGE, PlatformLanguage.header(acceptLanguage))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(new ParameterizedTypeReference<GatewayEnvelope<RemoteLeadDto>>() {
                    });
            lead = envelope == null ? null : envelope.data();
        } catch (HttpStatusCodeException e) {
            if (e.getStatusCode().is4xxClientError()) {
                log.warn("lead-service rejected draft confirm (draft={}, status={})", draft.getId(), e.getStatusCode());
                throw new ActionDraftStateException("rejected", "The request could not be submitted: " + e.getStatusCode());
            }
            throw new GatewayUnavailableException("lead-service error on draft confirm " + draft.getId() + ": " + e.getStatusCode(), e);
        } catch (RestClientException e) {
            throw new GatewayUnavailableException("lead-service unavailable for draft confirm " + draft.getId(), e);
        }
        if (lead == null || lead.id() == null) {
            throw new GatewayUnavailableException("lead-service returned no lead id for draft confirm " + draft.getId(), null);
        }

        draft.setStatus(DraftStatus.CONFIRMED);
        draft.setLeadId(lead.id());
        draft.setConfirmedAt(Instant.now());
        actionDraftService.save(draft);
        return DraftActionResponse.builder().draftId(draft.getId()).status(draft.getStatus()).leadId(lead.id()).build();
    }

    /** Extracts exactly the {@code LeadCreateRequest} fields (PLAN.md §7 item 14) from the richer stored draft payload, applying any non-blank buyer overrides. */
    private Map<String, Object> buildLeadCreateRequestBody(Map<String, Object> payload, DraftConfirmRequest overrides) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("source", payload.get("source"));
        if (payload.get("productId") != null) {
            body.put("productId", payload.get("productId"));
        }
        if (payload.get("productIds") != null) {
            body.put("productIds", payload.get("productIds"));
        }
        body.put("quantity", payload.get("quantity"));
        body.put("contactName", override(overrides == null ? null : overrides.getContactName(), payload.get("contactName")));
        body.put("contactPhone", override(overrides == null ? null : overrides.getContactPhone(), payload.get("contactPhone")));
        body.put("contactEmail", override(overrides == null ? null : overrides.getContactEmail(), payload.get("contactEmail")));
        body.put("deliveryAddress", override(overrides == null ? null : overrides.getDeliveryAddress(), payload.get("deliveryAddress")));
        body.put("neededDate", override(overrides == null ? null : overrides.getNeededDate(), payload.get("neededDate")));
        body.put("comment", override(overrides == null ? null : overrides.getComment(), payload.get("comment")));

        Object contactName = body.get("contactName");
        Object contactPhone = body.get("contactPhone");
        if (!(contactName instanceof String s1 && !s1.isBlank()) || !(contactPhone instanceof String s2 && !s2.isBlank())) {
            throw new ActionDraftStateException("invalid", "contactName and contactPhone are required to confirm.");
        }
        return body;
    }

    private Object override(String overrideValue, Object storedValue) {
        return overrideValue != null && !overrideValue.isBlank() ? overrideValue : storedValue;
    }
}
