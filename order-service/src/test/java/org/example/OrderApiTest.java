package org.example;

import com.fasterxml.jackson.databind.*;
import feign.*;
import org.example.client.*;
import org.example.enums.OrderStatus;
import org.example.repository.OrderRepository;
import org.example.service.OrderService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrderApiTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired OrderRepository orders;
    @Autowired OrderService service;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean ProductClient products;
    @MockitoBean CompanyClient companies;
    @MockitoBean ControlClient control;
    @MockitoBean JwtDecoder decoder;
    @MockitoBean Clock clock;
    final Instant now = Instant.parse("2026-11-02T10:00:00Z");
    Map<Long, String> remoteStates;

    @BeforeEach void setup() {
        orders.deleteAll(); remoteStates = new ConcurrentHashMap<>();
        when(clock.instant()).thenReturn(now);
        when(decoder.decode("buyer")).thenReturn(token("buyer", 7L, "BUYER"));
        when(decoder.decode("other")).thenReturn(token("other", 8L, "BUYER"));
        when(decoder.decode("seller")).thenReturn(token("seller", 42L, "SELLER"));
        when(decoder.decode("stranger")).thenReturn(token("stranger", 77L, "SELLER"));
        when(decoder.decode("service")).thenReturn(token("service", null, "CONTROL_ORDER_WRITE"));
        when(decoder.decode("no-profile")).thenReturn(token("no-profile", null, "BUYER"));
        when(products.get(100L)).thenReturn(product(100L, 1L, "UZS", "12500.125", true));
        when(products.get(101L)).thenReturn(product(101L, 1L, "UZS", "4000", true));
        when(companies.ownership(anyLong(), anyLong())).thenAnswer(call ->
                new CompanyClient.Ownership(call.getArgument(0), true, Objects.equals(call.getArgument(1), 42L), true));
        when(control.reserve(anyLong(), any())).thenAnswer(call -> {
            Long id = call.getArgument(0); remoteStates.putIfAbsent(id, "ACTIVE"); return reservation(id);
        });
        when(control.get(anyLong(), anyLong())).thenAnswer(call -> reservation(call.getArgument(0)));
        when(control.commit(anyLong(), anyLong())).thenAnswer(call -> {
            Long id = call.getArgument(0); remoteStates.computeIfPresent(id, (k,v) -> v.equals("ACTIVE") ? "COMMITTED" : v);
            return reservation(id);
        });
        when(control.release(anyLong(), anyLong())).thenAnswer(call -> {
            Long id = call.getArgument(0); remoteStates.computeIfPresent(id, (k,v) -> v.equals("ACTIVE") ? "RELEASED" : v);
            return reservation(id);
        });
    }

    @Test void createTakesServerPriceAndLinksReservationToOrder() throws Exception {
        var data = data(call(post("/api/v1/orders").content(payload("new", "2.125")), "buyer", 200));
        assertThat(data.path("status").asText()).isEqualTo("RESERVED");
        assertThat(data.path("buyerId").asLong()).isEqualTo(7);
        assertThat(data.path("totalAmount").decimalValue()).isEqualByComparingTo("26562.765625");
        assertThat(data.path("items").get(0).path("unitPrice").decimalValue()).isEqualByComparingTo("12500.125");
        verify(control).reserve(eq(data.path("id").asLong()), argThat(r -> r.companyId().equals(1L)
                && r.buyerId().equals(7L) && r.items().getFirst().productId().equals(100L)
                && r.expiresAt().equals(now.plus(Duration.ofDays(1)))));
    }

    @Test void retryKeepsOriginalPriceAndOrderAndRejectsDifferentRequestWithSameKey() throws Exception {
        long id = create("retry", "2");
        when(products.get(100L)).thenReturn(product(100L, 1L, "UZS", "99999", true));
        assertThat(create("retry", "2.000")).isEqualTo(id);
        assertThat(orders.count()).isEqualTo(1);
        verify(products, times(1)).get(100L);
        verify(control, times(1)).reserve(eq(id), any());
        call(post("/api/v1/orders").content(payload("retry", "3")), "buyer", 409);
    }

    @Test void sellerCanShipAndRetryButBuyerAndOtherSellerCannot() throws Exception {
        long id = create("ship", "2");
        call(post(root(id) + "/ship"), "buyer", 403);
        call(post(root(id) + "/ship"), "stranger", 403);
        assertThat(data(call(post(root(id) + "/ship"), "seller", 200)).path("status").asText()).isEqualTo("SHIPPED");
        call(post(root(id) + "/ship"), "seller", 200);
        verify(control, times(1)).commit(id, 1L);
        call(post(root(id) + "/cancel"), "buyer", 409);
    }

    @Test void buyerOrOwnerCanCancelAndRepeatedCancellationDoesNotReleaseAgain() throws Exception {
        long id = create("cancel", "2");
        call(post(root(id) + "/cancel"), "other", 403);
        call(post(root(id) + "/cancel"), "stranger", 403);
        assertThat(data(call(post(root(id) + "/cancel"), "buyer", 200)).path("status").asText()).isEqualTo("CANCELLED");
        call(post(root(id) + "/cancel"), "seller", 200);
        verify(control, times(1)).release(id, 1L);
        call(post(root(id) + "/ship"), "seller", 409);
    }

    @Test void detailsAndListsNeverExposeOtherBuyersOrOtherSellersOrders() throws Exception {
        long id = create("private", "1");
        call(get(root(id)), "other", 403); call(get(root(id)), "stranger", 403);
        call(get(root(id)), "seller", 200); call(get(root(id)), "buyer", 200);
        assertThat(data(call(get("/api/v1/orders"), "other", 200)).path("totalElements").asInt()).isZero();
        assertThat(data(call(get("/api/v1/orders/seller?companyId=1"), "seller", 200)).path("totalElements").asInt()).isEqualTo(1);
        call(get("/api/v1/orders/seller?companyId=1"), "stranger", 403);
        call(get("/api/v1/orders/seller?companyId=1"), "buyer", 403);
        call(get("/api/v1/orders/seller"), "seller", 400);
        call(get("/api/v1/orders/999999"), "buyer", 404);
    }

    @Test void creationRejectsMixedCompaniesCurrenciesDuplicateProductsHiddenCardsAndBelowMinimum() throws Exception {
        when(products.get(101L)).thenReturn(product(101L, 2L, "UZS", "4000", true));
        call(post("/api/v1/orders").content(twoItems("mixed")), "buyer", 400);
        when(products.get(101L)).thenReturn(product(101L, 1L, "USD", "4000", true));
        call(post("/api/v1/orders").content(twoItems("currency")), "buyer", 400);
        call(post("/api/v1/orders").content(twoItems("duplicate").replace("101", "100")), "buyer", 400);
        when(products.get(100L)).thenReturn(product(100L, 1L, "UZS", "100", false));
        call(post("/api/v1/orders").content(payload("hidden", "2")), "buyer", 409);
        when(products.get(100L)).thenReturn(new ProductClient.ProductSnapshot(100L, 1L, 42L, "Steel", new BigDecimal("100"), "UZS", 5L, true));
        call(post("/api/v1/orders").content(payload("min", "2")), "buyer", 400);
        assertThat(orders.count()).isZero(); verify(control, never()).reserve(anyLong(), any());
    }

    @Test void missingCompanyVerificationFailsClosed() throws Exception {
        when(companies.ownership(1L, 42L)).thenReturn(null);
        call(post("/api/v1/orders").content(payload("company", "2")), "buyer", 503);
        assertThat(orders.count()).isZero(); verify(control, never()).reserve(anyLong(), any());
    }

    @Test void insufficientStockPersistsRejectedOrderWithoutReportingSuccess() throws Exception {
        when(control.reserve(anyLong(), any())).thenThrow(failure(409));
        call(post("/api/v1/orders").content(payload("insufficient", "2")), "buyer", 409);
        assertThat(orders.findAll().getFirst().getStatus()).isEqualTo(OrderStatus.REJECTED);
        call(post("/api/v1/orders").content(payload("insufficient", "2")), "buyer", 409);
        verify(control, times(1)).reserve(anyLong(), any());
    }

    @Test void lostReserveResponseLeavesDurableIntentAndRetriesSameOrderId() throws Exception {
        AtomicBoolean first = new AtomicBoolean(true);
        when(control.reserve(anyLong(), any())).thenAnswer(call -> {
            Long id = call.getArgument(0); remoteStates.putIfAbsent(id, "ACTIVE");
            if (first.getAndSet(false)) throw failure(503);
            return reservation(id);
        });
        call(post("/api/v1/orders").content(payload("timeout", "2")), "buyer", 503);
        var saved = orders.findAll().getFirst(); assertThat(saved.getStatus()).isEqualTo(OrderStatus.RESERVING);
        assertThat(create("timeout", "2")).isEqualTo(saved.getId());
        assertThat(orders.count()).isEqualTo(1); verify(control, times(2)).reserve(eq(saved.getId()), any());
    }

    @Test void lostCommitResponseIsRecoveredWithoutCreatingAnotherOrder() throws Exception {
        long id = create("commit-timeout", "2"); AtomicBoolean first = new AtomicBoolean(true);
        doAnswer(call -> {
            remoteStates.put(id, "COMMITTED");
            if (first.getAndSet(false)) throw failure(503);
            return reservation(id);
        }).when(control).commit(id, 1L);
        call(post(root(id) + "/ship"), "seller", 503);
        assertThat(orders.findById(id).orElseThrow().getStatus()).isEqualTo(OrderStatus.SHIPPING);
        service.recover();
        assertThat(orders.findById(id).orElseThrow().getStatus()).isEqualTo(OrderStatus.SHIPPED);
        call(post(root(id) + "/ship"), "seller", 200);
        verify(control, times(2)).commit(id, 1L);
    }

    @Test void lostReleaseResponseIsRecoveredAndExpiryCannotBeShipped() throws Exception {
        long id = create("release-timeout", "2"); AtomicBoolean first = new AtomicBoolean(true);
        doAnswer(call -> {
            remoteStates.put(id, "RELEASED"); if (first.getAndSet(false)) throw failure(503); return reservation(id);
        }).when(control).release(id, 1L);
        call(post(root(id) + "/cancel"), "buyer", 503);
        assertThat(orders.findById(id).orElseThrow().getStatus()).isEqualTo(OrderStatus.CANCELLING);
        service.recover();
        assertThat(orders.findById(id).orElseThrow().getStatus()).isEqualTo(OrderStatus.CANCELLED);
        long expired = create("expired", "1"); remoteStates.put(expired, "EXPIRED");
        call(post(root(expired) + "/ship"), "seller", 409);
        assertThat(orders.findById(expired).orElseThrow().getStatus()).isEqualTo(OrderStatus.EXPIRED);
        verify(control, never()).commit(expired, 1L);
    }

    @Test void malformedOrMismatchedControlResponseDoesNotInventSuccess() throws Exception {
        when(control.reserve(anyLong(), any())).thenReturn(new ControlClient.Envelope(true,
                new ControlClient.Reservation(1L, 999L, 99L, "ACTIVE")));
        call(post("/api/v1/orders").content(payload("mismatch", "1")), "buyer", 503);
        assertThat(orders.findAll().getFirst().getStatus()).isEqualTo(OrderStatus.RESERVING);
    }

    @Test void paginationAndFiltersCoverAllRowsAndUseTashkentCalendarDays() throws Exception {
        for (int i = 0; i < 12; i++) create("page-" + i, "1");
        var page = data(call(get("/api/v1/orders?companyId=1&status=RESERVED&page=2&perPage=5&dateFrom=2026-11-02&dateTo=2026-11-02"), "buyer", 200));
        assertThat(page.path("totalElements").asInt()).isEqualTo(12); assertThat(page.path("content").size()).isEqualTo(5);
        long id = page.path("content").get(0).path("id").asLong();
        jdbc.update("update market_orders set created_at=? where id=?", java.sql.Timestamp.from(Instant.parse("2026-11-02T19:00:00Z")), id);
        var day = data(call(get("/api/v1/orders?dateTo=2026-11-02"), "buyer", 200));
        assertThat(day.path("totalElements").asInt()).isEqualTo(11);
    }

    @Test void validationAndAuthenticationRejectInvalidRequests() throws Exception {
        call(get("/api/v1/orders"), null, 401); call(get("/api/v1/orders"), "service", 403);
        call(get("/api/v1/orders"), "no-profile", 403);
        for (String query : List.of("page=0", "perPage=101", "companyId=-1", "status=WRONG", "dateFrom=wrong", "dateFrom=2026-11-03&dateTo=2026-11-02"))
            call(get("/api/v1/orders?" + query), "buyer", 400);
        for (String amount : List.of("0", "-1", "1.0001")) call(post("/api/v1/orders").content(payload("invalid", amount)), "buyer", 400);
        call(post("/api/v1/orders").content("{}"), "buyer", 400);
        call(get("/api/v1/orders/-1"), "buyer", 400);
    }

    @Test void concurrentCreateAndShipCancelRacesHaveOneFinalOutcome() throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        try {
            var created = executor.invokeAll(List.<Callable<Long>>of(() -> create("parallel", "1"), () -> create("parallel", "1")));
            long id = created.get(0).get(20, TimeUnit.SECONDS);
            assertThat(created.get(1).get(20, TimeUnit.SECONDS)).isEqualTo(id); assertThat(orders.count()).isEqualTo(1);
            var results = executor.invokeAll(List.of(
                    () -> mvc.perform(post(root(id)+"/ship").header("Authorization", "Bearer seller")).andReturn().getResponse().getStatus(),
                    () -> mvc.perform(post(root(id)+"/cancel").header("Authorization", "Bearer buyer")).andReturn().getResponse().getStatus()));
            assertThat(results.stream().map(f -> { try { return f.get(); } catch(Exception e) { throw new RuntimeException(e); } }).toList())
                    .containsExactlyInAnyOrder(200, 409);
            assertThat(orders.findById(id).orElseThrow().getStatus()).isIn(OrderStatus.SHIPPED, OrderStatus.CANCELLED);
        } finally { executor.shutdownNow(); }
    }

    private ControlClient.Envelope reservation(Long id) {
        return new ControlClient.Envelope(true, new ControlClient.Reservation(id + 1000, 1L, id, remoteStates.get(id)));
    }
    private ProductClient.ProductSnapshot product(Long id, Long company, String currency, String price, boolean orderable) {
        return new ProductClient.ProductSnapshot(id, company, 42L, "Steel-"+id, new BigDecimal(price), currency, 1L, orderable);
    }
    private long create(String key, String qty) throws Exception {
        return data(call(post("/api/v1/orders").content(payload(key, qty)), "buyer", 200)).path("id").asLong();
    }
    private String payload(String key, String qty) throws Exception {
        return json.writeValueAsString(Map.of("companyId", 1, "idempotencyKey", key, "deliveryAddress", "Toshkent",
                "items", List.of(Map.of("productId", 100, "quantity", new BigDecimal(qty)))));
    }
    private String twoItems(String key) throws Exception {
        return json.writeValueAsString(Map.of("companyId", 1, "idempotencyKey", key, "items", List.of(
                Map.of("productId", 100, "quantity", 2), Map.of("productId", 101, "quantity", 2))));
    }
    private MvcResult call(MockHttpServletRequestBuilder request, String token, int expected) throws Exception {
        request.contentType("application/json"); if(token != null) request.header("Authorization", "Bearer " + token);
        var result = mvc.perform(request).andReturn();
        assertThat(result.getResponse().getStatus()).describedAs(result.getResponse().getContentAsString()).isEqualTo(expected);
        return result;
    }
    private JsonNode data(MvcResult result) throws Exception { return json.readTree(result.getResponse().getContentAsString()).path("data"); }
    private static String root(long id) { return "/api/v1/orders/" + id; }
    private static Jwt token(String text, Long profile, String role) {
        var builder = Jwt.withTokenValue(text).header("alg", "RS256").subject(text).claim("realm_access", Map.of("roles", List.of(role)));
        if(profile != null) builder.claim("profileId", profile); return builder.build();
    }
    private static FeignException failure(int status) {
        return FeignException.errorStatus("test", Response.builder().status(status).reason("test").headers(Map.of())
                .request(Request.create(Request.HttpMethod.GET, "http://test", Map.of(), null, StandardCharsets.UTF_8, null)).build());
    }
}
