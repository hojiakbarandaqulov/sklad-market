package org.example.ai.tool.impl;

import org.example.ai.gateway.GatewayClient;
import org.example.ai.gateway.GatewayNotFoundException;
import org.example.ai.gateway.GatewayUnavailableException;
import org.example.ai.gateway.dto.GatewayEnvelope;
import org.example.ai.gateway.dto.RemoteLeadDto;
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
 * {@code GET /api/v1/leads/seller} (verified in {@code LeadController}, {@code hasRole('SELLER')};
 * params {@code status}/{@code page}/{@code perPage} — same {@code perPage} camelCase as the buyer
 * listing endpoint, unlike chat-service's {@code per_page}). Returns leads addressed to the
 * caller's company, scoped server-side by the JWT's {@code profileId} claim as {@code sellerId} —
 * no company lookup needed here.
 */
@Component
public class GetSellerLeadsTool implements AgentTool {

    private static final int PAGE_SIZE = 10;
    private static final List<String> STATUSES = List.of("NEW", "VIEWED", "CONTACTED", "CLOSED", "CANCELED");

    private final GatewayClient gatewayClient;

    public GetSellerLeadsTool(GatewayClient gatewayClient) {
        this.gatewayClient = gatewayClient;
    }

    @Override
    public String name() {
        return "get_seller_leads";
    }

    @Override
    public String description() {
        return "List leads/RFQs that buyers have sent to the current seller's company, optionally "
                + "filtered by status. Use for 'do I have any new requests?' style questions.";
    }

    @Override
    public Map<String, Object> parametersSchema() {
        Map<String, Object> properties = Map.of("status", Map.of("type", "STRING", "enum", STATUSES,
                "description", "Filter by lead status (optional)."));
        return Map.of("type", "OBJECT", "properties", properties, "required", List.of());
    }

    @Override
    public Set<String> allowedRoles() {
        return Set.of("SELLER");
    }

    @Override
    public ToolResult execute(Map<String, Object> args, ToolExecutionContext context) {
        String status = ToolArgs.asString(args.get("status"));
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        if (status != null) {
            params.add("status", status);
        }
        params.add("page", "1");
        params.add("perPage", String.valueOf(PAGE_SIZE));

        try {
            GatewayEnvelope<RemotePagedResponse<RemoteLeadDto>> envelope = gatewayClient.get(
                    "/api/v1/leads/seller",
                    params,
                    context.bearerToken(),
                    PlatformLanguage.header(context.acceptLanguage()),
                    new ParameterizedTypeReference<GatewayEnvelope<RemotePagedResponse<RemoteLeadDto>>>() {
                    });
            RemotePagedResponse<RemoteLeadDto> paged = envelope == null ? null : envelope.data();
            List<RemoteLeadDto> leads = paged == null || paged.items() == null ? List.of() : paged.items();

            List<Map<String, Object>> projected = leads.stream().map(this::project).toList();
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("leads", projected);
            result.put("count", projected.size());
            result.put("totalAvailable", paged != null && paged.meta() != null ? paged.meta().total() : projected.size());
            return ToolResult.ok(result);
        } catch (GatewayNotFoundException e) {
            return ToolResult.ok(Map.of("leads", List.of(), "count", 0, "totalAvailable", 0));
        } catch (GatewayUnavailableException e) {
            return ToolResult.error("The requests service is temporarily unavailable", null);
        }
    }

    private Map<String, Object> project(RemoteLeadDto lead) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", lead.id());
        item.put("status", lead.status());
        item.put("itemCount", lead.items() == null ? 0 : lead.items().size());
        item.put("firstItemName", lead.items() == null || lead.items().isEmpty() ? null : lead.items().get(0).productNameSnapshot());
        item.put("neededDate", lead.neededDate());
        item.put("buyerComment", ToolArgs.truncate(lead.comment(), 160));
        return item;
    }
}
