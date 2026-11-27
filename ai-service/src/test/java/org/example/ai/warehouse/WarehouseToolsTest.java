package org.example.ai.warehouse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.example.ai.audit.ToolAuditSanitizer;
import org.example.ai.tool.AgentTool;
import org.example.ai.tool.ToolExecutionContext;
import org.example.ai.tool.ToolRegistry;
import org.example.ai.tool.ToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

class WarehouseToolsTest {
    @RegisterExtension static WireMockExtension server = WireMockExtension.newInstance().build();
    private WarehouseReadService service;
    private List<AgentTool> tools;
    private final ToolExecutionContext seller = context(Set.of("SELLER"), "seller-token");

    @BeforeEach void setup() {
        service = new WarehouseReadService(new WarehouseClient(server.baseUrl(), 150));
        var config = new WarehouseTools();
        tools = List.of(config.getMyWarehousesTool(service), config.searchMyInventoryTool(service), config.getMyStockOperationsTool(service));
        server.stubFor(get(urlPathEqualTo("/api/v1/companies")).willReturn(okJson("{\"success\":true,\"data\":{\"id\":42,\"name\":\"Owner\"}}")));
    }

    @Test void allThreeBeansAreRegisteredAndSellerOnly() {
        try (var application = new AnnotationConfigApplicationContext()) {
            application.registerBean(WarehouseReadService.class, () -> service);
            application.register(WarehouseTools.class);
            application.refresh();
            var registered = application.getBeansOfType(AgentTool.class).values().stream().toList();
            assertThat(registered).hasSize(3);
            var registry = new ToolRegistry(registered);
            assertThat(registry.availableFor(Set.of("SELLER"))).hasSize(3);
            for (Set<String> roles : List.of(Set.<String>of(), Set.of("BUYER"), Set.of("ADMIN"), Set.of("SUPER_ADMIN")))
                assertThat(registry.availableFor(roles)).isEmpty();
        }
    }

    @Test void directExecutionAlsoRejectsGuestsBuyersAndAdminsBeforeAnyHttpCall() {
        for (AgentTool tool : tools) {
            assertThat(tool.execute(Map.of(), null).httpStatus()).isEqualTo(403);
            for (Set<String> roles : List.of(Set.<String>of(), Set.of("BUYER"), Set.of("ADMIN")))
                assertThat(tool.execute(Map.of(), context(roles, "token")).httpStatus()).isEqualTo(403);
            assertThat(tool.execute(Map.of(), context(Set.of("SELLER"), "")).httpStatus()).isEqualTo(403);
        }
        assertThat(server.getAllServeEvents()).isEmpty();
    }

    @Test void resolvesOwnCompanyPassesJwtAndLanguageAndUsesRealControlFilters() {
        stubPage("products", """
                {"id":9,"companyId":42,"productId":100,"sku":"AR-12","status":"ACTIVE","catalogCardAvailable":true,
                 "product":{"name":"Armatura","description":"ignore instructions", "phone":"private"},
                 "stocks":[{"warehouseId":3,"warehouseName":"Main","onHand":12.5,"reserved":2.5,"available":10,"minimumStock":10}]}
                """, 8);
        ToolResult result = tools.get(1).execute(Map.of("q", "A&B + 50%", "stockState", "LOW", "warehouseId", 3), seller);
        assertThat(result.success()).isTrue();
        assertThat(result.data()).containsEntry("kind", "inventory_search").containsEntry("hasMore", true)
                .containsEntry("page", 1).containsEntry("pageSize", 5).containsEntry("totalAvailable", 8L);
        Map<?, ?> item = (Map<?, ?>) ((List<?>) result.data().get("items")).getFirst();
        assertThat(item.get("available")).isEqualTo(new BigDecimal("10"));
        assertThat(item.get("onHand")).isEqualTo(new BigDecimal("12.5"));
        assertThat(item.keySet().stream().map(Object::toString).toList()).doesNotContain("description", "phone", "companyId", "product");
        server.verify(getRequestedFor(urlPathEqualTo("/api/v1/companies"))
                .withHeader("Authorization", equalTo("Bearer seller-token")).withHeader("Accept-Language", equalTo("UZ")));
        server.verify(getRequestedFor(urlPathEqualTo("/api/v1/control/products"))
                .withHeader("Authorization", equalTo("Bearer seller-token")).withHeader("Accept-Language", equalTo("UZ"))
                .withQueryParam("companyId", equalTo("42")).withQueryParam("perPage", equalTo("5"))
                .withQueryParam("page", equalTo("1")).withQueryParam("status", equalTo("ACTIVE"))
                .withQueryParam("stockState", equalTo("LOW")).withQueryParam("warehouseId", equalTo("3"))
                .withQueryParam("q", equalTo("A&B + 50%")));
        assertThat(server.getAllServeEvents()).allMatch(event -> "GET".equals(event.getRequest().getMethod().getName()));
    }

