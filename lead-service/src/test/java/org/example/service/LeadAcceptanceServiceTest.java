package org.example.service;

import com.fasterxml.jackson.databind.ObjectMapper;
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
                .isInstanceOfSatisfying(LeadAcceptanceException.class,e -> assertThat(e.getStatus()).isEqualTo(403));
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
}
