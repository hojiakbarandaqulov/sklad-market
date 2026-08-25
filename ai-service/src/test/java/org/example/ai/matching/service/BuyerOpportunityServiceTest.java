package org.example.ai.matching.service;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.example.ai.gateway.GatewayClient;
import org.example.ai.matching.dto.BuyerOpportunityResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

class BuyerOpportunityServiceTest {

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance().build();

    @Test
    void ranksOnlyCallerScopedSellerLeads_andReturnsPrivacySafeDeterministicProjection() {
        wireMock.stubFor(get(urlPathEqualTo("/api/v1/leads/seller"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody("""
                        {"success":true,"data":{"items":[
                          {"id":11,"buyerId":91,"sellerId":7,"status":"NEW",
                           "contactName":"Private Buyer","contactPhone":"+998901234567",
                           "contactEmail":"private@example.test","deliveryAddress":"Secret address",
                           "comment":"SECRET_COMMENT cement required quickly","neededDate":"2026-08-20",
                           "items":[{"productId":5,"productNameSnapshot":"M500 Cement","quantity":500}]},
                          {"id":12,"buyerId":92,"sellerId":7,"status":"VIEWED",
                           "comment":"bricks required","neededDate":"2026-09-20",
                           "items":[{"productId":6,"productNameSnapshot":"Red brick","quantity":1000}]}
                        ],"meta":{"total":2,"page":1,"perPage":100,"totalPages":1}}}
                        """)));
        BuyerOpportunityService service = new BuyerOpportunityService(
                new GatewayClient(wireMock.baseUrl(), 5),
                Clock.fixed(Instant.parse("2026-08-11T10:00:00Z"), ZoneOffset.UTC));

        BuyerOpportunityResult result = service.recommend("caller-jwt", "uz", "cement", List.of(), 10);

        assertThat(result.opportunities()).hasSize(1);
        assertThat(result.opportunities().get(0).leadId()).isEqualTo(11L);
        assertThat(result.opportunities().get(0).matchScore()).isEqualTo(90);
        assertThat(result.opportunities().get(0).reasons())
                .containsExactly("NEW_REQUEST", "PRODUCT_OR_NEED_MATCH", "NEEDED_SOON", "QUANTITY_SPECIFIED");
        assertThat(result.automaticOutreachAllowed()).isFalse();
        assertThat(result.evaluatedLeadCount()).isEqualTo(2);
        assertThat(result.totalLeadCount()).isEqualTo(2);
        assertThat(result.candidatesTruncated()).isFalse();
        assertThat(result.asOf()).isEqualTo(Instant.parse("2026-08-11T10:00:00Z"));
        assertThat(result.toString())
                .doesNotContain("Private Buyer", "+998901234567", "private@example.test", "Secret address", "SECRET_COMMENT");

        wireMock.verify(getRequestedFor(urlPathEqualTo("/api/v1/leads/seller"))
                .withHeader("Authorization", equalTo("Bearer caller-jwt"))
                .withQueryParam("page", equalTo("1"))
                .withQueryParam("perPage", equalTo("100")));
        wireMock.verify(0, getRequestedFor(urlPathEqualTo("/api/v1/leads")));
    }

    @Test
    void repeatBuyerSignal_isDerivedOnlyInMemoryAcrossAuthorizedPages() {
        wireMock.stubFor(get(urlPathEqualTo("/api/v1/leads/seller")).withQueryParam("page", equalTo("1"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody("""
                        {"success":true,"data":{"items":[
                          {"id":1,"buyerId":77,"status":"NEW","items":[{"productId":1,"productNameSnapshot":"Steel","quantity":2}]}
                        ],"meta":{"total":2,"page":1,"perPage":100,"totalPages":2}}}
                        """)));
        wireMock.stubFor(get(urlPathEqualTo("/api/v1/leads/seller")).withQueryParam("page", equalTo("2"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody("""
                        {"success":true,"data":{"items":[
                          {"id":2,"buyerId":77,"status":"VIEWED","items":[{"productId":2,"productNameSnapshot":"Steel sheet","quantity":5}]}
                        ],"meta":{"total":2,"page":2,"perPage":100,"totalPages":2}}}
                        """)));
        BuyerOpportunityService service = new BuyerOpportunityService(
                new GatewayClient(wireMock.baseUrl(), 5),
                Clock.fixed(Instant.parse("2026-08-11T10:00:00Z"), ZoneOffset.UTC));

        BuyerOpportunityResult result = service.recommend("jwt", "en", null, List.of(), 10);

        assertThat(result.evaluatedLeadCount()).isEqualTo(2);
        assertThat(result.totalLeadCount()).isEqualTo(2);
        assertThat(result.candidatesTruncated()).isFalse();
        assertThat(result.opportunities()).allSatisfy(opportunity ->
                assertThat(opportunity.reasons()).contains("REPEAT_BUYER_INTEREST"));
    }

    @Test
    void reportsWhenAuthorizedLeadCandidatesAreTruncatedByBoundedCrawl() {
        wireMock.stubFor(get(urlPathEqualTo("/api/v1/leads/seller"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody("""
                        {"success":true,"data":{"items":[
                          {"id":1,"buyerId":77,"status":"NEW","items":[{"productId":1,"productNameSnapshot":"Steel","quantity":2}]}
                        ],"meta":{"total":1000,"page":1,"perPage":100,"totalPages":10}}}
                        """)));
        BuyerOpportunityService service = new BuyerOpportunityService(
                new GatewayClient(wireMock.baseUrl(), 5),
                Clock.fixed(Instant.parse("2026-08-11T10:00:00Z"), ZoneOffset.UTC));

        BuyerOpportunityResult result = service.recommend("jwt", "en", null, List.of(), 10);

        assertThat(result.evaluatedLeadCount()).isEqualTo(5);
        assertThat(result.totalLeadCount()).isEqualTo(1000);
        assertThat(result.candidatesTruncated()).isTrue();
        assertThat(result.asOf()).isEqualTo(Instant.parse("2026-08-11T10:00:00Z"));
    }
}
