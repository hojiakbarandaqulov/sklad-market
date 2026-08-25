package org.example.ai.tool.impl;

import org.example.ai.gateway.GatewayClient;
import org.example.ai.gateway.GatewayNotFoundException;
import org.example.ai.gateway.GatewayUnavailableException;
import org.example.ai.gateway.dto.GatewayEnvelope;
import org.example.ai.gateway.dto.RemoteReportDto;
import org.example.ai.gateway.dto.RemoteSpringPage;
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
 * {@code GET /api/v1/admin/reports} (report-service, verified in source: {@code
 * hasAnyRole('SUPER_ADMIN','ADMIN')}, params {@code status}/{@code targetType}/{@code page}/{@code
 * size}, returns Jackson-default {@code PageImpl}). User-submitted complaints against an existing
 * product/company/chat, each carrying the platform's REAL {@code ReasonCode} enum value
 * ({@code SAME|FAKE|OFFENSIVE|DUPLICATE|SCAM}) — PLAN.md's Phase 6 spec assumed this enum lived on
 * product/company-service's own moderation flow; verified in source that it actually lives here, on
 * reports, not on new-submission moderation (which uses a free-text reason instead).
 */
@Component
public class GetReportsTool implements AgentTool {

    private static final List<String> STATUSES = List.of("NEW", "RESOLVED", "REJECTED");
    private static final List<String> TARGET_TYPES = List.of("PRODUCT", "COMPANY", "CHAT");
    private static final int PAGE_SIZE = 10;

    private final GatewayClient gatewayClient;

    public GetReportsTool(GatewayClient gatewayClient) {
        this.gatewayClient = gatewayClient;
    }

    @Override
    public String name() {
        return "get_reports";
    }

    @Override
    public String description() {
        return "List user-submitted complaints/reports against products, companies, or chats, "
                + "optionally filtered by status and targetType. Each report carries the platform's real "
                + "reason code (e.g. FAKE, OFFENSIVE, DUPLICATE, SCAM, SAME) — cite it exactly as returned.";
    }

    @Override
    public Map<String, Object> parametersSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("status", Map.of("type", "STRING", "enum", STATUSES, "description", "Filter by report status (optional)."));
        properties.put("targetType", Map.of("type", "STRING", "enum", TARGET_TYPES,
                "description", "Filter by what the report targets (optional)."));
        return Map.of("type", "OBJECT", "properties", properties, "required", List.of());
    }

    @Override
    public Set<String> allowedRoles() {
        return Set.of("ADMIN", "SUPER_ADMIN");
    }

    @Override
    public ToolResult execute(Map<String, Object> args, ToolExecutionContext context) {
        String status = ToolArgs.asString(args.get("status"));
        String targetType = ToolArgs.asString(args.get("targetType"));
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        if (status != null) {
            params.add("status", status);
        }
        if (targetType != null) {
            params.add("targetType", targetType);
        }
        params.add("page", "1");
        params.add("size", String.valueOf(PAGE_SIZE));

        try {
            GatewayEnvelope<RemoteSpringPage<RemoteReportDto>> envelope = gatewayClient.get(
                    "/api/v1/admin/reports",
                    params,
                    context.bearerToken(),
                    PlatformLanguage.header(context.acceptLanguage()),
                    new ParameterizedTypeReference<GatewayEnvelope<RemoteSpringPage<RemoteReportDto>>>() {
                    });
            RemoteSpringPage<RemoteReportDto> page = envelope == null ? null : envelope.data();
            List<RemoteReportDto> reports = page == null || page.content() == null ? List.of() : page.content();

            List<Map<String, Object>> projected = reports.stream().map(this::project).toList();
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("reports", projected);
            result.put("count", projected.size());
            return ToolResult.ok(result);
        } catch (GatewayNotFoundException e) {
            return ToolResult.ok(Map.of("reports", List.of(), "count", 0));
        } catch (GatewayUnavailableException e) {
            return ToolResult.error("The reports service is temporarily unavailable", null);
        }
    }

    private Map<String, Object> project(RemoteReportDto report) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", report.id());
        item.put("status", report.status());
        item.put("targetType", report.targetType());
        item.put("targetId", report.targetId());
        item.put("reasonCode", report.reasonCode());
        item.put("createdDate", report.createdDate());
        return item;
    }
}
