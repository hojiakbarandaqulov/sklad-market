package org.example.ai.tool.impl;

import org.example.ai.gateway.GatewayClient;
import org.example.ai.gateway.GatewayNotFoundException;
import org.example.ai.gateway.GatewayUnavailableException;
import org.example.ai.gateway.dto.GatewayEnvelope;
import org.example.ai.gateway.dto.RemoteCompanyDto;
import org.example.ai.business.index.CompanyEmbeddingRepository;
import org.example.ai.tool.AgentTool;
import org.example.ai.tool.PlatformLanguage;
import org.example.ai.tool.ToolArgs;
import org.example.ai.tool.ToolExecutionContext;
import org.example.ai.tool.ToolResult;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@code GET /api/v1/companies/{slug}} (verified live in {@code CompanyController} on {@code
 * main}, {@code @PermitAll}): returns the public {@code CompanySlugMapResponse}. The projection is
 * an explicit allowlist containing identity/location plus public business phones, website, and
 * establishment date; unknown or future wire fields are not forwarded. A live
 * probe of an unknown slug against skladmarket.uz on 2026-07-08 returned HTTP 400 with a
 * plain-text body ("companiya topilmadi") — different from product-service's JSON error shape,
 * confirming §7 item 10's "treat every 4xx uniformly" guidance is still the right approach.
 */
@Component
public class GetCompanyTool implements AgentTool {

    private final GatewayClient gatewayClient;
    private final CompanyEmbeddingRepository companyIndex;

    @Autowired
    public GetCompanyTool(GatewayClient gatewayClient, CompanyEmbeddingRepository companyIndex) {
        this.gatewayClient = gatewayClient;
        this.companyIndex = companyIndex;
    }

    /** Compatibility constructor for focused tests that provide only the remote gateway. */
    public GetCompanyTool(GatewayClient gatewayClient) {
        this(gatewayClient, null);
    }

    @Override
    public String name() {
        return "get_company";
    }

    @Override
    public String description() {
        return "Fetch a seller company's public profile by slug (from a product's company info or a "
                + "/company/<slug> link): name, verification status, location, public business "
                + "phone numbers, website, and establishment date.";
    }

    @Override
    public Map<String, Object> parametersSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("slug", Map.of("type", "STRING", "description", "Exact company slug."));
        properties.put("presentation", Map.of("type", "STRING", "enum", List.of("CARDS", "PLAIN_TEXT"),
                "description", "Use PLAIN_TEXT only when the user explicitly asks for no card."));
        return Map.of("type", "OBJECT", "properties", properties, "required", List.of("slug"));
    }

    @Override
    public Set<String> allowedRoles() {
        return Set.of();
    }

    @Override
    public ToolResult execute(Map<String, Object> args, ToolExecutionContext context) {
        String slug = ToolArgs.asString(args.get("slug"));
        if (slug == null) {
            return ToolResult.error("slug is required", null);
        }
        try {
            GatewayEnvelope<RemoteCompanyDto> envelope = gatewayClient.get(
                    "/api/v1/companies/{slug}",
                    null,
                    context.bearerToken(),
                    PlatformLanguage.header(context.acceptLanguage()),
                    new ParameterizedTypeReference<GatewayEnvelope<RemoteCompanyDto>>() {
                    },
                    slug);
            RemoteCompanyDto company = envelope == null ? null : envelope.data();
            if (company == null) {
                return ToolResult.notFound("Company not found: " + slug);
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("type", "COMPANY");
            item.put("id", company.id());
            item.put("name", company.name());
            item.put("slug", company.slug());
            item.put("logoUrl", resolvedLogoUrl(company));
            item.put("status", company.status());
            item.put("verificationStatus", company.status());
            item.put("regionId", company.regionId());
            item.put("districtId", company.districtId());
            item.put("address", company.address());
            item.put("phonePrimary", company.phonePrimary());
            item.put("phoneSecondary", company.phoneSecondary());
            item.put("website", company.website());
            item.put("companyCreatedDate", company.companyCreatedDate());
            boolean contactAvailable = hasText(company.phonePrimary()) || hasText(company.phoneSecondary())
                    || hasText(company.website()) || hasText(company.address());
            item.put("contactStatus", contactAvailable ? "AVAILABLE" : "NO_PUBLIC_FIELDS");
            if (contactAvailable) {
                Map<String, Object> contact = new LinkedHashMap<>();
                contact.put("phonePrimary", company.phonePrimary());
                contact.put("phoneSecondary", company.phoneSecondary());
                contact.put("website", company.website());
                contact.put("address", company.address());
                item.put("contact", contact);
            }
            Map<String, Object> result = new LinkedHashMap<>(item);
            result.put("kind", "business_search");
            result.put("presentation", ToolArgs.presentation(args.get("presentation")));
            result.put("items", List.of(item));
            return ToolResult.ok(result);
        } catch (GatewayNotFoundException e) {
            return ToolResult.notFound("Company not found: " + slug);
        } catch (GatewayUnavailableException e) {
            return ToolResult.error("The company service is temporarily unavailable", null);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String resolvedLogoUrl(RemoteCompanyDto company) {
        if (hasText(company.logoUrl())) return company.logoUrl();
        if (companyIndex == null) return null;
        try {
            return companyIndex.findLogoUrlBySlug(company.slug()).orElse(null);
        } catch (RuntimeException unavailable) {
            // The public detail response remains useful while the optional AI index is unavailable.
            return null;
        }
    }
}
