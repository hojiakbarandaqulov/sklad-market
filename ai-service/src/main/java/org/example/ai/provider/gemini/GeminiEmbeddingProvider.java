package org.example.ai.provider.gemini;

import com.google.genai.Client;
import com.google.genai.errors.ApiException;
import com.google.genai.errors.ClientException;
import com.google.genai.errors.GenAiIOException;
import com.google.genai.errors.ServerException;
import com.google.genai.types.ContentEmbedding;
import com.google.genai.types.EmbedContentConfig;
import com.google.genai.types.EmbedContentResponse;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.HttpRetryOptions;
import lombok.extern.slf4j.Slf4j;
import org.example.ai.error.AiChatException;
import org.example.ai.error.AiErrorCode;
import org.example.ai.provider.EmbeddingProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.InterruptedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * {@link EmbeddingProvider} over the official {@code com.google.genai} SDK (verified 1.60.0),
 * mirroring {@link GeminiChatModelProvider}'s hardening (PLAN.md §7 items 1-3):
 * <ul>
 *   <li>the SDK's default 5-attempt blocking retry is disabled ({@code HttpRetryOptions.attempts(1)});
 *       this class implements its own single retry with jitter, preserving the real HTTP status;</li>
 *   <li>batching uses {@code Models.embedContent(model, List&lt;String&gt;, EmbedContentConfig)} which
 *       the SDK routes to a single {@code :batchEmbedContents} HTTP call (§7 item 2) — read from the
 *       actual 1.60.0 classes: {@code EmbedContentResponse.embeddings() -> List<ContentEmbedding>},
 *       {@code ContentEmbedding.values() -> List<Float>};</li>
 *   <li>{@code output_dimensionality} MRL-truncates to 768 dims, which is NOT unit-length — every
 *       vector is L2-renormalized here (§2) so cosine math stays correct.</li>
 * </ul>
 * The {@link Client} is built lazily so the service still boots when {@code GEMINI_API_KEY} is unset;
 * the failure then surfaces as a typed {@link AiErrorCode#PROVIDER_ERROR} to the indexer (which
 * records it as a FAILURE run and keeps chat untouched) or to the search endpoint.
 */
@Slf4j
@Component
public class GeminiEmbeddingProvider implements EmbeddingProvider {

    private static final List<Integer> RETRYABLE_HTTP_STATUS = List.of(408, 429, 500, 502, 503, 504);
    private static final long RETRY_BASE_DELAY_MILLIS = 400L;
    private static final long RETRY_JITTER_MILLIS = 600L;
    private static final String TASK_DOCUMENT = "RETRIEVAL_DOCUMENT";
    private static final String TASK_QUERY = "RETRIEVAL_QUERY";

    private final String apiKey;
    private final String baseUrl;
    private final int timeoutSeconds;
    private final String model;
    private final int dimension;
    private final int batchSize;

    private volatile Client client;
    private final Object clientLock = new Object();

