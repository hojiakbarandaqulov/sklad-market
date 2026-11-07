package com.example;

import com.example.config.client.CompanyClient;
import com.example.config.client.ProductClient;
import com.example.entity.*;
import com.example.enums.*;
import com.example.repository.*;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.*;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:order-reservations;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "spring.jpa.hibernate.ddl-auto=validate", "control.reservations.expiry-enabled=false"
})
@AutoConfigureMockMvc @ActiveProfiles("test")
class OrderReservationApiTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired StockReservationRepository reservations;
    @Autowired StockReservationItemRepository items;
    @Autowired WarehouseRepository warehouses;
    @Autowired InventoryProductRepository products;
    @Autowired WarehouseStockRepository stocks;
    @MockitoSpyBean StockOperationRepository operations;
    @MockitoBean JwtDecoder decoder;
    @MockitoBean CompanyClient companies;
    @MockitoBean ProductClient cards;
    @MockitoBean Clock clock;
    Instant now = Instant.parse("2026-11-02T10:00:00Z");
    Long firstStock, secondStock;

    @BeforeEach void setup() {
        operations.deleteAll(); items.deleteAll(); reservations.deleteAll(); stocks.deleteAll(); products.deleteAll(); warehouses.deleteAll();
        when(clock.instant()).thenReturn(now);
        for (String role : List.of("CONTROL_ORDER_WRITE", "SELLER", "BUYER", "CONTROL_AVAILABILITY_READ"))
            when(decoder.decode(role)).thenReturn(Jwt.withTokenValue(role).header("alg", "RS256").subject(role)
                    .claim("realm_access", Map.of("roles", List.of(role))).build());
        var product = product(1L, 100L, "SKU-100");
        firstStock = stock(warehouse(1L), product, "2.500").getId();
        secondStock = stock(warehouse(1L), product, "4.750").getId();
    }

    @Test void allocatesAcrossWarehousesAndCommitsExactlyOnceWithOrderLinkedLedger() throws Exception {
        var data = data(call(post(root(10)).content(payload("6.125")), 200));
        assertThat(data.path("orderId").asLong()).isEqualTo(10);
        assertThat(data.path("items").size()).isEqualTo(2);
        balance(firstStock, "2.500", "2.500"); balance(secondStock, "4.750", "3.625");
        call(post(root(10)).content(payload("6.125")), 200);
        assertThat(reservations.count()).isEqualTo(1); assertThat(items.count()).isEqualTo(2);
        call(post(root(10)+"/commit?companyId=1"), 200); call(post(root(10)+"/commit?companyId=1"), 200);
        balance(firstStock, "0", "0"); balance(secondStock, "1.125", "0");
        assertThat(operations.count()).isEqualTo(2);
        assertThat(operations.findAll()).allSatisfy(o -> {
            assertThat(o.getType()).isEqualTo(StockOperationType.SALE); assertThat(o.getOrderId()).isEqualTo(10L);
            assertThat(o.getReservationId()).isEqualTo(data.path("id").asLong());
        });
        // Masofaviy holat yo'qolmaydi: bekor qilish COMMITTED natijasini qaytaradi, chiqimni qaytarmaydi.
        assertThat(data(call(post(root(10)+"/release?companyId=1"), 200)).path("status").asText()).isEqualTo("COMMITTED");
    }

    @Test void releaseIsIdempotentAndDoesNotReducePhysicalStock() throws Exception {
        call(post(root(11)).content(payload("3")), 200);
        call(post(root(11)+"/release?companyId=1"), 200); call(post(root(11)+"/release?companyId=1"), 200);
        balance(firstStock, "2.500", "0"); balance(secondStock, "4.750", "0"); assertThat(operations.count()).isZero();
        assertThat(data(call(post(root(11)+"/commit?companyId=1"), 200)).path("status").asText()).isEqualTo("RELEASED");
    }

    @Test void checkoutIncludesOwnReserveAndSaleCanReduceQuantityExactlyOnce() throws Exception {
        call(post(root(41)).content(payload("6.125")),200);
        var availability=call(get(root(41)+"/availability?companyId=1"),200);
        var line=json.readTree(availability.getResponse().getContentAsString()).get(0);
        assertThat(line.path("maxSaleQuantity").decimalValue()).isEqualByComparingTo("7.250");
        assertThat(line.path("reservedQuantity").decimalValue()).isEqualByComparingTo("6.125");
        String sale="{\"companyId\":1,\"items\":[{\"productId\":100,\"quantity\":3}]}";
        call(post(root(41)+"/complete").content(sale),200);
        call(post(root(41)+"/complete").content(sale),200);
        balance(firstStock,"0","0"); balance(secondStock,"4.250","0");
        assertThat(operations.count()).isEqualTo(2);
        call(post(root(41)+"/complete").content(sale.replace("quantity\":3","quantity\":4")),409);
        balance(secondStock,"4.250","0");
    }

    @Test void saleShortageRollsBackReallocationAndIncreaseUsesOnlyFreeStock() throws Exception {
        call(post(root(42)).content(payload("2")),200);
        String sale="{\"companyId\":1,\"items\":[{\"productId\":100,\"quantity\":20}]}";
        call(post(root(42)+"/complete").content(sale),409);
        balance(firstStock,"2.500","2"); balance(secondStock,"4.750","0");
        assertThat(operations.count()).isZero();
        call(post(root(42)+"/complete").content(sale.replace("quantity\":20","quantity\":5")),200);
        balance(firstStock,"0","0"); balance(secondStock,"2.250","0");
    }

    @Test void insufficientAnyProductRollsBackWholeReservation() throws Exception {
        stock(warehouse(1L), product(1L, 101L, "SKU-101"), "1");
        var request = json.readTree(payload("2"));
        ((com.fasterxml.jackson.databind.node.ArrayNode)request.path("items")).add(json.readTree("{\"productId\":101,\"quantity\":2}"));
        call(post(root(12)).content(json.writeValueAsString(request)), 409);
        assertThat(reservations.count()).isZero(); assertThat(items.count()).isZero();
        balance(firstStock, "2.500", "0"); balance(secondStock, "4.750", "0");
    }

    @Test void ignoresForeignArchivedAndDeletedStocks() throws Exception {
        var own = products.findAll().getFirst();
        var archived = warehouse(1L); archived.setStatus(WarehouseStatus.ARCHIVED); warehouses.saveAndFlush(archived);
        stock(archived, own, "100"); stock(warehouse(2L), own, "100");
        var deleted = stock(warehouse(1L), own, "100"); deleted.setDeleted(true); stocks.saveAndFlush(deleted);
        call(post(root(13)).content(payload("8")), 409);
        assertThat(reservations.count()).isZero();
        call(post(root(13)).content(payload("7.250")), 200);
        assertThat(items.count()).isEqualTo(2);
    }

    @Test void archivedOrUntrackedProductCannotBeReserved() throws Exception {
        var own = products.findAll().getFirst(); own.setStatus(InventoryProductStatus.ARCHIVED); products.saveAndFlush(own);
        call(post(root(14)).content(payload("1")), 409);
        call(post(root(14)).content(payload("1").replace("100", "999")), 409);
        assertThat(reservations.count()).isZero();
    }

    @Test void sameOrderCannotBeReusedForDifferentBuyerQuantityOrExpiry() throws Exception {
        call(post(root(15)).content(payload("2")), 200);
        call(post(root(15)).content(payload("3")), 409);
        call(post(root(15)).content(payload("2").replace("\"buyerId\":7", "\"buyerId\":8")), 409);
        var altered = json.readTree(payload("2")); ((com.fasterxml.jackson.databind.node.ObjectNode)altered).put("expiresAt", now.plusSeconds(700).toString());
        call(post(root(15)).content(json.writeValueAsString(altered)), 409);
        balance(firstStock, "2.500", "2");
    }

    @Test void expiryReleasesStockAndNeverCreatesSale() throws Exception {
        call(post(root(16)).content(payload("3")), 200);
        when(clock.instant()).thenReturn(now.plusSeconds(601));
        assertThat(data(call(post(root(16)+"/commit?companyId=1"), 200)).path("status").asText()).isEqualTo("EXPIRED");
        balance(firstStock, "2.500", "0"); balance(secondStock, "4.750", "0"); assertThat(operations.count()).isZero();
        call(post(root(16)).content(payload("3")), 200);
        call(post(root(17)).content(payload("3")), 400);
    }

    @Test void journalFailureRollsBackAllStockAndReservationChanges() throws Exception {
        call(post(root(18)).content(payload("3")), 200);
        doThrow(new IllegalStateException("test ledger failure")).when(operations).save(any(StockOperation.class));
        call(post(root(18)+"/commit?companyId=1"), 500);
        balance(firstStock, "2.500", "2.500"); balance(secondStock, "4.750", ".500");
        assertThat(reservations.findAll().getFirst().getStatus()).isEqualTo(ReservationStatus.ACTIVE);
    }

    @Test void onlyScopedServiceRoleCanAccessInternalOrdersAndForeignCompanyIsNotFound() throws Exception {
        for (String role : List.of("SELLER", "BUYER", "CONTROL_AVAILABILITY_READ")) {
            var result = mvc.perform(post(root(19)).contentType("application/json").content(payload("1"))
                    .header("Authorization", "Bearer "+role)).andReturn();
            assertThat(result.getResponse().getStatus()).isEqualTo(403);
        }
        assertThat(mvc.perform(get(root(19)+"?companyId=1")).andReturn().getResponse().getStatus()).isEqualTo(401);
        call(post(root(19)).content(payload("1")), 200);
        call(get(root(19)+"?companyId=2"), 404);
        call(post(root(19)+"/commit?companyId=2"), 404);
        call(post(root(19)+"/release?companyId=2"), 404);
        assertThat(mvc.perform(get("/api/v1/control/reservations?companyId=1")
                .header("Authorization", "Bearer CONTROL_ORDER_WRITE")).andReturn().getResponse().getStatus()).isEqualTo(403);
        verifyNoInteractions(companies);
    }

    @Test void validatesBodyIdsAndDuplicateItems() throws Exception {
        call(post(root(20)).content("{}"), 400);
        call(post(root(20)).content(payload("0")), 400);
        call(post(root(20)).content(payload("1.0001")), 400);
        call(post(root(-1)).content(payload("1")), 400);
        call(get(root(20)+"?companyId=-1"), 400);
        var body = json.readTree(payload("1")); var list = (com.fasterxml.jackson.databind.node.ArrayNode)body.path("items"); list.add(list.get(0).deepCopy());
        call(post(root(20)).content(json.writeValueAsString(body)), 400);
    }

    @Test void concurrentDuplicateOrderAndCompetingOrdersCannotOverReserve() throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        try {
            var same = executor.invokeAll(List.of(() -> call(post(root(21)).content(payload("2")), 200),
                    () -> call(post(root(21)).content(payload("2")), 200)));
            for (var future : same) future.get(20, TimeUnit.SECONDS);
            assertThat(reservations.count()).isEqualTo(1); balance(firstStock, "2.500", "2");
            var other = executor.invokeAll(List.of(() -> status(22, "4"), () -> status(23, "4")));
            assertThat(List.of(other.get(0).get(20, TimeUnit.SECONDS), other.get(1).get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200, 409);
            assertThat(stocks.findById(firstStock).orElseThrow().getReserved().add(stocks.findById(secondStock).orElseThrow().getReserved()))
                    .isEqualByComparingTo("6");
        } finally { executor.shutdownNow(); }
    }

    private int status(long order, String quantity) throws Exception {
        return mvc.perform(post(root(order)).contentType("application/json").content(payload(quantity))
                .header("Authorization", "Bearer CONTROL_ORDER_WRITE")).andReturn().getResponse().getStatus();
    }
    private Warehouse warehouse(Long company) {
        var w = new Warehouse(); w.setCompanyId(company); w.setName("Ombor"); return warehouses.saveAndFlush(w);
    }
    private InventoryProduct product(Long company, Long card, String sku) {
        var p = new InventoryProduct(); p.setCompanyId(company); p.setCatalogCardId(card); p.setSku(sku); return products.saveAndFlush(p);
    }
    private WarehouseStock stock(Warehouse w, InventoryProduct p, String quantity) {
        var s = new WarehouseStock(); s.setWarehouse(w); s.setInventoryProduct(p); s.setOnHand(new BigDecimal(quantity)); return stocks.saveAndFlush(s);
    }
    private void balance(Long id, String onHand, String reserved) {
        var s = stocks.findById(id).orElseThrow(); assertThat(s.getOnHand()).isEqualByComparingTo(onHand); assertThat(s.getReserved()).isEqualByComparingTo(reserved);
    }
    private String payload(String qty) throws Exception {
        return json.writeValueAsString(Map.of("companyId", 1, "buyerId", 7, "expiresAt", now.plusSeconds(600),
                "items", List.of(Map.of("productId", 100, "quantity", new BigDecimal(qty)))));
    }
    private MvcResult call(MockHttpServletRequestBuilder request, int expected) throws Exception {
        var result = mvc.perform(request.contentType("application/json").header("Authorization", "Bearer CONTROL_ORDER_WRITE")).andReturn();
        assertThat(result.getResponse().getStatus()).describedAs(result.getResponse().getContentAsString()).isEqualTo(expected); return result;
    }
    private JsonNode data(MvcResult result) throws Exception { return json.readTree(result.getResponse().getContentAsString()).path("data"); }
    private String root(long id) { return "/internal/control/orders/"+id+"/reservation"; }
}
