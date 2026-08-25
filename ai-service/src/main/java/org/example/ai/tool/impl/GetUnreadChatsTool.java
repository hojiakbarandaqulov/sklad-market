package org.example.ai.tool.impl;

import org.example.ai.gateway.GatewayClient;
import org.example.ai.gateway.GatewayNotFoundException;
import org.example.ai.gateway.GatewayUnavailableException;
import org.example.ai.gateway.dto.GatewayEnvelope;
import org.example.ai.gateway.dto.RemoteChatThreadDto;
import org.example.ai.gateway.dto.RemotePagedResponse;
import org.example.ai.tool.AgentTool;
import org.example.ai.tool.PlatformLanguage;
import org.example.ai.tool.ToolArgs;
import org.example.ai.tool.ToolExecutionContext;
import org.example.ai.tool.ToolResult;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@code GET /api/v1/chats} (verified in {@code ChatController}: no {@code @PreAuthorize}, just
 * authenticated; page param is {@code per_page}, unlike lead-service/product-service's
 * {@code perPage}). Projects only threads with unread messages.
 */
@Component
public class GetUnreadChatsTool implements AgentTool {

    private static final int PAGE_SIZE = 20;

    private final GatewayClient gatewayClient;

    public GetUnreadChatsTool(GatewayClient gatewayClient) {
        this.gatewayClient = gatewayClient;
    }

    @Override
    public String name() {
        return "get_unread_chats";
    }

    @Override
    public String description() {
        return "List the current user's chat threads that have unread messages, with the other "
                + "party's name and a preview of the last message.";
    }

    @Override
    public Map<String, Object> parametersSchema() {
        return Map.of("type", "OBJECT", "properties", Map.of(), "required", List.of());
    }

    @Override
    public Set<String> allowedRoles() {
        return Set.of();
    }

    @Override
    public ToolResult execute(Map<String, Object> args, ToolExecutionContext context) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("page", "1");
        params.add("per_page", String.valueOf(PAGE_SIZE));

        try {
            GatewayEnvelope<RemotePagedResponse<RemoteChatThreadDto>> envelope = gatewayClient.get(
                    "/api/v1/chats",
                    params,
                    context.bearerToken(),
                    PlatformLanguage.header(context.acceptLanguage()),
                    new ParameterizedTypeReference<GatewayEnvelope<RemotePagedResponse<RemoteChatThreadDto>>>() {
                    });
            RemotePagedResponse<RemoteChatThreadDto> paged = envelope == null ? null : envelope.data();
            List<RemoteChatThreadDto> threads = paged == null || paged.items() == null ? List.of() : paged.items();

            List<Map<String, Object>> unread = threads.stream()
                    .filter(t -> t.unreadCount() > 0)
                    .map(this::project)
                    .toList();

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("unreadThreads", unread);
            result.put("count", unread.size());
            return ToolResult.ok(result);
        } catch (GatewayNotFoundException e) {
            return ToolResult.ok(Map.of("unreadThreads", List.of(), "count", 0));
        } catch (GatewayUnavailableException e) {
            return ToolResult.error("The chat service is temporarily unavailable", null);
        }
    }

    private Map<String, Object> project(RemoteChatThreadDto thread) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("threadId", thread.threadId());
        item.put("otherPartyName", thread.otherParty() == null ? null : thread.otherParty().displayName());
        item.put("unreadCount", thread.unreadCount());
        item.put("lastMessage", thread.lastMessage() == null ? null : ToolArgs.truncate(thread.lastMessage().body(), 160));
        return item;
    }
}
