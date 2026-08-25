package org.example.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.example.ai.gateway.GatewayUnavailableException;
import org.example.dto.DraftActionResponse;
import org.example.dto.DraftConfirmRequest;
import org.example.entity.ActionDraft;
import org.example.entity.DraftStatus;
import org.example.exception.ActionDraftStateException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The only test class exercising {@link ActionDraftConfirmService}'s real HTTP call — verifies the
 * outgoing body matches lead-service's real {@code LeadCreateRequest} field-for-field (PLAN.md §7
 * item 14), plus the draft lifecycle invariants (idempotent double-confirm, expiry, state errors).
 */
@ExtendWith(MockitoExtension.class)
class ActionDraftConfirmServiceTest {

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance().build();

    private static final String USER_SUB = "sub-buyer-1";
    private static final String BEARER_TOKEN = "fresh-confirm-jwt";

    @Mock
    private ActionDraftService actionDraftService;

    private ActionDraftConfirmService confirmService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        confirmService = new ActionDraftConfirmService(actionDraftService, wireMock.baseUrl(), 5);
    }

    @Test
    void confirm_draftStatus_postsExactLeadCreateRequestShapeAndMarksConfirmed() throws Exception {
        ActionDraft draft = draft(DraftStatus.DRAFT);
        Map<String, Object> payload = payloadMap();
        when(actionDraftService.loadForTransition(USER_SUB, draft.getId())).thenReturn(draft);
        when(actionDraftService.readPayload(draft)).thenReturn(payload);
        when(actionDraftService.save(any(ActionDraft.class))).thenAnswer(inv -> inv.getArgument(0));

        wireMock.stubFor(post(urlPathEqualTo("/api/v1/leads"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{\"success\":true,\"data\":{\"id\":555,\"status\":\"NEW\"}}")));

        DraftActionResponse response = confirmService.confirm(USER_SUB, draft.getId(), null, BEARER_TOKEN, "RU");

        assertThat(response.getStatus()).isEqualTo(DraftStatus.CONFIRMED);
        assertThat(response.getLeadId()).isEqualTo(555L);

        String expectedBody = objectMapper.writeValueAsString(Map.of(
                "source", "PRODUCT",
                "productId", 42,
                "quantity", 3,
                "contactName", "Ali Valiyev",
                "contactPhone", "+998901234567",
                "contactEmail", "ali@example.com",
                "deliveryAddress", "Tashkent",
                "neededDate", "2026-08-01",
                "comment", "Need it fast"));
        wireMock.verify(postRequestedFor(urlPathEqualTo("/api/v1/leads"))
                .withHeader("Authorization", equalTo("Bearer " + BEARER_TOKEN))
                .withRequestBody(equalToJson(expectedBody, true, false)));

        ArgumentCaptor<ActionDraft> captor = ArgumentCaptor.forClass(ActionDraft.class);
        verify(actionDraftService).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(DraftStatus.CONFIRMED);
        assertThat(captor.getValue().getLeadId()).isEqualTo(555L);
        assertThat(captor.getValue().getConfirmedAt()).isNotNull();
    }

    @Test
    void confirm_appliesContactOverridesButNeverProductFields() throws Exception {
        ActionDraft draft = draft(DraftStatus.DRAFT);
        when(actionDraftService.loadForTransition(USER_SUB, draft.getId())).thenReturn(draft);
        when(actionDraftService.readPayload(draft)).thenReturn(payloadMap());
        when(actionDraftService.save(any(ActionDraft.class))).thenAnswer(inv -> inv.getArgument(0));
        wireMock.stubFor(post(urlPathEqualTo("/api/v1/leads"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{\"success\":true,\"data\":{\"id\":9}}")));

        DraftConfirmRequest overrides = new DraftConfirmRequest();
        overrides.setContactPhone("+998900000000");

        confirmService.confirm(USER_SUB, draft.getId(), overrides, BEARER_TOKEN, "RU");

        wireMock.verify(postRequestedFor(urlPathEqualTo("/api/v1/leads"))
                .withRequestBody(com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath("$.contactPhone", equalTo("+998900000000")))
                .withRequestBody(com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath("$.contactName", equalTo("Ali Valiyev")))
                .withRequestBody(com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath("$.productId", equalTo("42"))));
    }

    @Test
    void confirm_alreadyConfirmed_isIdempotent_neverCallsLeadServiceAgain() {
        ActionDraft draft = draft(DraftStatus.CONFIRMED);
        draft.setLeadId(777L);
        when(actionDraftService.loadForTransition(USER_SUB, draft.getId())).thenReturn(draft);

        DraftActionResponse first = confirmService.confirm(USER_SUB, draft.getId(), null, BEARER_TOKEN, "RU");
        DraftActionResponse second = confirmService.confirm(USER_SUB, draft.getId(), null, BEARER_TOKEN, "RU");

        assertThat(first.getLeadId()).isEqualTo(777L);
        assertThat(second.getLeadId()).isEqualTo(777L);
        wireMock.verify(0, postRequestedFor(urlPathEqualTo("/api/v1/leads")));
        verify(actionDraftService, never()).save(any());
    }

    @Test
    void confirm_expiredDraft_throwsWithoutCallingLeadService() {
        ActionDraft draft = draft(DraftStatus.EXPIRED);
        when(actionDraftService.loadForTransition(USER_SUB, draft.getId())).thenReturn(draft);

        assertThatThrownBy(() -> confirmService.confirm(USER_SUB, draft.getId(), null, BEARER_TOKEN, "RU"))
                .isInstanceOf(ActionDraftStateException.class);
        wireMock.verify(0, postRequestedFor(urlPathEqualTo("/api/v1/leads")));
    }

    @Test
    void confirm_cancelledDraft_throwsWithoutCallingLeadService() {
        ActionDraft draft = draft(DraftStatus.CANCELLED);
        when(actionDraftService.loadForTransition(USER_SUB, draft.getId())).thenReturn(draft);

        assertThatThrownBy(() -> confirmService.confirm(USER_SUB, draft.getId(), null, BEARER_TOKEN, "RU"))
                .isInstanceOf(ActionDraftStateException.class);
        wireMock.verify(0, postRequestedFor(urlPathEqualTo("/api/v1/leads")));
    }

    @Test
    void confirm_leadServiceUnavailable_throwsGatewayUnavailable() {
        ActionDraft draft = draft(DraftStatus.DRAFT);
        when(actionDraftService.loadForTransition(USER_SUB, draft.getId())).thenReturn(draft);
        when(actionDraftService.readPayload(draft)).thenReturn(payloadMap());
        wireMock.stubFor(post(urlPathEqualTo("/api/v1/leads")).willReturn(aResponse().withStatus(500)));

        assertThatThrownBy(() -> confirmService.confirm(USER_SUB, draft.getId(), null, BEARER_TOKEN, "RU"))
                .isInstanceOf(GatewayUnavailableException.class);
        verify(actionDraftService, times(0)).save(any());
    }

    @Test
    void cancel_delegatesToActionDraftService() {
        ActionDraft draft = draft(DraftStatus.CANCELLED);
        when(actionDraftService.cancel(USER_SUB, draft.getId())).thenReturn(draft);

        DraftActionResponse response = confirmService.cancel(USER_SUB, draft.getId());

        assertThat(response.getStatus()).isEqualTo(DraftStatus.CANCELLED);
        assertThat(response.getDraftId()).isEqualTo(draft.getId());
    }

    private ActionDraft draft(String status) {
        ActionDraft draft = new ActionDraft();
        draft.setId(UUID.randomUUID());
        draft.setUserSub(USER_SUB);
        draft.setConversationId(UUID.randomUUID());
        draft.setType("LEAD");
        draft.setStatus(status);
        draft.setIdempotencyKey(UUID.randomUUID().toString());
        draft.setExpiresAt(Instant.now().plus(10, ChronoUnit.MINUTES));
        return draft;
    }

    private Map<String, Object> payloadMap() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("source", "PRODUCT");
        payload.put("productId", 42);
        payload.put("quantity", 3);
        payload.put("contactName", "Ali Valiyev");
        payload.put("contactPhone", "+998901234567");
        payload.put("contactEmail", "ali@example.com");
        payload.put("deliveryAddress", "Tashkent");
        payload.put("neededDate", "2026-08-01");
        payload.put("comment", "Need it fast");
        payload.put("items", java.util.List.of(Map.of("name", "Cement M500", "slug", "cement-m500")));
        payload.put("companyName", "Acme");
        payload.put("companySlug", "acme");
        return payload;
    }
}
