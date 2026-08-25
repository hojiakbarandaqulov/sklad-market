package org.example.ai.intent.service;

import org.example.ai.intent.dto.BuyingIntentMatchResponse;
import org.example.ai.intent.dto.BuyingIntentMatchResult;
import org.example.ai.intent.dto.BuyingIntentRequest;
import org.example.ai.intent.entity.BuyingIntent;
import org.example.ai.intent.entity.BuyingIntentStatus;
import org.example.ai.intent.repository.BuyingIntentRepository;
import org.example.exception.AiNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BuyingIntentServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-11T10:00:00Z");

    @Mock
    private BuyingIntentRepository repository;

    private BuyingIntentService service;

    @BeforeEach
    void setUp() {
        service = new BuyingIntentService(
                repository, new BuyingIntentPrivacyGuard(), Clock.fixed(NOW, ZoneOffset.UTC));
        lenient().when(repository.save(any(BuyingIntent.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void create_isPrivateDraft_andStoresOnlyBusinessNeedFields() {
        BuyingIntentRequest request = request("Cement", "Tashkent", "Need M500 cement for a warehouse");

        var response = service.createDraft("buyer-sub", request);

        ArgumentCaptor<BuyingIntent> captor = ArgumentCaptor.forClass(BuyingIntent.class);
        verify(repository).save(captor.capture());
        BuyingIntent saved = captor.getValue();
        assertThat(saved.getOwnerSub()).isEqualTo("buyer-sub");
        assertThat(saved.getStatus()).isEqualTo(BuyingIntentStatus.DRAFT);
        assertThat(response.status()).isEqualTo("DRAFT");
        assertThat(response.contactAvailable()).isFalse();
        assertThat(response.contactAccess()).isEqualTo("NOT_COLLECTED");
        assertThat(response.automaticOutreachAllowed()).isFalse();
    }

    @Test
    void publish_requiresOwnedDraft_andIsExplicitIdempotentTransition() {
        BuyingIntent intent = intent(BuyingIntentStatus.DRAFT, NOW.plusSeconds(3600));
        when(repository.findOwnedForUpdate(intent.getId(), "buyer-sub")).thenReturn(Optional.of(intent));

        var first = service.publish("buyer-sub", intent.getId(), true);
        var second = service.publish("buyer-sub", intent.getId(), true);

        assertThat(first.status()).isEqualTo("PUBLISHED");
        assertThat(second.status()).isEqualTo("PUBLISHED");
        assertThat(intent.getPublishedAt()).isEqualTo(NOW);
        assertThat(intent.getPublicationConsentAt()).isEqualTo(NOW);
    }

    @Test
    void crossOwnerLookup_returnsGenericNotFound_andNeverFallsBackToUnscopedLookup() {
        UUID id = UUID.randomUUID();
        when(repository.findOwnedForUpdate(id, "different-owner")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.publish("different-owner", id, true))
                .isInstanceOf(AiNotFoundException.class)
                .hasMessage("Buying intent not found");
        verify(repository, never()).findById(id);
    }

    @Test
    void expiredDraft_cannotBePublished() {
        BuyingIntent intent = intent(BuyingIntentStatus.DRAFT, NOW.minusSeconds(1));
        when(repository.findOwnedForUpdate(intent.getId(), "buyer-sub")).thenReturn(Optional.of(intent));

        assertThatThrownBy(() -> service.publish("buyer-sub", intent.getId(), true))
                .isInstanceOf(BuyingIntentStateException.class)
                .hasMessageContaining("Only a draft");
        assertThat(intent.getStatus()).isEqualTo(BuyingIntentStatus.EXPIRED);
    }

    @Test
    void publish_withoutExplicitConsent_isRejectedBeforeOwnerLookup() {
        UUID id = UUID.randomUUID();

        assertThatThrownBy(() -> service.publish("buyer-sub", id, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("seller-visible");
        verify(repository, never()).findOwnedForUpdate(any(), anyString());
    }

    @Test
    void lifecycleStateFailures_commitExpiryInsteadOfRollingItBack() throws Exception {
        Transactional publish = BuyingIntentService.class
                .getMethod("publish", String.class, UUID.class, boolean.class)
                .getAnnotation(Transactional.class);
        Transactional update = BuyingIntentService.class
                .getMethod("updateDraft", String.class, UUID.class, BuyingIntentRequest.class)
                .getAnnotation(Transactional.class);
        Transactional close = BuyingIntentService.class
                .getMethod("close", String.class, UUID.class)
                .getAnnotation(Transactional.class);

        assertThat(publish.noRollbackFor()).contains(BuyingIntentStateException.class);
        assertThat(update.noRollbackFor()).contains(BuyingIntentStateException.class);
        assertThat(close.noRollbackFor()).contains(BuyingIntentStateException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Call me at +998 90 123 45 67",
            "Email buyer@example.test",
            "Details at https://example.test/request",
            "See catalog.example.uz/offers",
            "Telegram @private_buyer",
            "Delivery address: Amir Temur 15",
            "Meet at Ko'cha Navoi 42"
    })
    void contactDetailsInNeedText_areRejectedBeforePersistence(String needText) {
        BuyingIntentRequest request = request("Cement", "Tashkent", needText);

        assertThatThrownBy(() -> service.createDraft("buyer-sub", request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("privacy-restricted");
        verify(repository, never()).save(any());
    }

    @Test
    void contactDetailsInQuantityUnit_areRejectedBeforePersistence() {
        BuyingIntentRequest request = new BuyingIntentRequest(
                "Cement", "Tashkent", "Need cement", new BigDecimal("500"), "@private_buyer",
                new BigDecimal("1000000"), new BigDecimal("50000000"), "UZS", NOW.plusSeconds(3600));

        assertThatThrownBy(() -> service.createDraft("buyer-sub", request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("quantityUnit")
                .hasMessageContaining("privacy-restricted");
        verify(repository, never()).save(any());
    }

    @Test
    void create_enforcesPerOwnerActiveIntentCapAfterExpiringDueRows() {
        BuyingIntentService capped = new BuyingIntentService(
                repository, new BuyingIntentPrivacyGuard(), Clock.fixed(NOW, ZoneOffset.UTC), 2);
        when(repository.countByOwnerSubAndStatusInAndExpiresAtAfter(eq("buyer-sub"), any(), eq(NOW)))
                .thenReturn(2L);

        assertThatThrownBy(() -> capped.createDraft("buyer-sub", request("Cement", "Tashkent", "Need cement")))
                .isInstanceOf(BuyingIntentStateException.class)
                .hasMessageContaining("limit 2");
        verify(repository).acquireOwnerQuotaLock("buyer-sub");
        verify(repository).expireDueForOwner(eq("buyer-sub"), any(), eq(NOW));
        verify(repository, never()).save(any());
    }

    @Test
    void toolBypassCannotPersistInvalidNumericOrCurrencyValues() {
        BuyingIntentRequest invalid = new BuyingIntentRequest(
                "Cement", "Tashkent", "Need cement", new BigDecimal("-1"), "kg",
                BigDecimal.ZERO, BigDecimal.ONE, "SOMONI", NOW.plusSeconds(3600));

        assertThatThrownBy(() -> service.createDraft("buyer-sub", invalid))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("quantity");
        verify(repository, never()).save(any());
    }

    @Test
    void toolBypassCannotPersistValuesOutsideDatabasePrecisionOrScale() {
        BuyingIntentRequest excessiveScale = new BuyingIntentRequest(
                "Cement", "Tashkent", "Need cement", new BigDecimal("0.0001"), "kg",
                BigDecimal.ZERO, BigDecimal.ONE, "UZS", NOW.plusSeconds(3600));
        BuyingIntentRequest excessiveBudget = new BuyingIntentRequest(
                "Cement", "Tashkent", "Need cement", BigDecimal.ONE, "kg",
                BigDecimal.ZERO, new BigDecimal("100000000000000000.00"), "UZS", NOW.plusSeconds(3600));

        assertThatThrownBy(() -> service.createDraft("buyer-sub", excessiveScale))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("quantity")
                .hasMessageContaining("precision");
        assertThatThrownBy(() -> service.createDraft("buyer-sub", excessiveBudget))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("budgetMax")
                .hasMessageContaining("precision");
        verify(repository, never()).save(any());
    }

    @Test
    void listOwn_isOwnerScopedStatusFilteredAndBoundedWithPageMetadata() {
        BuyingIntent published = intent(BuyingIntentStatus.PUBLISHED, NOW.plusSeconds(3600));
        when(repository.findAllByOwnerSubAndStatusOrderByCreatedAtDesc(
                eq("buyer-sub"), eq(BuyingIntentStatus.PUBLISHED), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(published), PageRequest.of(1, 25), 75));

        var result = service.listOwn("buyer-sub", 2, 25, "published");

        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getMeta().getPage()).isEqualTo(2);
        assertThat(result.getMeta().getPerPage()).isEqualTo(25);
        assertThat(result.getMeta().getTotal()).isEqualTo(75);
        assertThat(result.getMeta().getTotalPages()).isEqualTo(3);
        verify(repository).expireDueForOwner(eq("buyer-sub"), any(), eq(NOW));
    }

    @Test
    void listOwn_rejectsUnboundedPageSize() {
        assertThatThrownBy(() -> service.listOwn("buyer-sub", 1, 51, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("between 1 and 50");
    }

    @Test
    void sellerSearch_usesPublishedAndFutureBoundary_andReturnsPrivacyMinimizedRankedProjection() {
        BuyingIntent complete = intent(BuyingIntentStatus.PUBLISHED, NOW.plusSeconds(3600));
        complete.setCategory("Cement");
        complete.setRegion("Tashkent");
        complete.setNeedText("Need white cement for facade work");
        complete.setQuantity(new BigDecimal("500"));
        complete.setBudgetMax(new BigDecimal("50000000"));
        complete.setPublishedAt(NOW.minusSeconds(60));
        when(repository.searchPublished(
                eq(BuyingIntentStatus.PUBLISHED), eq(NOW), eq("Cement"), eq("Tashkent"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(complete), PageRequest.of(0, 100), 135));

        BuyingIntentMatchResult matchResult =
                service.searchPublished("Cement", "Tashkent", "white cement", 10);
        List<BuyingIntentMatchResponse> matches = matchResult.items();

        assertThat(matches).hasSize(1);
        BuyingIntentMatchResponse match = matches.get(0);
        assertThat(match.matchScore()).isEqualTo(100);
        assertThat(match.reasons()).contains("CATEGORY_MATCH", "REGION_MATCH", "NEED_TEXT_MATCH");
        assertThat(match.contactAvailable()).isFalse();
        assertThat(match.contactAccess()).isEqualTo("NOT_COLLECTED");
        assertThat(match.automaticOutreachAllowed()).isFalse();
        assertThat(matchResult.evaluatedIntentCount()).isEqualTo(1);
        assertThat(matchResult.totalIntentCount()).isEqualTo(135);
        assertThat(matchResult.candidatesTruncated()).isTrue();
        assertThat(matchResult.asOf()).isEqualTo(NOW);
        assertThat(matchResult.privacy()).contains("cannot guarantee anonymity");
    }

    private BuyingIntentRequest request(String category, String region, String need) {
        return new BuyingIntentRequest(
                category, region, need, new BigDecimal("500"), "kg",
                new BigDecimal("1000000"), new BigDecimal("50000000"), "UZS", NOW.plusSeconds(3600));
    }

    private BuyingIntent intent(BuyingIntentStatus status, Instant expiresAt) {
        BuyingIntent intent = new BuyingIntent();
        intent.setId(UUID.randomUUID());
        intent.setOwnerSub("buyer-sub");
        intent.setStatus(status);
        intent.setCategory("Cement");
        intent.setNeedText("Need cement");
        intent.setCurrency("UZS");
        intent.setExpiresAt(expiresAt);
        intent.setCreatedAt(NOW.minusSeconds(120));
        intent.setUpdatedAt(NOW.minusSeconds(120));
        return intent;
    }
}
