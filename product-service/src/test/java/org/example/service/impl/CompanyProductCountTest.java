package org.example.service.impl;

import org.example.repository.*;
import org.example.enums.ProductModerationStatus;
import org.example.exp.AppBadException;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CompanyProductCountTest {
    ProductRepository repository=mock(ProductRepository.class);
    InternalProductStatsServiceImpl service=new InternalProductStatsServiceImpl(repository,
        mock(ProductViewRepository.class),mock(ProductImageRepository.class));

    @Test void fillsMissingCompaniesWithZeroAndUsesApprovedProducts() {
        ProductRepository.CompanyProductCount row=mock(ProductRepository.CompanyProductCount.class);
        when(row.getCompanyId()).thenReturn(10L);when(row.getProductCount()).thenReturn(8L);
        when(repository.countPublicProductsByCompany(List.of(10L,20L),ProductModerationStatus.APPROVED)).thenReturn(List.of(row));
        assertEquals(Map.of(10L,8L,20L,0L),service.getCompanyProductCounts(List.of(10L,20L,10L)));
    }
    @Test void emptyAndInvalidRequestsNeverQuery() {
        assertTrue(service.getCompanyProductCounts(List.of()).isEmpty());
        assertThrows(AppBadException.class,()->service.getCompanyProductCounts(null));
        assertThrows(AppBadException.class,()->service.getCompanyProductCounts(List.of(-1L)));
        assertThrows(AppBadException.class,()->service.getCompanyProductCounts(Collections.nCopies(501,1L)));
        verifyNoInteractions(repository);
    }
}