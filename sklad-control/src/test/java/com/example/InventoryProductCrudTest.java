package com.example;

import com.example.config.client.*;
import com.example.dto.ApiResponse;
import com.example.dto.internal.CompanyOwnershipResponse;
import com.example.entity.*;
import com.example.enums.WarehouseStatus;
import com.example.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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
    "spring.liquibase.enabled=false","spring.jpa.hibernate.ddl-auto=create-drop",
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
    @Test void ownershipIsCheckedForReadUpdateArchiveAndRestore() throws Exception {
        Long id=create();
        var product=products.findById(id).orElseThrow(); product.setCompanyId(2L); products.saveAndFlush(product);
        mvc.perform(get("/api/v1/control/products/"+id).header("Authorization","Bearer seller")).andExpect(status().isForbidden());
        mvc.perform(patch("/api/v1/control/products/"+id).header("Authorization","Bearer seller")
            .contentType("application/json").content("{\"showInMarket\":true}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/control/products/"+id+"/archive").header("Authorization","Bearer seller")).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/control/products/"+id+"/restore").header("Authorization","Bearer seller")).andExpect(status().isForbidden());
    }
}
