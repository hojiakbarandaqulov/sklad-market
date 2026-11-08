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
    @MockitoBean SellerWorkflowClient leadWorkflow;
    @MockitoBean SaleCustomerClient saleCustomers;
    @MockitoBean SaleFileClient saleFiles;
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
        jdbc.update("DELETE FROM order_invoices");
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
        when(control.reserve(anyLong(), any(), anyString())).thenAnswer(call -> {
            Long id = call.getArgument(0); remoteStates.putIfAbsent(id, "ACTIVE"); return reservation(id);
        });
        when(control.get(anyLong(), anyLong(), anyString())).thenAnswer(call -> reservation(call.getArgument(0)));
        when(control.commit(anyLong(), anyLong(), anyString())).thenAnswer(call -> {
            Long id = call.getArgument(0); remoteStates.computeIfPresent(id, (k,v) -> v.equals("ACTIVE") ? "COMMITTED" : v);
            return reservation(id);
        });
        when(control.release(anyLong(), anyLong(), anyString())).thenAnswer(call -> {
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
                && r.expiresAt().equals(now.plus(Duration.ofDays(1)))), anyString());
    }

    @Test void retryKeepsOriginalPriceAndOrderAndRejectsDifferentRequestWithSameKey() throws Exception {
        long id = create("retry", "2");
        when(products.get(100L)).thenReturn(product(100L, 1L, "UZS", "99999", true));
        assertThat(create("retry", "2.000")).isEqualTo(id);
        assertThat(orders.count()).isEqualTo(1);
        verify(products, times(1)).get(100L);
        verify(control, times(1)).reserve(eq(id), any(), anyString());
        call(post("/api/v1/orders").content(payload("retry", "3")), "buyer", 409);
    }

    @Test void sellerCanShipAndRetryButBuyerAndOtherSellerCannot() throws Exception {
        long id = create("ship", "2");
        call(post(root(id) + "/ship"), "buyer", 403);
        call(post(root(id) + "/ship"), "stranger", 403);
        assertThat(data(call(post(root(id) + "/ship"), "seller", 200)).path("status").asText()).isEqualTo("SHIPPED");
        call(post(root(id) + "/ship"), "seller", 200);
        verify(control, times(1)).commit(id, 1L, "UZ");
        call(post(root(id) + "/cancel"), "buyer", 409);
    }

    @Test void buyerOrOwnerCanCancelAndRepeatedCancellationDoesNotReleaseAgain() throws Exception {
        long id = create("cancel", "2");
        call(post(root(id) + "/cancel"), "other", 403);
        call(post(root(id) + "/cancel"), "stranger", 403);
        assertThat(data(call(post(root(id) + "/cancel"), "buyer", 200)).path("status").asText()).isEqualTo("CANCELLED");
        call(post(root(id) + "/cancel"), "seller", 200);
        verify(control, times(1)).release(id, 1L, "UZ");
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
        assertThat(orders.count()).isZero(); verify(control, never()).reserve(anyLong(), any(), anyString());
    }

    @Test void missingCompanyVerificationFailsClosed() throws Exception {
        when(companies.ownership(1L, 42L)).thenReturn(null);
        call(post("/api/v1/orders").content(payload("company", "2")), "buyer", 503);
        assertThat(orders.count()).isZero(); verify(control, never()).reserve(anyLong(), any(), anyString());
    }

    @Test void insufficientStockPersistsRejectedOrderWithoutReportingSuccess() throws Exception {
        when(control.reserve(anyLong(), any(), anyString())).thenThrow(failure(409));
        call(post("/api/v1/orders").content(payload("insufficient", "2")), "buyer", 409);
        assertThat(orders.findAll().getFirst().getStatus()).isEqualTo(OrderStatus.REJECTED);
        call(post("/api/v1/orders").content(payload("insufficient", "2")), "buyer", 409);
        verify(control, times(1)).reserve(anyLong(), any(), anyString());
    }

    @Test void lostReserveResponseLeavesDurableIntentAndRetriesSameOrderId() throws Exception {
        AtomicBoolean first = new AtomicBoolean(true);
        when(control.reserve(anyLong(), any(), anyString())).thenAnswer(call -> {
            Long id = call.getArgument(0); remoteStates.putIfAbsent(id, "ACTIVE");
            if (first.getAndSet(false)) throw failure(503);
            return reservation(id);
        });
        call(post("/api/v1/orders").content(payload("timeout", "2")), "buyer", 503);
        var saved = orders.findAll().getFirst(); assertThat(saved.getStatus()).isEqualTo(OrderStatus.RESERVING);
        assertThat(create("timeout", "2")).isEqualTo(saved.getId());
        assertThat(orders.count()).isEqualTo(1); verify(control, times(2)).reserve(eq(saved.getId()), any(), anyString());
    }

    @Test void lostCommitResponseIsRecoveredWithoutCreatingAnotherOrder() throws Exception {
        long id = create("commit-timeout", "2"); AtomicBoolean first = new AtomicBoolean(true);
        doAnswer(call -> {
            remoteStates.put(id, "COMMITTED");
            if (first.getAndSet(false)) throw failure(503);
            return reservation(id);
        }).when(control).commit(id, 1L, "UZ");
        call(post(root(id) + "/ship"), "seller", 503);
        assertThat(orders.findById(id).orElseThrow().getStatus()).isEqualTo(OrderStatus.SHIPPING);
        service.recover(org.example.enums.AppLanguage.UZ);
        assertThat(orders.findById(id).orElseThrow().getStatus()).isEqualTo(OrderStatus.SHIPPED);
        call(post(root(id) + "/ship"), "seller", 200);
        verify(control, times(2)).commit(id, 1L, "UZ");
    }

    @Test void lostReleaseResponseIsRecoveredAndExpiryCannotBeShipped() throws Exception {
        long id = create("release-timeout", "2"); AtomicBoolean first = new AtomicBoolean(true);
        doAnswer(call -> {
            remoteStates.put(id, "RELEASED"); if (first.getAndSet(false)) throw failure(503); return reservation(id);
        }).when(control).release(id, 1L, "UZ");
        call(post(root(id) + "/cancel"), "buyer", 503);
        assertThat(orders.findById(id).orElseThrow().getStatus()).isEqualTo(OrderStatus.CANCELLING);
        service.recover(org.example.enums.AppLanguage.UZ);
        assertThat(orders.findById(id).orElseThrow().getStatus()).isEqualTo(OrderStatus.CANCELLED);
        long expired = create("expired", "1"); remoteStates.put(expired, "EXPIRED");
        call(post(root(expired) + "/ship"), "seller", 409);
        assertThat(orders.findById(expired).orElseThrow().getStatus()).isEqualTo(OrderStatus.EXPIRED);
        verify(control, never()).commit(expired, 1L, "UZ");
    }

    @Test void malformedOrMismatchedControlResponseDoesNotInventSuccess() throws Exception {
        when(control.reserve(anyLong(), any(), anyString())).thenReturn(new ControlClient.Envelope(true,
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
    @Test void dependencyForbiddenStays503AndMessagesFollowLanguage() throws Exception {
        when(products.get(100L)).thenThrow(failure(403));
        for (String lang : List.of("UZ", "RU", "en-US,en;q=0.9")) {
            var result = call(post("/api/v1/orders").header("Accept-Language", lang)
                    .content(payload("localized-" + lang, "2")), "buyer", 503);
            String message = json.readTree(result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8))
                    .path("message").asText();
            assertThat(message).startsWith(lang.equals("UZ") ? "Bog'liq" : lang.equals("RU") ? "Зависимый" : "A dependent");
            assertThat(message).doesNotContain("upstream_status", "product.order-summary");
        }
    }

    @Test void serviceUsesExplicitLanguageWithoutAnHttpRequest() {
        org.springframework.context.i18n.LocaleContextHolder.setLocale(Locale.ENGLISH);
        try {
            assertThatThrownBy(() -> service.resume(Long.MAX_VALUE, org.example.enums.AppLanguage.RU))
                    .isInstanceOf(org.example.exp.AppNotFoundException.class)
                    .hasMessage("Заказ не найден");
        } finally {
            org.springframework.context.i18n.LocaleContextHolder.resetLocaleContext();
        }
    }

    @Test void reservationRequestForwardsTheSelectedLanguage() throws Exception {
        call(post("/api/v1/orders").header("Accept-Language", "RU")
                .content(payload("russian-reservation", "2")), "buyer", 200);
        verify(control).reserve(anyLong(), any(), eq("RU"));
    }

    @Test void authenticationAndValidationAreLocalized() throws Exception {
        var anonymous = call(get("/api/v1/orders").header("Accept-Language", "RU"), null, 401);
        assertThat(anonymous.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).contains("Требуется авторизация");
        var forbidden = call(get("/api/v1/orders").header("Accept-Language", "EN"), "no-profile", 403);
        assertThat(forbidden.getResponse().getContentAsString()).contains("Token has no valid profileId");
        var invalid = call(post("/api/v1/orders").header("Accept-Language", "RU").content("{}"), "buyer", 400);
        assertThat(invalid.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8))
                .contains("Некорректные поля", "Значение обязательно");
    }

    @Test void sellerSaleIssuesImmutableInvoiceAndRetriesDoNotCommitTwice() throws Exception {
        long id=create("sale-new","2");
        when(control.availability(id,1L, "UZ")).thenReturn(List.of(new ControlClient.Availability(100L,new BigDecimal("2"),new BigDecimal("5"),new BigDecimal("7"))));
        call(get(root(id)+"/checkout"),"buyer",403);
        assertThat(data(call(get(root(id)+"/checkout"),"seller",200)).path("availability").get(0).path("maxSaleQuantity").asInt()).isEqualTo(7);
        when(control.complete(eq(id),any(), anyString())).thenAnswer(c -> { remoteStates.put(id,"COMMITTED"); return reservation(id); });
        String sale=salePayload("sale-1");
        var invoice=data(call(post(root(id)+"/complete-sale").content(sale),"seller",200));
        assertThat(invoice.path("totalAmount").decimalValue()).isEqualByComparingTo("15000");
        call(post(root(id)+"/complete-sale").content(sale),"seller",200);
        verify(control,times(1)).complete(eq(id),any(), anyString());
        assertThat(data(call(get(root(id)+"/invoice"),"buyer",200))).isEqualTo(invoice);
        call(get(root(id)+"/invoice"),"stranger",403);
        call(post(root(id)+"/complete-sale").content(sale.replace("15000","16000")),"seller",409);
        assertThat(data(call(get(root(id)),"buyer",200)).path("status").asText()).isEqualTo("SHIPPED");
    }

    @Test void lostSaleResponseRetriesSavedIntentAndRejectsOrdinaryShipping() throws Exception {
        long id=create("sale-lost","2");
        var first=new AtomicBoolean(true);
        when(control.complete(eq(id),any(), anyString())).thenAnswer(c -> {
            remoteStates.put(id,"COMMITTED"); if(first.getAndSet(false)) throw failure(503); return reservation(id);
        });
        call(post(root(id)+"/complete-sale").content(salePayload("lost-key")),"seller",503);
        assertThat(orders.findById(id).orElseThrow().getStatus()).isEqualTo(OrderStatus.FINALIZING);
        call(post(root(id)+"/ship"),"seller",409);
        call(post(root(id)+"/cancel"),"buyer",409);
        call(post(root(id)+"/complete-sale").content(salePayload("lost-key")),"seller",200);
        verify(control,times(2)).complete(eq(id),any(), anyString());
        verify(control,never()).commit(eq(id),anyLong(), anyString());
    }

    @Test void creditNeedsOwnedContractAndBadDeliveryAndForeignOwnerFail() throws Exception {
        long id=create("sale-invalid","2");
        String request=salePayload("credit-key").replace("CASH","CREDIT");
        var denied=call(post(root(id)+"/complete-sale").header("Accept-Language","RU").content(request),"seller",409);
        assertThat(denied.getResponse().getContentAsString(StandardCharsets.UTF_8)).contains("Для продажи в долг");
        call(post(root(id)+"/complete-sale").content(salePayload("x")),"stranger",403);
        call(post(root(id)+"/complete-sale").content(salePayload("x").replace("PICKUP","DELIVERY")),"seller",400);
        verify(control,never()).complete(anyLong(),any(), anyString());
    }

    @Test void contractCreditStoresDueDateAndDefiniteStockFailureAllowsANewAttempt() throws Exception {
        long id=create("credit-success","2");
        when(saleCustomers.get(5L,"Bearer seller")).thenReturn(new SaleCustomerClient.Envelope(true,
                new SaleCustomerClient.Customer(5L,1L,"Buyer","123","CONTRACT-1",30)));
        when(control.complete(eq(id),any(), anyString())).thenThrow(failure(409));
        String request=salePayload("first-attempt").replace("CASH","CREDIT").replace("\"buyerName\"","\"customerId\":5,\"buyerName\"");
        call(post(root(id)+"/complete-sale").content(request),"seller",409);
        assertThat(orders.findById(id).orElseThrow().getStatus()).isEqualTo(OrderStatus.RESERVED);
        when(control.complete(eq(id),any(), anyString())).thenAnswer(c -> {remoteStates.put(id,"COMMITTED");return reservation(id);});
        call(post(root(id)+"/complete-sale").content(request),"seller",409);
        var invoice=data(call(post(root(id)+"/complete-sale").content(request.replace("first-attempt","second-attempt")),"seller",200));
        assertThat(invoice.path("contractNumber").asText()).isEqualTo("CONTRACT-1");
        assertThat(invoice.path("paymentStatus").asText()).isEqualTo("UNPAID");
        assertThat(invoice.path("paymentDueDate").asText()).isEqualTo("2026-12-02");
    }

    @Test void sellerAcceptsLeadForActualBuyerAndCannotIncreaseRequestedQuantity() throws Exception {
        when(leadWorkflow.lead(11L,"Bearer seller")).thenReturn(new SellerWorkflowClient.LeadEnvelope(true,
                new SellerWorkflowClient.Lead(11L,7L,42L,1L,"NEW","Buyer","123",null,"Lead note",
                        List.of(new SellerWorkflowClient.Item(100L,BigDecimal.TEN,"Steel",BigDecimal.TEN)))));
        String request="{\"idempotencyKey\":\"accept-11\",\"buyerAgreementConfirmed\":true,\"items\":[{\"productId\":100,\"quantity\":2}]}";
        var response=data(call(post("/internal/orders/leads/11/accept").content(request),"seller",200));
        assertThat(response.path("buyerId").asLong()).isEqualTo(7L);
        call(post("/internal/orders/leads/11/accept").content(request),"seller",200);
        assertThat(orders.count()).isEqualTo(1);
        call(post("/internal/orders/leads/11/accept").content(request.replace("quantity\":2","quantity\":11")),"seller",409);
        call(post("/internal/orders/leads/11/accept").content(request.replace("Confirmed\":true","Confirmed\":false")),"seller",409);
        var saved=orders.findByLeadId(11L).orElseThrow(); saved.setStatus(OrderStatus.SHIPPED); orders.saveAndFlush(saved);
        call(post("/internal/orders/leads/11/accept").content(request),"seller",200);
    }

    @Test void pressMinimumRejectsOneUnitBeforeReservationAndAcceptsFiftyWithoutIncreasingOriginalLead() throws Exception {
        when(products.get(60L)).thenReturn(new ProductClient.ProductSnapshot(60L, 1L, 42L, "press",
                new BigDecimal("14000"), "UZS", 50L, true));
        when(leadWorkflow.lead(60L, "Bearer seller")).thenReturn(new SellerWorkflowClient.LeadEnvelope(true,
                new SellerWorkflowClient.Lead(60L, 7L, 42L, 1L, "NEW", "Buyer", "123", null, null,
                        List.of(new SellerWorkflowClient.Item(60L, BigDecimal.ONE, "press", new BigDecimal("14000"))))));
        String body = "{\"idempotencyKey\":\"press-accept\",\"buyerAgreementConfirmed\":false,\"items\":[{\"productId\":60,\"quantity\":1.000}]}";
        var rejected = call(post("/internal/orders/leads/60/accept").content(body), "seller", 400);
        assertThat(json.readTree(rejected.getResponse().getContentAsString()).path("message").asText())
                .isEqualTo("Mahsulotning minimal buyurtma miqdoriga rioya qiling");
        assertThat(orders.count()).isZero();
        verifyNoInteractions(control);
        // Existing one-unit leads cannot be silently inflated to the minimum.
        call(post("/internal/orders/leads/60/accept").content(body.replace("1.000", "50")), "seller", 409);

        when(leadWorkflow.lead(61L, "Bearer seller")).thenReturn(new SellerWorkflowClient.LeadEnvelope(true,
                new SellerWorkflowClient.Lead(61L, 7L, 42L, 1L, "NEW", "Buyer", "123", null, null,
                        List.of(new SellerWorkflowClient.Item(60L, new BigDecimal("50"), "press", new BigDecimal("14000"))))));
        var accepted = data(call(post("/internal/orders/leads/61/accept").content(body.replace("1.000", "50.000")), "seller", 200));
        assertThat(accepted.path("buyerId").asLong()).isEqualTo(7L);
        assertThat(accepted.path("status").asText()).isEqualTo("RESERVED");
        verify(control).reserve(anyLong(), argThat(request -> request.buyerId().equals(7L)
                && request.items().size() == 1 && request.items().getFirst().productId().equals(60L)
                && request.items().getFirst().quantity().compareTo(new BigDecimal("50")) == 0), eq("UZ"));
    }

    private String salePayload(String key) {
        return "{\"idempotencyKey\":\""+key+"\",\"paymentMethod\":\"CASH\",\"buyerName\":\"Buyer\",\"deliveryMethod\":\"PICKUP\",\"items\":[{\"productId\":100,\"quantity\":1,\"unitPrice\":15000}]}";
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
