package org.example.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.example.ai.error.AiChatException;
import org.example.ai.error.AiErrorCode;
import org.example.ai.gateway.GatewayClient;
import org.example.ai.gateway.GatewayImageBytes;
import org.example.ai.gateway.GatewayNotFoundException;
import org.example.ai.gateway.GatewayUnavailableException;
import org.example.ai.gateway.dto.GatewayEnvelope;
import org.example.ai.gateway.dto.RemoteCategoryAttributeDto;
import org.example.ai.gateway.dto.RemoteCategoryDto;
import org.example.ai.gateway.dto.RemoteSpringPage;
import org.example.ai.guardrail.RpmRateLimiter;
import org.example.ai.guardrail.TokenBudgetGuard;
import org.example.ai.guardrail.UsageLedgerService;
import org.example.ai.provider.ChatModelProvider;
import org.example.ai.provider.ImagePart;
import org.example.ai.provider.StructuredCompletionResult;
import org.example.ai.provider.StructuredGenerationRequest;
import org.example.ai.provider.TokenUsage;
import org.example.ai.seller.CategoryAttributeSchema;
import org.example.ai.tool.PlatformLanguage;
import org.example.dto.SuggestListingRequest;
import org.example.dto.SuggestListingResponse;
import org.example.dto.SuggestedAttributeDto;
import org.example.dto.SuggestedCategoryDto;
import org.example.service.SellerListingSuggestionService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Two-call vision-assisted listing suggestion (PLAN.md Phase 6, C8), entirely suggest-only:
 * <ol>
 *   <li>pick the single best-matching category from the platform's REAL active category list
 *       (never a model-hallucinated one — the JSON-schema {@code enum} constrains the model, and
 *       the result is re-verified against the same list server-side regardless, PLAN.md §4.2 item 5);</li>
 *   <li>fetch that category's REAL attribute schema (additive {@code GET
 *       /api/v1/categories/{slug}/attributes}, PLAN.md Phase 6) and ask for attribute values
 *       constrained to a dynamically-built JSON schema derived from it, then STRICTLY validate every
 *       returned value against the real {@code DataType}/{@code optionsJson} — anything that fails
 *       validation is dropped, never passed through.</li>
 * </ol>
 * Both calls use the advanced model (vision-capable) and go through the same per-user RPM/budget
 * guardrails the chat turn uses (PLAN.md §4.2 item 6) — this endpoint is a real Gemini cost center
 * too, not just the chat loop.
 */
@Slf4j
@Service
public class SellerListingSuggestionServiceImpl implements SellerListingSuggestionService {

    private static final List<String> IMAGE_MIME_PREFIX = List.of("image/");
    private static final float CATEGORY_TEMPERATURE = 0.2f;
    private static final float ATTRIBUTE_TEMPERATURE = 0.3f;
    private static final int CATEGORY_MAX_OUTPUT_TOKENS = 200;
    private static final int ATTRIBUTE_MAX_OUTPUT_TOKENS = 800;
    private static final int CATEGORY_PAGE_SIZE = 200;

    private final GatewayClient gatewayClient;
    private final ChatModelProvider chatModelProvider;
    private final RpmRateLimiter rateLimiter;
    private final TokenBudgetGuard budgetGuard;
    private final UsageLedgerService usageLedgerService;
    private final ObjectMapper objectMapper;
    private final String advancedModel;
    private final int maxDescriptionChars;
    private final int maxImages;
    private final long maxImageBytes;

    public SellerListingSuggestionServiceImpl(
            GatewayClient gatewayClient,
            ChatModelProvider chatModelProvider,
            RpmRateLimiter rateLimiter,
            TokenBudgetGuard budgetGuard,
            UsageLedgerService usageLedgerService,
            ObjectMapper objectMapper,
            @Value("${ai.gemini.chat-model-advanced:gemini-2.5-pro}") String advancedModel,
            @Value("${ai.seller.max-description-chars:2000}") int maxDescriptionChars,
            @Value("${ai.seller.max-images:4}") int maxImages,
            @Value("${ai.seller.max-image-bytes:6000000}") long maxImageBytes) {
        this.gatewayClient = gatewayClient;
        this.chatModelProvider = chatModelProvider;
        this.rateLimiter = rateLimiter;
        this.budgetGuard = budgetGuard;
        this.usageLedgerService = usageLedgerService;
        this.objectMapper = objectMapper;
        this.advancedModel = advancedModel;
        this.maxDescriptionChars = maxDescriptionChars;
        this.maxImages = maxImages;
        this.maxImageBytes = maxImageBytes;
    }

