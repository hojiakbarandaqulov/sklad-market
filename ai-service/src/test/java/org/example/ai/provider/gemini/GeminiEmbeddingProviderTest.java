package org.example.ai.provider.gemini;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.example.ai.error.AiChatException;
import org.example.ai.error.AiErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class GeminiEmbeddingProviderTest {

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance().build();

    private GeminiEmbeddingProvider provider(String apiKey) {
        return new GeminiEmbeddingProvider(apiKey, wireMock.baseUrl(), 5, "gemini-embedding-001", 3, 32);
    }

    @Test
    void normalize_producesUnitLengthVector() {
        float[] normalized = GeminiEmbeddingProvider.normalize(new float[] {0f, 3f, 4f});
        assertVectorCloseTo(normalized, 0f, 0.6f, 0.8f);
        double norm = Math.sqrt(dot(normalized, normalized));
        assertThat(norm).isCloseTo(1.0, within(1e-6));
    }

    @Test
    void normalize_zeroVectorIsReturnedUnchanged() {
        assertThat(GeminiEmbeddingProvider.normalize(new float[] {0f, 0f, 0f})).containsExactly(0f, 0f, 0f);
    }

    @Test
    void embedDocuments_batchesInOneCallAndReturnsNormalizedVectors() {
        wireMock.stubFor(post(urlPathMatching(".*:batchEmbedContents"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{\"embeddings\":[{\"values\":[0.0,3.0,4.0]},{\"values\":[6.0,0.0,8.0]}]}")));

        List<float[]> vectors = provider("test-key").embedDocuments(List.of("цемент", "cement"));

        assertThat(vectors).hasSize(2);
        assertVectorCloseTo(vectors.get(0), 0f, 0.6f, 0.8f);   // 0,3,4 -> /5
        assertVectorCloseTo(vectors.get(1), 0.6f, 0f, 0.8f);   // 6,0,8 -> /10
        // Batch of 2 texts must be a SINGLE HTTP call (PLAN.md §7 item 2).
        wireMock.verify(1, postRequestedFor(urlPathMatching(".*:batchEmbedContents")));
    }

    @Test
    void embedQuery_returnsNormalizedVector() {
        wireMock.stubFor(post(urlPathMatching(".*:batchEmbedContents"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{\"embeddings\":[{\"values\":[0.0,0.0,5.0]}]}")));

        float[] vector = provider("test-key").embedQuery("оптовый рис");

        assertVectorCloseTo(vector, 0f, 0f, 1f);
    }

    @Test
    void embedDocuments_emptyInputMakesNoCall() {
        assertThat(provider("test-key").embedDocuments(List.of())).isEmpty();
        wireMock.verify(0, postRequestedFor(urlPathMatching(".*")));
    }

    @Test
    void missingApiKey_throwsProviderErrorWithoutCallingNetwork() {
        assertThatThrownBy(() -> provider("").embedQuery("x"))
                .isInstanceOf(AiChatException.class)
                .satisfies(e -> assertThat(((AiChatException) e).code()).isEqualTo(AiErrorCode.PROVIDER_ERROR));
        wireMock.verify(0, postRequestedFor(urlPathMatching(".*")));
    }

    @Test
    void providerError_isMappedToTypedException() {
        wireMock.stubFor(post(urlPathMatching(".*:batchEmbedContents"))
                .willReturn(aResponse().withStatus(400).withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":{\"code\":400,\"message\":\"bad\",\"status\":\"INVALID_ARGUMENT\"}}")));

        assertThatThrownBy(() -> provider("test-key").embedQuery("x"))
                .isInstanceOf(AiChatException.class)
                .satisfies(e -> assertThat(((AiChatException) e).code()).isEqualTo(AiErrorCode.PROVIDER_ERROR));
    }

    private static void assertVectorCloseTo(float[] actual, float... expected) {
        assertThat(actual).hasSize(expected.length);
        for (int i = 0; i < expected.length; i++) {
            assertThat(actual[i]).as("component %d", i).isCloseTo(expected[i], within(1e-6f));
        }
    }

    private static double dot(float[] a, float[] b) {
        double sum = 0;
        for (int i = 0; i < a.length; i++) {
            sum += (double) a[i] * b[i];
        }
        return sum;
    }
}
