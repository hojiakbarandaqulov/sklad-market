package org.example.service.impl;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import org.example.client.CompanyClient;
import org.example.client.dto.CompanyOwnershipResponse;
import org.example.controller.ControlProductInternalController;
import org.example.entity.Product;
import org.example.enums.AppLanguage;
import org.example.enums.ProductModerationStatus;
import org.example.enums.SaleStatus;
import org.example.exp.AppBadException;
import org.example.repository.ProductRepository;
import org.example.service.ProductService;
import org.example.service.ResourceBundleService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class ControlSaleStatusFilterTest {
    @Mock ProductRepository products;
    @Mock CompanyClient companies;
    @Mock ResourceBundleService messages;
    @InjectMocks ProductServiceImpl service;

    @BeforeEach void authenticate() {
        var jwt = Jwt.withTokenValue("seller").header("alg", "RS256").claim("profileId", 42L).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    @AfterEach void clearAuthentication() { SecurityContextHolder.clearContext(); }

    @Test void onSaleRequiresActiveApprovedNonDeletedCompanyCards() {
        var spec = captureSpecification(SaleStatus.ON_SALE);
        Root<Product> root = mock(Root.class, RETURNS_DEEP_STUBS);
        CriteriaBuilder cb = mock(CriteriaBuilder.class, RETURNS_MOCKS);
        spec.toPredicate(root, mock(CriteriaQuery.class), cb);
        verify(cb).equal(root.get("companyId"), 1L);
        verify(cb).isNull(root.get("deletedAt"));
        verify(cb).isTrue(root.get("isActive"));
        verify(cb).equal(root.get("moderationStatus"), ProductModerationStatus.APPROVED);
        verify(cb, never()).notEqual(root.get("moderationStatus"), ProductModerationStatus.APPROVED);
    }

    @Test void offSaleIncludesHiddenPendingRejectedArchivedAndLegacyNullStates() {
        var spec = captureSpecification(SaleStatus.OFF_SALE);
        Root<Product> root = mock(Root.class, RETURNS_DEEP_STUBS);
        CriteriaBuilder cb = mock(CriteriaBuilder.class, RETURNS_MOCKS);
        spec.toPredicate(root, mock(CriteriaQuery.class), cb);
        verify(cb).equal(root.get("companyId"), 1L);
        verify(cb).isNull(root.get("deletedAt"));
        verify(cb).isFalse(root.get("isActive"));
        verify(cb).isNull(root.get("isActive"));
        verify(cb).notEqual(root.get("moderationStatus"), ProductModerationStatus.APPROVED);
        verify(cb).isNull(root.get("moderationStatus"));
    }

    @Test void foreignCompanyCannotQuerySaleMatches() {
        var owner = owner(); owner.setOwner(false);
        when(companies.checkOwnership(1L, 42L)).thenReturn(owner);
        assertThatThrownBy(() -> service.matchingControlCardsBySaleStatus(1L, SaleStatus.ON_SALE, AppLanguage.UZ))
                .isInstanceOf(AppBadException.class);
        verifyNoInteractions(products);
    }

    @Test void internalEndpointBindsSaleEnumAndPreservesExistingMatchingEndpoint() throws Exception {
        ProductService api = mock(ProductService.class);
        when(api.matchingControlCardsBySaleStatus(1L, SaleStatus.ON_SALE, AppLanguage.UZ)).thenReturn(List.of(100L));
        when(api.matchingControlCards(1L, "Steel", null, AppLanguage.UZ)).thenReturn(List.of(200L));
        var mvc = MockMvcBuilders.standaloneSetup(new ControlProductInternalController(api)).build();
        mvc.perform(get("/internal/control/products/matching-sale").param("companyId", "1")
                        .param("saleStatus", "ON_SALE"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0]").value(100));
        mvc.perform(get("/internal/control/products/matching").param("companyId", "1").param("q", "Steel"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0]").value(200));
    }

    @Test void internalEndpointRejectsInvalidOrMissingSaleStatus() throws Exception {
        ProductService api = mock(ProductService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new ControlProductInternalController(api)).build();
        mvc.perform(get("/internal/control/products/matching-sale").param("companyId", "1")
                        .param("saleStatus", "ACTIVE"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/internal/control/products/matching-sale").param("companyId", "1"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(api);
    }

    @SuppressWarnings("unchecked")
    private Specification<Product> captureSpecification(SaleStatus status) {
        when(companies.checkOwnership(1L, 42L)).thenReturn(owner());
        Product product = new Product(); product.setId(100L);
        when(products.findAll(org.mockito.ArgumentMatchers.<Specification<Product>>any())).thenReturn(List.of(product));
        assertThat(service.matchingControlCardsBySaleStatus(1L, status, AppLanguage.UZ)).containsExactly(100L);
        ArgumentCaptor<Specification<Product>> captor = ArgumentCaptor.forClass(Specification.class);
        verify(products).findAll(captor.capture());
        return captor.getValue();
    }

    private CompanyOwnershipResponse owner() {
        var owner = new CompanyOwnershipResponse(); owner.setCompanyId(1L);
        owner.setExists(true); owner.setOwner(true); owner.setActive(true);
        return owner;
    }
}
