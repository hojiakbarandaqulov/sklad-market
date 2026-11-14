package org.example.customer;

import org.example.config.SecurityConfig;
import org.example.controller.CustomerController;
import org.example.dto.customer.CustomerResponse;
import org.example.enums.AppLanguage;
import org.example.exp.handle.CustomerExceptionHandler;
import org.example.service.CustomerService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.example.service.ResourceBundleService;
import org.example.service.impl.ResourceBundleServiceImpl;
import org.example.exp.CustomerException;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CustomerController.class)
@ContextConfiguration(classes = {CustomerController.class, CustomerExceptionHandler.class, SecurityConfig.class,
        CustomerControllerTest.Messages.class})
@TestPropertySource(properties = "server.domain=http://localhost")
class CustomerControllerTest {
    @TestConfiguration
    static class Messages {
        @Bean
        ResourceBundleService customerMessages() {
            var source = new ResourceBundleMessageSource();
            source.setBasename("messages/message");
            source.setDefaultEncoding("UTF-8");
            return new ResourceBundleServiceImpl(source);
        }
    }
    @Autowired MockMvc mvc;
    @MockitoBean CustomerService service;
    @MockitoBean JwtDecoder decoder;

    @Test
    void acceptsMultipartFormAndReturnsCreated() throws Exception {
        when(service.create(any(), any(), any())).thenReturn(customer());
        mvc.perform(multipart("/api/v1/customers")
                        .file(new MockMultipartFile("file", "contract.pdf", "application/pdf", "%PDF-1.7".getBytes()))
                        .param("companyId", "7").param("name", "Baraka").param("contractNumber", "SH-1")
                        .param("paymentTermDays", "15")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_SELLER"))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(101));
    }

    @Test
    void rejectsUnauthenticatedAndBuyerRequests() throws Exception {
        mvc.perform(get("/api/v1/customers").param("companyId", "7")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/customers").param("companyId", "7")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_BUYER"))))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void rejectsInvalidPagingAndBlankPatch() throws Exception {
        mvc.perform(get("/api/v1/customers").param("companyId", "7").param("page", "0")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_SELLER"))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false));
        mvc.perform(patch("/api/v1/customers/101").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"   \"}")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_SELLER"))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.name").exists());
        verifyNoInteractions(service);
    }

    @Test
    void allowsPatchCorsPreflight() throws Exception {
        mvc.perform(options("/api/v1/customers/101").header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "PATCH")
                        .header("Access-Control-Request-Headers", "authorization,content-type"))
                .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"));
    }

    @Test
    void missingCompanyAndContractFileUseStructuredErrors() throws Exception {
        mvc.perform(get("/api/v1/customers")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_SELLER"))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false));
        mvc.perform(multipart("/api/v1/customers/101/contract")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_SELLER"))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false));
        verifyNoInteractions(service);
    }

    private CustomerResponse customer() {
        return new CustomerResponse(101L, 7L, "Baraka", "SH-1", null, 15, null, null, null, null, null, null);
    }

    @ParameterizedTest
    @CsvSource({"UZ,Mijoz topilmadi", "RU,Клиент не найден", "EN,Customer not found"})
    void translatesServiceErrors(String language, String message) throws Exception {
        when(service.get(101L, AppLanguage.valueOf(language))).thenThrow(new CustomerException(404, message));
        mvc.perform(get("/api/v1/customers/101").header("Accept-Language", language)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_SELLER"))))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.message").value(message));
    }

    @ParameterizedTest
    @CsvSource({"UZ,So'rov maydonlari noto'g'ri,Maydon formati noto'g'ri",
            "RU,Некорректные поля запроса,Некорректный формат поля",
            "EN,Invalid request fields,Invalid field format"})
    void translatesValidationErrors(String language, String message, String fieldMessage) throws Exception {
        mvc.perform(patch("/api/v1/customers/101").header("Accept-Language", language)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"   \"}")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_SELLER"))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value(message))
                .andExpect(jsonPath("$.errors.name").value(fieldMessage));
    }
}
