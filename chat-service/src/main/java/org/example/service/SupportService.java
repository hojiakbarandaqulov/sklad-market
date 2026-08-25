package org.example.service;

import org.example.dto.PagedResponse;
import org.example.dto.chat.WsTokenResponse;
import org.example.dto.support.SupportCreateRequest;
import org.example.dto.support.SupportMessageResponse;
import org.example.dto.support.SupportOpenResponse;
import org.example.dto.support.SupportReadReceiptResponse;
import org.example.dto.support.SupportThreadResponse;
import org.example.enums.SupportParticipantRole;
import org.example.enums.SupportThreadStatus;
import org.example.enums.SupportThreadType;

import java.util.List;

public interface SupportService {
    SupportOpenResponse openThread(SupportCreateRequest request);

    PagedResponse<SupportMessageResponse> getMessages(Long threadId, int page, int perPage, Long beforeId);

    PagedResponse<SupportThreadResponse> getAdminQueue(SupportThreadStatus status, SupportThreadType threadType, int page, int perPage);

    SupportThreadResponse assignToCurrentAdmin(Long threadId);

    SupportThreadResponse close(Long threadId);

    WsTokenResponse issueWsToken();

    void validateThreadAccess(Long userId, Long threadId);

    SupportMessageResponse sendMessage(Long userId, Long threadId, String body, String attachmentKey);

    SupportReadReceiptResponse markMessagesRead(Long userId, Long threadId, List<Long> messageIds);

    SupportParticipantRole resolveParticipantRole(Long userId, Long threadId);

    SupportOpenResponse createThread(SupportCreateRequest request, SupportThreadType threadType);
}
