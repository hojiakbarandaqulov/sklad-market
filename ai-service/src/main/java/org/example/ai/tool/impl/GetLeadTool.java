package org.example.ai.tool.impl;

import org.example.ai.gateway.GatewayClient;
import org.example.ai.gateway.GatewayNotFoundException;
import org.example.ai.gateway.GatewayUnavailableException;
import org.example.ai.gateway.dto.GatewayEnvelope;
import org.example.ai.gateway.dto.RemoteLeadDto;
import org.example.ai.tool.AgentTool;
import org.example.ai.tool.PlatformLanguage;
import org.example.ai.tool.ToolArgs;
import org.example.ai.tool.ToolExecutionContext;
import org.example.ai.tool.ToolResult;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@code GET /api/v1/leads/{id}} (verified in {@code LeadController}, {@code
 * hasAnyRole('BUYER','SELLER')}; the service itself enforces that the caller is either the lead's
 * buyer or its seller — a foreign lead id 400s uniformly like every other not-found case).
 */
@Component
public class GetLeadTool implements AgentTool {

    private final GatewayClient gatewayClient;

    public GetLeadTool(GatewayClient gatewayClient) {
        this.gatewayClient = gatewayClient;
    }

    @Override
    public String name() {
        return "get_lead";
    }

    @Override
    public String description() {
        return "Fetch full details of one lead/RFQ by its numeric id (from get_my_leads results), "
                + "including items, contact info, and status.";
    }

    @Override
    public Map<String, Object> parametersSchema() {
        Map<String, Object> properties = Map.of("id", Map.of("type", "INTEGER", "description", "Lead id."));
        return Map.of("type", "OBJECT", "properties", properties, "required", List.of("id"));
    }

    @Override
    public Set<String> allowedRoles() {
        return Set.of("BUYER", "SELLER");
    }

    @Override
    public ToolResult execute(Map<String, Object> args, ToolExecutionContext context) {
        Object rawId = args.get("id");
        if (!(rawId instanceof Number number)) {
            return ToolResult.error("id is required", 400);
        }
        long id = number.longValue();

        try {
            GatewayEnvelope<RemoteLeadDto> envelope = gatewayClient.get(
                    "/api/v1/leads/{id}",
                    null,
                    context.bearerToken(),
                    PlatformLanguage.header(context.acceptLanguage()),
                    new ParameterizedTypeReference<GatewayEnvelope<RemoteLeadDto>>() {
                    },
                    id);
            RemoteLeadDto lead = envelope == null ? null : envelope.data();
            if (lead == null) {
                return ToolResult.notFound("Lead not found: " + id);
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("id", lead.id());
            result.put("status", lead.status());
            result.put("contactName", lead.contactName());
            result.put("contactPhone", lead.contactPhone());
            result.put("deliveryAddress", lead.deliveryAddress());
            result.put("neededDate", lead.neededDate());
            result.put("comment", ToolArgs.truncate(lead.comment(), 300));
            result.put("closeReason", lead.closeReason());
            result.put("items", lead.items() == null ? List.of() : lead.items().stream().map(item -> {
                Map<String, Object> projected = new LinkedHashMap<>();
                projected.put("productId", item.productId());
                projected.put("name", item.productNameSnapshot());
                projected.put("price", item.priceSnapshot());
                projected.put("quantity", item.quantity());
                return projected;
            }).toList());
            return ToolResult.ok(result);
        } catch (GatewayNotFoundException e) {
            return ToolResult.notFound("Lead not found: " + id);
        } catch (GatewayUnavailableException e) {
            return ToolResult.error("The requests service is temporarily unavailable", null);
        }
    }
}
