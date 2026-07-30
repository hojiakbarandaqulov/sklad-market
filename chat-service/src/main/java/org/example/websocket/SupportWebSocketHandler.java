package org.example.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.dto.support.SupportMessageResponse;
import org.example.dto.support.SupportReadReceiptResponse;
import org.example.exp.AppBadException;
import org.example.service.ChatWebSocketTokenService;
import org.example.service.ResourceBundleService;
import org.example.service.SupportService;
import org.example.service.impl.ChatRateLimitService;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class SupportWebSocketHandler extends TextWebSocketHandler {
    private final ObjectMapper objectMapper;
    private final SupportService supportService;
    private final ChatWebSocketTokenService tokenService;
    private final ChatRateLimitService rateLimitService;
    private final ResourceBundleService messageService;

    private final Map<Long, Set<WebSocketSession>> threadSubscribers = new ConcurrentHashMap<>();
    private final Map<String, Set<Long>> sessionSubscriptions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Locale locale = resolveLocale(session);
        session.getAttributes().put("locale", locale);
        LocaleContextHolder.setLocale(locale);
        try {
            String token = extractQueryParam(session.getUri(), "token");
            session.getAttributes().put("userId", tokenService.parseToken(token));
            sessionSubscriptions.put(session.getId(), ConcurrentHashMap.newKeySet());
        } finally {
            LocaleContextHolder.resetLocaleContext();
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        Locale locale = (Locale) session.getAttributes()
                .getOrDefault("locale", Locale.forLanguageTag("uz"));
        LocaleContextHolder.setLocale(locale);
        try {
            JsonNode payload = objectMapper.readTree(message.getPayload());
            String event = requiredText(payload, "event");
            Long userId = (Long) session.getAttributes().get("userId");

            switch (event) {
                case "subscribe" -> subscribe(session, userId, payload);
                case "message" -> sendMessage(session, userId, payload);
                case "read" -> markRead(userId, payload);
                case "typing" -> sendTyping(userId, payload);
                default -> sendError(session, "bad_request",
                        messageService.getMessage("support.websocket.event.unsupported"));
            }
        } catch (AppBadException e) {
            sendError(session, "bad_request", e.getMessage());
        } catch (Exception e) {
            log.error("Support WebSocket handler error", e);
            sendError(session, "internal_error",
                    messageService.getMessage("support.websocket.unexpected.error"));
        } finally {
            LocaleContextHolder.resetLocaleContext();
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

    private void subscribe(WebSocketSession session, Long userId, JsonNode payload) throws Exception {
        Long threadId = requiredLong(payload, "thread_id");
        supportService.validateThreadAccess(userId, threadId);
        threadSubscribers.computeIfAbsent(threadId, ignored -> ConcurrentHashMap.newKeySet()).add(session);
        sessionSubscriptions.computeIfAbsent(session.getId(), ignored -> ConcurrentHashMap.newKeySet()).add(threadId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("event", "subscribed");
        response.put("thread_id", threadId);
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
    }

    private void sendMessage(WebSocketSession session, Long userId, JsonNode payload) throws Exception {
        if (!rateLimitService.allowMessage(userId)) {
            sendError(session, "rate_limited", messageService.getMessage("chat.rate.limit.exceeded"));
            return;
        }

        Long threadId = requiredLong(payload, "thread_id");
        String body = payload.hasNonNull("body") ? payload.get("body").asText() : null;
        String attachmentKey = payload.hasNonNull("attachment_key")
                ? payload.get("attachment_key").asText()
                : null;

        SupportMessageResponse saved = supportService.sendMessage(userId, threadId, body, attachmentKey);
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("event", "new_message");
        event.put("thread_id", threadId);
        event.put("message", saved);
        broadcast(threadId, event);
    }

    private void markRead(Long userId, JsonNode payload) throws Exception {
        Long threadId = requiredLong(payload, "thread_id");
        JsonNode idsNode = payload.get("message_ids");
        if (idsNode == null || !idsNode.isArray()) {
            throw new AppBadException(messageService.getMessage("support.message.ids.required"));
        }

        List<Long> messageIds = new ArrayList<>();
        idsNode.forEach(node -> messageIds.add(node.asLong()));
        SupportReadReceiptResponse receipt = supportService.markMessagesRead(userId, threadId, messageIds);
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

    private void sendTyping(Long userId, JsonNode payload) throws Exception {
        Long threadId = requiredLong(payload, "thread_id");
        supportService.validateThreadAccess(userId, threadId);

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
        List<WebSocketSession> closed = new ArrayList<>();
        for (WebSocketSession subscriber : sessions) {
            if (!subscriber.isOpen()) {
                closed.add(subscriber);
                continue;
            }
            subscriber.sendMessage(new TextMessage(text));
        }
        sessions.removeAll(closed);
    }

    private void sendError(WebSocketSession session, String code, String message) throws Exception {
        if (!session.isOpen()) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("event", "error");
        payload.put("code", code);
        payload.put("message", message);
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(payload)));
    }

    private String requiredText(JsonNode payload, String field) {
        if (!payload.hasNonNull(field) || payload.get(field).asText().isBlank()) {
            throw new AppBadException(messageService.getMessage("validation.field.required", field));
        }
        return payload.get(field).asText();
    }

    private Long requiredLong(JsonNode payload, String field) {
        if (!payload.hasNonNull(field) || !payload.get(field).canConvertToLong()) {
            throw new AppBadException(messageService.getMessage("validation.field.required", field));
        }
        return payload.get(field).asLong();
    }

    private String extractQueryParam(URI uri, String name) {
        if (uri == null || uri.getQuery() == null) {
            throw new AppBadException(messageService.getMessage("chat.websocket.token.missing"));
        }
        for (String part : uri.getQuery().split("&")) {
            String[] pair = part.split("=", 2);
            if (pair.length == 2 && name.equals(pair[0])) {
                return pair[1];
            }
        }
        throw new AppBadException(messageService.getMessage("chat.websocket.token.missing"));
    }

    private Locale resolveLocale(WebSocketSession session) {
        String language = extractOptionalQueryParam(session.getUri(), "lang");
        if (language == null) {
            language = session.getHandshakeHeaders().getFirst("Accept-Language");
        }
        if (language == null || language.isBlank()) {
            return Locale.forLanguageTag("uz");
        }
        String code = Locale.forLanguageTag(language.split(",")[0].trim()).getLanguage();
        return switch (code.toLowerCase(Locale.ROOT)) {
            case "ru" -> Locale.forLanguageTag("ru");
            case "en" -> Locale.forLanguageTag("en");
            default -> Locale.forLanguageTag("uz");
        };
    }

    private String extractOptionalQueryParam(URI uri, String name) {
        if (uri == null || uri.getQuery() == null) {
            return null;
        }
        for (String part : uri.getQuery().split("&")) {
            String[] pair = part.split("=", 2);
            if (pair.length == 2 && name.equals(pair[0])) {
                return pair[1];
            }
        }
        return null;
    }
}
