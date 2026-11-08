package org.example.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;
import feign.Request;
import feign.Response;
import java.nio.charset.StandardCharsets;
import org.example.clent.OrderAcceptClient;
import org.example.dto.*;
import org.example.entity.Lead;
import org.example.enums.*;
import org.example.exp.LeadAcceptanceException;
import org.example.repository.LeadRepository;
import org.example.service.impl.LeadAcceptanceService;
import org.junit.jupiter.api.*;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import java.math.BigDecimal;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LeadAcceptanceServiceTest {
    @Mock LeadRepository repository;
    @Mock OrderAcceptClient orders;
    @Mock ResourceBundleService messages;
    @Spy ObjectMapper json = new ObjectMapper();
    @InjectMocks LeadAcceptanceService service;
    Lead lead;
    LeadAcceptRequest request=new LeadAcceptRequest("accept-key",true,List.of(new LeadAcceptRequest.Item(60L,new BigDecimal("50"))));
    @BeforeEach void setup() {
        var jwt=Jwt.withTokenValue("seller").header("alg","RS256").claim("profileId",42L).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
        lead=new Lead(); lead.setId(1L); lead.setCompanyId(7L); lead.setBuyerId(99L); lead.setSellerId(42L); lead.setStatus(LeadStatus.NEW);
        when(repository.lockPresent(1L)).thenReturn(Optional.of(lead));
    }
    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); }
    @Test void acceptedOrderKeepsOriginalBuyerAndOriginalRequestItems() throws Exception {
        var response=new ObjectMapper().readTree("{\"id\":123,\"buyerId\":99,\"companyId\":7,\"status\":\"RESERVED\"}");
        when(orders.accept(1L,request,"Bearer seller","RU")).thenReturn(ApiResponse.successResponse(response));
        assertThat(service.accept(1L,request,"Bearer seller",AppLanguage.RU)).isEqualTo(response);
        assertThat(lead.getStatus()).isEqualTo(LeadStatus.ACCEPTED);
        assertThat(lead.getAcceptedOrderId()).isEqualTo(123L);
        verify(repository).save(lead);
    }
    @Test void anotherSellerCannotAccept() {
        lead.setSellerId(88L);
        assertThatThrownBy(() -> service.accept(1L,request,"Bearer seller",AppLanguage.UZ))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        verifyNoInteractions(orders); verify(repository,never()).save(lead);
    }
    @Test void failedRemoteAcceptanceDoesNotMarkLeadAccepted() {
        when(orders.accept(1L,request,"Bearer seller","EN")).thenReturn(null);
        assertThatThrownBy(() -> service.accept(1L,request,"Bearer seller",AppLanguage.EN)).isInstanceOf(LeadAcceptanceException.class);
        assertThat(lead.getStatus()).isEqualTo(LeadStatus.NEW); verify(repository,never()).save(lead);
    }
    @Test void canceledRequestCannotBeAccepted() {
        lead.setStatus(LeadStatus.CANCELED);
        assertThatThrownBy(() -> service.accept(1L,request,"Bearer seller",AppLanguage.EN)).isInstanceOf(LeadAcceptanceException.class);
        verifyNoInteractions(orders);
    }

    @Test void minimumQuantityRejectionReachesSellerAndCanBeTracedWithoutMarkingAccepted() {
        String upstreamTrace = "23707a95-9015-4859-a75f-ac347635b224";
        when(messages.getMessage("lead.accept.failed", AppLanguage.UZ)).thenReturn("Generic fallback");
        when(orders.accept(1L, request, "Bearer seller", "UZ")).thenThrow(failure(400,
                "{\"success\":false,\"message\":\"Respect the minimum order quantity\",\"errors\":{\"items[0].quantity\":\"Minimum 50\"},\"trace_id\":\"" + upstreamTrace + "\"}"));
        LeadAcceptanceException error = catchThrowableOfType(() -> service.accept(1L, request, "Bearer seller", AppLanguage.UZ), LeadAcceptanceException.class);
        assertThat(error.getStatus()).isEqualTo(400);
        assertThat(error.getMessage()).isEqualTo("Respect the minimum order quantity");
        assertThat(error.getErrors()).containsEntry("items[0].quantity", "Minimum 50");
        assertThat(error.getUpstreamTraceId()).isEqualTo(upstreamTrace);
        var response = new org.example.exp.handle.ExceptionHandleController(messages).handleAccept(error);
        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).containsEntry("message", error.getMessage()).containsEntry("errors", error.getErrors());
        assertThat(response.getBody().get("trace_id").toString()).isNotBlank();
        assertThat(lead.getStatus()).isEqualTo(LeadStatus.NEW);
        verify(repository, never()).save(lead);
    }

    @Test void invalidBodiesAndServerDetailsUseSafeFallback() {
        when(messages.getMessage("lead.accept.failed", AppLanguage.UZ)).thenReturn("Generic fallback");
        for (String body : List.of("<html>private details</html>", "{}", "{\"success\":false,\"message\":\"secret server details\"}")) {
            doThrow(failure(500, body)).when(orders).accept(1L, request, "Bearer seller", "UZ");
            LeadAcceptanceException error = catchThrowableOfType(() -> service.accept(1L, request, "Bearer seller", AppLanguage.UZ), LeadAcceptanceException.class);
            assertThat(error.getStatus()).isEqualTo(503);
            assertThat(error.getMessage()).isEqualTo("Generic fallback");
            assertThat(error.getErrors()).isEmpty();
        }
        doThrow(failure(400, "<html>private details</html>")).when(orders).accept(1L, request, "Bearer seller", "UZ");
        assertThatThrownBy(() -> service.accept(1L, request, "Bearer seller", AppLanguage.UZ)).hasMessage("Generic fallback");
        verify(repository, never()).save(lead);
    }

    private FeignException failure(int status, String body) {
        return FeignException.errorStatus("OrderAcceptClient#accept", Response.builder().status(status).reason("test")
                .headers(Map.of()).body(body, StandardCharsets.UTF_8)
                .request(Request.create(Request.HttpMethod.POST, "http://order/internal/orders/leads/1/accept", Map.of(), null, StandardCharsets.UTF_8, null)).build());
    }
}