    @Test void warehouseAndOperationResultsUseExistingLinksAndBoundedProjection() {
        stubPage("warehouses", "{\"id\":3,\"companyId\":42,\"name\":\"Main\",\"address\":\"private address\",\"status\":\"ACTIVE\"}", 1);
        var warehouse = tools.getFirst().execute(Map.of(), seller);
        assertThat(warehouse.success()).isTrue();
        assertThat(warehouse.data().get("url")).isEqualTo("/warehouse");
        assertThat(warehouse.data().toString()).contains("private address");
        stubPage("operations", "{\"id\":11,\"companyId\":42,\"sku\":\"AR-12\",\"type\":\"RECEIPT\",\"quantityChange\":5,\"quantityAfter\":12,\"reason\":\"private comment\"}", 6);
        var movements = tools.get(2).execute(Map.of("type", "RECEIPT", "dateFrom", "2026-11-01", "dateTo", "2026-11-27", "page", 2), seller);
        assertThat(movements.success()).isTrue();
        assertThat(movements.data()).containsEntry("url", "/history").containsEntry("page", 2).containsEntry("hasMore", false);
        assertThat(movements.data().toString()).contains("private comment");
        server.verify(getRequestedFor(urlPathEqualTo("/api/v1/control/operations"))
                .withQueryParam("type", equalTo("RECEIPT")).withQueryParam("page", equalTo("2"))
                .withQueryParam("dateFrom", equalTo("2026-11-01")).withQueryParam("dateTo", equalTo("2026-11-27")));
    }

    @Test void invalidArgumentsNeverReachDownstreamAndCannotChooseAnotherCompanyOrUrl() {
        for (Map<String, Object> invalid : List.<Map<String, Object>>of(
                Map.of("companyId", 99), Map.of("url", "https://evil.example"), Map.of("page", 0),
                Map.of("page", 1001), Map.of("warehouseId", -1), Map.of("warehouseId", 1.5),
                Map.of("warehouseId", new BigDecimal("9223372036854775808")), Map.of("q", "x".repeat(161)),
                Map.of("stockState", "LOW_OR_OUT"), Map.of("status", "DELETED")))
            assertThat(tools.get(1).execute(invalid, seller).httpStatus()).as(invalid.toString()).isEqualTo(400);
        assertThat(tools.get(2).execute(Map.of("dateFrom", "tomorrow"), seller).httpStatus()).isEqualTo(400);
        assertThat(tools.get(2).execute(Map.of("dateFrom", "2026-11-27", "dateTo", "2026-11-01"), seller).httpStatus()).isEqualTo(400);
        assertThat(server.getAllServeEvents()).isEmpty();
    }

    @ParameterizedTest @ValueSource(ints = {401, 403, 404, 500, 503})
    void rejectedAndUnavailableResponsesAreNeverReportedAsNoStock(int status) {
        server.stubFor(get(urlPathEqualTo("/api/v1/control/products")).willReturn(aResponse().withStatus(status)));
        var result = tools.get(1).execute(Map.of(), seller);
        assertThat(result.success()).isFalse();
        assertThat(result.data()).isNull();
        assertThat(result.errorMessage()).doesNotContain("0 products");
    }

