package org.example.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.entity.ActionDraft;
import org.example.entity.DraftStatus;
import org.example.dto.DraftDetailsResponse;
import org.example.exception.ActionDraftStateException;
import org.example.exception.AiNotFoundException;
import org.example.repository.ActionDraftRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActionDraftServiceTest {

    private static final String USER_A = "sub-user-a";
    private static final String USER_B = "sub-user-b";

    @Mock
    private ActionDraftRepository repository;

    private ActionDraftService service;

    @BeforeEach
    void setUp() {
        service = new ActionDraftService(repository, new ObjectMapper(), 30);
    }

    @Test
    void create_persistsDraftStatusWithGeneratedIdempotencyKeyAndExpiry() {
        when(repository.save(any(ActionDraft.class))).thenAnswer(invocation -> {
            ActionDraft d = invocation.getArgument(0);
            d.setId(UUID.randomUUID());
            return d;
        });
        UUID conversationId = UUID.randomUUID();

        ActionDraft draft = service.create(conversationId, USER_A, "LEAD", Map.of("contactName", "Ali"));

        ArgumentCaptor<ActionDraft> captor = ArgumentCaptor.forClass(ActionDraft.class);
        org.mockito.Mockito.verify(repository).save(captor.capture());
        ActionDraft saved = captor.getValue();
        assertThat(saved.getConversationId()).isEqualTo(conversationId);
        assertThat(saved.getUserSub()).isEqualTo(USER_A);
        assertThat(saved.getType()).isEqualTo("LEAD");
        assertThat(saved.getStatus()).isEqualTo(DraftStatus.DRAFT);
        assertThat(saved.getIdempotencyKey()).isNotBlank();
        assertThat(saved.getPayload()).contains("\"contactName\":\"Ali\"");
        assertThat(saved.getExpiresAt()).isAfter(Instant.now());
        assertThat(draft.getId()).isNotNull();
    }

    @Test
    void loadForTransition_pastTtl_flipsDraftToExpiredAndSaves() {
        UUID draftId = UUID.randomUUID();
        ActionDraft draft = draftOwnedBy(USER_A, draftId, DraftStatus.DRAFT);
        draft.setExpiresAt(Instant.now().minus(1, ChronoUnit.MINUTES));
        when(repository.findLockedByIdAndUserSub(draftId, USER_A)).thenReturn(Optional.of(draft));
        when(repository.save(any(ActionDraft.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ActionDraft result = service.loadForTransition(USER_A, draftId);

        assertThat(result.getStatus()).isEqualTo(DraftStatus.EXPIRED);
        org.mockito.Mockito.verify(repository).save(draft);
    }

    @Test
    void loadForTransition_stillWithinTtl_leavesStatusAsDraft() {
        UUID draftId = UUID.randomUUID();
        ActionDraft draft = draftOwnedBy(USER_A, draftId, DraftStatus.DRAFT);
        draft.setExpiresAt(Instant.now().plus(10, ChronoUnit.MINUTES));
        when(repository.findLockedByIdAndUserSub(draftId, USER_A)).thenReturn(Optional.of(draft));

        ActionDraft result = service.loadForTransition(USER_A, draftId);

        assertThat(result.getStatus()).isEqualTo(DraftStatus.DRAFT);
        org.mockito.Mockito.verify(repository).findLockedByIdAndUserSub(draftId, USER_A);
        org.mockito.Mockito.verify(repository, org.mockito.Mockito.never()).findByIdAndUserSub(draftId, USER_A);
        org.mockito.Mockito.verify(repository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void requireOwned_foreignUser_throwsNotFound() {
        UUID draftId = UUID.randomUUID();
        when(repository.findByIdAndUserSub(draftId, USER_B)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.requireOwned(USER_B, draftId)).isInstanceOf(AiNotFoundException.class);
    }

    @Test
    void getDetails_pendingDraftReturnsOwnerPayloadWhileTerminalDraftDoesNot() {
        UUID pendingId = UUID.randomUUID();
        ActionDraft pending = draftOwnedBy(USER_A, pendingId, DraftStatus.DRAFT);
        pending.setPayload("{\"contactName\":\"Ali\"}");
        pending.setExpiresAt(Instant.now().plus(10, ChronoUnit.MINUTES));
        when(repository.findLockedByIdAndUserSub(pendingId, USER_A)).thenReturn(Optional.of(pending));

        DraftDetailsResponse pendingDetails = service.getDetails(USER_A, pendingId);

        assertThat(pendingDetails.status()).isEqualTo(DraftStatus.DRAFT);
        assertThat(pendingDetails.payload()).containsEntry("contactName", "Ali");

        UUID confirmedId = UUID.randomUUID();
        ActionDraft confirmed = draftOwnedBy(USER_A, confirmedId, DraftStatus.CONFIRMED);
        confirmed.setPayload("{\"contactName\":\"Ali\"}");
        confirmed.setExpiresAt(Instant.now().plus(10, ChronoUnit.MINUTES));
        when(repository.findLockedByIdAndUserSub(confirmedId, USER_A)).thenReturn(Optional.of(confirmed));

        DraftDetailsResponse confirmedDetails = service.getDetails(USER_A, confirmedId);

        assertThat(confirmedDetails.status()).isEqualTo(DraftStatus.CONFIRMED);
        assertThat(confirmedDetails.payload()).isEmpty();
    }

    @Test
    void getDetails_foreignDraftIsNotFoundWithoutLeakingExistence() {
        UUID draftId = UUID.randomUUID();
        when(repository.findLockedByIdAndUserSub(draftId, USER_B)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getDetails(USER_B, draftId))
                .isInstanceOf(AiNotFoundException.class);
    }

    @Test
    void cancel_draftStatus_transitionsToCancelled() {
        UUID draftId = UUID.randomUUID();
        ActionDraft draft = draftOwnedBy(USER_A, draftId, DraftStatus.DRAFT);
        draft.setExpiresAt(Instant.now().plus(10, ChronoUnit.MINUTES));
        when(repository.findLockedByIdAndUserSub(draftId, USER_A)).thenReturn(Optional.of(draft));
        when(repository.save(any(ActionDraft.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ActionDraft result = service.cancel(USER_A, draftId);

        assertThat(result.getStatus()).isEqualTo(DraftStatus.CANCELLED);
    }

    @Test
    void cancel_alreadyCancelled_isIdempotentNoOp() {
        UUID draftId = UUID.randomUUID();
        ActionDraft draft = draftOwnedBy(USER_A, draftId, DraftStatus.CANCELLED);
        draft.setExpiresAt(Instant.now().plus(10, ChronoUnit.MINUTES));
        when(repository.findLockedByIdAndUserSub(draftId, USER_A)).thenReturn(Optional.of(draft));

        ActionDraft result = service.cancel(USER_A, draftId);

        assertThat(result.getStatus()).isEqualTo(DraftStatus.CANCELLED);
        org.mockito.Mockito.verify(repository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void cancel_alreadyConfirmed_throwsStateException() {
        UUID draftId = UUID.randomUUID();
        ActionDraft draft = draftOwnedBy(USER_A, draftId, DraftStatus.CONFIRMED);
        draft.setExpiresAt(Instant.now().plus(10, ChronoUnit.MINUTES));
        when(repository.findLockedByIdAndUserSub(draftId, USER_A)).thenReturn(Optional.of(draft));

        assertThatThrownBy(() -> service.cancel(USER_A, draftId)).isInstanceOf(ActionDraftStateException.class);
    }

    private ActionDraft draftOwnedBy(String userSub, UUID id, String status) {
        ActionDraft draft = new ActionDraft();
        draft.setId(id);
        draft.setUserSub(userSub);
        draft.setConversationId(UUID.randomUUID());
        draft.setType("LEAD");
        draft.setPayload("{}");
        draft.setStatus(status);
        draft.setIdempotencyKey(UUID.randomUUID().toString());
        return draft;
    }
}
