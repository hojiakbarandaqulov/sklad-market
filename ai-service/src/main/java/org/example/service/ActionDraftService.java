package org.example.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.example.entity.ActionDraft;
import org.example.entity.DraftStatus;
import org.example.dto.DraftDetailsResponse;
import org.example.exception.ActionDraftStateException;
import org.example.exception.AiNotFoundException;
import org.example.repository.ActionDraftRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * CRUD/lifecycle for {@code action_draft} rows (PLAN.md §4.2 item 3: draft -&gt; confirm). This
 * class never calls the platform's write endpoints — only {@link ActionDraftConfirmService} does
 * that, and only after reading a DRAFT-status row back through {@link #loadForTransition}.
 */
@Slf4j
@Service
public class ActionDraftService {

    private final ActionDraftRepository repository;
    private final ObjectMapper objectMapper;
    private final int draftTtlMinutes;

    public ActionDraftService(
            ActionDraftRepository repository,
            ObjectMapper objectMapper,
            @Value("${ai.limits.draft-ttl-minutes:30}") int draftTtlMinutes) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.draftTtlMinutes = draftTtlMinutes;
    }

    @Transactional
    public ActionDraft create(UUID conversationId, String userSub, String type, Map<String, Object> payload) {
        ActionDraft draft = new ActionDraft();
        draft.setConversationId(conversationId);
        draft.setUserSub(userSub);
        draft.setType(type);
        draft.setPayload(writeJson(payload));
        draft.setStatus(DraftStatus.DRAFT);
        draft.setIdempotencyKey(UUID.randomUUID().toString());
        draft.setExpiresAt(Instant.now().plus(Duration.ofMinutes(draftTtlMinutes)));
        return repository.save(draft);
    }

    /**
     * Loads a draft owned by {@code userSub}, lazily flipping it to EXPIRED if its TTL has passed
     * while still DRAFT (PLAN.md Phase 4: "auto-expiry after AI_DRAFT_TTL_MINUTES" — enforced on
     * read rather than via a background sweep, since nothing but confirm/cancel ever needs to know).
     */
    @Transactional
    public ActionDraft loadForTransition(String userSub, UUID draftId) {
        ActionDraft draft = repository.findLockedByIdAndUserSub(draftId, userSub)
                .orElseThrow(() -> new AiNotFoundException("Draft not found"));
        if (DraftStatus.DRAFT.equals(draft.getStatus()) && Instant.now().isAfter(draft.getExpiresAt())) {
            draft.setStatus(DraftStatus.EXPIRED);
            draft = repository.save(draft);
        }
        return draft;
    }

    public ActionDraft requireOwned(String userSub, UUID draftId) {
        return repository.findByIdAndUserSub(draftId, userSub)
                .orElseThrow(() -> new AiNotFoundException("Draft not found"));
    }

    /**
     * Returns current owner-scoped state for reload recovery. Only a still-pending draft includes
     * its private payload; terminal drafts expose status metadata but no stale confirmable data.
     */
    @Transactional
    public DraftDetailsResponse getDetails(String userSub, UUID draftId) {
        ActionDraft draft = loadForTransition(userSub, draftId);
        Map<String, Object> payload = DraftStatus.DRAFT.equals(draft.getStatus())
                ? readPayload(draft)
                : Map.of();
        return new DraftDetailsResponse(
                draft.getId(), draft.getType(), draft.getStatus(), payload,
                draft.getLeadId(), draft.getExpiresAt());
    }

    @Transactional
    public ActionDraft cancel(String userSub, UUID draftId) {
        ActionDraft draft = loadForTransition(userSub, draftId);
        if (DraftStatus.CANCELLED.equals(draft.getStatus())) {
            return draft;
        }
        if (!DraftStatus.DRAFT.equals(draft.getStatus())) {
            throw new ActionDraftStateException(draft.getStatus(), "Cannot cancel a draft with status " + draft.getStatus());
        }
        draft.setStatus(DraftStatus.CANCELLED);
        return repository.save(draft);
    }

    public ActionDraft save(ActionDraft draft) {
        return repository.save(draft);
    }

    public Map<String, Object> readPayload(ActionDraft draft) {
        try {
            return objectMapper.readValue(draft.getPayload(), Map.class);
        } catch (Exception e) {
            log.error("Failed to parse action_draft payload for draft {}", draft.getId(), e);
            throw new IllegalStateException("Corrupt draft payload", e);
        }
    }

    private String writeJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize draft payload", e);
        }
    }
}
