package com.example;

import com.example.config.client.CompanyClient;
import com.example.config.client.ProductClient;
import com.example.dto.internal.CompanyOwnershipResponse;
import com.example.entity.*;
import com.example.enums.*;
import com.example.repository.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
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
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/** Haqiqiy HTTP, JPA va transaction sinovi. Jadval faqat izolyatsiyalangan H2 bazasida yaratiladi. */
@SpringBootTest(properties={
    "spring.liquibase.enabled=false","spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.datasource.url=jdbc:h2:mem:adjustments;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
    "spring.datasource.username=sa","spring.datasource.password=","spring.datasource.driver-class-name=org.h2.Driver",
    "control.reservations.expiry-delay-ms=3600000"
})
@AutoConfigureMockMvc @ActiveProfiles("test")
class StockAdjustmentApiTest {
    static final String ROOT="/api/v1/control/operations/adjustments";
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
        operations.deleteAll(); stocks.deleteAll(); products.deleteAll(); warehouses.deleteAll();
        when(decoder.decode("seller")).thenReturn(token("SELLER",true));
        when(decoder.decode("buyer")).thenReturn(token("BUYER",true));
        when(decoder.decode("no-profile")).thenReturn(token("SELLER",false));
        when(companies.ownershipCheck(1L,42L)).thenReturn(company(1L,true));
        when(companies.ownershipCheck(2L,42L)).thenReturn(company(2L,false));
        var warehouse=new Warehouse(); warehouse.setCompanyId(1L); warehouse.setName("Asosiy ombor");
        warehouseId=warehouses.saveAndFlush(warehouse).getId();
        var product=new InventoryProduct(); product.setCompanyId(1L); product.setCatalogCardId(100L); product.setSku("ST-1");
        productId=products.saveAndFlush(product).getId();
        var stock=new WarehouseStock(); stock.setWarehouse(warehouse); stock.setInventoryProduct(product);
        stock.setOnHand(new BigDecimal("100")); stock.setReserved(new BigDecimal("20"));
        stockId=stocks.saveAndFlush(stock).getId();
    }
    Jwt token(String role,boolean profile) {
        var builder=Jwt.withTokenValue("token").header("alg","RS256")
                .claim("realm_access",Map.of("roles",List.of(role)));
        if(profile) builder.claim("profileId",42L);
        return builder.build();
    }
    CompanyOwnershipResponse company(Long id,boolean owner) {
        var result=new CompanyOwnershipResponse(); result.setCompanyId(id); result.setExists(true);
        result.setOwner(owner); result.setActive(true); return result;
    }
    String request(String key,String quantity) throws Exception {
        return json.writeValueAsString(Map.of("warehouseId",warehouseId,"inventoryProductId",productId,
                "actualQuantity",new BigDecimal(quantity),"reason","Sanash natijasi","idempotencyKey",key));
    }
    JsonNode call(String body,int status) throws Exception { return call(body,status,"seller","UZ"); }
    JsonNode call(String body,int expected,String token,String lang) throws Exception {
        var builder=post(ROOT).contentType("application/json").header("Accept-Language",lang).content(body);
        if(token!=null) builder.header("Authorization","Bearer "+token);
        var response=mvc.perform(builder).andReturn().getResponse();
        assertThat(response.getStatus()).as(response.getContentAsString()).isEqualTo(expected);
        return response.getContentAsString().isBlank()?json.nullNode():json.readTree(response.getContentAsString());
    }
    @Test void decreasingStockPreservesReserveAndWritesSignedLedger() throws Exception {
        var result=call(request("decrease","95.125"),200).path("data");
        assertThat(result.path("quantityBefore").decimalValue()).isEqualByComparingTo("100");
        assertThat(result.path("quantityChange").decimalValue()).isEqualByComparingTo("-4.875");
        assertThat(result.path("quantityAfter").decimalValue()).isEqualByComparingTo("95.125");
        assertThat(result.path("available").decimalValue()).isEqualByComparingTo("75.125");
        assertThat(result.path("type").asText()).isEqualTo("ADJUSTMENT");
        assertThat(result.path("createdBy").asLong()).isEqualTo(42L);
        assertThat(result.path("createdDate").isTextual()).isTrue();
        assertThat(stocks.findById(stockId).orElseThrow().getReserved()).isEqualByComparingTo("20");
        assertThat(operations.count()).isEqualTo(1);
    }
    @Test void increasingStockAndSettingExactlyReserveAreAllowed() throws Exception {
        call(request("increase","150"),200);
        call(request("reserve-limit","20"),200);
        assertThat(stocks.findById(stockId).orElseThrow().getAvailable()).isEqualByComparingTo("0");
        assertThat(operations.count()).isEqualTo(2);
    }
    @Test void settingZeroIsAllowedWhenNoReserveExists() throws Exception {
        var stock=stocks.findById(stockId).orElseThrow(); stock.setReserved(BigDecimal.ZERO); stocks.saveAndFlush(stock);
        call(request("zero","0"),200);
        assertThat(stocks.findById(stockId).orElseThrow().getOnHand()).isEqualByComparingTo("0");
    }
    @Test void belowReserveAndNoChangeDoNotWriteAnything() throws Exception {
        call(request("below","19.999"),409);
        call(request("same","100"),409);
        assertThat(operations.count()).isZero();
        assertThat(stocks.findById(stockId).orElseThrow().getOnHand()).isEqualByComparingTo("100");
    }
    @Test void replayUsesOriginalSnapshotAfterLaterStockAndReserveChanges() throws Exception {
        var first=call(request("retry","95"),200).path("data");
        call(request("later","85"),200);
        var stock=stocks.findById(stockId).orElseThrow(); stock.setReserved(new BigDecimal("22")); stocks.saveAndFlush(stock);
        var second=call(request("retry","95.000"),200).path("data");
        assertThat(second).isEqualTo(first);
        assertThat(stocks.findById(stockId).orElseThrow().getOnHand()).isEqualByComparingTo("85");
        assertThat(operations.count()).isEqualTo(2);
    }
    @Test void sameKeyCannotBeReusedWithDifferentPayload() throws Exception {
        call(request("key","95"),200);
        call(request("key","90"),409);
        call(request("key","95").replace("Sanash natijasi","Boshqa sabab"),409);
        assertThat(operations.count()).isEqualTo(1);
    }
    @Test void replayStillRequiresCurrentOwnership() throws Exception {
        call(request("owned","95"),200);
        when(companies.ownershipCheck(1L,42L)).thenReturn(company(1L,false));
        call(request("owned","95"),403);
    }
    @Test void foreignWarehouseAndProductAreRejected() throws Exception {
        var warehouse=warehouses.findById(warehouseId).orElseThrow(); warehouse.setCompanyId(2L); warehouses.saveAndFlush(warehouse);
        call(request("foreign-warehouse","95"),403);
        warehouse.setCompanyId(1L); warehouses.saveAndFlush(warehouse);
        var product=products.findById(productId).orElseThrow(); product.setCompanyId(2L); products.saveAndFlush(product);
        call(request("foreign-product","95"),403);
        assertThat(operations.count()).isZero();
    }
    @Test void inactiveAndDeletedRecordsAreRejected() throws Exception {
        var warehouse=warehouses.findById(warehouseId).orElseThrow(); warehouse.setStatus(WarehouseStatus.ARCHIVED); warehouses.saveAndFlush(warehouse);
        call(request("inactive-warehouse","95"),409);
        warehouse.setStatus(WarehouseStatus.ACTIVE); warehouses.saveAndFlush(warehouse);
        var product=products.findById(productId).orElseThrow(); product.setStatus(InventoryProductStatus.ARCHIVED); products.saveAndFlush(product);
        call(request("inactive-product","95"),409);
        product.setStatus(InventoryProductStatus.ACTIVE); products.saveAndFlush(product);
        var stock=stocks.findById(stockId).orElseThrow(); stock.setDeleted(true); stocks.saveAndFlush(stock);
        call(request("deleted-stock","95"),404);
    }
    @Test void invalidAmountsAndMissingFieldsAreRejected() throws Exception {
        for(String value:List.of("-1","1.1234","10000000000000000"))
            call(request("invalid",value),400);
        call("{}",400);
        call(request("blank","95").replace("Sanash natijasi","   "),400);
        assertThat(operations.count()).isZero();
    }
    @Test void authenticationAndRoleAreRequired() throws Exception {
        call(request("auth","95"),401,null,"UZ");
        call(request("auth","95"),403,"buyer","UZ");
        call(request("auth","95"),403,"no-profile","UZ");
    }
    @Test void translatedConflictMessagesAreReturned() throws Exception {
        assertThat(call(request("ru","1"),409,"seller","RU").path("message").asText())
                .contains("зарезервированного");
        assertThat(call(request("en","1"),409,"seller","EN").path("message").asText())
                .contains("reserved");
    }
    @Test void ledgerFailureRollsBackStockUpdate() throws Exception {
        doThrow(new IllegalStateException("Test ledger failure")).when(operations).saveAndFlush(any(StockOperation.class));
        call(request("rollback","95"),500);
        assertThat(stocks.findById(stockId).orElseThrow().getOnHand()).isEqualByComparingTo("100");
        assertThat(operations.count()).isZero();
    }
    @Test void concurrentRetriesWriteOneAdjustment() throws Exception {
        String body=request("parallel","95");
        ExecutorService pool=Executors.newFixedThreadPool(2);
        CountDownLatch start=new CountDownLatch(1);
        try {
            Callable<Long> task=() -> { start.await(); return call(body,200).path("data").path("id").asLong(); };
            Future<Long> one=pool.submit(task); Future<Long> two=pool.submit(task); start.countDown();
            assertThat(one.get(30,TimeUnit.SECONDS)).isEqualTo(two.get(30,TimeUnit.SECONDS));
            assertThat(operations.count()).isEqualTo(1);
            assertThat(stocks.findById(stockId).orElseThrow().getOnHand()).isEqualByComparingTo("95");
        } finally { pool.shutdownNow(); }
    }
}
