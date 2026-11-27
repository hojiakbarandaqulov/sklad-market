package org.example.ai.warehouse;

import com.fasterxml.jackson.databind.JsonNode;
import org.example.ai.gateway.GatewayNotFoundException;
import org.example.ai.gateway.GatewayUnavailableException;
import org.example.ai.gateway.dto.GatewayEnvelope;
import org.example.ai.tool.PlatformLanguage;
import org.example.ai.tool.ToolExecutionContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Private, GET-only Control adapter. No service credentials, shared caches or model-supplied URLs. */
@Component
public class WarehouseClient {
    static final int PAGE_SIZE = 5;
    private final RestClient client;

    public WarehouseClient(@Value("${ai.gateway.base-url}") String baseUrl,
            @Value("${ai.warehouse.timeout-ms:3000}") int timeout) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Math.max(100, Math.min(timeout, 5000)));
        factory.setReadTimeout(Math.max(100, Math.min(timeout, 5000)));
        client = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    /** Ownership comes from the caller's JWT, not a tool argument or public company search. */
    public long ownCompany(ToolExecutionContext context) {
        JsonNode company = get("/api/v1/companies", Map.of(), context);
        if (!positiveId(company.path("id"))) throw unavailable();
        return company.path("id").longValue();
    }

    public record Page(List<JsonNode> items, long total) {}

    public Page page(WarehouseTools.Resource resource, Map<String, String> filters,
            long companyId, ToolExecutionContext context) {
        var params = new LinkedHashMap<>(filters);
        params.put("companyId", Long.toString(companyId));
        params.put("perPage", Integer.toString(PAGE_SIZE));
        JsonNode data = get(resource.path, params, context);
        JsonNode content = data.path("content");
        JsonNode total = data.path("totalElements");
        if (!content.isArray() || content.size() > PAGE_SIZE || !total.isIntegralNumber()
                || !total.canConvertToLong() || total.longValue() < content.size()) throw unavailable();
        List<JsonNode> items = new ArrayList<>();
        for (JsonNode item : content) {
            // Control also checks ownership. Reject unexpected cross-company payloads defensively.
            if (!item.isObject() || !positiveId(item.path("id"))
                    || !positiveId(item.path("companyId")) || item.path("companyId").longValue() != companyId)
                throw unavailable();
            items.add(item);
        }
        return new Page(List.copyOf(items), total.longValue());
    }

    private JsonNode get(String path, Map<String, String> params, ToolExecutionContext context) {
        try {
            var envelope = client.get().uri(builder -> {
                builder.path(path);
                // URI variables encode reserved characters in searches, including &, + and %.
                params.keySet().forEach(key -> builder.queryParam(key, "{" + key + "}"));
                return builder.build(params);
            }).header(HttpHeaders.AUTHORIZATION, "Bearer " + context.bearerToken())
                    .header(HttpHeaders.ACCEPT_LANGUAGE, PlatformLanguage.header(context.acceptLanguage()))
                    .retrieve().body(new ParameterizedTypeReference<GatewayEnvelope<JsonNode>>() {});
            if (envelope == null || !Boolean.TRUE.equals(envelope.success())
                    || envelope.data() == null || !envelope.data().isObject()) throw unavailable();
            return envelope.data();
        } catch (HttpStatusCodeException failure) {
            if (failure.getStatusCode().is4xxClientError())
                throw new GatewayNotFoundException("Warehouse access or filters were rejected", failure);
            throw unavailable();
        } catch (RestClientException failure) {
            throw unavailable();
        }
    }

    static boolean positiveId(JsonNode node) {
        return node.isIntegralNumber() && node.canConvertToLong() && node.longValue() > 0;
    }

    private static GatewayUnavailableException unavailable() {
        return new GatewayUnavailableException("Warehouse information is temporarily unavailable", null);
    }
}
