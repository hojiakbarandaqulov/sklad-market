package org.example.service.impl;

import lombok.RequiredArgsConstructor;
import org.example.dto.PageMeta;
import org.example.dto.PagedResponse;
import org.example.dto.chat.WsTokenResponse;
import org.example.dto.context.AdminContext;
import org.example.dto.context.ParticipantContext;
import org.example.dto.support.SupportCreateRequest;
import org.example.dto.support.SupportMessageResponse;
import org.example.dto.support.SupportOpenResponse;
import org.example.dto.support.SupportReadReceiptResponse;
import org.example.dto.support.SupportThreadResponse;
import org.example.entity.SupportMessage;
import org.example.entity.SupportThread;
import org.example.enums.*;
import org.example.exp.AppBadException;
import org.example.repository.SupportMessageRepository;
import org.example.repository.SupportThreadRepository;
import org.example.service.ChatWebSocketTokenService;
import org.example.service.ResourceBundleService;
import org.example.service.SupportService;
import org.example.utils.SpringSecurityUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class SupportServiceImpl implements SupportService {
    private static final Set<SupportThreadStatus> ACTIVE_STATUSES =
            Set.of(SupportThreadStatus.OPEN, SupportThreadStatus.ASSIGNED);

    private final SupportMessageRepository supportMessageRepository;
    private final SupportThreadRepository supportThreadRepository;
    private final ChatWebSocketTokenService chatWebSocketTokenService;
    private final ResourceBundleService messageService;

    @Value("${media.base-url}")
    private String mediaBaseUrl;

    @Override
    @Transactional
    public SupportOpenResponse openThread(SupportCreateRequest request) {
        return createThread(request, SupportThreadType.GENERAL_SUPPORT);
    }

    @Override
    @Transactional
    public SupportOpenResponse createThread(SupportCreateRequest request, SupportThreadType threadType) {
        Long requesterId = requireCurrentUserId();
        RequesterRole requesterRole = resolveRequesterRole();

        SupportThreadType resolvedType = threadType == null
                ? SupportThreadType.GENERAL_SUPPORT
                : threadType;

        if (resolvedType == SupportThreadType.BANNER_REQUEST && requesterRole != RequesterRole.SELLER) {
            throw new AppBadException(messageService.getMessage("support.banner.seller.required"));
        }

        SupportThread existing = supportThreadRepository
                .findFirstByRequesterIdAndRequesterRoleAndTypeAndStatusInAndDeletedFalseOrderByIdDesc(
                        requesterId, requesterRole, resolvedType, ACTIVE_STATUSES)
                .orElse(null);

        if (existing != null) {
            return new SupportOpenResponse(
                    existing.getId(), requesterRole, resolvedType, existing.getStatus(), false);
        }

        SupportThread thread = new SupportThread();
        thread.setRequesterId(requesterId);
        thread.setRequesterRole(requesterRole);
        thread.setType(resolvedType);
        thread.setStatus(SupportThreadStatus.OPEN);
        thread.setSubject(normalizeSubject(
                request == null ? null : request.getSubject(),
                resolvedType
        ));

        SupportThread saved = supportThreadRepository.save(thread);
        return new SupportOpenResponse(
                saved.getId(), requesterRole, resolvedType, saved.getStatus(), true);
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<SupportMessageResponse> getMessages(Long threadId, int page, int perPage, Long beforeId) {
        validatePage(page, perPage);
        resolveContext(requireCurrentUserId(), threadId);

        PageRequest pageable = PageRequest.of(
                beforeId == null ? page - 1 : 0,
                perPage,
                Sort.by(Sort.Direction.DESC, "id")
        );
        Page<SupportMessage> result = beforeId == null
                ? supportMessageRepository.findByThreadIdAndDeletedFalse(threadId, pageable)
                : supportMessageRepository.findByThreadIdAndIdLessThanAndDeletedFalse(threadId, beforeId, pageable);

        List<SupportMessageResponse> items = result.getContent().stream()
                .sorted(Comparator.comparing(SupportMessage::getId))
                .map(this::toMessageResponse)
                .toList();

        return new PagedResponse<>(items,
                new PageMeta(result.getTotalElements(), page, perPage, result.getTotalPages()));
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<SupportThreadResponse> getAdminQueue(
            SupportThreadStatus status, int page, int perPage) {
        requireCurrentAdmin();
        validatePage(page, perPage);

        Sort sort = Sort.by(
                Sort.Order.desc("lastMessageAt"),
                Sort.Order.desc("id")
        );
        PageRequest pageable = PageRequest.of(page - 1, perPage, sort);
        Page<SupportThread> result = status == null
                ? supportThreadRepository.findByDeletedFalse(pageable)
                : supportThreadRepository.findByStatusAndDeletedFalse(status, pageable);

        return new PagedResponse<>(
                result.getContent().stream().map(this::toThreadResponse).toList(),
                new PageMeta(result.getTotalElements(), page, perPage, result.getTotalPages())
        );
    }

    @Override
    @Transactional
    public SupportThreadResponse assignToCurrentAdmin(Long threadId) {
        AdminContext admin = requireCurrentAdmin();
        SupportThread thread = supportThreadRepository.findByIdForUpdate(threadId)
                .orElseThrow(() -> new AppBadException(messageService.getMessage("support.thread.not.found")));

        if (thread.getStatus() == SupportThreadStatus.CLOSED) {
            throw new AppBadException(messageService.getMessage("support.thread.closed"));
        }
        if (thread.getAssignedAdminId() != null && !thread.getAssignedAdminId().equals(admin.userId())) {
            throw new AppBadException(messageService.getMessage("support.thread.already.assigned"));
        }

        thread.setAssignedAdminId(admin.userId());
        thread.setAssignedAdminRole(admin.role());
        thread.setStatus(SupportThreadStatus.ASSIGNED);
        return toThreadResponse(supportThreadRepository.save(thread));
    }

    @Override
    @Transactional
    public SupportThreadResponse close(Long threadId) {
        AdminContext admin = requireCurrentAdmin();
        SupportThread thread = supportThreadRepository.findByIdForUpdate(threadId)
                .orElseThrow(() -> new AppBadException(messageService.getMessage("support.thread.not.found")));

        boolean superAdmin = admin.role() == AssignedAdminRole.SUPER_ADMIN;
        boolean assignedToCurrentAdmin = admin.userId().equals(thread.getAssignedAdminId());
        if (!superAdmin && !assignedToCurrentAdmin) {
            throw new AppBadException(messageService.getMessage("support.thread.access.denied"));
        }

        thread.setStatus(SupportThreadStatus.CLOSED);
        return toThreadResponse(supportThreadRepository.save(thread));
    }

    @Override
    public WsTokenResponse issueWsToken() {
        return chatWebSocketTokenService.issueToken();
    }

    @Override
    @Transactional(readOnly = true)
    public void validateThreadAccess(Long userId, Long threadId) {
        resolveContext(userId, threadId);
    }

    @Override
    @Transactional(readOnly = true)
    public SupportParticipantRole resolveParticipantRole(Long userId, Long threadId) {
        return resolveContext(userId, threadId).role();
    }

    @Override
    @Transactional
    public SupportMessageResponse sendMessage(Long userId, Long threadId, String body, String attachmentKey) {
        ParticipantContext context = resolveContext(userId, threadId);
        if (context.thread().getStatus() == SupportThreadStatus.CLOSED) {
            throw new AppBadException(messageService.getMessage("support.thread.closed"));
        }

        String normalizedBody = normalize(body);
        String normalizedAttachmentKey = normalize(attachmentKey);
        if (normalizedBody == null && normalizedAttachmentKey == null) {
            throw new AppBadException(messageService.getMessage("support.message.content.required"));
        }

        LocalDateTime now = LocalDateTime.now();
        SupportMessage message = new SupportMessage();
        message.setThreadId(threadId);
        message.setSenderId(userId);
        message.setSenderRole(context.role());
        message.setBody(normalizedBody);
        message.setAttachmentKey(normalizedAttachmentKey);
        message.setAttachmentUrl(normalizedAttachmentKey == null ? null : mediaBaseUrl + "/" + normalizedAttachmentKey);
        message.setSentAt(now);
        message.setDeliveredAt(now);

        SupportMessage saved = supportMessageRepository.save(message);
        context.thread().setLastMessageAt(now);
        supportThreadRepository.save(context.thread());
        return toMessageResponse(saved);
    }

    @Override
    @Transactional
    public SupportReadReceiptResponse markMessagesRead(Long userId, Long threadId, List<Long> messageIds) {
        ParticipantContext context = resolveContext(userId, threadId);
        if (messageIds == null || messageIds.isEmpty()) {
            throw new AppBadException(messageService.getMessage("support.message.ids.required"));
        }

        List<SupportMessage> messages = supportMessageRepository
                .findByThreadIdAndIdInAndDeletedFalse(threadId, messageIds);
        List<Long> updatedIds = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        for (SupportMessage message : messages) {
            if (message.getSenderId().equals(userId)) {
                continue;
            }
            if (context.role().isAdmin() && message.getAdminReadAt() == null) {
                message.setAdminReadAt(now);
                updatedIds.add(message.getId());
            } else if (!context.role().isAdmin() && message.getRequesterReadAt() == null) {
                message.setRequesterReadAt(now);
                updatedIds.add(message.getId());
            }
        }

        if (!updatedIds.isEmpty()) {
            supportMessageRepository.saveAll(messages);
        }
        return new SupportReadReceiptResponse(threadId, updatedIds, userId);
    }

    private ParticipantContext resolveContext(Long userId, Long threadId) {
        if (userId == null) {
            throw new AppBadException(messageService.getMessage("auth.unauthorized"));
        }
        SupportThread thread = supportThreadRepository.findByIdAndDeletedFalse(threadId)
                .orElseThrow(() -> new AppBadException(messageService.getMessage("support.thread.not.found")));

        if (userId.equals(thread.getRequesterId())) {
            return new ParticipantContext(thread,
                    SupportParticipantRole.valueOf(thread.getRequesterRole().name()));
        }
        if (userId.equals(thread.getAssignedAdminId()) && thread.getAssignedAdminRole() != null) {
            return new ParticipantContext(thread,
                    SupportParticipantRole.valueOf(thread.getAssignedAdminRole().name()));
        }
        throw new AppBadException(messageService.getMessage("support.thread.access.denied"));
    }

    private RequesterRole resolveRequesterRole() {
        if (SpringSecurityUtil.hasRole("SELLER")) {
            return RequesterRole.SELLER;
        }
        if (SpringSecurityUtil.hasRole("BUYER")) {
            return RequesterRole.BUYER;
        }
        throw new AppBadException(messageService.getMessage("support.requester.role.invalid"));
    }

    private AdminContext requireCurrentAdmin() {
        Long userId = requireCurrentUserId();
        if (SpringSecurityUtil.hasRole("SUPER_ADMIN")) {
            return new AdminContext(userId, AssignedAdminRole.SUPER_ADMIN);
        }
        if (SpringSecurityUtil.hasRole("ADMIN")) {
            return new AdminContext(userId, AssignedAdminRole.ADMIN);
        }
        throw new AppBadException(messageService.getMessage("support.admin.role.required"));
    }

    private Long requireCurrentUserId() {
        Long userId = SpringSecurityUtil.getProfileId();
        if (userId == null) {
            throw new AppBadException(messageService.getMessage("auth.unauthorized"));
        }
        return userId;
    }

    private void validatePage(int page, int perPage) {
        if (page < 1) {
            throw new AppBadException(messageService.getMessage("validation.page.min"));
        }
        if (perPage < 1 || perPage > 100) {
            throw new AppBadException(messageService.getMessage("validation.per.page.range"));
        }
    }

    private String normalizeSubject(String value) {
        String normalized = normalize(value);
        return normalized == null ? messageService.getMessage("support.default.subject") : normalized;
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private SupportThreadResponse toThreadResponse(SupportThread thread) {
        SupportThreadResponse response = new SupportThreadResponse();
        response.setThreadId(thread.getId());
        response.setRequesterId(thread.getRequesterId());
        response.setRequesterRole(thread.getRequesterRole());
        response.setAssignedAdminId(thread.getAssignedAdminId());
        response.setAssignedAdminRole(thread.getAssignedAdminRole());
        response.setStatus(thread.getStatus());
        response.setSubject(thread.getSubject());
        response.setLastMessageAt(thread.getLastMessageAt());
        response.setCreatedDate(thread.getCreatedDate());
        return response;
    }

    private SupportMessageResponse toMessageResponse(SupportMessage message) {
        SupportMessageResponse response = new SupportMessageResponse();
        response.setId(message.getId());
        response.setThreadId(message.getThreadId());
        response.setSenderId(message.getSenderId());
        response.setSenderRole(message.getSenderRole());
        response.setBody(message.getBody());
        response.setAttachmentKey(message.getAttachmentKey());
        response.setAttachmentUrl(message.getAttachmentUrl());
        response.setSentAt(message.getSentAt());
        response.setDeliveredAt(message.getDeliveredAt());

        LocalDateTime readAt = message.getSenderRole().isAdmin()
                ? message.getRequesterReadAt()
                : message.getAdminReadAt();
        response.setReadAt(readAt);
        response.setStatus(readAt != null ? "read" : message.getDeliveredAt() != null ? "delivered" : "sent");
        return response;
    }

}
