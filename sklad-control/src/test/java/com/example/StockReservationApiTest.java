package com.example;

import com.example.config.client.CompanyClient;
import com.example.config.client.ProductClient;
import com.example.dto.internal.CompanyOwnershipResponse;
import com.example.entity.*;
import com.example.enums.ReservationStatus;
import com.example.enums.StockOperationType;
import com.example.repository.*;
import com.example.service.ReservationExpiryJob;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

// Haqiqiy controller, Liquibase migratsiyasi, Hibernate validate va tranzaksiyalar tekshiriladi.
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:control-reservations;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "spring.datasource.username=sa", "spring.datasource.password=", "spring.datasource.driver-class-name=org.h2.Driver",
        "control.reservations.expiry-delay-ms=3600000"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StockReservationApiTest {
    static final String ROOT = "/api/v1/control/reservations";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired StockReservationRepository reservations;
    @Autowired StockReservationItemRepository items;
    @Autowired WarehouseRepository warehouses;
    @Autowired WarehouseStockRepository stocks;
    @Autowired InventoryProductRepository products;
    @Autowired ReservationExpiryJob expiryJob;
    @MockitoSpyBean StockOperationRepository operations;
    @MockitoBean CompanyClient companies;
    @MockitoBean ProductClient cards;
    @MockitoBean JwtDecoder decoder;
    @MockitoBean Clock clock;
    AtomicReference<Instant> now;
    Long warehouseId, productId, stockId;
    Instant expiresAt;

    @BeforeEach
    void setup() {
        operations.deleteAll(); items.deleteAll(); reservations.deleteAll();
        stocks.deleteAll(); products.deleteAll(); warehouses.deleteAll();
        now = new AtomicReference<>(Instant.parse("2026-10-29T10:00:00Z"));
        expiresAt = now.get().plusSeconds(600);
        when(clock.instant()).thenAnswer(call -> now.get());
        when(decoder.decode("seller")).thenReturn(token("seller", "SELLER"));
        when(decoder.decode("buyer")).thenReturn(token("buyer", "BUYER"));
        var company = new CompanyOwnershipResponse();
        company.setCompanyId(1L); company.setExists(true); company.setOwner(true); company.setActive(true);
        when(companies.ownershipCheck(1L, 42L)).thenReturn(company);
        Warehouse warehouse = new Warehouse();
        warehouse.setCompanyId(1L); warehouse.setName("Asosiy ombor");
        warehouseId = warehouses.saveAndFlush(warehouse).getId();
        var stock = addStock("SKU-1", "10.500");
        stockId = stock.getId(); productId = stock.getInventoryProduct().getId();
    }

    @Test
    void createReadAndCommitChangeStockOnceAndWriteLinkedLedger() throws Exception {
        long id = create("key-1", "2.125");
        assertBalance("10.500", "2.125", "8.375");
        JsonNode data = body(call(get(ROOT + "/" + id), 200));
        assertThat(data.path("items").get(0).path("quantity").decimalValue()).isEqualByComparingTo("2.125");
        call(post(ROOT + "/" + id + "/commit"), 200);
        call(post(ROOT + "/" + id + "/commit"), 200);
        assertBalance("8.375", "0", "8.375");
        assertThat(operations.count()).isEqualTo(1);
        var entry = operations.findAll().get(0);
        assertThat(entry.getReservationId()).isEqualTo(id);
        assertThat(entry.getType()).isEqualTo(StockOperationType.SALE);
        assertThat(entry.getQuantityBefore()).isEqualByComparingTo("10.500");
        assertThat(entry.getQuantityChange()).isEqualByComparingTo("-2.125");
        assertThat(entry.getQuantityAfter()).isEqualByComparingTo("8.375");
        assertThat(entry.getCreatedBy()).isEqualTo(42L);
        call(post(ROOT + "/" + id + "/release"), 409);
    }

    @Test
    void releaseOnlyFreesReservedStockAndCannotBeCommitted() throws Exception {
        long id = create("release", "2");
        call(post(ROOT + "/" + id + "/release"), 200);
        call(post(ROOT + "/" + id + "/release"), 200);
        assertBalance("10.500", "0", "10.500");
        assertThat(operations.count()).isZero();
        assertThat(reservations.findById(id).orElseThrow().getStatus()).isEqualTo(ReservationStatus.RELEASED);
        call(post(ROOT + "/" + id + "/commit"), 409);
    }

    @Test
    void idempotentCreateRejectsReusingKeyWithDifferentQuantity() throws Exception {
        long id = create("retry", "2.125");
        assertThat(create("retry", "2.125")).isEqualTo(id);
        call(post(ROOT).contentType("application/json").content(request("retry", "3")), 409);
        assertThat(reservations.count()).isEqualTo(1);
        assertBalance("10.500", "2.125", "8.375");
    }

    @Test
    void insufficientSecondItemRollsBackTheEntireReservation() throws Exception {
        var second = addStock("SKU-2", "1");
        String payload = request("atomic", "2");
        payload = payload.substring(0, payload.length() - 2) + "," + position(second.getInventoryProduct().getId(), "2") + "]}";
        call(post(ROOT).contentType("application/json").content(payload), 409);
        assertThat(reservations.count()).isZero();
        assertThat(items.count()).isZero();
        assertBalance("10.500", "0", "10.500");
    }

    @Test
    void journalFailureRollsBackCommitAndBalances() throws Exception {
        long id = create("journal-fail", "2");
        doThrow(new IllegalStateException("Test journal failure")).when(operations).save(any(StockOperation.class));
        call(post(ROOT + "/" + id + "/commit"), 500);
        assertBalance("10.500", "2", "8.500");
        assertThat(reservations.findById(id).orElseThrow().getStatus()).isEqualTo(ReservationStatus.ACTIVE);
        assertThat(operations.count()).isZero();
    }

    @Test
    void expiredReservationCannotCreateAStockIssueEvenBeforeTheSchedulerRuns() throws Exception {
        long id = create("expired", "2");
        now.set(expiresAt);
        JsonNode response = body(call(post(ROOT + "/" + id + "/commit"), 200));
        assertThat(response.path("status").asText()).isEqualTo("EXPIRED");
        assertBalance("10.500", "0", "10.500");
        assertThat(operations.count()).isZero();
    }

    @Test
    void schedulerFreesExpiredStockWithoutAUserRequest() throws Exception {
        long id = create("scheduled", "2");
        now.set(expiresAt.plusSeconds(1));
        expiryJob.releaseExpired(); expiryJob.releaseExpired();
        assertBalance("10.500", "0", "10.500");
        assertThat(reservations.findById(id).orElseThrow().getStatus()).isEqualTo(ReservationStatus.EXPIRED);
    }

    @Test
    void validationAndAuthorizationAreAppliedToAllEndpoints() throws Exception {
        call(post(ROOT).contentType("application/json").content(request("invalid", "0")), 400);
        call(post(ROOT).contentType("application/json").content(request("past", "1").replace(expiresAt.toString(), now.get().toString())), 400);
        String item = position(productId, "1");
        call(post(ROOT).contentType("application/json").content(request("duplicate", "1").replace("[" + item + "]", "[" + item + "," + item + "]")), 400);
        mvc.perform(post(ROOT).header("Authorization", "Bearer buyer").contentType("application/json").content(request("buyer", "1")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
        mvc.perform(get(ROOT + "/1")).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isUnauthorized());
        long id = create("owner", "1");
        var foreign = new CompanyOwnershipResponse();
        foreign.setCompanyId(1L); foreign.setExists(true); foreign.setActive(true);
        when(companies.ownershipCheck(1L, 42L)).thenReturn(foreign);
        call(get(ROOT + "/" + id), 403);
        call(post(ROOT + "/" + id + "/commit"), 403);
        call(post(ROOT + "/" + id + "/release"), 403);
        call(post(ROOT).contentType("application/json").content(request("foreign", "1")), 403);
        assertBalance("10.500", "1", "9.500");
    }

    @Test
    void severalProductsCanBeReservedAndCommittedTogether() throws Exception {
        var second = addStock("SKU-2", "4");
        String payload = request("multi", "2");
        payload = payload.substring(0, payload.length() - 2) + "," + position(second.getInventoryProduct().getId(), "1.250") + "]}";
        long id = body(call(post(ROOT).contentType("application/json").content(payload), 200)).path("id").asLong();
        assertThat(items.count()).isEqualTo(2);
        call(post(ROOT + "/" + id + "/commit"), 200);
        assertBalance("8.500", "0", "8.500");
        var other = stocks.findById(second.getId()).orElseThrow();
        assertThat(other.getOnHand()).isEqualByComparingTo("2.750");
        assertThat(other.getReserved()).isEqualByComparingTo("0");
        assertThat(operations.count()).isEqualTo(2);
    }

    @Test
    void legacyEndpointAlsoReservesOnceAndReusesItsExpiryOnRetry() throws Exception {
        String payload = "{\"warehouseId\":" + warehouseId + ",\"inventoryProductId\":" + productId
                + ",\"quantity\":2,\"reason\":\"Mijoz uchun\",\"idempotencyKey\":\"legacy\"}";
        var first = body(call(post("/api/v1/control/operations/receipts").contentType("application/json").content(payload), 200));
        now.set(now.get().plusSeconds(60));
        var retry = body(call(post("/api/v1/control/operations/receipts").contentType("application/json").content(payload), 200));
        assertThat(retry.path("id")).isEqualTo(first.path("id"));
        assertBalance("10.500", "2", "8.500");
        assertThat(reservations.count()).isEqualTo(1);
    }

    @Test
    void differentReservationsOnTheSameStockCanCommitConcurrently() throws Exception {
        long first = create("commit-a", "3");
        long second = create("commit-b", "4");
        var responses = race(
                () -> mvc.perform(post(ROOT + "/" + first + "/commit").header("Authorization", "Bearer seller")).andReturn(),
                () -> mvc.perform(post(ROOT + "/" + second + "/commit").header("Authorization", "Bearer seller")).andReturn());
        assertThat(responses.stream().map(r -> r.getResponse().getStatus()).toList()).containsOnly(200);
        assertBalance("3.500", "0", "3.500");
        assertThat(operations.count()).isEqualTo(2);
    }

    @Test
    void concurrentReservationsCannotOverbookStock() throws Exception {
        var responses = race(
                () -> mvc.perform(post(ROOT).header("Authorization", "Bearer seller").contentType("application/json").content(request("race-a", "7"))).andReturn(),
                () -> mvc.perform(post(ROOT).header("Authorization", "Bearer seller").contentType("application/json").content(request("race-b", "7"))).andReturn());
        assertThat(responses.stream().map(r -> r.getResponse().getStatus()).toList()).containsExactlyInAnyOrder(200, 409);
        assertBalance("10.500", "7", "3.500");
    }

    @Test
    void concurrentIdenticalCreatesReturnTheSameReservation() throws Exception {
        var responses = race(
                () -> mvc.perform(post(ROOT).header("Authorization", "Bearer seller").contentType("application/json").content(request("same", "2"))).andReturn(),
                () -> mvc.perform(post(ROOT).header("Authorization", "Bearer seller").contentType("application/json").content(request("same", "2"))).andReturn());
        assertThat(responses.stream().map(r -> r.getResponse().getStatus()).toList()).containsOnly(200);
        assertThat(body(responses.get(0)).path("id")).isEqualTo(body(responses.get(1)).path("id"));
        assertBalance("10.500", "2", "8.500");
        assertThat(reservations.count()).isEqualTo(1);
    }

    @Test
    void concurrentCommitAndReleaseCanOnlyFinalizeOnce() throws Exception {
        long id = create("finalize-race", "2");
        var responses = race(
                () -> mvc.perform(post(ROOT + "/" + id + "/commit").header("Authorization", "Bearer seller")).andReturn(),
                () -> mvc.perform(post(ROOT + "/" + id + "/release").header("Authorization", "Bearer seller")).andReturn());
        assertThat(responses.stream().map(r -> r.getResponse().getStatus()).toList()).containsExactlyInAnyOrder(200, 409);
        if (reservations.findById(id).orElseThrow().getStatus() == ReservationStatus.COMMITTED) {
            assertBalance("8.500", "0", "8.500"); assertThat(operations.count()).isEqualTo(1);
        } else {
            assertBalance("10.500", "0", "10.500"); assertThat(operations.count()).isZero();
        }
    }

    private List<MvcResult> race(Callable<MvcResult> first, Callable<MvcResult> second) throws Exception {
        var start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            Future<MvcResult> one = pool.submit(() -> { start.await(); return first.call(); });
            Future<MvcResult> two = pool.submit(() -> { start.await(); return second.call(); });
            start.countDown();
            return List.of(one.get(20, TimeUnit.SECONDS), two.get(20, TimeUnit.SECONDS));
        } finally { pool.shutdownNow(); }
    }

    private WarehouseStock addStock(String sku, String quantity) {
        InventoryProduct product = new InventoryProduct();
        product.setCompanyId(1L); product.setCatalogCardId(100L + products.count()); product.setSku(sku);
        products.saveAndFlush(product);
        WarehouseStock stock = new WarehouseStock();
        stock.setWarehouse(warehouses.findById(warehouseId).orElseThrow()); stock.setInventoryProduct(product);
        stock.setOnHand(new BigDecimal(quantity));
        return stocks.saveAndFlush(stock);
    }
    private long create(String key, String quantity) throws Exception {
        return body(call(post(ROOT).contentType("application/json").content(request(key, quantity)), 200)).path("id").asLong();
    }
    private String request(String key, String quantity) {
        return "{\"companyId\":1,\"idempotencyKey\":\"" + key + "\",\"expiresAt\":\"" + expiresAt + "\",\"reason\":\"Mijoz uchun\",\"items\":[" + position(productId, quantity) + "]}";
    }
    private String position(Long id, String quantity) {
        return "{\"warehouseId\":" + warehouseId + ",\"inventoryProductId\":" + id + ",\"quantity\":" + quantity + "}";
    }
    private MvcResult call(MockHttpServletRequestBuilder request, int expected) throws Exception {
        MvcResult result = mvc.perform(request.header("Authorization", "Bearer seller")).andReturn();
        assertThat(result.getResponse().getStatus()).as(result.getResponse().getContentAsString()).isEqualTo(expected);
        return result;
    }
    private JsonNode body(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString()).path("data");
    }
    private void assertBalance(String onHand, String reserved, String available) {
        WarehouseStock stock = stocks.findById(stockId).orElseThrow();
        assertThat(stock.getOnHand()).isEqualByComparingTo(onHand);
        assertThat(stock.getReserved()).isEqualByComparingTo(reserved);
        assertThat(stock.getAvailable()).isEqualByComparingTo(available);
    }
    private Jwt token(String value, String role) {
        return Jwt.withTokenValue(value).header("alg", "RS256").subject(value)
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(600))
                .claim("profileId", 42L).claim("realm_access", Map.of("roles", List.of(role))).build();
    }
}
