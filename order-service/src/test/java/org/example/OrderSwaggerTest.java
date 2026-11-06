package org.example;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrderSwaggerTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockitoBean JwtDecoder decoder;

    @Test
    void schemaDocumentsAllSixOperationsAndBearerAuthorization() throws Exception {
        var response = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn();
        JsonNode spec = json.readTree(response.getResponse().getContentAsString());
        assertThat(spec.path("info").path("title").asText()).isEqualTo("Order — Buyurtmalar API");
        var paths = spec.path("paths");
        assertThat(paths.size()).isEqualTo(5);
        assertThat(paths.path("/api/v1/orders").has("get")).isTrue();
        assertThat(paths.path("/api/v1/orders").has("post")).isTrue();
        assertThat(paths.path("/api/v1/orders/seller").has("get")).isTrue();
        assertThat(paths.path("/api/v1/orders/{id}").has("get")).isTrue();
        assertThat(paths.path("/api/v1/orders/{id}/ship").has("post")).isTrue();
        assertThat(paths.path("/api/v1/orders/{id}/cancel").has("post")).isTrue();
        assertThat(paths.path("/api/v1/orders").path("post").path("responses").has("200")).isTrue();
        assertThat(paths.path("/api/v1/orders").path("post").path("responses").path("200").path("content").isEmpty()).isFalse();
        assertThat(spec.path("components").path("securitySchemes").path("bearerAuth").path("scheme").asText()).isEqualTo("bearer");
        assertThat(spec.path("security").get(0).has("bearerAuth")).isTrue();
        assertThat(spec.path("servers").get(0).path("url").asText()).isEqualTo("/");
        assertThat(paths.path("/api/v1/orders/{id}/ship").path("post").path("summary").asText()).contains("jo'natish");
        var filters = new HashSet<String>();
        paths.path("/api/v1/orders").path("get").path("parameters").forEach(p -> {
            if (p.path("in").asText().equals("header")) {
                assertThat(p.path("name").asText()).isEqualTo("Accept-Language");
                assertThat(p.path("schema").path("default").asText()).isEqualTo("UZ");
                return;
            }
            assertThat(p.path("in").asText()).isEqualTo("query"); filters.add(p.path("name").asText());
        });
        assertThat(filters).containsExactlyInAnyOrder("companyId", "status", "dateFrom", "dateTo", "page", "perPage");
        assertThat(spec.path("components").path("schemas").path("OrderCreateRequest").path("properties").path("idempotencyKey").path("example").asText())
                .isEqualTo("checkout-20261102-001");
        var schemas = spec.path("components").path("schemas");
        assertThat(schemas.path("OrderCreateItem").path("properties").has("quantity")).isTrue();
        assertThat(schemas.path("OrderCreateItem").path("properties").has("unitPrice")).isFalse();
        assertThat(schemas.path("OrderResponseItem").path("properties").has("unitPrice")).isTrue();
    }

    @Test
    void swaggerPageAssetsAndConfigurationOpenWithoutTokenButBusinessApiStillRequiresIt() throws Exception {
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("swagger-ui")));
        mvc.perform(get("/swagger-ui/swagger-ui.css")).andExpect(status().isOk());
        mvc.perform(get("/swagger-ui/swagger-initializer.js")).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/v3/api-docs/swagger-config")));
        mvc.perform(get("/v3/api-docs/swagger-config")).andExpect(status().isOk()).andExpect(jsonPath("$.url").value("/v3/api-docs"));
        mvc.perform(get("/api/v1/orders")).andExpect(status().isUnauthorized());
    }

    @Test
    void gatewayPrefixIsPreservedForUiConfigurationAndSchemaLinks() throws Exception {
        // Gateway StripPrefix=2 bajarib, keyin shu headerni beradi.
        mvc.perform(get("/swagger-ui/swagger-initializer.js").header("X-Forwarded-Prefix", "/swagger/order"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/swagger/order/v3/api-docs/swagger-config")));
        mvc.perform(get("/v3/api-docs/swagger-config").header("X-Forwarded-Prefix", "/swagger/order"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.url").value("/swagger/order/v3/api-docs"));
        mvc.perform(get("/swagger-ui.html").header("X-Forwarded-Prefix", "/swagger/order"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/swagger/order/swagger-ui/index.html"));
    }
}
