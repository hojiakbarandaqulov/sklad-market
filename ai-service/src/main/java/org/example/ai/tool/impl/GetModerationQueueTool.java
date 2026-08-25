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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@code GET /api/v1/admin/products/moderation-queue} and {@code GET
 * /api/v1/admin/companies/moderation-queue} (both verified in source, {@code
 * hasAnyRole('ADMIN','SUPER_ADMIN')}, unpaged — hardcoded to PENDING/PENDING_VERIFICATION items
 * only). Lists NEW submissions awaiting a first moderation decision — distinct from user-submitted
 * complaints against already-live items, which {@code get_reports} covers instead.
 */
@Component
public class GetModerationQueueTool implements AgentTool {

    private static final List<String> TARGET_TYPES = List.of("PRODUCT", "COMPANY");
    private static final int MAX_PER_TYPE = 10;

    private final GatewayClient gatewayClient;

    public GetModerationQueueTool(GatewayClient gatewayClient) {
        this.gatewayClient = gatewayClient;
    }

    @Override
    public String name() {
        return "get_moderation_queue";
    }

    @Override
    public String description() {
        return "List products and/or companies currently pending moderation (new submissions awaiting "
                + "a first approve/reject decision). Optionally filter by targetType; omit it to see both.";
    }

    @Override
    public Map<String, Object> parametersSchema() {
        Map<String, Object> properties = Map.of("targetType", Map.of("type", "STRING", "enum", TARGET_TYPES,
                "description", "PRODUCT or COMPANY. Omit to list both."));
        return Map.of("type", "OBJECT", "properties", properties, "required", List.of());
    }

    @Override
    public Set<String> allowedRoles() {
        return Set.of("ADMIN", "SUPER_ADMIN");
    }

    @Override
    public ToolResult execute(Map<String, Object> args, ToolExecutionContext context) {
        String targetType = ToolArgs.asString(args.get("targetType"));
        boolean wantProducts = targetType == null || "PRODUCT".equalsIgnoreCase(targetType);
        boolean wantCompanies = targetType == null || "COMPANY".equalsIgnoreCase(targetType);

        List<Map<String, Object>> products = wantProducts ? fetchProducts(context) : List.of();
        List<Map<String, Object>> companies = wantCompanies ? fetchCompanies(context) : List.of();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("pendingProducts", products);
        result.put("pendingCompanies", companies);
        result.put("count", products.size() + companies.size());
        return ToolResult.ok(result);
    }

    private List<Map<String, Object>> fetchProducts(ToolExecutionContext context) {
        try {
            GatewayEnvelope<List<RemoteAdminProductDto>> envelope = gatewayClient.get(
                    "/api/v1/admin/products/moderation-queue",
                    null,
                    context.bearerToken(),
                    PlatformLanguage.header(context.acceptLanguage()),
                    new ParameterizedTypeReference<GatewayEnvelope<List<RemoteAdminProductDto>>>() {
                    });
            List<RemoteAdminProductDto> items = envelope == null || envelope.data() == null ? List.of() : envelope.data();
            List<Map<String, Object>> projected = new ArrayList<>();
            for (RemoteAdminProductDto product : items) {
                if (projected.size() >= MAX_PER_TYPE) break;
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("targetType", "PRODUCT");
                item.put("id", product.id());
                item.put("name", product.name());
                item.put("companyId", product.companyId());
                item.put("categoryId", product.categoryId());
                item.put("createdAt", product.createdAt());
                projected.add(item);
            }
            return projected;
        } catch (GatewayNotFoundException e) {
            return List.of();
        } catch (GatewayUnavailableException e) {
            return List.of(Map.of("error", "The product moderation queue is temporarily unavailable"));
        }
    }

    private List<Map<String, Object>> fetchCompanies(ToolExecutionContext context) {
        try {
            GatewayEnvelope<List<RemoteAdminCompanyDto>> envelope = gatewayClient.get(
                    "/api/v1/admin/companies/moderation-queue",
                    null,
                    context.bearerToken(),
                    PlatformLanguage.header(context.acceptLanguage()),
                    new ParameterizedTypeReference<GatewayEnvelope<List<RemoteAdminCompanyDto>>>() {
                    });
            List<RemoteAdminCompanyDto> items = envelope == null || envelope.data() == null ? List.of() : envelope.data();
            List<Map<String, Object>> projected = new ArrayList<>();
            for (RemoteAdminCompanyDto company : items) {
                if (projected.size() >= MAX_PER_TYPE) break;
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("targetType", "COMPANY");
                item.put("id", company.id());
                item.put("name", company.name());
                item.put("slug", company.slug());
                item.put("createdAt", company.createdAt());
                projected.add(item);
            }
            return projected;
        } catch (GatewayNotFoundException e) {
            return List.of();
        } catch (GatewayUnavailableException e) {
            return List.of(Map.of("error", "The company moderation queue is temporarily unavailable"));
        }
    }
}
