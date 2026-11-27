package org.example.ai.jobs;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.security.access.AccessDeniedException;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.*;

class AuthenticatedJobsClientTest {
    @RegisterExtension static WireMockExtension server = WireMockExtension.newInstance().build();
    @Test void privateReadsUseCallerCredentialAndDropContactsFromProjection() {
        server.stubFor(get(urlPathEqualTo("/api/v1/vacancies/ai/candidates/11")).willReturn(okJson("""
                {"success":true,"data":{"id":11,"specialty":"Java developer","experienceLevel":"NO_EXPERIENCE",
                "regionId":2,"skills":["Java"],"phone":"+998901234567","email":"private@example.com","fileId":"private-file"}}
                """)));
        var client = new AuthenticatedJobsClient(server.baseUrl(),1000);
        var candidate = client.candidate(11,7,"caller-only-jwt","uz");
        assertThat(candidate.specialty()).isEqualTo("Java developer");
        assertThat(candidate.toString()).doesNotContain("private@example", "private-file", "+998");
        server.verify(getRequestedFor(urlPathEqualTo("/api/v1/vacancies/ai/candidates/11"))
                .withHeader("Authorization",equalTo("Bearer caller-only-jwt"))
                .withHeader("Accept-Language",equalTo("UZ")).withQueryParam("vacancyId",equalTo("7")));
    }
    @Test void missingCredentialAndDownstreamForbiddenCannotBecomeEmptyResults() {
        var client = new AuthenticatedJobsClient(server.baseUrl(),1000);
        assertThatThrownBy(() -> client.ownedVacancies(null,"en")).isInstanceOf(AccessDeniedException.class);
        server.stubFor(get(urlPathEqualTo("/api/v1/vacancies/ai/owned")).willReturn(forbidden()));
        assertThatThrownBy(() -> client.ownedVacancies("jwt","en")).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> client.resumeProfile(-1,"jwt","en")).isInstanceOf(IllegalArgumentException.class);
    }
}
