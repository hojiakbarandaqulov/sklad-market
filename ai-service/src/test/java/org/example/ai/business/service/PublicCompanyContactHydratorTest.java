package org.example.ai.business.service;

import org.example.ai.business.dto.BusinessContactStatus;
import org.example.ai.business.remote.PublicCompanyContactClient;
import org.example.ai.business.remote.RemoteCompanyDetail;
import org.example.ai.gateway.GatewayNotFoundException;
import org.example.ai.gateway.GatewayUnavailableException;
import org.example.ai.tool.ToolExecutionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PublicCompanyContactHydratorTest {

    private PublicCompanyContactHydrator hydrator;

    @AfterEach
    void tearDown() {
        if (hydrator != null) hydrator.shutdown();
    }

    @Test
    void capsLookupsAndKeepsSuccessfulPublicContactsWhenAnotherLookupFails() {
        PublicCompanyContactClient client = mock(PublicCompanyContactClient.class);
        hydrator = new PublicCompanyContactHydrator(client, 2, 2);
        ToolExecutionContext context = new ToolExecutionContext(null, "buyer", "jwt", Set.of("BUYER"), "uz");
        when(client.fetch("one", "UZ")).thenReturn(detail("one", "+998"));
        when(client.fetch("two", "UZ")).thenThrow(new GatewayUnavailableException("downstream timeout", null));

        var contacts = hydrator.hydrateBatch(List.of("one", "two", "three"), context);

        assertThat(contacts).containsOnlyKeys("one", "two");
        assertThat(contacts.get("one").status()).isEqualTo(BusinessContactStatus.AVAILABLE);
        assertThat(contacts.get("one").contact().phonePrimary()).isEqualTo("+998");
        assertThat(contacts.get("two").status()).isEqualTo(BusinessContactStatus.TEMPORARILY_UNAVAILABLE);
        verify(client, never()).fetch("three", "UZ");
    }

    @Test
    void distinguishesNotFoundFromAProfileWithNoPublicContactFields() {
        PublicCompanyContactClient client = mock(PublicCompanyContactClient.class);
        hydrator = new PublicCompanyContactHydrator(client, 2, 2);
        ToolExecutionContext context = new ToolExecutionContext(null, "buyer", "jwt", Set.of("BUYER"), "en");
        when(client.fetch("empty", "EN")).thenReturn(
                new RemoteCompanyDetail(1L, "Empty", "empty", "VERIFIED",
                        null, null, null, null, null, null));
        when(client.fetch("missing", "EN")).thenThrow(new GatewayNotFoundException("missing", null));

        var contacts = hydrator.hydrateBatch(List.of("empty", "missing"), context);

        assertThat(contacts.get("empty").status()).isEqualTo(BusinessContactStatus.NO_PUBLIC_FIELDS);
        assertThat(contacts.get("missing").status()).isEqualTo(BusinessContactStatus.NOT_FOUND);
    }

    private RemoteCompanyDetail detail(String slug, String phone) {
        return new RemoteCompanyDetail(1L, "Company", slug, "VERIFIED", "Tashkent",
                phone, null, "https://example.test", null, null);
    }
}
