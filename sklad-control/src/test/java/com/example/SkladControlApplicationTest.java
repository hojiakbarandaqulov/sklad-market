package com.example;

import com.example.entity.Warehouse;
import com.example.enums.AppLanguage;
import com.example.enums.WarehouseStatus;
import com.example.exp.AppBadException;
import com.example.exp.AppConflictException;
import com.example.exp.AppNotFoundException;
import com.example.repository.WarehouseRepository;
import com.example.utils.SpringSecurityUtil;
import jakarta.persistence.EntityManager;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import liquibase.integration.spring.SpringLiquibase;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(SkladControlApplicationTest.ProbeConfig.class)
class SkladControlApplicationTest {
    @Autowired WarehouseRepository warehouses;
    @Autowired EntityManager entityManager;
    @Autowired JdbcTemplate jdbc;
    @Autowired SpringLiquibase liquibase;
    @Autowired MockMvc mvc;
    @MockitoBean JwtDecoder decoder;

    @BeforeEach
    void tokens() {
        when(decoder.decode("seller-token")).thenReturn(token("seller-token", "SELLER"));
        when(decoder.decode("buyer-token")).thenReturn(token("buyer-token", "BUYER"));
        when(decoder.decode("invalid-token")).thenThrow(new BadJwtException("Invalid JWT"));
    }

    @AfterEach
    void clear() { SecurityContextHolder.clearContext(); }

    @Test
    void liquibaseCreatedInventoryTablesAndRecordedTheChangesets() {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM databasechangelog WHERE id = '001-create-warehouses'", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForList("SELECT LOWER(table_name) FROM information_schema.tables WHERE LOWER(table_schema) = 'public'", String.class))
                .containsExactlyInAnyOrder("warehouses", "inventory_products", "warehouse_stocks",
                        "stock_reservations", "stock_reservation_item", "stock_operations",
                        "databasechangelog", "databasechangeloglock");
    }

    @Test
    void applyingTheSameChangelogAgainDoesNotRunItTwice() throws Exception {
        Integer before = jdbc.queryForObject("SELECT COUNT(*) FROM databasechangelog", Integer.class);
        liquibase.afterPropertiesSet();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM databasechangelog", Integer.class)).isEqualTo(before);
    }

