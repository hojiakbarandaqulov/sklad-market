package com.example;

import com.example.config.client.CompanyClient;
import com.example.config.client.ProductClient;
import com.example.dto.internal.CompanyOwnershipResponse;
import com.example.entity.*;
import com.example.enums.InventoryProductStatus;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** Hisobdan chiqarish sinovlari faqat izolyatsiyalangan H2 bazasida bajariladi. */
@SpringBootTest(properties = {
        "spring.liquibase.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.datasource.url=jdbc:h2:mem:writeoffs;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver", "control.reservations.expiry-enabled=false"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StockWriteOffApiTest {
    static final String ROOT = "/api/v1/control/operations/write-offs";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired WarehouseRepository warehouses;
    @Autowired InventoryProductRepository products;
    @Autowired WarehouseStockRepository stocks;
    @MockitoSpyBean StockOperationRepository operations;
    @MockitoBean CompanyClient companies;
    @MockitoBean ProductClient cards;
    @MockitoBean JwtDecoder decoder;
    Long warehouseId, productId, stockId;

    @BeforeEach void setup() {
        operations.deleteAll();
        stocks.deleteAll();
        products.deleteAll();
        warehouses.deleteAll();
        when(decoder.decode("seller")).thenReturn(token("SELLER"));
        when(decoder.decode("buyer")).thenReturn(token("BUYER"));
        var company = new CompanyOwnershipResponse();
        company.setCompanyId(1L); company.setExists(true); company.setOwner(true); company.setActive(true);
        when(companies.ownershipCheck(1L, 42L)).thenReturn(company);
        var warehouse = new Warehouse(); warehouse.setCompanyId(1L); warehouse.setName("Asosiy ombor");
        warehouseId = warehouses.saveAndFlush(warehouse).getId();
        var product = new InventoryProduct();
        product.setCompanyId(1L); product.setCatalogCardId(100L); product.setSku("WO-1");
        productId = products.saveAndFlush(product).getId();
        var stock = new WarehouseStock(); stock.setWarehouse(warehouse); stock.setInventoryProduct(product);
        stock.setOnHand(new BigDecimal("100")); stock.setReserved(new BigDecimal("20"));
        stockId = stocks.saveAndFlush(stock).getId();
    }

    private Jwt token(String role) {
        return Jwt.withTokenValue("token").header("alg", "RS256")
                .claim("realm_access", Map.of("roles", List.of(role))).claim("profileId", 42L).build();
    }

    private String request(String key, String quantity) throws Exception {
        return json.writeValueAsString(Map.of("warehouseId", warehouseId, "inventoryProductId", productId,
                "quantity", new BigDecimal(quantity), "reason", "Mahsulot singan", "idempotencyKey", key));
    }

    private JsonNode call(String body, int status) throws Exception {
        var response = mvc.perform(post(ROOT).header("Authorization", "Bearer seller")
                .contentType("application/json").content(body)).andReturn().getResponse();
        assertThat(response.getStatus()).as(response.getContentAsString()).isEqualTo(status);
        return json.readTree(response.getContentAsString());
    }

    @Test void fractionalWriteOffPreservesReserveAndReturnsSavedOperation() throws Exception {
        var result = call(request("first", "4.875"), 200).path("data");
        var stock = stocks.findById(stockId).orElseThrow();
        var operation = operations.findAll().getFirst();
        assertThat(stock.getOnHand()).isEqualByComparingTo("95.125");
        assertThat(stock.getReserved()).isEqualByComparingTo("20");
        assertThat(result.path("id").asLong()).isEqualTo(operation.getId());
        assertThat(result.path("type").asText()).isEqualTo("WRITE_OFF");
        assertThat(result.path("quantityBefore").decimalValue()).isEqualByComparingTo("100");
        assertThat(result.path("quantityChange").decimalValue()).isEqualByComparingTo("-4.875");
        assertThat(result.path("quantityAfter").decimalValue()).isEqualByComparingTo("95.125");
        assertThat(result.path("available").decimalValue()).isEqualByComparingTo("75.125");
        assertThat(result.path("reserved").decimalValue()).isEqualByComparingTo("20");
        assertThat(result.path("createdBy").asLong()).isEqualTo(42L);
        assertThat(result.path("createdDate").isTextual()).isTrue();
        assertThat(operation.getCompanyId()).isEqualTo(1L);
        assertThat(operations.count()).isEqualTo(1);
    }

    @Test void onlyUnreservedStockCanBeWrittenOff() throws Exception {
        call(request("too-much", "80.001"), 409);
        assertThat(operations.count()).isZero();
        assertThat(stocks.findById(stockId).orElseThrow().getOnHand()).isEqualByComparingTo("100");
        call(request("all-available", "80"), 200);
        var stock = stocks.findById(stockId).orElseThrow();
        assertThat(stock.getOnHand()).isEqualByComparingTo("20");
        assertThat(stock.getReserved()).isEqualByComparingTo("20");
        call(request("reserved", "1"), 409);
        assertThat(operations.count()).isEqualTo(1);
    }

    @Test void replayReturnsOriginalSnapshotAfterOtherChanges() throws Exception {
        var first = call(request("retry", "5"), 200).path("data");
        call(request("later", "10"), 200);
        var stock = stocks.findById(stockId).orElseThrow();
        stock.setReserved(new BigDecimal("22")); stocks.saveAndFlush(stock);
        assertThat(call(request("retry", "5.000"), 200).path("data")).isEqualTo(first);
        assertThat(stocks.findById(stockId).orElseThrow().getOnHand()).isEqualByComparingTo("85");
        assertThat(operations.count()).isEqualTo(2);
    }

    @Test void changedPayloadAndAnotherOperationTypeCannotReuseKey() throws Exception {
        call(request("same-key", "5"), 200);
        call(request("same-key", "6"), 409);
        call(request("same-key", "5").replace("Mahsulot singan", "Boshqa sabab"), 409);
        var operation = operations.findAll().getFirst();
        operation.setType(com.example.enums.StockOperationType.ADJUSTMENT);
        operations.saveAndFlush(operation);
        call(request("same-key", "5"), 409);
        assertThat(operations.count()).isEqualTo(1);
        assertThat(stocks.findById(stockId).orElseThrow().getOnHand()).isEqualByComparingTo("95");
    }

    @Test void inactiveProductAndWarehouseAreRejected() throws Exception {
        var product = products.findById(productId).orElseThrow();
        product.setStatus(InventoryProductStatus.ARCHIVED); products.saveAndFlush(product);
        call(request("inactive-product", "5"), 409);
        product.setStatus(InventoryProductStatus.ACTIVE); products.saveAndFlush(product);
        var warehouse = warehouses.findById(warehouseId).orElseThrow();
        warehouse.setStatus(WarehouseStatus.ARCHIVED); warehouses.saveAndFlush(warehouse);
        call(request("inactive-warehouse", "5"), 409);
        assertThat(operations.count()).isZero();
    }

    @Test void invalidQuantitiesAndBlankReasonAreRejected() throws Exception {
        for (String quantity : List.of("0", "-1", "1.1234", "10000000000000000"))
            call(request("invalid", quantity), 400);
        call(request("blank", "5").replace("Mahsulot singan", "   "), 400);
        call("{}", 400);
        assertThat(operations.count()).isZero();
    }

    @Test void ownershipIsRequiredEvenForReplay() throws Exception {
        call(request("owned", "5"), 200);
        var company = new CompanyOwnershipResponse();
        company.setCompanyId(1L); company.setExists(true); company.setOwner(false); company.setActive(true);
        when(companies.ownershipCheck(1L, 42L)).thenReturn(company);
        call(request("owned", "5"), 403);
        assertThat(operations.count()).isEqualTo(1);
    }

    @Test void journalFailureRollsBackStockChange() throws Exception {
        doThrow(new IllegalStateException("Test journal failure")).when(operations).saveAndFlush(any(StockOperation.class));
        call(request("rollback", "5"), 500);
        assertThat(stocks.findById(stockId).orElseThrow().getOnHand()).isEqualByComparingTo("100");
        assertThat(operations.count()).isZero();
    }

    @Test void concurrentRetriesWriteOffOnlyOnce() throws Exception {
        String body = request("parallel", "5");
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Callable<Long> task = () -> { start.await(); return call(body, 200).path("data").path("id").asLong(); };
            var first = pool.submit(task); var second = pool.submit(task); start.countDown();
            assertThat(first.get(30, TimeUnit.SECONDS)).isEqualTo(second.get(30, TimeUnit.SECONDS));
            assertThat(operations.count()).isEqualTo(1);
            assertThat(stocks.findById(stockId).orElseThrow().getOnHand()).isEqualByComparingTo("95");
        } finally { pool.shutdownNow(); }
    }
}
