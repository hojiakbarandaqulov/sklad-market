package org.example.ai.guardrail;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.ai.error.AiChatException;
import org.example.ai.error.AiErrorCode;
import org.example.ai.provider.ChatGenerationRequest;
import org.example.ai.provider.ChatMessageInput;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/** Count complete question/answer pairs, not arbitrary rows that can start mid-exchange. */
@Component
public class ChatContextWindow {
    private final ObjectMapper mapper;
    private final int maxBytes;

    public ChatContextWindow(ObjectMapper mapper, @Value("${ai.limits.max-context-bytes:131072}") int maxBytes) {
        this.mapper = mapper;
        this.maxBytes = Math.max(8192, Math.min(maxBytes, 262144));
    }

    public static List<ChatMessageInput> recentPairs(List<ChatMessageInput> messages, int priorPairs) {
        if (messages.isEmpty()) return List.of();
        ChatMessageInput current = messages.get(messages.size() - 1);
        List<List<ChatMessageInput>> pairs = new ArrayList<>();
        ChatMessageInput question = null;
        for (int i = 0; i < messages.size() - 1; i++) {
            var message = messages.get(i);
            if ("user".equals(message.role())) question = message;
            else if (question != null && "model".equals(message.role())) {
                pairs.add(List.of(question, message));
                question = null;
            }
        }
        List<ChatMessageInput> result = new ArrayList<>();
        for (int i = Math.max(0, pairs.size() - priorPairs); i < pairs.size(); i++) result.addAll(pairs.get(i));
        result.add(current);
        return result;
    }

    public ChatGenerationRequest fit(ChatGenerationRequest original) {
        var history = new ArrayList<>(original.history());
        var exchange = new ArrayList<>(original.pendingToolExchange());
        while (true) {
            var bounded = new ChatGenerationRequest(original.model(), original.systemInstruction(), history,
                    original.tools(), exchange, original.temperature(), original.maxOutputTokens());
            try {
                if (mapper.writeValueAsBytes(bounded).length <= maxBytes) return bounded;
            } catch (JsonProcessingException e) { throw new IllegalStateException("Cannot encode AI context", e); }
            if (history.size() > 2) history.subList(0, 2).clear();
            else if (exchange.size() > 2) {
                // Never split a model tool-call batch from its matching tool responses.
                exchange.subList(0, 2).clear();
            } else throw new AiChatException(AiErrorCode.INVALID_INPUT,
                    "This request contains too much detail. Please narrow the search.");
        }
    }
}