    @Test
    @Transactional
    void entityMatchesXmlAndPreservesExplicitActorAndAuditDates() {
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(token("seller-token", "SELLER"),
                List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_SELLER"))));
        Warehouse warehouse = new Warehouse();
        warehouse.setCompanyId(7L);
        warehouse.setName("Toshkent ombori");
        warehouse.setCompanyBranchId(11L);
        warehouse.setRegionId(1L);
        // Servislar actor'ni aniq o'rnatadi; umumiy AuditorAware bean mavjud emas.
        warehouse.setCreatedBy(SpringSecurityUtil.getProfileId());
        warehouse.setModifiedBy(SpringSecurityUtil.getProfileId());
        Warehouse saved = warehouses.saveAndFlush(warehouse);
        entityManager.clear();
        Warehouse actual = warehouses.findById(saved.getId()).orElseThrow();
        assertThat(actual.getStatus()).isEqualTo(WarehouseStatus.ACTIVE);
        assertThat(actual.getDeleted()).isFalse();
        assertThat(actual.getCreatedDate()).isNotNull();
        assertThat(actual.getModifiedDate()).isNotNull();
        assertThat(actual.getCreatedBy()).isEqualTo(42L);
        assertThat(actual.getModifiedBy()).isEqualTo(42L);
        assertThat(actual.getCompanyBranchId()).isEqualTo(11L);
        actual.setStatus(WarehouseStatus.ARCHIVED);
        warehouses.saveAndFlush(actual);
        entityManager.clear();
        assertThat(warehouses.findById(saved.getId()).orElseThrow().getStatus()).isEqualTo(WarehouseStatus.ARCHIVED);
    }

    @Test
    @Transactional
    void databaseDefaultsWorkWithoutHibernateGeneratingSchema() {
        jdbc.update("INSERT INTO warehouses(company_id, name) VALUES (9, 'Default warehouse')");
        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM warehouses WHERE company_id = 9");
        assertThat(row.get("status")).isEqualTo("ACTIVE");
        assertThat(row.get("deleted")).isEqualTo(false);
        assertThat(row.get("created_date")).isNotNull();
        assertThat(row.get("modified_date")).isNotNull();
    }

    @Test
    @Transactional
    void databaseRejectsWarehouseWithoutCompany() {
        assertThatThrownBy(() -> jdbc.update("INSERT INTO warehouses(name) VALUES ('Invalid')"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void healthAndSwaggerCanBeOpenedWithoutLogin() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andExpect(jsonPath("$.info.title").value("Sklad Control API"));
    }

    @Test
    void missingOrInvalidTokenIsRejected() throws Exception {
        mvc.perform(get("/api/v1/control/test-context")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/control/test-context").header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void buyerCannotOpenSellerInventory() throws Exception {
        mvc.perform(get("/api/v1/control/test-context").header("Authorization", "Bearer buyer-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void sellerRoleIsReadFromKeycloakAndContextDoesNotLeak() throws Exception {
        mvc.perform(get("/api/v1/control/test-context").header("Authorization", "Bearer seller-token"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.profileId").value(42))
                .andExpect(jsonPath("$.seller").value(true));
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        mvc.perform(get("/api/v1/control/test-context")).andExpect(status().isUnauthorized());
    }

    @Test
    void patchPreflightIsAllowedForConfiguredOrigins() throws Exception {
        mvc.perform(options("/api/v1/control/warehouses/1").header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "PATCH")
                        .header("Access-Control-Request-Headers", "Authorization,Content-Type"))
                .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
    }

    @Test
    void businessExceptionsKeepTheirHttpStatus() throws Exception {
        mvc.perform(get("/api/v1/control/test-error/bad").header("Authorization", "Bearer seller-token"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false));
        mvc.perform(get("/api/v1/control/test-error/missing").header("Authorization", "Bearer seller-token"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.trace_id").exists());
        mvc.perform(get("/api/v1/control/test-error/conflict").header("Authorization", "Bearer seller-token"))
                .andExpect(status().isConflict());
    }

    @Test
    void unexpectedErrorsDoNotExposeTheirDetails() throws Exception {
        mvc.perform(get("/api/v1/control/test-error/unexpected").header("Authorization", "Bearer seller-token")
                        .header("Accept-Language", "en"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("An internal server error occurred"));
    }

    @Test
    void validationReturnsFieldErrors() throws Exception {
        mvc.perform(post("/api/v1/control/test-validation").header("Authorization", "Bearer seller-token")
                        .contentType("application/json").content("{\"name\":\"\"}").header("Accept-Language", "en"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.name").exists())
                .andExpect(jsonPath("$.message").value("Check the submitted fields"));
    }

    @Test
    void languageConverterAcceptsBrowserLanguageTags() throws Exception {
        mvc.perform(get("/api/v1/control/test-language").header("Authorization", "Bearer seller-token")
                        .header("Accept-Language", "ru-RU,ru;q=0.9"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.language").value("RU"));
    }

    @Test
    void uzbekMessagesFallBackToTheSingleBaseFile() throws Exception {
        mvc.perform(post("/api/v1/control/test-validation").header("Authorization", "Bearer seller-token")
                        .contentType("application/json").content("{\"name\":\"\"}").header("Accept-Language", "uz-UZ"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Kiritilgan ma'lumotlarni tekshiring"));
    }

    private Jwt token(String value, String role) {
        return Jwt.withTokenValue(value).header("alg", "RS256").subject("keycloak-user")
                .claim("profileId", "42").claim("realm_access", Map.of("roles", List.of(role)))
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300)).build();
    }

    @TestConfiguration
    static class ProbeConfig {
        @Bean ProbeController probeController() { return new ProbeController(); }
    }

    // Test controller is never included in the production JAR.
    @org.springframework.boot.test.context.TestComponent
    @RestController
    static class ProbeController {
        @GetMapping("/api/v1/control/test-context")
        Map<String, Object> context() {
            return Map.of("profileId", SpringSecurityUtil.getProfileId(), "seller", SpringSecurityUtil.hasRole("SELLER"));
        }

        @GetMapping("/api/v1/control/test-error/{kind}")
        void error(@PathVariable String kind) {
            switch (kind) {
                case "bad" -> throw new AppBadException("Bad request");
                case "missing" -> throw new AppNotFoundException("Warehouse not found");
                case "conflict" -> throw new AppConflictException("Warehouse is in use");
                default -> throw new IllegalStateException("Private database connection details");
            }
        }

        @PostMapping("/api/v1/control/test-validation")
        void validation(@Valid @RequestBody TestInput input) { }

        @GetMapping("/api/v1/control/test-language")
        Map<String, String> language(@RequestHeader("Accept-Language") AppLanguage language) {
            return Map.of("language", language.name());
        }
    }

    record TestInput(@NotBlank String name) { }
}
