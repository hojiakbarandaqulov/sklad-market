package org.example.service;

import org.example.config.internal.ProductClient;
import org.example.service.impl.CompanyProductCountServiceImpl;
import org.example.dto.CompanyShortDTO;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class CompanyProductCountTest {
    ProductClient client = mock(ProductClient.class);
    CompanyProductCountService service = new CompanyProductCountServiceImpl(client);
    CompanyShortDTO dto(long id) { var dto = new CompanyShortDTO(); dto.setId(id); return dto; }

    @Test void batchesCountsAndKeepsZeroDistinctFromUnavailable() {
        when(client.getCompanyProductCounts(List.of(1L,2L))).thenReturn(Map.of(1L,7L,2L,0L));
        var items = List.of(dto(1),dto(2),dto(1));
        service.enrich(items,CompanyShortDTO::getId,CompanyShortDTO::setProductCount);
        assertEquals(7L,items.get(0).getProductCount());
        assertEquals(0L,items.get(1).getProductCount());
        assertEquals(7L,items.get(2).getProductCount());
        verify(client,times(1)).getCompanyProductCounts(List.of(1L,2L));
    }
    @Test void emptyListsDoNotCallProductService() {
        service.enrich(List.<CompanyShortDTO>of(),CompanyShortDTO::getId,CompanyShortDTO::setProductCount);
        verifyNoInteractions(client);
    }
    @Test void unavailableServiceDoesNotPretendCountIsZero() {
        when(client.getCompanyProductCounts(anyList())).thenThrow(mock(feign.FeignException.class));
        assertNull(service.get(1L));
    }
    @Test void largeListsAreChunked() {
        when(client.getCompanyProductCounts(anyList())).thenReturn(Map.of());
        var items = java.util.stream.LongStream.rangeClosed(1,501).mapToObj(this::dto).toList();
        service.enrich(items,CompanyShortDTO::getId,CompanyShortDTO::setProductCount);
        var calls=org.mockito.ArgumentCaptor.forClass(List.class);
        verify(client,times(2)).getCompanyProductCounts(calls.capture());
        assertEquals(500,calls.getAllValues().get(0).size());
        assertEquals(1,calls.getAllValues().get(1).size());
    }
}