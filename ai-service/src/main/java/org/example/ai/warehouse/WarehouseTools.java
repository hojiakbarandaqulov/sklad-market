package org.example.ai.warehouse;

import org.example.ai.tool.AgentTool;
import org.example.ai.tool.ToolExecutionContext;
import org.example.ai.tool.ToolResult;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Three explicit seller-only tools, using the existing server-side AI registry. */
@Configuration
public class WarehouseTools {
    public enum Resource {
        WAREHOUSES("warehouse_list", "/api/v1/control/warehouses", "/warehouse"),
        INVENTORY("inventory_search", "/api/v1/control/products", "/warehouse"),
        OPERATIONS("stock_operations", "/api/v1/control/operations", "/history");

        final String kind;
        final String path;
        final String url;
        Resource(String kind, String path, String url) { this.kind = kind; this.path = path; this.url = url; }
    }

    @Bean
    AgentTool getMyWarehousesTool(WarehouseReadService service) {
        return new ReadTool("get_my_warehouses", "List the current seller's own warehouses (ombor), optionally by name. "
                + "Private operational data, not public warehouse rentals or warehouse jobs.", Resource.WAREHOUSES, service);
    }

    @Bean
    AgentTool searchMyInventoryTool(WarehouseReadService service) {
        return new ReadTool("search_my_inventory", "Search the current seller's private warehouse inventory by product name or SKU. "
                + "Read on-hand, reserved, available and minimum stock; filter LOW, OUT or AVAILABLE stock, sale or archive status. "
                + "Use for my stock, not the public catalog. Five results per page, no changes.", Resource.INVENTORY, service);
    }

    @Bean
    AgentTool getMyStockOperationsTool(WarehouseReadService service) {
        return new ReadTool("get_my_stock_operations", "Read the current seller's stock movement history: receipts, sales, returns, "
                + "write-offs or adjustments. Filter by returned warehouse/inventory IDs, search, type or ISO dates. "
                + "Five recent records per page; never performs a stock operation.", Resource.OPERATIONS, service);
    }

    private record ReadTool(String name, String description, Resource resource, WarehouseReadService service) implements AgentTool {
        @Override public Set<String> allowedRoles() { return Set.of("SELLER"); }
        @Override public Map<String, Object> parametersSchema() { return schema(resource); }
        @Override public ToolResult execute(Map<String, Object> args, ToolExecutionContext context) {
            return service.read(resource, args, context);
        }
    }

    static Map<String, Object> schema(Resource resource) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("q", Map.of("type", "STRING", "description", "Optional product/SKU or warehouse search, at most 160 characters. Never the whole conversation."));
        properties.put("page", Map.of("type", "INTEGER", "description", "1-based page, default 1. Next page only when requested."));
        properties.put("presentation", Map.of("type", "STRING", "enum", List.of("CARDS", "PLAIN_TEXT")));
        if (resource != Resource.OPERATIONS)
            properties.put("status", Map.of("type", "STRING", "enum", List.of("ACTIVE", "ARCHIVED"), "description", "Default ACTIVE."));
        if (resource != Resource.WAREHOUSES)
            properties.put("warehouseId", Map.of("type", "INTEGER", "description", "Optional warehouse ID returned by a tool or the user. Never guess."));
        if (resource == Resource.INVENTORY) {
            properties.put("stockState", Map.of("type", "STRING", "enum", List.of("ALL", "LOW", "OUT", "AVAILABLE")));
            properties.put("saleStatus", Map.of("type", "STRING", "enum", List.of("ON_SALE", "OFF_SALE")));
        }
        if (resource == Resource.OPERATIONS) {
            properties.put("inventoryProductId", Map.of("type", "INTEGER", "description", "Inventory ID, not the public catalog product ID."));
            properties.put("type", Map.of("type", "STRING", "enum", List.of("OPENING_BALANCE", "RECEIPT", "SALE", "RETURN", "WRITE_OFF", "ADJUSTMENT")));
            properties.put("dateFrom", Map.of("type", "STRING", "description", "Optional inclusive ISO date YYYY-MM-DD."));
            properties.put("dateTo", Map.of("type", "STRING", "description", "Optional inclusive ISO date YYYY-MM-DD."));
        }
        return Map.of("type", "OBJECT", "properties", properties, "required", List.of());
    }
}