    @Override
    public SuggestListingResponse suggest(String userSub, String bearerToken, String acceptLanguage, SuggestListingRequest request) {
        if (!rateLimiter.tryConsume(userSub)) {
            throw new AiChatException(AiErrorCode.RATE_LIMITED, "Too many requests, please slow down.");
        }
        if (!budgetGuard.hasRemainingBudget(userSub)) {
            throw new AiChatException(AiErrorCode.BUDGET_EXCEEDED, "Daily usage limit reached.");
        }

        String description = request.getDescription() == null ? "" : request.getDescription().trim();
        if (description.isEmpty()) {
            throw new AiChatException(AiErrorCode.INVALID_INPUT, "description is required");
        }
        if (description.length() > maxDescriptionChars) {
            throw new AiChatException(AiErrorCode.INVALID_INPUT, "description exceeds " + maxDescriptionChars + " characters");
        }
        List<String> imageIds = request.getImageIds() == null ? List.of() : request.getImageIds();
        if (imageIds.size() > maxImages) {
            throw new AiChatException(AiErrorCode.INVALID_INPUT, "at most " + maxImages + " images are supported");
        }

        String platformLanguage = PlatformLanguage.header(acceptLanguage);
        List<ImagePart> images = fetchImages(imageIds, bearerToken, platformLanguage);

        long tokensIn = 0;
        long tokensOut = 0;

        List<RemoteCategoryDto> categories = fetchActiveCategories(bearerToken, platformLanguage);
        if (categories.isEmpty()) {
            usageLedgerService.recordUsage(userSub, tokensIn, tokensOut);
            return SuggestListingResponse.builder()
                    .category(null)
                    .categoryConfidence(null)
                    .attributes(List.of())
                    .missingRequired(List.of())
                    .notes("The platform has no active categories yet — nothing to classify against.")
                    .build();
        }

        CategoryPick pick = pickCategory(description, images, categories);
        tokensIn += pick.usage().promptTokens();
        tokensOut += pick.usage().candidatesTokens();

        RemoteCategoryDto matched = categories.stream()
                .filter(c -> c.slug() != null && c.slug().equalsIgnoreCase(pick.categorySlug()))
                .findFirst().orElse(null);
        if (matched == null) {
            usageLedgerService.recordUsage(userSub, tokensIn, tokensOut);
            return SuggestListingResponse.builder()
                    .category(null)
                    .categoryConfidence(pick.confidence())
                    .attributes(List.of())
                    .missingRequired(List.of())
                    .notes("Could not confidently match a real category for this description.")
                    .build();
        }

        List<RemoteCategoryAttributeDto> schema = fetchAttributeSchema(matched.slug(), bearerToken, platformLanguage);
        SuggestedCategoryDto suggestedCategory = SuggestedCategoryDto.builder()
                .slug(matched.slug())
                .name(matched.displayName(normalizeLocale(acceptLanguage)))
                .build();

        if (schema.isEmpty()) {
            usageLedgerService.recordUsage(userSub, tokensIn, tokensOut);
            return SuggestListingResponse.builder()
                    .category(suggestedCategory)
                    .categoryConfidence(pick.confidence())
                    .attributes(List.of())
                    .missingRequired(List.of())
                    .notes("This category has no configured attributes yet.")
                    .build();
        }

        AttributeExtraction extraction = extractAttributes(description, images, matched, schema);
        tokensIn += extraction.usage().promptTokens();
        tokensOut += extraction.usage().candidatesTokens();

        usageLedgerService.recordUsage(userSub, tokensIn, tokensOut);

        List<SuggestedAttributeDto> accepted = new ArrayList<>();
        List<String> missingRequired = new ArrayList<>();
        for (RemoteCategoryAttributeDto attribute : schema) {
            Object rawValue = extraction.values().get(attribute.code());
            List<String> options = CategoryAttributeSchema.parseOptions(attribute.optionsJson());
            if (CategoryAttributeSchema.isValidValue(attribute, rawValue, options)) {
                accepted.add(SuggestedAttributeDto.builder()
                        .code(attribute.code())
                        .label(attribute.label())
                        .dataType(attribute.dataType())
                        .value(rawValue)
                        .build());
            } else if (Boolean.TRUE.equals(attribute.isRequired())) {
                missingRequired.add(attribute.code());
            }
        }

        return SuggestListingResponse.builder()
                .category(suggestedCategory)
                .categoryConfidence(pick.confidence())
                .attributes(accepted)
                .missingRequired(missingRequired)
                .notes(null)
                .build();
    }