    public GeminiEmbeddingProvider(
            @Value("${ai.gemini.api-key:}") String apiKey,
            @Value("${ai.gemini.base-url:}") String baseUrl,
            @Value("${ai.limits.request-timeout-seconds:60}") int timeoutSeconds,
            @Value("${ai.embedding.model:gemini-embedding-001}") String model,
            @Value("${ai.embedding.dim:768}") int dimension,
            @Value("${ai.embedding.batch-size:32}") int batchSize) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.timeoutSeconds = timeoutSeconds;
        this.model = model;
        this.dimension = dimension;
        this.batchSize = Math.max(batchSize, 1);
    }

    @Override
    public int dimension() {
        return dimension;
    }

    @Override
    public List<float[]> embedDocuments(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }
        Client activeClient = client();
        EmbedContentConfig config = configFor(TASK_DOCUMENT);
        List<float[]> results = new ArrayList<>(texts.size());
        for (int start = 0; start < texts.size(); start += batchSize) {
            List<String> batch = texts.subList(start, Math.min(start + batchSize, texts.size()));
            EmbedContentResponse response =
                    withSingleRetry(() -> activeClient.models.embedContent(model, batch, config));
            List<ContentEmbedding> embeddings = response.embeddings().orElse(List.of());
            if (embeddings.size() != batch.size()) {
                throw new AiChatException(AiErrorCode.PROVIDER_ERROR,
                        "Embedding provider returned " + embeddings.size() + " vectors for " + batch.size() + " inputs");
            }
            for (ContentEmbedding embedding : embeddings) {
                results.add(normalize(toFloatArray(embedding)));
            }
        }
        return results;
    }

    @Override
    public float[] embedQuery(String text) {
        if (text == null || text.isBlank()) {
            throw new AiChatException(AiErrorCode.INVALID_INPUT, "Query text must not be empty");
        }
        Client activeClient = client();
        EmbedContentConfig config = configFor(TASK_QUERY);
        EmbedContentResponse response =
                withSingleRetry(() -> activeClient.models.embedContent(model, List.of(text), config));
        List<ContentEmbedding> embeddings = response.embeddings().orElse(List.of());
        if (embeddings.isEmpty()) {
            throw new AiChatException(AiErrorCode.PROVIDER_ERROR, "Embedding provider returned no vector for the query");
        }
        return normalize(toFloatArray(embeddings.get(0)));
    }

    private EmbedContentConfig configFor(String taskType) {
        return EmbedContentConfig.builder()
                .taskType(taskType)
                .outputDimensionality(dimension)
                .build();
    }

    private float[] toFloatArray(ContentEmbedding embedding) {
        List<Float> values = embedding.values().orElse(List.of());
        if (values.size() != dimension) {
            throw new AiChatException(AiErrorCode.PROVIDER_ERROR,
                    "Embedding provider returned a " + values.size() + "-dim vector, expected " + dimension);
        }
        float[] array = new float[values.size()];
        for (int i = 0; i < values.size(); i++) {
            array[i] = values.get(i);
        }
        return array;
    }

    /**
     * L2-normalizes to unit length. MRL truncation ({@code output_dimensionality} &lt; the native
     * dim) breaks the model's own normalization, so this is mandatory, not cosmetic (PLAN.md §2).
     */
    static float[] normalize(float[] vector) {
        double sumSquares = 0.0;
        for (float v : vector) {
            sumSquares += (double) v * v;
        }
        double norm = Math.sqrt(sumSquares);
        if (norm == 0.0) {
            return vector;
        }
        float[] normalized = new float[vector.length];
        for (int i = 0; i < vector.length; i++) {
            normalized[i] = (float) (vector[i] / norm);
        }
        return normalized;
    }

    private Client client() {
        Client existing = client;
        if (existing != null) {
            return existing;
        }
        synchronized (clientLock) {
            if (client == null) {
                if (apiKey == null || apiKey.isBlank()) {
                    throw new AiChatException(AiErrorCode.PROVIDER_ERROR, "AI embedding provider is not configured");
                }
                HttpOptions.Builder httpOptions = HttpOptions.builder()
                        .retryOptions(HttpRetryOptions.builder().attempts(1).build())
                        .timeout(timeoutSeconds * 1000);
                if (baseUrl != null && !baseUrl.isBlank()) {
                    httpOptions.baseUrl(baseUrl);
                }
                client = Client.builder().apiKey(apiKey).httpOptions(httpOptions.build()).build();
            }
            return client;
        }
    }

    private <T> T withSingleRetry(Supplier<T> call) {
        try {
            return call.get();
        } catch (RuntimeException first) {
            if (!isRetryable(first)) {
                throw mapException(first);
            }
            log.warn("Gemini embed call failed, retrying once: {}", first.getMessage());
            sleepWithJitter();
            try {
                return call.get();
            } catch (RuntimeException second) {
                throw mapException(second);
            }
        }
    }

    private boolean isRetryable(RuntimeException e) {
        if (e instanceof ServerException) {
            return true;
        }
        if (e instanceof ClientException clientException) {
            return RETRYABLE_HTTP_STATUS.contains(clientException.code());
        }
        return e instanceof GenAiIOException;
    }

    private AiChatException mapException(RuntimeException e) {
        if (e instanceof ClientException clientException) {
            if (clientException.code() == 429) {
                return new AiChatException(AiErrorCode.RATE_LIMITED, "The embedding provider is rate-limiting requests", e);
            }
            return new AiChatException(
                    AiErrorCode.PROVIDER_ERROR, "The embedding provider rejected the request (" + clientException.code() + ")", e);
        }
        if (e instanceof GenAiIOException && isTimeout(e.getCause())) {
            return new AiChatException(AiErrorCode.TIMEOUT, "The embedding provider timed out", e);
        }
        if (e instanceof ApiException apiException) {
            return new AiChatException(
                    AiErrorCode.PROVIDER_ERROR, "The embedding provider returned an error (" + apiException.code() + ")", e);
        }
        return new AiChatException(AiErrorCode.PROVIDER_ERROR, "The embedding provider is temporarily unavailable", e);
    }

    private boolean isTimeout(Throwable cause) {
        return cause instanceof InterruptedIOException || cause instanceof java.net.SocketTimeoutException;
    }

    private void sleepWithJitter() {
        try {
            long delay = RETRY_BASE_DELAY_MILLIS + (long) (Math.random() * RETRY_JITTER_MILLIS);
            Thread.sleep(delay);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AiChatException(AiErrorCode.PROVIDER_ERROR, "Interrupted while retrying the embedding provider", interrupted);
        }
    }
}
