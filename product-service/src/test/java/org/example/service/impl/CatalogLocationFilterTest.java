package org.example.service.impl;

import jakarta.persistence.criteria.*;
import org.example.client.CompanyClient;
import org.example.entity.Product;
import org.example.enums.AppLanguage;
import org.example.enums.Currency;
import org.example.enums.ProductModerationStatus;
import org.example.dto.product.ProductResponse;
import org.example.dto.product.ProductSearchResponse;
import org.example.repository.ProductRepository;
import org.example.service.BannerService;
import org.example.service.ProductSearchService;
import org.junit.jupiter.api.Test;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SuppressWarnings("unchecked")
class CatalogLocationFilterTest {
    final ProductRepository repository = mock(ProductRepository.class);
    final CompanyClient client = mock(CompanyClient.class);
    final ProductServiceImpl mapper = mock(ProductServiceImpl.class);
    final ProductSearchService productSearchService = mock(ProductSearchService.class);
    final CatalogServiceImpl service = new CatalogServiceImpl(repository, mapper,
            mock(BannerService.class), new ModelMapper(), client, productSearchService);

    @Test
    void suggestionsUseElasticsearchSearchAndKeepDistinctNames() {
        when(productSearchService.search("кабел", 1, 10)).thenReturn(List.of(
                ProductSearchResponse.builder().name("kabel").build(),
                ProductSearchResponse.builder().name("kabel").build(),
                ProductSearchResponse.builder().name("Kabel premium").build()));

        var result = service.suggestions("  кабел  ", AppLanguage.UZ);

        assertEquals(List.of("kabel", "Kabel premium"), result.getSuggestions());
        verify(productSearchService).search("кабел", 1, 10);
        verifyNoInteractions(repository);
    }

    @Test
    void shortSuggestionDoesNotCallElasticsearch() {
        assertTrue(service.suggestions("к", AppLanguage.UZ).getSuggestions().isEmpty());
        verifyNoInteractions(productSearchService);
    }

    @Test
    void catalogUsesCompanyIdsAndEnumCurrencyAndExcludesDeletedProducts() {
        when(client.getCompanyIdsByRegion(3L)).thenReturn(List.of(10L, 20L));
        when(repository.findAll(any(Specification.class), any(Pageable.class))).thenAnswer(call -> {
            Specification<Product> spec = call.getArgument(0);
            Pageable page = call.getArgument(1);
            Root<Product> root = mock(Root.class);
            CriteriaBuilder cb = mock(CriteriaBuilder.class);
            Path<Object> company = mock(Path.class), currency = mock(Path.class), deleted = mock(Path.class);
            when(root.get("companyId")).thenReturn(company);
            when(root.get("currency")).thenReturn(currency);
            when(root.get("deletedAt")).thenReturn(deleted);
            spec.toPredicate(root, mock(CriteriaQuery.class), cb);
            verify(root, never()).get("regionId");
            verify(company).in(List.of(10L, 20L));
            verify(cb).equal(currency, Currency.USD);
            verify(cb).isNull(deleted);
            assertEquals(1, page.getPageNumber());
            assertTrue(page.getSort().getOrderFor("id").isDescending());
            return Page.empty(page);
        });
        assertEquals(2, service.getCatalog(null, null, 3L, " usd ", 2, 5, AppLanguage.UZ).getMeta().getPage());
    }

    @Test
    void emptyRegionDoesNotReturnAllProducts() {
        when(client.getCompanyIdsByRegion(3L)).thenReturn(List.of());
        when(repository.findAll(any(Specification.class), any(Pageable.class))).thenAnswer(call -> {
            Specification<Product> spec = call.getArgument(0);
            CriteriaBuilder cb = mock(CriteriaBuilder.class);
            spec.toPredicate(mock(Root.class), mock(CriteriaQuery.class), cb);
            verify(cb).disjunction();
            return Page.empty(call.getArgument(1));
        });
        assertTrue(service.getCatalog(null, null, 3L, null, 1, 20, AppLanguage.UZ).getItems().isEmpty());
    }

    @Test
    void locationEndpointPreservesTotalAndMapsProducts() {
        when(client.getCompanyIdsByRegion(3L)).thenReturn(List.of(10L, 20L));
        Product product = new Product();
        ProductResponse response = new ProductResponse();
        when(mapper.toResponse(product)).thenReturn(response);
        when(repository.findByCompanyIdInAndModerationStatusAndIsActiveTrueAndDeletedAtIsNull(
                eq(List.of(10L, 20L)), eq(ProductModerationStatus.APPROVED), any(Pageable.class)))
                .thenAnswer(call -> new PageImpl<>(List.of(product), call.getArgument(2), 12));
        var result = service.getLocationFilterProduct(3L, 2, 5, AppLanguage.UZ);
        assertEquals(12, result.getTotalElements());
        assertSame(response, result.getContent().get(0));
    }

    @Test
    void emptyLocationSkipsRepository() {
        when(client.getCompanyIdsByRegion(3L)).thenReturn(List.of());
        assertTrue(service.getLocationFilterProduct(3L, 1, 20, AppLanguage.UZ).isEmpty());
        verifyNoInteractions(repository);
    }

    @Test
    void invalidInputsAreRejectedBeforeQuerying() {
        assertThrows(IllegalArgumentException.class, () -> service.getCatalog(null, "abc", null, null, 1, 20, AppLanguage.UZ));
        assertThrows(IllegalArgumentException.class, () -> service.getCatalog(null, "0", null, null, 1, 20, AppLanguage.UZ));
        assertThrows(IllegalArgumentException.class, () -> service.getCatalog(null, null, null, "invalid", 1, 20, AppLanguage.UZ));
        assertThrows(IllegalArgumentException.class, () -> service.getCatalog(null, null, -1L, null, 1, 20, AppLanguage.UZ));
        assertThrows(IllegalArgumentException.class, () -> service.getCatalog(null, null, null, null, 0, 20, AppLanguage.UZ));
        assertThrows(IllegalArgumentException.class, () -> service.getLocationFilterProduct(3L, 1, 0, AppLanguage.UZ));
        assertThrows(IllegalArgumentException.class, () -> service.getLocationFilterProduct(3L, 1, 101, AppLanguage.UZ));
        verifyNoInteractions(repository, client);
    }
}