    private List<ImagePart> fetchImages(List<String> imageIds, String bearerToken, String platformLanguage) {
        List<ImagePart> images = new ArrayList<>();
        for (String imageId : imageIds) {
            if (imageId == null || imageId.isBlank()) {
                continue;
            }
            try {
                GatewayImageBytes bytes = gatewayClient.getBytes(
                        "/api/v1/attach/open/{id}", bearerToken, platformLanguage, imageId);
                if (bytes.data() == null || bytes.data().length == 0) {
                    continue;
                }
                if (bytes.data().length > maxImageBytes) {
                    log.info("Skipping seller-supplied image {} — exceeds max-image-bytes", imageId);
                    continue;
                }
                String contentType = bytes.contentType();
                boolean isImage = contentType != null
                        && IMAGE_MIME_PREFIX.stream().anyMatch(prefix -> contentType.toLowerCase().startsWith(prefix));
                if (!isImage) {
                    log.info("Skipping attachment {} — not an image content-type ({})", imageId, contentType);
                    continue;
                }
                images.add(new ImagePart(bytes.data(), contentType));
            } catch (GatewayNotFoundException | GatewayUnavailableException e) {
                log.info("Could not fetch attachment {} for suggest-listing: {}", imageId, e.getMessage());
            }
        }
        return images;
    }

