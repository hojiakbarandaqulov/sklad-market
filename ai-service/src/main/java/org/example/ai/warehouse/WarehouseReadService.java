package org.example.ai.warehouse;

import com.fasterxml.jackson.databind.JsonNode;
import org.example.ai.gateway.GatewayNotFoundException;
import org.example.ai.gateway.GatewayUnavailableException;
import org.example.ai.tool.ToolArgumentValidator;
import org.example.ai.tool.ToolArgumentException;
import org.example.ai.tool.ToolArgs;
import org.example.ai.tool.ToolExecutionContext;
import org.example.ai.tool.ToolResult;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class WarehouseReadService {
    private final WarehouseClient client;
    public WarehouseReadService(WarehouseClient client) { this.client = client; }

    public ToolResult read(WarehouseTools.Resource resource, Map<String, Object> args, ToolExecutionContext context) {
        // Defense in depth: direct invocation cannot bypass the registry's role filter.
        if (context == null || context.roles() == null || !context.roles().contains("SELLER")
                || context.userSub() == null || context.userSub().isBlank()
                || context.bearerToken() == null || context.bearerToken().isBlank())
            return ToolResult.error("Private warehouse information requires an authenticated seller with company access", 403);
        try {
            ToolArgumentValidator.validate(WarehouseTools.schema(resource), args);
            Map<String, String> filters = filters(resource, args);
            long companyId = client.ownCompany(context);
            var page = client.page(resource, filters, companyId, context);
            List<Map<String, Object>> items = page.items().stream().map(item -> project(resource, item)).toList();
            int requestedPage = Integer.parseInt(filters.get("page"));
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("kind", resource.kind);
            result.put("items", items);
            result.put("page", requestedPage);
            result.put("pageSize", WarehouseClient.PAGE_SIZE);
            result.put("totalAvailable", page.total());
            result.put("hasMore", (long) requestedPage * WarehouseClient.PAGE_SIZE < page.total());
            result.put("asOf", Instant.now().toString());
            result.put("url", resource.url);
            result.put("readOnly", true);
            result.put("presentation", ToolArgs.presentation(args.get("presentation")));
            return ToolResult.ok(result);
        } catch (ToolArgumentException | IllegalArgumentException | ArithmeticException | DateTimeException invalid) {
            return ToolResult.error("Invalid warehouse filters: " + invalid.getMessage(), 400);
        } catch (GatewayNotFoundException denied) {
            return ToolResult.error("Could not access warehouse information. Check seller/company access and the requested filters. This is not an empty inventory result.", 403);
        } catch (GatewayUnavailableException unavailable) {
            return ToolResult.error("Warehouse information is temporarily unavailable. Do not infer zero stock; please try again later.", 503);
        }
    }

    private Map<String, String> filters(WarehouseTools.Resource resource, Map<String, Object> args) {
        Map<String, String> params = new LinkedHashMap<>();
        for (var entry : args.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (value == null || "presentation".equals(key)) continue;
            if ("page".equals(key) || key.endsWith("Id")) {
                long id = new BigDecimal(value.toString()).longValueExact();
                if (id < 1 || ("page".equals(key) && id > 1000))
                    throw new IllegalArgumentException(key + " must be positive (page at most 1000)");
                params.put(key, Long.toString(id));
            } else {
                String text = value.toString().strip();
                if (text.length() > 160) throw new IllegalArgumentException(key + " exceeds 160 characters");
                if (!text.isEmpty()) params.put(key, text);
            }
        }
        params.putIfAbsent("page", "1");
        if (resource != WarehouseTools.Resource.OPERATIONS) params.putIfAbsent("status", "ACTIVE");
        if (resource == WarehouseTools.Resource.INVENTORY) params.putIfAbsent("stockState", "ALL");
        LocalDate from = params.containsKey("dateFrom") ? LocalDate.parse(params.get("dateFrom")) : null;
        LocalDate to = params.containsKey("dateTo") ? LocalDate.parse(params.get("dateTo")) : null;
        if (from != null && to != null && from.isAfter(to)) throw new IllegalArgumentException("dateFrom must not be after dateTo");
        return params;
    }

    private Map<String, Object> project(WarehouseTools.Resource resource, JsonNode row) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", row.path("id").longValue());
        item.put("url", resource.url);
        switch (resource) {
            case WAREHOUSES -> copy(item, row, "name", "status", "regionId", "address");
            case OPERATIONS -> copy(item, row, "warehouseId", "warehouseName", "inventoryProductId", "productId", "sku", "type",
                    "quantityBefore", "quantityChange", "quantityAfter", "reserved", "available", "createdDate", "reason");
            case INVENTORY -> {
                copy(item, row, "productId", "sku", "status", "catalogCardAvailable");
                copy(item, row.path("product"), "name");
                List<Map<String, Object>> balances = new ArrayList<>();
                JsonNode stocks = row.path("stocks");
                boolean complete = stocks.isArray() && !stocks.isEmpty();
                Map<String, BigDecimal> totals = new LinkedHashMap<>();
                for (String field : List.of("onHand", "reserved", "available", "minimumStock")) totals.put(field, BigDecimal.ZERO);
                if (stocks.isArray()) for (JsonNode stock : stocks) {
                    Map<String, Object> balance = new LinkedHashMap<>();
                    copy(balance, stock, "warehouseId", "warehouseName", "onHand", "reserved", "available", "minimumStock");
                    if (balances.size() < 10) balances.add(balance);
                    for (String field : totals.keySet()) {
                        if (!stock.path(field).isNumber()) complete = false;
                        else totals.put(field, totals.get(field).add(stock.path(field).decimalValue()));
                    }
                }
                item.put("stocks", balances);
                item.put("stockLocationsTruncated", stocks.isArray() && stocks.size() > 10);
                if (complete) item.putAll(totals); // Missing balances are unknown, never manufactured zeroes.
            }
        }
        return item;
    }

    private void copy(Map<String, Object> target, JsonNode source, String... fields) {
        for (String field : fields) {
            JsonNode value = source.path(field);
            if (value.isTextual()) target.put(field, ToolArgs.truncate(value.textValue(), 160));
            else if (value.isNumber()) target.put(field, value.decimalValue());
            else if (value.isBoolean()) target.put(field, value.booleanValue());
        }
    }
}
