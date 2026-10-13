package org.example.ai.jobs;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.example.ai.LocaleNormalizer;
import org.example.ai.gateway.GatewayNotFoundException;
import org.example.ai.gateway.GatewayUnavailableException;
import org.example.ai.gateway.dto.GatewayEnvelope;
import org.example.ai.gateway.dto.RemoteSpringPage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

/** Only fixed PUBLIC GET endpoints; never forwards JWTs or accepts a model-supplied URL. */
@Component
public class PublicJobsClient {
    private final RestClient client;
    public PublicJobsClient(@Value("${ai.gateway.base-url}") String baseUrl,
            @Value("${ai.jobs.timeout-ms:3000}") int timeout) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Math.max(100, Math.min(timeout, 5000)));
        factory.setReadTimeout(Math.max(100, Math.min(timeout, 5000)));
        client = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Category(Long id, String name, String nameUz, String nameRu, String nameEn) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Candidate(Long id, String positionName, String companyName, String companyLogo,
            BigDecimal price, Long regionId, String address, String employmentType, String workSchedule,
            String experienceLevel, Long categoryId, Instant publishedAt, String shortDescription, String requirements) {}

    public List<Candidate> candidates(JobCriteria hard, List<String> terms, List<Long> ids, String language) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("limit", ids == null ? "40" : String.valueOf(Math.min(ids.size(), 6)));
        add(params, "regionId", hard.regionId()); add(params, "categoryId", hard.categoryId());
        add(params, "salaryMin", hard.salaryMin()); add(params, "experienceLevel", hard.experienceLevel());
        add(params, "employmentType", hard.employmentType()); add(params, "workSchedule", hard.workSchedule());
        if (terms != null) terms.stream().limit(12).forEach(term -> params.add("terms", term));
        if (ids != null) ids.stream().limit(6).forEach(id -> params.add("ids", id.toString()));
        List<Candidate> data = get("/api/v1/vacancies/ai-candidates", params, language,
                new ParameterizedTypeReference<GatewayEnvelope<List<Candidate>>>() {}).data();
        if (data == null || data.size() > 40) throw unavailable();
        return data;
    }

    public List<Category> categories(String language) {
        List<Category> items = new ArrayList<>();
        // Real Jobs taxonomy only, bounded to 500 categories; no permanent/user-specific cache.
        for (int page = 1; page <= 5; page++) {
            var params = new LinkedMultiValueMap<String, String>();
            params.add("page", Integer.toString(page)); params.add("perPage", "100");
            var data = get("/api/v1/job-categories", params, language,
                    new ParameterizedTypeReference<GatewayEnvelope<RemoteSpringPage<Category>>>() {}).data();
            if (data == null || data.content() == null || data.content().size() > 100) throw unavailable();
            items.addAll(data.content());
            if (data.content().size() < 100) break;
        }
        return List.copyOf(items);
    }

    public void validateCategory(Long id, String language) {
        if (id == null) return;
        try {
            var category = get("/api/v1/job-categories/" + id, null, language,
                    new ParameterizedTypeReference<GatewayEnvelope<Category>>() {}).data();
            if (category == null || !Objects.equals(category.id(), id)) throw new IllegalArgumentException("Select an available job category");
        } catch (GatewayNotFoundException missing) { throw new IllegalArgumentException("Select an available job category"); }
    }

    public void validateRegion(Long id, String language) {
        try {
            var region = get("/api/v1/regions/" + id, null, language,
                    new ParameterizedTypeReference<GatewayEnvelope<Map<String, Object>>>() {}).data();
            if (region == null || !(region.get("id") instanceof Number actual) || actual.longValue() != id)
                throw new IllegalArgumentException("Select an available region");
        } catch (GatewayNotFoundException missing) { throw new IllegalArgumentException("Select an available region"); }
    }

    private <T> GatewayEnvelope<T> get(String path, MultiValueMap<String, String> params, String language,
            ParameterizedTypeReference<GatewayEnvelope<T>> type) {
        try {
            var envelope = client.get().uri(builder -> {
                builder.path(path); if (params != null) builder.queryParams(params); return builder.build();
            }).header(HttpHeaders.ACCEPT_LANGUAGE, LocaleNormalizer.normalize(language).toUpperCase(Locale.ROOT))
                    .retrieve().body(type);
            if (envelope == null || !Boolean.TRUE.equals(envelope.success())) throw unavailable();
            return envelope;
        } catch (HttpStatusCodeException failure) {
            if (failure.getStatusCode().is4xxClientError()) throw new GatewayNotFoundException("Jobs endpoint unavailable", failure);
            throw new GatewayUnavailableException("Jobs service unavailable", failure);
        } catch (RestClientException failure) { throw new GatewayUnavailableException("Jobs service unavailable", failure); }
    }
    private static void add(MultiValueMap<String, String> params, String key, Object value) { if (value != null) params.add(key, value.toString()); }
    private static GatewayUnavailableException unavailable() { return new GatewayUnavailableException("Jobs service unavailable", null); }
}
