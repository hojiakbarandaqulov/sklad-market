package org.example.ai.tool.impl;

import org.example.ai.gateway.GatewayClient;
import org.example.ai.gateway.GatewayNotFoundException;
import org.example.ai.gateway.GatewayUnavailableException;
import org.example.ai.gateway.dto.GatewayEnvelope;
import org.example.ai.gateway.dto.RemoteAdminCompanyDto;
import org.example.ai.gateway.dto.RemoteAdminProductDto;
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
 * Full detail + risk-summary material for one pending moderation item (PLAN.md Phase 6, C10).
 * PRODUCT uses the real per-id admin detail endpoint ({@code GET /api/v1/admin/products/{id}},
 * verified in source). COMPANY has NO per-id admin detail endpoint anywhere on the platform
 * (verified: {@code AdminCompanyController} only has list/moderation-queue/verify/reject/block) —
 * documented platform gap, not worked around: this tool searches the unpaged moderation-queue list
 * for a matching id instead of guessing at a nonexistent endpoint, and returns a clear "not found /
 * not exposed" result if the company isn't in that list (e.g. already decided).
 */
@Component
public class SummarizeModerationItemTool implements AgentTool {

    private static final List<String> TARGET_TYPES = List.of("PRODUCT", "COMPANY");

    private final GatewayClient gatewayClient;

    public SummarizeModerationItemTool(GatewayClient gatewayClient) {
        this.gatewayClient = gatewayClient;
    }

    @Override
    public String name() {
        return "summarize_moderation_item";
    }

    @Override
    public String description() {
        return "Fetch full detail for one pending product or company (by targetType and id, from "
                + "get_moderation_queue) to summarize its moderation risk. Never invents fields not "
                + "actually returned by the platform.";
    }

    @Override
    public Map<String, Object> parametersSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("targetType", Map.of("type", "STRING", "enum", TARGET_TYPES, "description", "PRODUCT or COMPANY."));
        properties.put("id", Map.of("type", "INTEGER", "description", "The item's numeric id (from get_moderation_queue)."));
        return Map.of("type", "OBJECT", "properties", properties, "required", List.of("targetType", "id"));
    }

    @Override
    public Set<String> allowedRoles() {
        return Set.of("ADMIN", "SUPER_ADMIN");
    }

    @Override
    public ToolResult execute(Map<String, Object> args, ToolExecutionContext context) {
        String targetType = ToolArgs.asString(args.get("targetType"));
        Object rawId = args.get("id");
        if (targetType == null || !(rawId instanceof Number number)) {
            return ToolResult.error("targetType and id are required", 400);
        }
        long id = number.longValue();

        if ("PRODUCT".equalsIgnoreCase(targetType)) {
            return summarizeProduct(id, context);
        }
        if ("COMPANY".equalsIgnoreCase(targetType)) {
            return summarizeCompany(id, context);
        }
        return ToolResult.error("targetType must be PRODUCT or COMPANY", 400);
    }

    private ToolResult summarizeProduct(long id, ToolExecutionContext context) {
        try {
            GatewayEnvelope<RemoteAdminProductDto> envelope = gatewayClient.get(
                    "/api/v1/admin/products/{id}",
                    null,
                    context.bearerToken(),
                    PlatformLanguage.header(context.acceptLanguage()),
                    new ParameterizedTypeReference<GatewayEnvelope<RemoteAdminProductDto>>() {
                    },
                    id);
            RemoteAdminProductDto product = envelope == null ? null : envelope.data();
            if (product == null) {
                return ToolResult.notFound("Product not found: " + id);
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("targetType", "PRODUCT");
            result.put("id", product.id());
            result.put("name", product.name());
            result.put("status", product.status());
            result.put("companyId", product.companyId());
            result.put("categoryId", product.categoryId());
            result.put("shortDescription", ToolArgs.truncate(product.shortDescription(), 300));
            result.put("description", ToolArgs.truncate(product.description(), 500));
            result.put("attributes", product.attributes());
            result.put("priorRejectReason", product.rejectReason());
            result.put("createdAt", product.createdAt());
            return ToolResult.ok(result);
        } catch (GatewayNotFoundException e) {
            return ToolResult.notFound("Product not found: " + id);
        } catch (GatewayUnavailableException e) {
            return ToolResult.error("The product moderation service is temporarily unavailable", null);
        }
    }

    private ToolResult summarizeCompany(long id, ToolExecutionContext context) {
        try {
            GatewayEnvelope<List<RemoteAdminCompanyDto>> envelope = gatewayClient.get(
                    "/api/v1/admin/companies/moderation-queue",
                    null,
                    context.bearerToken(),
                    PlatformLanguage.header(context.acceptLanguage()),
                    new ParameterizedTypeReference<GatewayEnvelope<List<RemoteAdminCompanyDto>>>() {
                    });
            List<RemoteAdminCompanyDto> items = envelope == null || envelope.data() == null ? List.of() : envelope.data();
            RemoteAdminCompanyDto company = items.stream().filter(c -> id == c.id()).findFirst().orElse(null);
            if (company == null) {
                return ToolResult.error(
                        "Company " + id + " is not in the current moderation queue, and the platform has no "
                                + "per-id admin detail endpoint for companies yet — use get_moderation_queue for "
                                + "an overview instead of a single-item detail for companies.", 404);
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("targetType", "COMPANY");
            result.put("id", company.id());
            result.put("name", company.name());
            result.put("slug", company.slug());
            result.put("verificationStatus", company.verificationStatus());
            result.put("shortDescription", ToolArgs.truncate(company.shortDescription(), 300));
            result.put("address", company.address());
            result.put("createdAt", company.createdAt());
            return ToolResult.ok(result);
        } catch (GatewayNotFoundException e) {
            return ToolResult.notFound("Company not found: " + id);
        } catch (GatewayUnavailableException e) {
            return ToolResult.error("The company moderation service is temporarily unavailable", null);
        }
    }
}
