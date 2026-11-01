package com.example;

import com.example.config.client.CompanyClient;
import com.example.config.client.ProductClient;
import com.example.dto.internal.CompanyOwnershipResponse;
import com.example.entity.InventoryProduct;
import com.example.entity.StockOperation;
import com.example.entity.Warehouse;
import com.example.entity.WarehouseStock;
import com.example.enums.InventoryProductStatus;
import com.example.enums.StockOperationType;
import com.example.enums.WarehouseStatus;
import com.example.repository.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/** Dashboard hisoblari va kompaniyalar orasidagi ajratish haqiqiy H2 querylari bilan tekshiriladi. */
@SpringBootTest(properties = {
        "spring.liquibase.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.datasource.url=jdbc:h2:mem:controlstats;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver", "control.reservations.expiry-enabled=false"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ControlStatisticsApiTest {
    private static final String ROOT = "/internal/control/statistics";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired InventoryProductRepository products;
    @Autowired WarehouseRepository warehouses;
    @Autowired WarehouseStockRepository stocks;
    @Autowired StockOperationRepository operations;
    @MockitoBean CompanyClient companies;
    @MockitoBean ProductClient cards;
    @MockitoBean JwtDecoder decoder;

    @BeforeEach void setup() {
        operations.deleteAll();
        stocks.deleteAll();
        products.deleteAll();
        warehouses.deleteAll();
        when(decoder.decode("seller")).thenReturn(token("SELLER"));
        when(decoder.decode("buyer")).thenReturn(token("BUYER"));
        when(companies.ownershipCheck(1L, 42L)).thenReturn(company(1L, true));
        when(companies.ownershipCheck(2L, 42L)).thenReturn(company(2L, false));
    }

    @Test void emptyCompanyReturnsZerosAndEmptyHistory() throws Exception {
        var data = call(1L, "seller", 200).path("data");
        assertThat(data.path("companyId").asLong()).isEqualTo(1);
        for (String field : List.of("productsCount", "lowStockCount", "outOfStockCount", "reservedProductsCount"))
            assertThat(data.path(field).asLong()).as(field).isZero();
        assertThat(data.path("recentOperations").isEmpty()).isTrue();
    }

    @Test void countsCombineWarehousesAndExcludeArchivedDeletedAndForeignRows() throws Exception {
        var first = warehouse(1L);
        var second = warehouse(1L);
        var enough = product(1L, "enough");
        stock(first, enough, "8", "0", "10");
        stock(second, enough, "100", "0", "10");
        var low = product(1L, "low");
        stock(first, low, "2", "1", "3");
        stock(second, low, "3", "1", "3");
        var out = product(1L, "out");
        stock(first, out, "5", "5", "0");
        stock(first, product(1L, "normal"), "100", "0", "0");

        var archived = product(1L, "archived");
        archived.setStatus(InventoryProductStatus.ARCHIVED);
        products.saveAndFlush(archived);
        stock(first, archived, "0", "0", "10");
        var deleted = product(1L, "deleted");
        deleted.setDeleted(true);
        products.saveAndFlush(deleted);
        stock(first, deleted, "0", "0", "10");
        stock(warehouse(2L), product(2L, "foreign"), "2", "1", "10");

        // O'chirilgan va arxiv omborlaridagi yozuvlar faol mahsulotning hisobini o'zgartirmaydi.
        stock(warehouse(1L), enough, "0", "0", "1000", false);
        var otherStock = stock(second, out, "100", "0", "0");
        otherStock.setDeleted(true);
        stocks.saveAndFlush(otherStock);
        var archivedWarehouse = warehouse(1L);
        archivedWarehouse.setStatus(WarehouseStatus.ARCHIVED);
        warehouses.saveAndFlush(archivedWarehouse);
        stock(archivedWarehouse, out, "100", "0", "0");
        var deletedWarehouse = warehouse(1L);
        deletedWarehouse.setDeleted(true);
        warehouses.saveAndFlush(deletedWarehouse);
        stock(deletedWarehouse, out, "100", "0", "0");

        var data = call(1L, "seller", 200).path("data");
        assertThat(data.path("productsCount").asLong()).isEqualTo(4);
        assertThat(data.path("lowStockCount").asLong()).isEqualTo(1);
        assertThat(data.path("outOfStockCount").asLong()).isEqualTo(1);
        assertThat(data.path("reservedProductsCount").asLong()).isEqualTo(2);
    }

    @Test void recentHistoryIsCompanyScopedLimitedAndIncludesLegacyOperations() throws Exception {
        var stock = stock(warehouse(1L), product(1L, "history"), "10", "0", "0");
        Long latestId = null;
        for (int index = 0; index < 12; index++) latestId = operation(stock, false).getId();
        operation(stock, true);
        operation(stock(warehouse(2L), product(2L, "foreign"), "10", "0", "0"), false);
        var data = call(1L, "seller", 200).path("data");
        var history = data.path("recentOperations");
        assertThat(history.size()).isEqualTo(10);
        assertThat(history.get(0).path("id").asLong()).isEqualTo(latestId);
        for (JsonNode row : history) {
            assertThat(row.path("inventoryProductId").asLong()).isEqualTo(stock.getInventoryProduct().getId());
            assertThat(row.path("sku").asText()).isEqualTo("history");
            assertThat(row.has("stock")).isFalse();
        }
    }

    @Test void authenticationRoleAndOwnershipAreRequired() throws Exception {
        call(1L, null, 401);
        call(1L, "buyer", 403);
        call(2L, "seller", 403);
        call(0L, "seller", 400);
    }

    @Test void invalidCompanyServiceResponseDoesNotReturnStatistics() throws Exception {
        when(companies.ownershipCheck(1L, 42L)).thenReturn(null);
        call(1L, "seller", 503);
        var mismatch = company(2L, true);
        when(companies.ownershipCheck(1L, 42L)).thenReturn(mismatch);
        call(1L, "seller", 503);
    }

    private JsonNode call(Long companyId, String bearer, int status) throws Exception {
        var request = get(ROOT).param("companyId", companyId.toString());
        if (bearer != null) request.header("Authorization", "Bearer " + bearer);
        var response = mvc.perform(request).andReturn().getResponse();
        assertThat(response.getStatus()).as(response.getContentAsString()).isEqualTo(status);
        return response.getContentAsString().isBlank() ? json.createObjectNode() : json.readTree(response.getContentAsString());
    }

    private Jwt token(String role) {
        return Jwt.withTokenValue("token").header("alg", "RS256")
                .claim("realm_access", Map.of("roles", List.of(role))).claim("profileId", 42L).build();
    }

    private CompanyOwnershipResponse company(Long id, boolean owner) {
        var result = new CompanyOwnershipResponse();
        result.setCompanyId(id); result.setExists(true); result.setOwner(owner); result.setActive(true);
        return result;
    }

    private Warehouse warehouse(Long companyId) {
        var warehouse = new Warehouse(); warehouse.setCompanyId(companyId); warehouse.setName("Ombor");
        return warehouses.saveAndFlush(warehouse);
    }

    private InventoryProduct product(Long companyId, String sku) {
        var product = new InventoryProduct(); product.setCompanyId(companyId);
        product.setCatalogCardId((long) sku.hashCode() & 0x7fffffffL); product.setSku(sku);
        return products.saveAndFlush(product);
    }

    private WarehouseStock stock(Warehouse warehouse, InventoryProduct product, String quantity, String reserved, String minimum) {
        return stock(warehouse, product, quantity, reserved, minimum, true);
    }

    private WarehouseStock stock(Warehouse warehouse, InventoryProduct product, String quantity, String reserved, String minimum, boolean active) {
        var stock = new WarehouseStock(); stock.setWarehouse(warehouse); stock.setInventoryProduct(product);
        stock.setOnHand(new BigDecimal(quantity)); stock.setReserved(new BigDecimal(reserved));
        stock.setMinimumStock(new BigDecimal(minimum)); stock.setDeleted(!active);
        return stocks.saveAndFlush(stock);
    }

    private StockOperation operation(WarehouseStock stock, boolean deleted) {
        var operation = new StockOperation(); operation.setStock(stock);
        operation.setType(StockOperationType.OPENING_BALANCE);
        operation.setQuantityBefore(BigDecimal.ZERO); operation.setQuantityChange(BigDecimal.ONE);
        operation.setQuantityAfter(BigDecimal.ONE); operation.setDeleted(deleted);
        // companyId ataylab null: ilgari yozilgan boshlang'ich qoldiqlar ham tarixda chiqishi kerak.
        return operations.saveAndFlush(operation);
    }
}