    private List<RemoteCategoryDto> fetchActiveCategories(String bearerToken, String platformLanguage) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("page", "0");
        params.add("size", String.valueOf(CATEGORY_PAGE_SIZE));
        try {
            GatewayEnvelope<RemoteSpringPage<RemoteCategoryDto>> envelope = gatewayClient.get(
                    "/api/v1/categories", params, bearerToken, platformLanguage,
                    new ParameterizedTypeReference<GatewayEnvelope<RemoteSpringPage<RemoteCategoryDto>>>() {
                    });
            RemoteSpringPage<RemoteCategoryDto> page = envelope == null ? null : envelope.data();
            List<RemoteCategoryDto> content = page == null || page.content() == null ? List.of() : page.content();
            return content.stream().filter(c -> !Boolean.FALSE.equals(c.isActive())).toList();
        } catch (GatewayNotFoundException e) {
            return List.of();
        } catch (GatewayUnavailableException e) {
            throw new AiChatException(AiErrorCode.PROVIDER_ERROR, "The category service is temporarily unavailable");
        }
    }

    private List<RemoteCategoryAttributeDto> fetchAttributeSchema(String slug, String bearerToken, String platformLanguage) {
        try {
            GatewayEnvelope<List<RemoteCategoryAttributeDto>> envelope = gatewayClient.get(
                    "/api/v1/categories/{slug}/attributes", null, bearerToken, platformLanguage,
                    new ParameterizedTypeReference<GatewayEnvelope<List<RemoteCategoryAttributeDto>>>() {
                    }, slug);
            return envelope == null || envelope.data() == null ? List.of() : envelope.data();
        } catch (GatewayNotFoundException e) {
            return List.of();
        } catch (GatewayUnavailableException e) {
            throw new AiChatException(AiErrorCode.PROVIDER_ERROR, "The category service is temporarily unavailable");
        }
    }

    private CategoryPick pickCategory(String description, List<ImagePart> images, List<RemoteCategoryDto> categories) {
        List<String> slugs = categories.stream().map(RemoteCategoryDto::slug).filter(s -> s != null).toList();
        Map<String, Object> categorySlugProperty = new LinkedHashMap<>();
        categorySlugProperty.put("type", "STRING");
        categorySlugProperty.put("enum", slugs);
        Map<String, Object> confidenceProperty = Map.of("type", "NUMBER", "description", "Confidence 0.0-1.0.");
        Map<String, Object> responseSchema = Map.of(
                "type", "OBJECT",
                "properties", Map.of("categorySlug", categorySlugProperty, "confidence", confidenceProperty),
                "required", List.of("categorySlug", "confidence"));

        StringBuilder candidateList = new StringBuilder();
        for (RemoteCategoryDto category : categories) {
            candidateList.append("- ").append(category.slug()).append(": ")
                    .append(category.displayName("ru")).append("\n");
        }

        String systemInstruction =
                "You classify a new Sklad Market (B2B wholesale marketplace) product listing into exactly one "
                        + "of the given candidate categories, using the seller's description and any photos. "
                        + "You MUST pick categorySlug from the candidate list only — never invent a slug that "
                        + "isn't listed. If nothing fits well, pick the closest one and report a low confidence.";
        String userText = "Seller's product description:\n" + description + "\n\nCandidate categories (slug: name):\n" + candidateList;

        StructuredCompletionResult result = generate(systemInstruction, userText, images, responseSchema,
                CATEGORY_TEMPERATURE, CATEGORY_MAX_OUTPUT_TOKENS);
        Map<String, Object> parsed = parseJsonObject(result.json());
        String categorySlug = asString(parsed.get("categorySlug"));
        Double confidence = asDouble(parsed.get("confidence"));
        return new CategoryPick(categorySlug, confidence, result.usage());
    }

    private AttributeExtraction extractAttributes(
            String description, List<ImagePart> images, RemoteCategoryDto category, List<RemoteCategoryAttributeDto> schema) {
        Map<String, Object> properties = new LinkedHashMap<>();
        for (RemoteCategoryAttributeDto attribute : schema) {
            properties.put(attribute.code(), CategoryAttributeSchema.jsonSchemaProperty(attribute));
        }
        Map<String, Object> responseSchema = Map.of("type", "OBJECT", "properties", properties, "required", List.of());

        StringBuilder fieldList = new StringBuilder();
        for (RemoteCategoryAttributeDto attribute : schema) {
            fieldList.append("- ").append(attribute.code()).append(" (").append(attribute.label()).append(", ")
                    .append(attribute.dataType())
                    .append(Boolean.TRUE.equals(attribute.isRequired()) ? ", required" : ", optional")
                    .append(")\n");
        }

        String systemInstruction =
                "You extract structured attribute values for a Sklad Market (B2B wholesale marketplace) product "
                        + "listing in category \"" + category.displayName("ru") + "\", from the seller's "
                        + "description and any photos. Only include a field if the description/photos give real "
                        + "evidence for it — omit fields you're not reasonably confident about rather than "
                        + "guessing. For SELECT-type fields, only use one of the exact enum values given.";
        String userText = "Seller's product description:\n" + description + "\n\nFields to extract:\n" + fieldList;

        StructuredCompletionResult result = generate(systemInstruction, userText, images, responseSchema,
                ATTRIBUTE_TEMPERATURE, ATTRIBUTE_MAX_OUTPUT_TOKENS);
        Map<String, Object> values = parseJsonObject(result.json());
        return new AttributeExtraction(values, result.usage());
    }

    private StructuredCompletionResult generate(
            String systemInstruction, String userText, List<ImagePart> images, Map<String, Object> responseSchema,
            float temperature, int maxOutputTokens) {
        try {
            return chatModelProvider.generateStructured(new StructuredGenerationRequest(
                    advancedModel, systemInstruction, userText, images, responseSchema, temperature, maxOutputTokens));
        } catch (AiChatException e) {
            throw e;
        } catch (Exception unexpected) {
            log.warn("Structured generation failed", unexpected);
            throw new AiChatException(AiErrorCode.PROVIDER_ERROR, "The AI provider is temporarily unavailable", unexpected);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJsonObject(String json) {
        try {
            Object parsed = objectMapper.readValue(json, Object.class);
            return parsed instanceof Map ? (Map<String, Object>) parsed : Map.of();
        } catch (Exception malformed) {
            log.warn("Structured model output was not valid JSON: {}", malformed.getMessage());
            return Map.of();
        }
    }

    private String asString(Object value) {
        return value instanceof String s && !s.isBlank() ? s : null;
    }

    private Double asDouble(Object value) {
        return value instanceof Number number ? number.doubleValue() : null;
    }

    private String normalizeLocale(String acceptLanguage) {
        if (acceptLanguage == null) {
            return "ru";
        }
        return acceptLanguage.trim().toLowerCase();
    }

    private record CategoryPick(String categorySlug, Double confidence, TokenUsage usage) {
    }

    private record AttributeExtraction(Map<String, Object> values, TokenUsage usage) {
    }
}
