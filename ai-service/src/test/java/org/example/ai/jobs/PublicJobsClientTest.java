package org.example.ai.jobs;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.example.ai.gateway.GatewayUnavailableException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.*;

class PublicJobsClientTest {
    @RegisterExtension static WireMockExtension server = WireMockExtension.newInstance().build();
    @Test void onlyPublicFixedPathsAreUsedWithoutCredentialsAndWithBoundedQuery() {
        server.stubFor(get(urlPathEqualTo("/api/v1/vacancies/ai-candidates"))
                .willReturn(okJson("{\"success\":true,\"data\":[]}")));
        var client = new PublicJobsClient(server.baseUrl(), 1000);
        assertThat(client.candidates(new JobCriteria("driver", 1L, null, null, null, null, null, 3),
                List.of("driver", "haydovch"), null, "uz")).isEmpty();
        server.verify(getRequestedFor(urlPathEqualTo("/api/v1/vacancies/ai-candidates"))
                .withHeader("Authorization", absent()).withHeader("Accept-Language", equalTo("UZ"))
                .withQueryParam("limit", equalTo("40")).withQueryParam("regionId", equalTo("1")));
    }
    @Test void jobsFailureIsNotSilentlyConvertedToNoMatches() {
        server.stubFor(get(urlPathEqualTo("/api/v1/vacancies/ai-candidates")).willReturn(serverError()));
        assertThatThrownBy(() -> new PublicJobsClient(server.baseUrl(), 1000)
                .candidates(JobCriteria.defaults(3), List.of("driver"), null, "en"))
                .isInstanceOf(GatewayUnavailableException.class);
    }
}
