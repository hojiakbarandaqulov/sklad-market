package com.example;

import com.example.config.client.CompanyClient;
import com.example.config.client.ProductClient;
import com.example.dto.internal.CompanyOwnershipResponse;
import com.example.entity.*;
import com.example.enums.*;
import com.example.repository.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/** Uchala endpoint haqiqiy JPA so'rovlari, HTTP validatsiyasi va JWT bilan sinovdan o'tadi. */
@SpringBootTest(properties = {
        "spring.liquibase.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.datasource.url=jdbc:h2:mem:controlqueries;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver", "control.reservations.expiry-enabled=false"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ControlQueryApiTest {
    private static final String OPERATIONS = "/api/v1/control/operations";
    private static final String RESERVATIONS = "/api/v1/control/reservations";
    private static final String AVAILABILITY = "/internal/control/availability";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired StockOperationRepository operations;
    @Autowired StockReservationItemRepository items;
    @Autowired StockReservationRepository reservations;
    @Autowired WarehouseStockRepository stocks;
    @Autowired InventoryProductRepository products;
    @Autowired WarehouseRepository warehouses;
    @MockitoBean CompanyClient companies;
    @MockitoBean ProductClient cards;
    @MockitoBean JwtDecoder decoder;

    @BeforeEach
    void setup() {
        operations.deleteAll(); items.deleteAll(); reservations.deleteAll();
        stocks.deleteAll(); products.deleteAll(); warehouses.deleteAll();
        when(decoder.decode("seller")).thenReturn(token("SELLER", true));
        when(decoder.decode("buyer")).thenReturn(token("BUYER", true));
        when(decoder.decode("service")).thenReturn(token("CONTROL_AVAILABILITY_READ", false));
        when(decoder.decode("seller-no-profile")).thenReturn(token("SELLER", false));
        when(companies.ownershipCheck(1L, 42L)).thenReturn(company(1L, true));
        when(companies.ownershipCheck(2L, 42L)).thenReturn(company(2L, false));
    }

    @Test
    void journalIsPagedAndIncludesArchivedAndLegacyHistoryButNotDeletedOrForeignOperations() throws Exception {
        var warehouse = warehouse(1L);
        var product = product(1L, 501L, "HISTORY");
        var stock = stock(warehouse, product, "100", "20");
        var ids = new ArrayList<Long>();
        for (int index = 0; index < 12; index++) {
            var operation = operation(stock, StockOperationType.ADJUSTMENT, "Sanash");
            date(operation, "2026-10-31T10:00:00");
            ids.add(operation.getId());
        }
        var deleted = operation(stock, StockOperationType.ADJUSTMENT, "deleted");
        deleted.setDeleted(true); operations.saveAndFlush(deleted);
        operation(stock(warehouse(2L), product(2L, 999L, "FOREIGN"), "50", "0"),
                StockOperationType.RECEIPT, "foreign");
        warehouse.setStatus(WarehouseStatus.ARCHIVED); warehouses.saveAndFlush(warehouse);
        product.setStatus(InventoryProductStatus.ARCHIVED); products.saveAndFlush(product);

        var data = call(get(OPERATIONS).param("companyId", "1").param("perPage", "5"), "seller", 200).path("data");
        assertThat(data.path("totalElements").asLong()).isEqualTo(12);
        assertThat(data.path("totalPages").asInt()).isEqualTo(3);
        assertThat(data.path("content").size()).isEqualTo(5);
        var first = data.path("content").get(0);
        assertThat(first.path("id").asLong()).isEqualTo(ids.getLast());
        assertThat(first.path("companyId").asLong()).isEqualTo(1);
        assertThat(first.path("quantityAfter").decimalValue()).isEqualByComparingTo("11");
        assertThat(first.path("reserved").decimalValue()).isEqualByComparingTo("2");
        assertThat(first.path("available").decimalValue()).isEqualByComparingTo("9");
        assertThat(first.has("stock")).isFalse();
        var second = call(get(OPERATIONS).param("companyId", "1").param("page", "2").param("perPage", "5"),
                "seller", 200).path("data");
        assertThat(second.path("number").asInt()).isEqualTo(1);
        assertThat(second.path("content").get(0).path("id").asLong()).isEqualTo(ids.get(6));
    }

    @Test
    void journalCombinesAllFiltersAndDateToIncludesWholeDayOnly() throws Exception {
        var warehouse = warehouse(1L);
        var product = product(1L, 501L, "PART_1%");
        var stock = stock(warehouse, product, "100", "20");
        var matching = operation(stock, StockOperationType.ADJUSTMENT, "Inventarizatsiya");
        matching.setOrderId(700L); matching.setReservationId(800L); operations.saveAndFlush(matching);
        date(matching, "2026-10-31T23:59:59.999999");
        var nextDay = operation(stock, StockOperationType.ADJUSTMENT, "Inventarizatsiya");
        nextDay.setOrderId(700L); nextDay.setReservationId(800L); operations.saveAndFlush(nextDay);
        date(nextDay, "2026-11-01T00:00:00");
        operation(stock, StockOperationType.RECEIPT, "Boshqa tur");

        var data = call(get(OPERATIONS).param("companyId", "1")
                .param("warehouseId", warehouse.getId().toString())
                .param("inventoryProductId", product.getId().toString()).param("productId", "501")
                .param("type", "ADJUSTMENT").param("orderId", "700").param("reservationId", "800")
                .param("createdBy", "42").param("q", "part_1%")
                .param("dateFrom", "2026-10-31").param("dateTo", "2026-10-31"), "seller", 200).path("data");
        assertThat(data.path("totalElements").asLong()).isEqualTo(1);
        assertThat(data.path("content").get(0).path("id").asLong()).isEqualTo(matching.getId());
        var byReason = call(get(OPERATIONS).param("companyId", "1").param("q", "INVENTARIZATSIYA"),
                "seller", 200).path("data");
        assertThat(byReason.path("totalElements").asLong()).isEqualTo(2);
        var absent = call(get(OPERATIONS).param("companyId", "1").param("reservationId", "999"),
                "seller", 200).path("data");
        assertThat(absent.path("content").isEmpty()).isTrue();
    }

    @Test
    void journalSearchTreatsPercentAndUnderscoreLiterallyAndDoesNotInventLegacySnapshots() throws Exception {
        var warehouse = warehouse(1L);
        var exact = operation(stock(warehouse, product(1L, 501L, "PART_1%"), "10", "0"),
                StockOperationType.OPENING_BALANCE, "Boshlang'ich qoldiq");
        exact.setReservedSnapshot(null); operations.saveAndFlush(exact);
        operation(stock(warehouse, product(1L, 502L, "PARTX1Y"), "10", "0"),
                StockOperationType.RECEIPT, "Boshqa");
        var data = call(get(OPERATIONS).param("companyId", "1").param("q", "PART_1%"), "seller", 200).path("data");
        assertThat(data.path("totalElements").asLong()).isEqualTo(1);
        assertThat(data.path("content").get(0).path("reserved").isNull()).isTrue();
        assertThat(data.path("content").get(0).path("available").isNull()).isTrue();
    }

    @Test
    void reservationListDoesNotDuplicateRowsAndReturnsAllItemsOfEachMatchingReservation() throws Exception {
        var firstWarehouse = warehouse(1L);
        var secondWarehouse = warehouse(1L);
        var firstProduct = product(1L, 501L, "FIRST");
        var secondProduct = product(1L, 502L, "SECOND");
        var firstStock = stock(firstWarehouse, firstProduct, "100", "2");
        var secondStock = stock(secondWarehouse, secondProduct, "100", "2");
        reservation(1L, "multi", ReservationStatus.ACTIVE, 700L, List.of(firstStock, secondStock));
        reservation(1L, "single", ReservationStatus.ACTIVE, 701L, List.of(firstStock));
        var deleted = reservation(1L, "deleted", ReservationStatus.ACTIVE, 702L, List.of(firstStock));
        deleted.setDeleted(true); reservations.saveAndFlush(deleted);
        reservation(2L, "foreign", ReservationStatus.ACTIVE, 703L,
                List.of(stock(warehouse(2L), product(2L, 999L, "FOREIGN"), "10", "1")));

        var page = call(get(RESERVATIONS).param("companyId", "1").param("perPage", "1"), "seller", 200).path("data");
        assertThat(page.path("totalElements").asLong()).isEqualTo(2);
        assertThat(page.path("content").size()).isEqualTo(1);
        var filtered = call(get(RESERVATIONS).param("companyId", "1")
                .param("warehouseId", secondWarehouse.getId().toString()), "seller", 200).path("data");
        assertThat(filtered.path("totalElements").asLong()).isEqualTo(1);
        assertThat(filtered.path("content").get(0).path("items").size()).isEqualTo(2);
        // Turli item'lardagi ombor va mahsulot bir-biriga noto'g'ri moslashtirilmasin.
        var unmatched = call(get(RESERVATIONS).param("companyId", "1")
                .param("warehouseId", firstWarehouse.getId().toString())
                .param("inventoryProductId", secondProduct.getId().toString()), "seller", 200).path("data");
        assertThat(unmatched.path("totalElements").asLong()).isZero();
    }

    @Test
    void reservationFiltersCoverStatusOrderProductCreatorCreationDateExpiryAndSearch() throws Exception {
        var warehouse = warehouse(1L);
        var product = product(1L, 501L, "PART_1%");
        var stock = stock(warehouse, product, "10", "1");
        var target = reservation(1L, "filter-key", ReservationStatus.RELEASED, 700L, List.of(stock));
        var expiry = target.getExpiresAt();
        reservation(1L, "another", ReservationStatus.ACTIVE, 701L, List.of(stock));
        var request = get(RESERVATIONS).param("companyId", "1").param("status", "RELEASED")
                .param("warehouseId", warehouse.getId().toString())
                .param("inventoryProductId", product.getId().toString()).param("productId", "501")
                .param("orderId", "700").param("createdBy", "42").param("q", "part_1%")
                .param("dateFrom", target.getCreatedDate().toLocalDate().toString())
                .param("dateTo", target.getCreatedDate().toLocalDate().toString())
                .param("expiresFrom", expiry.minusSeconds(1).toString())
                .param("expiresTo", expiry.plusSeconds(1).toString());
        var data = call(request, "seller", 200).path("data");
        assertThat(data.path("totalElements").asLong()).isEqualTo(1);
        assertThat(data.path("content").get(0).path("id").asLong()).isEqualTo(target.getId());
        var byKey = call(get(RESERVATIONS).param("companyId", "1").param("q", "FILTER-KEY"),
                "seller", 200).path("data");
        assertThat(byKey.path("totalElements").asLong()).isEqualTo(1);
    }

    @Test
    void privateListsRequireSellerOwnershipAndRejectForeignFilterIds() throws Exception {
        var foreignWarehouse = warehouse(2L);
        var foreignProduct = product(2L, 999L, "FOREIGN");
        for (String path : List.of(OPERATIONS, RESERVATIONS)) {
            call(get(path).param("companyId", "1"), null, 401);
            call(get(path).param("companyId", "1"), "buyer", 403);
            call(get(path).param("companyId", "1"), "seller-no-profile", 403);
            call(get(path).param("companyId", "2"), "seller", 403);
            call(get(path).param("companyId", "1").param("warehouseId", foreignWarehouse.getId().toString()),
                    "seller", 403);
            call(get(path).param("companyId", "1").param("inventoryProductId", foreignProduct.getId().toString()),
                    "seller", 403);
            call(get(path).param("companyId", "1").param("warehouseId", "999999"), "seller", 404);
            call(get(path).param("companyId", "1").param("inventoryProductId", "999999"), "seller", 404);
        }
    }

    @Test
    void privateListsFailClosedWhenCompanyServiceCannotVerifyOwnership() throws Exception {
        when(companies.ownershipCheck(1L, 42L)).thenReturn(null);
        for (String path : List.of(OPERATIONS, RESERVATIONS))
            call(get(path).param("companyId", "1"), "seller", 503);
        when(companies.ownershipCheck(1L, 42L)).thenReturn(company(2L, true));
        for (String path : List.of(OPERATIONS, RESERVATIONS))
            call(get(path).param("companyId", "1"), "seller", 503);
    }

    @Test
    void invalidListFiltersReturnBadRequest() throws Exception {
        for (String path : List.of(OPERATIONS, RESERVATIONS)) {
            call(get(path), "seller", 400);
            for (var entry : Map.of("companyId", "0", "warehouseId", "-1", "inventoryProductId", "0",
                    "productId", "0", "orderId", "0", "createdBy", "0", "page", "0", "perPage", "101").entrySet()) {
                var request = get(path).param("companyId", "1");
                request.queryParam(entry.getKey(), entry.getValue());
                if (entry.getKey().equals("companyId")) request = get(path).param("companyId", "0");
                call(request, "seller", 400);
            }
            call(get(path).param("companyId", "1").param("dateFrom", "2026-11-02").param("dateTo", "2026-11-01"),
                    "seller", 400);
            call(get(path).param("companyId", "1").param("q", "x".repeat(161)), "seller", 400);
            call(get(path).param("companyId", "1").param("dateFrom", "wrong"), "seller", 400);
        }
        call(get(OPERATIONS).param("companyId", "1").param("type", "WRONG"), "seller", 400);
        call(get(OPERATIONS).param("companyId", "1").param("reservationId", "0"), "seller", 400);
        call(get(RESERVATIONS).param("companyId", "1").param("status", "WRONG"), "seller", 400);
        call(get(RESERVATIONS).param("companyId", "1")
                .param("expiresFrom", "2030-01-02T00:00:00Z").param("expiresTo", "2030-01-01T00:00:00Z"), "seller", 400);
    }

    @Test
    void availabilityAggregatesOnlyActiveOwnedStocksAndKeepsUntrackedAndZeroDistinct() throws Exception {
        var firstWarehouse = warehouse(1L);
        var secondWarehouse = warehouse(1L);
        var product = product(1L, 501L, "MAIN");
        stock(firstWarehouse, product, "10.500", "2.250");
        stock(secondWarehouse, product, "5.000", "1.000");
        stock(firstWarehouse, product(1L, 502L, "ZERO"), "3", "3");
        var archivedProduct = product(1L, 503L, "ARCHIVED");
        archivedProduct.setStatus(InventoryProductStatus.ARCHIVED); products.saveAndFlush(archivedProduct);
        stock(firstWarehouse, archivedProduct, "100", "0");
        var deletedProduct = product(1L, 504L, "DELETED");
        deletedProduct.setDeleted(true); products.saveAndFlush(deletedProduct);
        stock(firstWarehouse, deletedProduct, "100", "0");
        var archivedWarehouse = warehouse(1L);
        archivedWarehouse.setStatus(WarehouseStatus.ARCHIVED); warehouses.saveAndFlush(archivedWarehouse);
        stock(archivedWarehouse, product, "1000", "0");
        var deletedWarehouse = warehouse(1L);
        deletedWarehouse.setDeleted(true); warehouses.saveAndFlush(deletedWarehouse);
        stock(deletedWarehouse, product, "1000", "0");
        var deletedStock = stock(secondWarehouse, product(1L, 505L, "NO-STOCK"), "100", "0");
        deletedStock.setDeleted(true); stocks.saveAndFlush(deletedStock);
        stock(warehouse(2L), product(2L, 501L, "FOREIGN-SAME-CARD"), "1000", "0");

        var data = availability(Map.of("companyId", 1, "productIds", List.of(501, 502, 503, 504, 505, 999, 501)),
                "service", 200).path("data");
        assertThat(data.size()).isEqualTo(6);
        assertThat(data.get(0).path("availableQuantity").decimalValue()).isEqualByComparingTo("12.250");
        assertThat(data.get(0).path("tracked").asBoolean()).isTrue();
        assertThat(data.get(0).path("inStock").asBoolean()).isTrue();
        assertThat(data.get(0).has("onHand")).isFalse();
        assertThat(data.get(0).has("reserved")).isFalse();
        for (int index = 1; index < data.size(); index++) {
            assertThat(data.get(index).path("availableQuantity").decimalValue()).isEqualByComparingTo("0");
            assertThat(data.get(index).path("inStock").asBoolean()).isFalse();
        }
        assertThat(data.get(1).path("tracked").asBoolean()).isTrue();
        assertThat(data.get(2).path("tracked").asBoolean()).isTrue();
        assertThat(data.get(3).path("tracked").asBoolean()).isFalse();
        assertThat(data.get(4).path("tracked").asBoolean()).isTrue();
        assertThat(data.get(5).path("tracked").asBoolean()).isFalse();
    }

    @Test
    void availabilityRequiresJwtButAcceptsServiceTokenWithoutSellerProfile() throws Exception {
        var body = Map.of("companyId", 1, "productIds", List.of(501));
        availability(body, null, 401);
        availability(body, "buyer", 403);
        availability(body, "service", 200);
        verify(companies, never()).ownershipCheck(anyLong(), anyLong());
    }

    @Test
    void ordinarySellerCanReadAvailabilityOnlyForOwnActiveCompany() throws Exception {
        availability(Map.of("companyId", 1, "productIds", List.of(501)), "seller", 200);
        availability(Map.of("companyId", 2, "productIds", List.of(501)), "seller", 403);
        availability(Map.of("companyId", 1, "productIds", List.of(501)), "seller-no-profile", 403);
    }

    @Test
    void availabilityValidatesBatchAndCompanyAndProductIds() throws Exception {
        for (String body : List.of("{}", "{\"companyId\":0,\"productIds\":[1]}",
                "{\"companyId\":1,\"productIds\":[]}", "{\"companyId\":1,\"productIds\":[null]}",
                "{\"companyId\":1,\"productIds\":[0]}", "{\"companyId\":1,\"productIds\":[-1]}"))
            call(post(AVAILABILITY).contentType("application/json").content(body), "service", 400);
        availability(Map.of("companyId", 1, "productIds", java.util.Collections.nCopies(101, 501)), "service", 400);
    }

    private JsonNode availability(Object body, String bearer, int status) throws Exception {
        return call(post(AVAILABILITY).contentType("application/json").content(json.writeValueAsString(body)), bearer, status);
    }

    private JsonNode call(MockHttpServletRequestBuilder request, String bearer, int status) throws Exception {
        if (bearer != null) request.header("Authorization", "Bearer " + bearer);
        var response = mvc.perform(request).andReturn().getResponse();
        assertThat(response.getStatus()).as(response.getContentAsString()).isEqualTo(status);
        return response.getContentAsString().isBlank() ? json.createObjectNode() : json.readTree(response.getContentAsString());
    }

    private Jwt token(String role, boolean profile) {
        var builder = Jwt.withTokenValue("token").header("alg", "RS256")
                .claim("realm_access", Map.of("roles", List.of(role)));
        if (profile) builder.claim("profileId", 42L);
        return builder.build();
    }

    private CompanyOwnershipResponse company(Long id, boolean owner) {
        var company = new CompanyOwnershipResponse(); company.setCompanyId(id);
        company.setExists(true); company.setOwner(owner); company.setActive(true);
        return company;
    }

    private Warehouse warehouse(Long companyId) {
        var warehouse = new Warehouse(); warehouse.setCompanyId(companyId); warehouse.setName("Ombor");
        return warehouses.saveAndFlush(warehouse);
    }

    private InventoryProduct product(Long companyId, Long cardId, String sku) {
        var product = new InventoryProduct(); product.setCompanyId(companyId);
        product.setCatalogCardId(cardId); product.setSku(sku);
        return products.saveAndFlush(product);
    }

    private WarehouseStock stock(Warehouse warehouse, InventoryProduct product, String onHand, String reserved) {
        var stock = new WarehouseStock(); stock.setWarehouse(warehouse); stock.setInventoryProduct(product);
        stock.setOnHand(new BigDecimal(onHand)); stock.setReserved(new BigDecimal(reserved));
        return stocks.saveAndFlush(stock);
    }

    private StockOperation operation(WarehouseStock stock, StockOperationType type, String reason) {
        var operation = new StockOperation(); operation.setStock(stock); operation.setType(type);
        operation.setQuantityBefore(BigDecimal.TEN); operation.setQuantityChange(BigDecimal.ONE);
        operation.setQuantityAfter(new BigDecimal("11")); operation.setReservedSnapshot(new BigDecimal("2"));
        operation.setReason(reason); operation.setCreatedBy(42L);
        // companyId null bo'lgan legacy yozuvlar ham ko'rinishi kerak.
        return operations.saveAndFlush(operation);
    }

    private void date(StockOperation operation, String value) {
        jdbc.update("update stock_operations set created_date=? where id=?",
                Timestamp.valueOf(LocalDateTime.parse(value)), operation.getId());
    }

    private StockReservation reservation(Long companyId, String key, ReservationStatus status,
                                         Long orderId, List<WarehouseStock> positions) {
        var reservation = new StockReservation(); reservation.setCompanyId(companyId);
        reservation.setIdempotencyKey(key); reservation.setStatus(status); reservation.setOrderId(orderId);
        reservation.setReason("Buyurtma uchun rezerv"); reservation.setCreatedBy(42L);
        reservation.setExpiresAt(Instant.now().plusSeconds(3600));
        reservation = reservations.saveAndFlush(reservation);
        for (var stock : positions) {
            var item = new StockReservationItem(); item.setReservation(reservation); item.setStock(stock);
            item.setQuantity(BigDecimal.ONE); items.saveAndFlush(item);
        }
        return reservation;
    }
}