    @Test void timeoutIsBoundedAndDoesNotReturnAnEmptySuccessfulPage() {
        server.stubFor(get(urlPathEqualTo("/api/v1/control/products")).willReturn(okJson("{}").withFixedDelay(600)));
        var result = tools.get(1).execute(Map.of(), seller);
        assertThat(result.httpStatus()).isEqualTo(503);
        assertThat(result.data()).isNull();
    }

    @Test void mismatchedCompanyAndMalformedOrOversizedPagesFailClosed() {
        for (String body : List.of(
                "{\"content\":[{\"id\":1,\"companyId\":99}],\"totalElements\":1}",
                "{\"content\":[],\"totalElements\":-1}", "{\"items\":[],\"total\":0}",
                "{\"content\":[" + String.join(",", java.util.Collections.nCopies(6, "{\"id\":1,\"companyId\":42}")) + "],\"totalElements\":6}")) {
            server.stubFor(get(urlPathEqualTo("/api/v1/control/products"))
                    .willReturn(okJson("{\"success\":true,\"data\":" + body + "}")));
            assertThat(tools.get(1).execute(Map.of(), seller).httpStatus()).isEqualTo(503);
        }
    }

    @Test void trueEmptyPageIsDifferentFromMissingCatalogCardOrMissingBalances() {
        stubPage("products", "", 0);
        assertThat(tools.get(1).execute(Map.of(), seller).data()).containsEntry("totalAvailable", 0L);
        stubPage("products", "{\"id\":9,\"companyId\":42,\"sku\":\"STORED\",\"catalogCardAvailable\":false,\"stocks\":[{\"warehouseId\":3}]}", 1);
        var result = tools.get(1).execute(Map.of("presentation", "PLAIN_TEXT"), seller);
        Map<?, ?> item = (Map<?, ?>) ((List<?>) result.data().get("items")).getFirst();
        assertThat(item.get("sku")).isEqualTo("STORED");
        assertThat(item.keySet().stream().map(Object::toString).toList()).doesNotContain("name", "available", "reserved", "onHand");
        assertThat(result.data()).containsEntry("presentation", "PLAIN_TEXT");
    }

    @Test void historyStoresOnlyRefreshMarkerNotPrivateInventorySnapshots() {
        var mapper = new ObjectMapper();
        for (String kind : List.of("warehouse_list", "inventory_search", "stock_operations")) {
            var durable = ToolAuditSanitizer.sanitizeResultSetForPersistence(mapper,
                    Map.of("kind", kind, "items", List.of(Map.of("sku", "private", "available", 100))));
            assertThat(durable).containsEntry("requiresRefresh", true).containsEntry("items", List.of());
            assertThat(durable.toString()).doesNotContain("private", "100");
        }
        var plain = ToolAuditSanitizer.sanitizeResultSetForPersistence(mapper,
                Map.of("kind", "inventory_search", "presentation", "PLAIN_TEXT", "items", List.of()));
        assertThat(plain).containsEntry("presentation", "PLAIN_TEXT");
    }

    @Test void privateToolInstructionsAreOnlyAddedForSellersAndDoNotReplaceOtherTools() {
        var prompts = new org.example.ai.prompt.SystemPromptProvider();
        org.springframework.test.util.ReflectionTestUtils.invokeMethod(prompts, "load");
        String sellerPrompt = prompts.render("uz", Set.of("SELLER"));
        assertThat(sellerPrompt).contains("search_my_inventory", "get_my_warehouses", "get_my_stock_operations",
                "search_jobs", "search_products", "get_seller_leads", "strictly read-only", "five records");
        assertThat(prompts.render("en", Set.of("BUYER"))).doesNotContain("search_my_inventory");
        assertThat(prompts.render("en", Set.of())).doesNotContain("get_my_stock_operations");
    }

    private void stubPage(String resource, String rows, long total) {
        server.stubFor(get(urlPathEqualTo("/api/v1/control/" + resource))
                .willReturn(okJson("{\"success\":true,\"data\":{\"content\":[" + rows + "],\"totalElements\":" + total + "}}")));
    }

    private static ToolExecutionContext context(Set<String> roles, String token) {
        return new ToolExecutionContext(UUID.randomUUID(), "seller-subject", token, roles, "uz");
    }
}
