package org.example.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.dto.chat.ChatMessageResponse;
import org.example.dto.chat.ReadReceiptResponse;
import org.example.exp.AppBadException;
import org.example.service.ChatService;
import org.example.service.ChatWebSocketTokenService;
import org.example.service.impl.ChatRateLimitService;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatWebSocketHandler extends TextWebSocketHandler {
    private final ObjectMapper objectMapper;
    private final ChatService chatService;
    private final ChatWebSocketTokenService chatWebSocketTokenService;
    private final ChatRateLimitService chatRateLimitService;

    private final Map<Long, Set<WebSocketSession>> threadSubscribers = new ConcurrentHashMap<>();
    private final Map<String, Set<Long>> sessionSubscriptions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String token = extractQueryParam(session.getUri(), "token");
        Long userId = chatWebSocketTokenService.parseToken(token);
        session.getAttributes().put("userId", userId);
        sessionSubscriptions.put(session.getId(), ConcurrentHashMap.newKeySet());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        try {
            JsonNode payload = objectMapper.readTree(message.getPayload());
            String event = getRequiredText(payload, "event");
            Long userId = (Long) session.getAttributes().get("userId");

            switch (event) {
                case "subscribe" -> handleSubscribe(session, userId, payload);
                case "message" -> handleMessage(session, userId, payload);
                case "read" -> handleRead(userId, payload);
                case "typing" -> handleTyping(userId, payload);
                default -> sendError(session, "bad_request", "Unsupported websocket event");
            }
        } catch (AppBadException e) {
            sendError(session, "bad_request", e.getMessage());
        } catch (Exception e) {
            log.error("WebSocket handler error", e);
            sendError(session, "internal_error", "Unexpected websocket error");
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Set<Long> threadIds = sessionSubscriptions.remove(session.getId());
        if (threadIds == null) {
            return;
        }

        for (Long threadId : threadIds) {
            Set<WebSocketSession> sessions = threadSubscribers.get(threadId);
            if (sessions != null) {
                sessions.remove(session);
                if (sessions.isEmpty()) {
                    threadSubscribers.remove(threadId);
                }
            }
        }
    }

    private void handleSubscribe(WebSocketSession session, Long userId, JsonNode payload) {
        Long threadId = getRequiredLong(payload, "thread_id");
        chatService.validateThreadAccess(userId, threadId);
        threadSubscribers.computeIfAbsent(threadId, ignored -> ConcurrentHashMap.newKeySet()).add(session);
        sessionSubscriptions.computeIfAbsent(session.getId(), ignored -> ConcurrentHashMap.newKeySet()).add(threadId);
    }

    private void handleMessage(WebSocketSession session, Long userId, JsonNode payload) throws Exception {
        if (!chatRateLimitService.allowMessage(userId)) {
            sendError(session, "rate_limited", "You can send at most 20 messages in 30 seconds");
            return;
        }

        Long threadId = getRequiredLong(payload, "thread_id");
        String body = payload.hasNonNull("body") ? payload.get("body").asText() : null;
        String attachmentKey = payload.hasNonNull("attachment_key") ? payload.get("attachment_key").asText() : null;

        ChatMessageResponse response = chatService.sendMessage(userId, threadId, body, attachmentKey);
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("event", "new_message");
        event.put("thread_id", threadId);
        event.put("message", response);
        broadcast(threadId, event);
    }

    private void handleRead(Long userId, JsonNode payload) throws Exception {
        Long threadId = getRequiredLong(payload, "thread_id");
        List<Long> messageIds = new ArrayList<>();
        if (!payload.has("message_ids")) {
            throw new AppBadException("message_ids is required");
        }
        for (JsonNode node : payload.get("message_ids")) {
            messageIds.add(node.asLong());
        }

        ReadReceiptResponse receipt = chatService.markMessagesRead(userId, threadId, messageIds);
        if (receipt.getMessageIds().isEmpty()) {
            return;
        }

        Map<String, Object> event = new LinkedHashMap<>();
        event.put("event", "read_receipt");
        event.put("thread_id", threadId);
        event.put("message_ids", receipt.getMessageIds());
        event.put("read_by", receipt.getReadBy());
        broadcast(threadId, event);
    }

    private void handleTyping(Long userId, JsonNode payload) throws Exception {
        Long threadId = getRequiredLong(payload, "thread_id");
        chatService.validateThreadAccess(userId, threadId);

        Map<String, Object> event = new LinkedHashMap<>();
        event.put("event", "typing");
        event.put("thread_id", threadId);
        event.put("user_id", userId);
        broadcast(threadId, event);
    }

    private void broadcast(Long threadId, Object payload) throws Exception {
        Set<WebSocketSession> sessions = threadSubscribers.get(threadId);
        if (sessions == null || sessions.isEmpty()) {
            return;
        }

        String text = objectMapper.writeValueAsString(payload);
        List<WebSocketSession> closedSessions = new ArrayList<>();
        for (WebSocketSession subscriber : sessions) {
            if (!subscriber.isOpen()) {
                closedSessions.add(subscriber);
                continue;
            }
            subscriber.sendMessage(new TextMessage(text));
        }

        sessions.removeAll(closedSessions);
    }

    private void sendError(WebSocketSession session, String code, String message) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("event", "error");
        payload.put("code", code);
        payload.put("message", message);
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(payload)));
    }

    private String getRequiredText(JsonNode payload, String field) {
        if (!payload.hasNonNull(field)) {
            throw new AppBadException(field + " is required");
        }
        return payload.get(field).asText();
    }

    private Long getRequiredLong(JsonNode payload, String field) {
        if (!payload.hasNonNull(field)) {
            throw new AppBadException(field + " is required");
        }
        return payload.get(field).asLong();
    }

    private String extractQueryParam(URI uri, String paramName) {
        if (uri == null || uri.getQuery() == null) {
            throw new AppBadException("Missing websocket token");
        }

        for (String part : uri.getQuery().split("&")) {
            String[] pair = part.split("=", 2);
            if (pair.length == 2 && pair[0].equals(paramName)) {
                return pair[1];
            }
        }
        throw new AppBadException("Missing websocket token");
    }
}
