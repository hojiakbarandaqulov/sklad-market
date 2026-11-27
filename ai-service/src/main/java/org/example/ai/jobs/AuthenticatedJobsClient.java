package org.example.ai.jobs;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.example.ai.LocaleNormalizer;
import org.example.ai.gateway.GatewayUnavailableException;
import org.example.ai.gateway.dto.GatewayEnvelope;
import org.example.exception.AiNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.util.*;

/** Fixed authenticated GETs only. Jobs, not AI, owns resume consent and vacancy authorization. */
@Component
public class AuthenticatedJobsClient {
    private final RestClient client;

    public AuthenticatedJobsClient(@Value("${ai.gateway.base-url}") String baseUrl,
            @Value("${ai.jobs.timeout-ms:3000}") int timeout) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Math.max(100, Math.min(timeout, 5000)));
        factory.setReadTimeout(Math.max(100, Math.min(timeout, 5000)));
        client = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ResumeProfile(Long resumeId, String desiredPosition, List<String> skills, Long regionId,
            String experienceLevel, BigDecimal salaryMin) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Candidate(Long id, String specialty, String experienceLevel, Long regionId,
            List<String> skills, List<String> reasons) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CandidatePage(List<Candidate> items, int page, int perPage, boolean hasMore, Long vacancyId) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OwnedVacancy(Long id, String positionName) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OwnedVacancies(List<OwnedVacancy> items) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ResumeSummary(Long id, String desiredPosition, Long regionId) {}

    public List<ResumeSummary> ownResumes(String token, String language) {
        var params = new LinkedMultiValueMap<String, String>(); params.add("page", "1"); params.add("perPage", "5");
        var data = get("/api/v1/me/resumes", params, token, language,
                new ParameterizedTypeReference<GatewayEnvelope<org.example.ai.gateway.dto.RemoteSpringPage<ResumeSummary>>>() {});
        if (data == null || data.content() == null || data.content().size() > 5) throw unavailable();
        return data.content();
    }

    public ResumeProfile resumeProfile(long id, String token, String language) {
        positive(id);
        var data = get("/api/v1/me/resumes/" + id + "/matching-profile", null, token, language,
                new ParameterizedTypeReference<GatewayEnvelope<ResumeProfile>>() {});
        if (data == null || !Objects.equals(data.resumeId(), id)) throw unavailable();
        return data;
    }

    public CandidatePage candidates(long vacancyId, String query, int page, int perPage, String token, String language) {
        positive(vacancyId);
        if (page < 1 || page > 1000 || perPage < 1 || perPage > 20) throw new IllegalArgumentException("Invalid candidate pagination");
        var params = new LinkedMultiValueMap<String, String>();
        params.add("vacancyId", Long.toString(vacancyId)); params.add("page", Integer.toString(page));
        params.add("perPage", Integer.toString(perPage));
        String safeQuery = JobPreferences.text(query, 120, "query");
        if (safeQuery != null) params.add("q", safeQuery);
        var data = get("/api/v1/vacancies/ai/candidates", params, token, language,
                new ParameterizedTypeReference<GatewayEnvelope<CandidatePage>>() {});
        if (data == null || data.items() == null || data.items().size() > perPage
                || !Objects.equals(data.vacancyId(), vacancyId)) throw unavailable();
        return data;
    }

    public Candidate candidate(long resumeId, long vacancyId, String token, String language) {
        positive(resumeId); positive(vacancyId);
        var params = new LinkedMultiValueMap<String, String>(); params.add("vacancyId", Long.toString(vacancyId));
        var data = get("/api/v1/vacancies/ai/candidates/" + resumeId, params, token, language,
                new ParameterizedTypeReference<GatewayEnvelope<Candidate>>() {});
        if (data == null || !Objects.equals(data.id(), resumeId)) throw unavailable();
        return data;
    }

    public List<OwnedVacancy> ownedVacancies(String token, String language) {
        var data = get("/api/v1/vacancies/ai/owned", null, token, language,
                new ParameterizedTypeReference<GatewayEnvelope<OwnedVacancies>>() {});
        if (data == null || data.items() == null || data.items().size() > 100) throw unavailable();
        return data.items();
    }

    private <T> T get(String path, MultiValueMap<String, String> params, String token, String language,
            ParameterizedTypeReference<GatewayEnvelope<T>> type) {
        if (token == null || token.isBlank()) throw new AccessDeniedException("Sign in to access job profiles");
        try {
            var envelope = client.get().uri(builder -> {
                builder.path(path); if (params != null) builder.queryParams(params); return builder.build();
            }).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .header(HttpHeaders.ACCEPT_LANGUAGE, LocaleNormalizer.normalize(language).toUpperCase(Locale.ROOT))
                    .retrieve().body(type);
            if (envelope == null || !Boolean.TRUE.equals(envelope.success())) throw unavailable();
            return envelope.data();
        } catch (HttpStatusCodeException error) {
            int status = error.getStatusCode().value();
            if (status == 401 || status == 403) throw new AccessDeniedException("Jobs access denied");
            if (status == 404) throw new AiNotFoundException("Job profile or vacancy is not available");
            if (status == 400) throw new IllegalArgumentException("Check the selected vacancy, resume and filters");
            throw unavailable();
        } catch (RestClientException error) { throw unavailable(); }
    }
    private static void positive(long id) { if (id <= 0) throw new IllegalArgumentException("A positive Jobs ID is required"); }
    private static GatewayUnavailableException unavailable() { return new GatewayUnavailableException("Jobs profiles are temporarily unavailable", null); }
}
