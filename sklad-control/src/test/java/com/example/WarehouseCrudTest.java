package com.example;

import com.example.config.client.CompanyClient;
import com.example.dto.internal.CompanyBranchResponse;
import com.example.dto.internal.CompanyOwnershipResponse;
import com.example.entity.Warehouse;
import com.example.enums.WarehouseStatus;
import com.example.repository.WarehouseRepository;
import feign.FeignException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Barcha test jadvallari faqat alohida H2 bazasida yaratiladi. Production Liquibase o'zgarmaydi.
@SpringBootTest(properties = {
        "spring.liquibase.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.datasource.url=jdbc:h2:mem:warehouse-crud;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WarehouseCrudTest {
    private static final String ROOT = "/api/v1/control/warehouses";
    @Autowired MockMvc mvc;
    @Autowired WarehouseRepository warehouses;
    @MockitoBean JwtDecoder decoder;
    @MockitoBean CompanyClient companies;

    @BeforeEach
    void setup() {
        warehouses.deleteAll();
        when(decoder.decode("seller-token")).thenReturn(token("seller-token", "SELLER", true));
        when(decoder.decode("buyer-token")).thenReturn(token("buyer-token", "BUYER", true));
        when(decoder.decode("no-profile")).thenReturn(token("no-profile", "SELLER", false));
        when(decoder.decode("invalid-token")).thenThrow(new BadJwtException("Invalid token"));
        when(companies.ownershipCheck(1L, 42L)).thenReturn(company(1L, true, true, true));
        when(companies.ownershipCheck(2L, 42L)).thenReturn(company(2L, true, false, true));
        when(companies.getBranch(1L, 10L)).thenReturn(branch(10L, 1L, true));
    }

    @Test
    void createPersistsOnlyAllowedFieldsAndUsesTheAuthenticatedActor() throws Exception {
        mvc.perform(post(ROOT).header("Authorization", "Bearer seller-token").contentType("application/json")
                        .content("""
                            {"companyId":1,"companyBranchId":10,"name":"  Toshkent ombori  ",
                             "regionId":5,"address":"  Chilonzor  ","status":"ARCHIVED","deleted":true,"createdBy":999}
                            """))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").isNumber())
                .andExpect(jsonPath("$.data.name").value("Toshkent ombori"))
                .andExpect(jsonPath("$.data.address").value("Chilonzor"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.createdDate").exists());
        Warehouse saved = warehouses.findAll().get(0);
        assertThat(saved.getCompanyId()).isEqualTo(1L);
        assertThat(saved.getDeleted()).isFalse();
        assertThat(saved.getCreatedBy()).isEqualTo(42L);
        assertThat(saved.getModifiedBy()).isEqualTo(42L);
        verify(companies).getBranch(1L, 10L);
    }

    @Test
    void createWithoutOptionalFieldsDoesNotLookUpABranch() throws Exception {
        create("{\"companyId\":1,\"name\":\"Main\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
        Warehouse saved = warehouses.findAll().get(0);
        assertThat(saved.getCompanyBranchId()).isNull();
        assertThat(saved.getAddress()).isNull();
        verify(companies, never()).getBranch(anyLong(), anyLong());
    }

    @Test
    void anotherCompanyCannotBeUsedToCreateWarehouse() throws Exception {
        create("{\"companyId\":2,\"name\":\"Not mine\"}").andExpect(status().isForbidden());
        assertThat(warehouses.count()).isZero();
    }

    @Test
    void inactiveOrMissingCompanyCannotCreateWarehouse() throws Exception {
        when(companies.ownershipCheck(1L, 42L)).thenReturn(company(1L, true, true, false));
        create("{\"companyId\":1,\"name\":\"Inactive\"}").andExpect(status().isForbidden());
        when(companies.ownershipCheck(1L, 42L)).thenReturn(company(1L, false, false, false));
        create("{\"companyId\":1,\"name\":\"Missing\"}").andExpect(status().isNotFound());
        assertThat(warehouses.count()).isZero();
    }

    @Test
    void branchMustExistAndBelongToTheSelectedCompany() throws Exception {
        when(companies.getBranch(1L, 20L)).thenReturn(branch(20L, 2L, true));
        create("{\"companyId\":1,\"companyBranchId\":20,\"name\":\"Wrong branch\"}")
                .andExpect(status().isBadRequest());
        when(companies.getBranch(1L, 20L)).thenReturn(branch(null, null, false));
        create("{\"companyId\":1,\"companyBranchId\":20,\"name\":\"Missing branch\"}")
                .andExpect(status().isBadRequest());
        assertThat(warehouses.count()).isZero();
    }

    @Test
    void companyServiceFailureDoesNotAllowWritingWithoutOwnershipProof() throws Exception {
        when(companies.ownershipCheck(1L, 42L)).thenThrow(mock(FeignException.class));
        create("{\"companyId\":1,\"name\":\"Main\"}").andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.success").value(false));
        assertThat(warehouses.count()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"name\":\"Main\"}",
            "{\"companyId\":0,\"name\":\"Main\"}",
            "{\"companyId\":1,\"name\":\"   \"}",
            "{\"companyId\":1,\"name\":\"Main\",\"companyBranchId\":-1}",
            "{\"companyId\":1,\"name\":\"Main\",\"regionId\":0}"
    })
    void invalidCreateRequestsAreRejected(String body) throws Exception {
        create(body).andExpect(status().isBadRequest());
        assertThat(warehouses.count()).isZero();
        verifyNoInteractions(companies);
    }

    @Test
    void createNameRespectsTheExistingLiquibaseColumnLength() throws Exception {
        create("{\"companyId\":1,\"name\":\"" + "a".repeat(161) + "\"}").andExpect(status().isBadRequest());
        assertThat(warehouses.count()).isZero();
    }

    @Test
    void sellerOnlySeesTheSelectedCompanyAndDeletedRowsAreExcluded() throws Exception {
        saved(1L, "First", false);
        saved(1L, "Second", false);
        saved(1L, "Deleted", true);
        saved(2L, "Other company", false);
        mvc.perform(get(ROOT).param("companyId", "1").param("page", "2").param("perPage", "1")
                        .header("Authorization", "Bearer seller-token"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.totalPages").value(2))
                .andExpect(jsonPath("$.data.number").value(1))
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].name").value("First"));
    }

    @Test
    void statusAndBranchFiltersAreApplied() throws Exception {
        Warehouse archived = saved(1L, "Archived", false);
        archived.setCompanyBranchId(10L);
        archived.setStatus(WarehouseStatus.ARCHIVED);
        warehouses.saveAndFlush(archived);
        saved(1L, "Active", false);
        mvc.perform(get(ROOT).param("companyId", "1").param("companyBranchId", "10").param("status", "ARCHIVED")
                        .header("Authorization", "Bearer seller-token"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].name").value("Archived"));
    }

    @Test
    void searchTreatsWildcardCharactersAsLiteralText() throws Exception {
        saved(1L, "100% Main", false);
        saved(1L, "100 Other", false);
        mvc.perform(get(ROOT).param("companyId", "1").param("q", "100%")
                        .header("Authorization", "Bearer seller-token"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].name").value("100% Main"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"page=0", "perPage=0", "perPage=101"})
    void invalidPaginationIsRejected(String query) throws Exception {
        mvc.perform(get(ROOT + "?companyId=1&" + query).header("Authorization", "Bearer seller-token"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void companyIdIsRequiredForTheListAndAnotherCompanyCannotBeListed() throws Exception {
        mvc.perform(get(ROOT).header("Authorization", "Bearer seller-token")).andExpect(status().isBadRequest());
        mvc.perform(get(ROOT).param("companyId", "2").header("Authorization", "Bearer seller-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void detailsAreReturnedOnlyToTheCompanyOwner() throws Exception {
        Warehouse mine = saved(1L, "My warehouse", false);
        Warehouse other = saved(2L, "Private warehouse", false);
        mvc.perform(get(ROOT + "/" + mine.getId()).header("Authorization", "Bearer seller-token"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.name").value("My warehouse"));
        mvc.perform(get(ROOT + "/" + other.getId()).header("Authorization", "Bearer seller-token"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void deletedOrMissingWarehouseIsNotReturned() throws Exception {
        Warehouse deleted = saved(1L, "Deleted", true);
        mvc.perform(get(ROOT + "/" + deleted.getId()).header("Authorization", "Bearer seller-token"))
                .andExpect(status().isNotFound());
        mvc.perform(get(ROOT + "/99999999").header("Authorization", "Bearer seller-token"))
                .andExpect(status().isNotFound());
        mvc.perform(get(ROOT + "/0").header("Authorization", "Bearer seller-token"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void patchChangesOnlyProvidedFieldsAndDoesNotTransferOwnershipOrStatus() throws Exception {
        Warehouse warehouse = saved(1L, "Old", false);
        warehouse.setCompanyBranchId(10L);
        warehouse.setRegionId(5L);
        warehouse.setAddress("Old address");
        warehouse.setCreatedBy(7L);
        warehouses.saveAndFlush(warehouse);
        patch(warehouse.getId(), "{\"name\":\"  Renamed  \",\"companyId\":2,\"status\":\"ARCHIVED\",\"deleted\":true}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.name").value("Renamed"))
                .andExpect(jsonPath("$.data.companyId").value(1)).andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.modifiedDate").exists());
        Warehouse actual = warehouses.findById(warehouse.getId()).orElseThrow();
        assertThat(actual.getCompanyBranchId()).isEqualTo(10L);
        assertThat(actual.getRegionId()).isEqualTo(5L);
        assertThat(actual.getAddress()).isEqualTo("Old address");
        assertThat(actual.getCreatedBy()).isEqualTo(7L);
        assertThat(actual.getModifiedBy()).isEqualTo(42L);
        assertThat(actual.getDeleted()).isFalse();
        verify(companies, never()).getBranch(anyLong(), anyLong());
    }

    @Test
    void explicitNullClearsOptionalFields() throws Exception {
        Warehouse warehouse = saved(1L, "Main", false);
        warehouse.setCompanyBranchId(10L);
        warehouse.setRegionId(5L);
        warehouse.setAddress("Address");
        warehouses.saveAndFlush(warehouse);
        patch(warehouse.getId(), "{\"companyBranchId\":null,\"regionId\":null,\"address\":null}")
                .andExpect(status().isOk());
        Warehouse actual = warehouses.findById(warehouse.getId()).orElseThrow();
        assertThat(actual.getCompanyBranchId()).isNull();
        assertThat(actual.getRegionId()).isNull();
        assertThat(actual.getAddress()).isNull();
        assertThat(actual.getName()).isEqualTo("Main");
        verify(companies, never()).getBranch(anyLong(), anyLong());
    }

    @Test
    void patchAcceptsAValidBranch() throws Exception {
        Warehouse warehouse = saved(1L, "Main", false);
        patch(warehouse.getId(), "{\"companyBranchId\":10}").andExpect(status().isOk());
        assertThat(warehouses.findById(warehouse.getId()).orElseThrow().getCompanyBranchId()).isEqualTo(10L);
        verify(companies).getBranch(1L, 10L);
    }

    @Test
    void invalidBranchRollsBackOtherChangesInThePatch() throws Exception {
        Warehouse warehouse = saved(1L, "Original", false);
        when(companies.getBranch(1L, 20L)).thenReturn(branch(20L, 2L, true));
        patch(warehouse.getId(), "{\"name\":\"Must not persist\",\"companyBranchId\":20}")
                .andExpect(status().isBadRequest());
        assertThat(warehouses.findById(warehouse.getId()).orElseThrow().getName()).isEqualTo("Original");
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"name\":null}", "{\"name\":\"  \"}", "{\"nameProvided\":true}"})
    void invalidOrEmptyPatchCannotChangeTheWarehouse(String body) throws Exception {
        Warehouse warehouse = saved(1L, "Original", false);
        patch(warehouse.getId(), body).andExpect(status().isBadRequest());
        assertThat(warehouses.findById(warehouse.getId()).orElseThrow().getName()).isEqualTo("Original");
    }

    @Test
    void anotherCompanyWarehouseCannotBeEdited() throws Exception {
        Warehouse warehouse = saved(2L, "Private", false);
        patch(warehouse.getId(), "{\"name\":\"Attempt\"}").andExpect(status().isForbidden());
        assertThat(warehouses.findById(warehouse.getId()).orElseThrow().getName()).isEqualTo("Private");
    }

    @Test
    void jwtWithoutProfileIdCannotUseWarehouseCrud() throws Exception {
        mvc.perform(post(ROOT).header("Authorization", "Bearer no-profile").contentType("application/json")
                        .content("{\"companyId\":1,\"name\":\"Main\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(companies);
        assertThat(warehouses.count()).isZero();
    }

    @Test
    void buyerAndUnauthenticatedRequestsCannotUseWarehouseCrud() throws Exception {
        mvc.perform(get(ROOT).param("companyId", "1")).andExpect(status().isUnauthorized());
        mvc.perform(get(ROOT).param("companyId", "1").header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get(ROOT).param("companyId", "1").header("Authorization", "Bearer buyer-token"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(companies);
    }

    private ResultActions create(String body) throws Exception {
        return mvc.perform(post(ROOT).header("Authorization", "Bearer seller-token").contentType("application/json").content(body));
    }

    private ResultActions patch(Long id, String body) throws Exception {
        return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(ROOT + "/" + id)
                .header("Authorization", "Bearer seller-token").contentType("application/json").content(body));
    }

    private Warehouse saved(Long companyId, String name, boolean deleted) {
        Warehouse warehouse = new Warehouse();
        warehouse.setCompanyId(companyId);
        warehouse.setName(name);
        warehouse.setDeleted(deleted);
        warehouse.setCreatedBy(7L);
        warehouse.setModifiedBy(7L);
        return warehouses.saveAndFlush(warehouse);
    }

    private CompanyOwnershipResponse company(Long id, boolean exists, boolean owner, boolean active) {
        CompanyOwnershipResponse response = new CompanyOwnershipResponse();
        response.setCompanyId(id);
        response.setExists(exists);
        response.setOwner(owner);
        response.setActive(active);
        return response;
    }

    private CompanyBranchResponse branch(Long id, Long companyId, boolean exists) {
        CompanyBranchResponse response = new CompanyBranchResponse();
        response.setId(id);
        response.setCompanyId(companyId);
        response.setExists(exists);
        return response;
    }

    private Jwt token(String value, String role, boolean profileId) {
        var builder = Jwt.withTokenValue(value).header("alg", "RS256").subject("keycloak-user")
                .claim("realm_access", Map.of("roles", List.of(role)))
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300));
        if (profileId) builder.claim("profileId", 42L);
        return builder.build();
    }
}
