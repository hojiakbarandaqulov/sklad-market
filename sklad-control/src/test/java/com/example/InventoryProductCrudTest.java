package com.example;

import com.example.config.client.*;
import com.example.dto.ApiResponse;
import com.example.dto.internal.CompanyOwnershipResponse;
import com.example.entity.*;
import com.example.enums.WarehouseStatus;
import com.example.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import feign.FeignException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Haqiqiy controller, JPA va transaction; faqat tashqi servislar hamda JWT decoder almashtiriladi. */
@SpringBootTest(properties={
    "spring.liquibase.enabled=true","spring.jpa.hibernate.ddl-auto=validate",
    "spring.datasource.url=jdbc:h2:mem:control-products;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa","spring.datasource.password=","spring.datasource.driver-class-name=org.h2.Driver"
})
@AutoConfigureMockMvc @ActiveProfiles("test")
class InventoryProductCrudTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired WarehouseRepository warehouses;
    @Autowired InventoryProductRepository products;
    @Autowired WarehouseStockRepository stocks;
    @Autowired StockOperationRepository operations;
    @MockitoBean JwtDecoder decoder;
    @MockitoBean CompanyClient companies;
    @MockitoBean ProductClient cards;
    Long warehouseId;
    JsonNode card;

    @BeforeEach void setup() throws Exception {
        operations.deleteAll(); stocks.deleteAll(); products.deleteAll(); warehouses.deleteAll();
        when(decoder.decode("seller")).thenReturn(Jwt.withTokenValue("seller").header("alg","RS256")
            .subject("seller").issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(600))
            .claim("profileId",42L).claim("realm_access",Map.of("roles",List.of("SELLER"))).build());
        var company=new CompanyOwnershipResponse();
        company.setCompanyId(1L); company.setExists(true); company.setOwner(true); company.setActive(true);
        when(companies.ownershipCheck(1L,42L)).thenReturn(company);
        var other=new CompanyOwnershipResponse();
        other.setCompanyId(2L); other.setExists(true); other.setOwner(false); other.setActive(true);
        when(companies.ownershipCheck(2L,42L)).thenReturn(other);
        Warehouse warehouse=new Warehouse();
        warehouse.setCompanyId(1L); warehouse.setName("Asosiy ombor"); warehouse.setStatus(WarehouseStatus.ACTIVE);
        warehouseId=warehouses.saveAndFlush(warehouse).getId();
        card=json.readTree("{\"id\":100,\"companyId\":1,\"name\":\"Polat\",\"status\":\"PENDING\",\"isActive\":false}");
        when(cards.cards(eq(1L),anyList(),anyString())).thenReturn(ApiResponse.successResponse(List.of(card)));
        when(cards.visibility(eq(100L),anyBoolean(),anyString())).thenReturn(ApiResponse.successResponse(card));
        when(cards.create(any(),anyString(),anyBoolean(),anyString())).thenReturn(ApiResponse.successResponse(card));
        when(cards.update(anyLong(),any(),anyString())).thenReturn(ApiResponse.successResponse(card));
        when(cards.matching(eq(1L),any(),any(),anyString())).thenReturn(ApiResponse.successResponse(List.of(100L)));
    }
    String existing() {
        return "{\"companyId\":1,\"warehouseId\":"+warehouseId+
            ",\"sku\":\"ST-1\",\"productId\":100,\"openingQuantity\":1.125,\"minimumStock\":2}";
    }
    Long create() throws Exception {
        String body=mvc.perform(post("/api/v1/control/products").header("Authorization","Bearer seller")
            .contentType("application/json").content(existing()))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).path("data").path("id").asLong();
    }
    @Test void creationPersistsFractionalStockAndOpeningLedger() throws Exception {
        create();
        assertThat(stocks.findAll().get(0).getOnHand()).isEqualByComparingTo("1.125");
        assertThat(operations.findAll().get(0).getQuantityChange()).isEqualByComparingTo("1.125");
        assertThat(products.findAll().get(0).getCreatedBy()).isEqualTo(42L);
    }
    @Test void retryDoesNotDuplicateCardStockOrOpeningOperation() throws Exception {
        Long first=create();
        assertThat(create()).isEqualTo(first);
        assertThat(products.count()).isEqualTo(1);
        assertThat(stocks.count()).isEqualTo(1);
        assertThat(operations.count()).isEqualTo(1);
        verify(cards,never()).create(any(),anyString(),anyBoolean(),anyString());
    }
    @Test void sameCardInSecondWarehouseReusesInventoryProduct() throws Exception {
        Long id=create();
        var warehouse=new Warehouse();
        warehouse.setCompanyId(1L); warehouse.setName("Ikkinchi ombor");
        Long second=warehouses.saveAndFlush(warehouse).getId();
        mvc.perform(post("/api/v1/control/products").header("Authorization","Bearer seller")
            .contentType("application/json").content(existing().replace("\"warehouseId\":"+warehouseId,
                "\"warehouseId\":"+second))).andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(id));
        assertThat(products.count()).isEqualTo(1);
        assertThat(stocks.count()).isEqualTo(2);
    }
    @Test void foreignRemoteCardAndAmbiguousCreateAreRejected() throws Exception {
        when(cards.cards(eq(1L),anyList(),anyString())).thenReturn(ApiResponse.successResponse(
            List.of(json.readTree("{\"id\":100,\"companyId\":2}"))));
        mvc.perform(post("/api/v1/control/products").header("Authorization","Bearer seller")
            .contentType("application/json").content(existing())).andExpect(status().isServiceUnavailable());
        mvc.perform(post("/api/v1/control/products").header("Authorization","Bearer seller")
            .contentType("application/json").content(existing().replace(",\"productId\":100","")))
            .andExpect(status().isBadRequest());
        assertThat(products.count()).isZero();
    }
    @Test void fullyReservedStockIsUnavailableAndCannotBeArchived() throws Exception {
        Long id=create();
        var stock=stocks.findAll().get(0);
        stock.setReserved(new java.math.BigDecimal("1.125"));
        stocks.saveAndFlush(stock);
        mvc.perform(post("/api/v1/control/products/"+id+"/archive").header("Authorization","Bearer seller"))
            .andExpect(status().isConflict());
        mvc.perform(get("/api/v1/control/products").param("companyId","1").param("stockState","OUT")
            .header("Authorization","Bearer seller")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.totalElements").value(1));
    }
    @Test void newCardUsesStableSkuAndStartsHidden() throws Exception {
        String body=existing().replace("\"productId\":100","\"product\":{\"companyId\":1,\"categoryId\":5,\"name\":\"Polat\","
            +"\"description\":\"Tavsif\",\"priceType\":\"FIXED\",\"price\":12,\"currency\":\"UZS\",\"minProduct\":1,\"phone\":\"99890\"}");
        mvc.perform(post("/api/v1/control/products").header("Authorization","Bearer seller")
            .contentType("application/json").content(body)).andExpect(status().isOk());
        verify(cards).create(any(),eq("ST-1"),eq(false),eq("UZ"));
    }
    @Test void archivedWarehouseRejectsCreationBeforeRemoteCall() throws Exception {
        var warehouse=warehouses.findById(warehouseId).orElseThrow();
        warehouse.setStatus(WarehouseStatus.ARCHIVED); warehouses.saveAndFlush(warehouse);
        mvc.perform(post("/api/v1/control/products").header("Authorization","Bearer seller")
            .contentType("application/json").content(existing())).andExpect(status().isConflict());
        assertThat(products.count()).isZero(); verifyNoInteractions(cards);
    }
    @Test void foreignWarehouseCannotBeUsed() throws Exception {
        var warehouse=warehouses.findById(warehouseId).orElseThrow();
        warehouse.setCompanyId(2L); warehouses.saveAndFlush(warehouse);
        mvc.perform(post("/api/v1/control/products").header("Authorization","Bearer seller")
            .contentType("application/json").content(existing())).andExpect(status().isForbidden());
        verifyNoInteractions(cards);
    }
    @Test void balanceBlocksProductArchive() throws Exception {
        Long id=create();
        mvc.perform(post("/api/v1/control/products/"+id+"/archive").header("Authorization","Bearer seller"))
            .andExpect(status().isConflict());
        assertThat(products.findById(id).orElseThrow().getStatus().name()).isEqualTo("ACTIVE");
    }
    @Test void emptyProductCanBeArchivedAndRestoredWithoutRepublishing() throws Exception {
        Long id=create();
        var stock=stocks.findAll().get(0); stock.setOnHand(java.math.BigDecimal.ZERO); stocks.saveAndFlush(stock);
        mvc.perform(post("/api/v1/control/products/"+id+"/archive").header("Authorization","Bearer seller"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("ARCHIVED"));
        clearInvocations(cards);
        mvc.perform(post("/api/v1/control/products/"+id+"/restore").header("Authorization","Bearer seller"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("ACTIVE"));
        verify(cards,never()).visibility(anyLong(),anyBoolean(),anyString());
    }
    @Test void listAndDetailReturnSharedCardAndCalculatedAvailability() throws Exception {
        Long id=create();
        mvc.perform(get("/api/v1/control/products").param("companyId","1").param("stockState","LOW")
            .header("Authorization","Bearer seller")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.totalElements").value(1))
            .andExpect(jsonPath("$.data.content[0].stocks[0].available").value(1.125));
        mvc.perform(get("/api/v1/control/products/"+id).header("Authorization","Bearer seller"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.product.id").value(100));
    }
    @Test void emptyPatchAndNegativeQuantityAreRejected() throws Exception {
        Long id=create();
        mvc.perform(patch("/api/v1/control/products/"+id).header("Authorization","Bearer seller")
            .contentType("application/json").content("{}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/control/products").header("Authorization","Bearer seller")
            .contentType("application/json").content(existing().replace("1.125","-1")))
            .andExpect(status().isBadRequest());
    }
    @Test void patchVisibilityCannotOverwriteQuantityOrCompany() throws Exception {
        Long id=create();
        mvc.perform(patch("/api/v1/control/products/"+id).header("Authorization","Bearer seller")
            .contentType("application/json").content("{\"showInMarket\":true,\"onHand\":999,\"companyId\":2}"))
            .andExpect(status().isOk());
        verify(cards).visibility(100L,true,"UZ");
        assertThat(stocks.findAll().get(0).getOnHand()).isEqualByComparingTo("1.125");
        assertThat(products.findById(id).orElseThrow().getCompanyId()).isEqualTo(1);
    }
    @Test void badRemoteResponseRollsBackLocalRows() throws Exception {
        when(cards.visibility(eq(100L),anyBoolean(),anyString())).thenReturn(null);
        mvc.perform(post("/api/v1/control/products").header("Authorization","Bearer seller")
            .contentType("application/json").content(existing())).andExpect(status().isServiceUnavailable());
        assertThat(products.count()).isZero(); assertThat(stocks.count()).isZero(); assertThat(operations.count()).isZero();
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void upstreamFailureLogsOperationStatusAndResponseTraceId(CapturedOutput output) throws Exception {
        FeignException failure = mock(FeignException.class);
        when(failure.status()).thenReturn(500);
        when(failure.getMessage()).thenReturn("private upstream response");
        when(cards.visibility(eq(100L), anyBoolean(), anyString())).thenThrow(failure);

        String body = mvc.perform(post("/api/v1/control/products").header("Authorization", "Bearer seller")
                        .contentType("application/json").content(existing()))
                .andExpect(status().isServiceUnavailable())
                .andReturn().getResponse().getContentAsString();
        String traceId = json.readTree(body).path("trace_id").asText();

        assertThat(traceId).isNotBlank();
        assertThat(output.getAll()).contains("trace_id=" + traceId
                + ", service=product-service, operation=visibility, upstream_status=500");
        assertThat(output.getAll()).doesNotContain("private upstream response", "Bearer seller");
    }

    @Test void ownershipIsCheckedForReadUpdateArchiveAndRestore() throws Exception {
        Long id=create();
        var product=products.findById(id).orElseThrow(); product.setCompanyId(2L); products.saveAndFlush(product);
        mvc.perform(get("/api/v1/control/products/"+id).header("Authorization","Bearer seller")).andExpect(status().isForbidden());
        mvc.perform(patch("/api/v1/control/products/"+id).header("Authorization","Bearer seller")
            .contentType("application/json").content("{\"showInMarket\":true}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/control/products/"+id+"/archive").header("Authorization","Bearer seller")).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/control/products/"+id+"/restore").header("Authorization","Bearer seller")).andExpect(status().isForbidden());
    }

    @Test void saleFilterRunsBeforePaginationAndCombinesWithInventoryStatusAndCompany() throws Exception {
        saleProduct(100L, "ON-1", 1L, com.example.enums.InventoryProductStatus.ACTIVE);
        saleProduct(200L, "HIDDEN", 1L, com.example.enums.InventoryProductStatus.ACTIVE);
        saleProduct(300L, "ON-2", 1L, com.example.enums.InventoryProductStatus.ACTIVE);
        saleProduct(500L, "ARCHIVED", 1L, com.example.enums.InventoryProductStatus.ARCHIVED);
        saleProduct(900L, "FOREIGN", 2L, com.example.enums.InventoryProductStatus.ACTIVE);
        stubSaleCards();
        when(cards.matchingBySaleStatus(1L, com.example.enums.SaleStatus.ON_SALE, "UZ"))
                .thenReturn(ApiResponse.successResponse(List.of(100L, 300L, 500L, 900L)));
        mvc.perform(get("/api/v1/control/products").header("Authorization", "Bearer seller")
                .param("companyId", "1").param("status", "ACTIVE").param("saleStatus", "ON_SALE")
                .param("page", "1").param("perPage", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.totalPages").value(2))
                .andExpect(jsonPath("$.data.content[0].productId").value(300));
        mvc.perform(get("/api/v1/control/products").header("Authorization", "Bearer seller")
                .param("companyId", "1").param("status", "ACTIVE").param("saleStatus", "ON_SALE")
                .param("page", "2").param("perPage", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.content[0].productId").value(100));
    }

    @Test void emptySaleMatchesReturnAnEmptyPageWithoutFetchingCards() throws Exception {
        saleProduct(100L, "ON-1", 1L, com.example.enums.InventoryProductStatus.ACTIVE);
        when(cards.matchingBySaleStatus(1L, com.example.enums.SaleStatus.ON_SALE, "UZ"))
                .thenReturn(ApiResponse.successResponse(List.of()));
        mvc.perform(get("/api/v1/control/products").header("Authorization", "Bearer seller")
                .param("companyId", "1").param("saleStatus", "ON_SALE"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(0))
                .andExpect(jsonPath("$.data.content").isEmpty());
        verify(cards, never()).cards(anyLong(), anyList(), anyString());
    }

    @Test void offSaleFilterAndSkuSearchAreAppliedTogether() throws Exception {
        saleProduct(100L, "MATCH-ON", 1L, com.example.enums.InventoryProductStatus.ACTIVE);
        saleProduct(200L, "MATCH-OFF", 1L, com.example.enums.InventoryProductStatus.ACTIVE);
        saleProduct(300L, "OTHER-OFF", 1L, com.example.enums.InventoryProductStatus.ACTIVE);
        stubSaleCards();
        when(cards.matchingBySaleStatus(1L, com.example.enums.SaleStatus.OFF_SALE, "UZ"))
                .thenReturn(ApiResponse.successResponse(List.of(200L, 300L)));
        when(cards.matching(1L, "MATCH", null, "UZ"))
                .thenReturn(ApiResponse.successResponse(List.of()));
        mvc.perform(get("/api/v1/control/products").header("Authorization", "Bearer seller")
                .param("companyId", "1").param("saleStatus", "OFF_SALE").param("q", "MATCH"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].productId").value(200));
    }

    @Test void omittedSaleFilterKeepsExistingListBehavior() throws Exception {
        saleProduct(100L, "ACTIVE", 1L, com.example.enums.InventoryProductStatus.ACTIVE);
        saleProduct(200L, "ARCHIVED", 1L, com.example.enums.InventoryProductStatus.ARCHIVED);
        stubSaleCards();
        mvc.perform(get("/api/v1/control/products").header("Authorization", "Bearer seller")
                .param("companyId", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(2));
        verify(cards, never()).matchingBySaleStatus(anyLong(), any(), anyString());
    }

    @Test void invalidSaleStatusAndForeignCompanyAreRejectedBeforeRemoteSaleLookup() throws Exception {
        mvc.perform(get("/api/v1/control/products").header("Authorization", "Bearer seller")
                .param("companyId", "1").param("saleStatus", "ACTIVE"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/control/products").header("Authorization", "Bearer seller")
                .param("companyId", "2").param("saleStatus", "ON_SALE"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(cards);
    }

    @Test void saleLookupFailureDoesNotFallBackToUnfilteredProducts() throws Exception {
        saleProduct(100L, "ON-1", 1L, com.example.enums.InventoryProductStatus.ACTIVE);
        when(cards.matchingBySaleStatus(1L, com.example.enums.SaleStatus.ON_SALE, "UZ")).thenReturn(null);
        mvc.perform(get("/api/v1/control/products").header("Authorization", "Bearer seller")
                .param("companyId", "1").param("saleStatus", "ON_SALE"))
                .andExpect(status().isServiceUnavailable());
        verify(cards, never()).cards(anyLong(), anyList(), anyString());
    }

    private void saleProduct(Long cardId, String sku, Long companyId, com.example.enums.InventoryProductStatus status) {
        var product = new InventoryProduct();
        product.setCompanyId(companyId); product.setCatalogCardId(cardId);
        product.setSku(sku); product.setStatus(status);
        products.saveAndFlush(product);
    }

    private void stubSaleCards() {
        when(cards.cards(eq(1L), anyList(), anyString())).thenAnswer(call -> {
            List<Long> ids = call.getArgument(1);
            return ApiResponse.successResponse(ids.stream().map(id -> (JsonNode) json.createObjectNode()
                    .put("id", id).put("companyId", 1L)).toList());
        });
    }
}
