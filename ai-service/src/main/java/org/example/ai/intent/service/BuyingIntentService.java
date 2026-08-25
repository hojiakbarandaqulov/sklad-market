package org.example.ai.intent.service;

import org.example.ai.intent.dto.BuyingIntentMatchResponse;
import org.example.ai.intent.dto.BuyingIntentMatchResult;
import org.example.ai.intent.dto.BuyingIntentRequest;
import org.example.ai.intent.dto.BuyingIntentResponse;
import org.example.ai.intent.entity.BuyingIntent;
import org.example.ai.intent.entity.BuyingIntentStatus;
import org.example.ai.intent.repository.BuyingIntentRepository;
import org.example.exception.AiNotFoundException;
import org.example.dto.PageMeta;
import org.example.dto.PagedResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class BuyingIntentService {

    private static final Duration MIN_LIFETIME = Duration.ofMinutes(5);
    private static final Duration MAX_LIFETIME = Duration.ofDays(90);
    private static final int SEARCH_CANDIDATE_LIMIT = 100;
    private static final int MAX_LIST_PAGE_SIZE = 50;
    private static final List<BuyingIntentStatus> ACTIVE_STATUSES =
            List.of(BuyingIntentStatus.DRAFT, BuyingIntentStatus.PUBLISHED);
    public static final String PUBLICATION_DISCLOSURE =
            "When published, category, region, need text, quantity, budget, and expiry are visible to sellers. "
                    + "Owner and contact columns are excluded, but automated screening cannot guarantee anonymity.";

    private final BuyingIntentRepository repository;
    private final BuyingIntentPrivacyGuard privacyGuard;
    private final Clock clock;
    private final int maxActivePerOwner;

    @Autowired
    public BuyingIntentService(
            BuyingIntentRepository repository,
            BuyingIntentPrivacyGuard privacyGuard,
            @Value("${ai.buying-intents.max-active-per-user:20}") int maxActivePerOwner) {
        this(repository, privacyGuard, Clock.systemUTC(), maxActivePerOwner);
    }

    BuyingIntentService(BuyingIntentRepository repository, BuyingIntentPrivacyGuard privacyGuard, Clock clock) {
        this(repository, privacyGuard, clock, 20);
    }

    BuyingIntentService(
            BuyingIntentRepository repository,
            BuyingIntentPrivacyGuard privacyGuard,
            Clock clock,
            int maxActivePerOwner) {
        this.repository = repository;
        this.privacyGuard = privacyGuard;
        this.clock = clock;
        if (maxActivePerOwner < 1 || maxActivePerOwner > 1_000) {
            throw new IllegalArgumentException("max active buying intents must be between 1 and 1000");
        }
        this.maxActivePerOwner = maxActivePerOwner;
    }

    @Transactional(noRollbackFor = BuyingIntentStateException.class)
    public BuyingIntentResponse createDraft(String ownerSub, BuyingIntentRequest request) {
        requireOwner(ownerSub);
        validate(request);
        Instant now = clock.instant();
        repository.acquireOwnerQuotaLock(ownerSub);
        repository.expireDueForOwner(ownerSub, ACTIVE_STATUSES, now);
        long activeCount = repository.countByOwnerSubAndStatusInAndExpiresAtAfter(ownerSub, ACTIVE_STATUSES, now);
        if (activeCount >= maxActivePerOwner) {
            throw new BuyingIntentStateException(
                    "ACTIVE_LIMIT_REACHED",
                    "Close an active buying intent before creating another (limit " + maxActivePerOwner + ").");
        }
        BuyingIntent intent = new BuyingIntent();
        intent.setOwnerSub(ownerSub);
        intent.setStatus(BuyingIntentStatus.DRAFT);
        copyRequest(request, intent);
        intent.setCreatedAt(now);
        intent.setUpdatedAt(now);
        return toOwnerResponse(repository.save(intent));
    }

    @Transactional(noRollbackFor = BuyingIntentStateException.class)
    public BuyingIntentResponse updateDraft(String ownerSub, UUID id, BuyingIntentRequest request) {
        requireOwner(ownerSub);
        validate(request);
        BuyingIntent intent = requireOwnedForUpdate(ownerSub, id);
        expireIfDue(intent);
        if (intent.getStatus() != BuyingIntentStatus.DRAFT) {
            throw state(intent, "Only a draft buying intent can be edited.");
        }
        copyRequest(request, intent);
        intent.setUpdatedAt(clock.instant());
        return toOwnerResponse(repository.save(intent));
    }

    /** Explicit buyer confirmation: this is the only DRAFT -&gt; PUBLISHED transition. */
    @Transactional(noRollbackFor = BuyingIntentStateException.class)
    public BuyingIntentResponse publish(String ownerSub, UUID id, boolean publicationConsent) {
        requireOwner(ownerSub);
        if (!publicationConsent) {
            throw new IllegalArgumentException("Explicit publication consent is required because business text becomes seller-visible.");
        }
        BuyingIntent intent = requireOwnedForUpdate(ownerSub, id);
        expireIfDue(intent);
        if (intent.getStatus() == BuyingIntentStatus.PUBLISHED) {
            return toOwnerResponse(intent);
        }
        if (intent.getStatus() != BuyingIntentStatus.DRAFT) {
            throw state(intent, "Only a draft buying intent can be published.");
        }
        Instant now = clock.instant();
        intent.setStatus(BuyingIntentStatus.PUBLISHED);
        intent.setPublishedAt(now);
        intent.setPublicationConsentAt(now);
        intent.setUpdatedAt(now);
        return toOwnerResponse(repository.save(intent));
    }

    @Transactional(noRollbackFor = BuyingIntentStateException.class)
    public BuyingIntentResponse close(String ownerSub, UUID id) {
        requireOwner(ownerSub);
        BuyingIntent intent = requireOwnedForUpdate(ownerSub, id);
        expireIfDue(intent);
        if (intent.getStatus() == BuyingIntentStatus.CLOSED) {
            return toOwnerResponse(intent);
        }
        if (intent.getStatus() == BuyingIntentStatus.EXPIRED) {
            throw state(intent, "This buying intent has already expired.");
        }
        Instant now = clock.instant();
        intent.setStatus(BuyingIntentStatus.CLOSED);
        intent.setClosedAt(now);
        intent.setUpdatedAt(now);
        return toOwnerResponse(repository.save(intent));
    }

    @Transactional
    public BuyingIntentResponse getOwn(String ownerSub, UUID id) {
        requireOwner(ownerSub);
        BuyingIntent intent = repository.findByIdAndOwnerSub(id, ownerSub)
                .orElseThrow(() -> new AiNotFoundException("Buying intent not found"));
        if (expireIfDue(intent)) {
            intent = repository.save(intent);
        }
        return toOwnerResponse(intent);
    }

    @Transactional
    public PagedResponse<BuyingIntentResponse> listOwn(
            String ownerSub, int page, int perPage, String requestedStatus) {
        requireOwner(ownerSub);
        if (page < 1) {
            throw new IllegalArgumentException("page must be greater than or equal to 1");
        }
        if (perPage < 1 || perPage > MAX_LIST_PAGE_SIZE) {
            throw new IllegalArgumentException("perPage must be between 1 and " + MAX_LIST_PAGE_SIZE);
        }
        Instant now = clock.instant();
        repository.expireDueForOwner(ownerSub, ACTIVE_STATUSES, now);
        BuyingIntentStatus status = parseStatus(requestedStatus);
        PageRequest pageable = PageRequest.of(page - 1, perPage);
        Page<BuyingIntent> result = status == null
                ? repository.findAllByOwnerSubOrderByCreatedAtDesc(ownerSub, pageable)
                : repository.findAllByOwnerSubAndStatusOrderByCreatedAtDesc(ownerSub, status, pageable);
        List<BuyingIntentResponse> items = result.getContent().stream().map(this::toOwnerResponse).toList();
        return new PagedResponse<>(items,
                new PageMeta(result.getTotalElements(), page, perPage, result.getTotalPages()));
    }

    /**
     * Seller discovery is deterministic and excludes owner/contact columns. Repository predicates
     * enforce that only explicitly published, unexpired records enter the ranking set. The business
     * fields are buyer-authored text, so callers are explicitly warned this is not anonymization.
     */
    @Transactional(readOnly = true)
    public BuyingIntentMatchResult searchPublished(
            String category, String region, String query, Integer requestedLimit) {
        String normalizedCategory = blankToNull(category);
        String normalizedRegion = blankToNull(region);
        int limit = clamp(requestedLimit == null ? 10 : requestedLimit, 1, 50);
        Instant now = clock.instant();
        Page<BuyingIntent> candidatePage = repository.searchPublished(
                BuyingIntentStatus.PUBLISHED,
                now,
                normalizedCategory,
                normalizedRegion,
                PageRequest.of(0, SEARCH_CANDIDATE_LIMIT,
                        Sort.by(Sort.Order.desc("publishedAt"), Sort.Order.asc("id"))));
        List<BuyingIntent> candidates = candidatePage.getContent();

        Set<String> queryTokens = tokenize(query);
        List<BuyingIntentMatchResponse> matches = candidates.stream()
                .map(intent -> score(intent, normalizedCategory, normalizedRegion, queryTokens))
                .filter(scored -> queryTokens.isEmpty() || scored.queryMatchCount > 0)
                .sorted(Comparator.comparingInt(ScoredIntent::score).reversed()
                        .thenComparing(scored -> scored.intent().getPublishedAt(), Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(scored -> scored.intent().getId()))
                .limit(limit)
                .map(this::toSellerResponse)
                .toList();
        return new BuyingIntentMatchResult(
                matches,
                candidates.size(),
                candidatePage.getTotalElements(),
                candidatePage.getTotalElements() > candidates.size(),
                now,
                "Owner/contact columns are excluded. Buyer-published category, region, need, quantity, and budget "
                        + "are seller-visible user text; best-effort screening cannot guarantee anonymity.",
                false);
    }

    private ScoredIntent score(BuyingIntent intent, String category, String region, Set<String> queryTokens) {
        int score = 10;
        int queryMatches = 0;
        List<String> reasons = new ArrayList<>();
        reasons.add("OPEN_BUYING_INTENT");
        if (category != null && category.equalsIgnoreCase(intent.getCategory())) {
            score += 40;
            reasons.add("CATEGORY_MATCH");
        }
        if (region != null && region.equalsIgnoreCase(intent.getRegion())) {
            score += 25;
            reasons.add("REGION_MATCH");
        }
        if (!queryTokens.isEmpty()) {
            Set<String> documentTokens = tokenize(String.join(" ",
                    safe(intent.getCategory()), safe(intent.getRegion()), safe(intent.getNeedText())));
            queryMatches = (int) queryTokens.stream().filter(documentTokens::contains).count();
            if (queryMatches > 0) {
                double ratio = (double) queryMatches / queryTokens.size();
                score += BigDecimal.valueOf(ratio * 20).setScale(0, RoundingMode.HALF_UP).intValue();
                reasons.add("NEED_TEXT_MATCH");
            }
        }
        if (intent.getQuantity() != null) {
            score += 5;
            reasons.add("QUANTITY_SPECIFIED");
        }
        if (intent.getBudgetMin() != null || intent.getBudgetMax() != null) {
            score += 5;
            reasons.add("BUDGET_SPECIFIED");
        }
        return new ScoredIntent(intent, Math.min(score, 100), queryMatches, List.copyOf(reasons));
    }

    private BuyingIntentMatchResponse toSellerResponse(ScoredIntent scored) {
        BuyingIntent intent = scored.intent();
        return new BuyingIntentMatchResponse(
                intent.getId(), intent.getCategory(), intent.getRegion(), intent.getNeedText(),
                intent.getQuantity(), intent.getQuantityUnit(), intent.getBudgetMin(), intent.getBudgetMax(),
                intent.getCurrency(), intent.getExpiresAt(), scored.score(), scored.reasons(),
                false, "NOT_COLLECTED", false);
    }

    private BuyingIntentResponse toOwnerResponse(BuyingIntent intent) {
        return new BuyingIntentResponse(
                intent.getId(), intent.getStatus().name(), intent.getCategory(), intent.getRegion(),
                intent.getNeedText(), intent.getQuantity(), intent.getQuantityUnit(), intent.getBudgetMin(),
                intent.getBudgetMax(), intent.getCurrency(), intent.getExpiresAt(), intent.getPublishedAt(),
                intent.getPublicationConsentAt(), intent.getClosedAt(), intent.getCreatedAt(), intent.getUpdatedAt(),
                false, "NOT_COLLECTED",
                intent.getStatus() == BuyingIntentStatus.PUBLISHED ? "SELLER_DISCOVERABLE" : "OWNER_ONLY",
                PUBLICATION_DISCLOSURE, false);
    }

    private BuyingIntent requireOwnedForUpdate(String ownerSub, UUID id) {
        return repository.findOwnedForUpdate(id, ownerSub)
                .orElseThrow(() -> new AiNotFoundException("Buying intent not found"));
    }

    private boolean expireIfDue(BuyingIntent intent) {
        if ((intent.getStatus() == BuyingIntentStatus.DRAFT || intent.getStatus() == BuyingIntentStatus.PUBLISHED)
                && !intent.getExpiresAt().isAfter(clock.instant())) {
            intent.setStatus(BuyingIntentStatus.EXPIRED);
            intent.setUpdatedAt(clock.instant());
            return true;
        }
        return false;
    }

    private void validate(BuyingIntentRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Buying intent body is required");
        }
        requireText(request.category(), "category", 160);
        requireText(request.needText(), "needText", 2000);
        requireOptionalLength(request.region(), "region", 160);
        requireOptionalLength(request.quantityUnit(), "quantityUnit", 32);
        privacyGuard.requireNoContactDetails(request.category(), "category");
        privacyGuard.requireNoContactDetails(request.region(), "region");
        privacyGuard.requireNoContactDetails(request.needText(), "needText");
        privacyGuard.requireNoContactDetails(request.quantityUnit(), "quantityUnit");
        if (request.quantity() != null && request.quantity().signum() <= 0) {
            throw new IllegalArgumentException("quantity must be greater than zero");
        }
        requirePrecisionAndScale(request.quantity(), "quantity", 16, 3);
        if (request.budgetMin() != null && request.budgetMin().signum() < 0) {
            throw new IllegalArgumentException("budgetMin cannot be negative");
        }
        requirePrecisionAndScale(request.budgetMin(), "budgetMin", 17, 2);
        if (request.budgetMax() != null && request.budgetMax().signum() < 0) {
            throw new IllegalArgumentException("budgetMax cannot be negative");
        }
        requirePrecisionAndScale(request.budgetMax(), "budgetMax", 17, 2);
        if (request.budgetMin() != null && request.budgetMax() != null
                && request.budgetMax().compareTo(request.budgetMin()) < 0) {
            throw new IllegalArgumentException("budgetMax must be greater than or equal to budgetMin");
        }
        if (request.currency() != null && !request.currency().isBlank()
                && !request.currency().matches("[A-Za-z]{3}")) {
            throw new IllegalArgumentException("currency must be a three-letter code");
        }
        Instant now = clock.instant();
        if (request.expiresAt() == null || request.expiresAt().isBefore(now.plus(MIN_LIFETIME))) {
            throw new IllegalArgumentException("expiresAt must be at least 5 minutes in the future");
        }
        if (request.expiresAt().isAfter(now.plus(MAX_LIFETIME))) {
            throw new IllegalArgumentException("expiresAt cannot be more than 90 days in the future");
        }
    }

    private void requireText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        if (value.length() > maxLength) {
            throw new IllegalArgumentException(field + " cannot exceed " + maxLength + " characters");
        }
    }

    private void requirePrecisionAndScale(
            BigDecimal value, String field, int maxIntegerDigits, int maxFractionDigits) {
        if (value == null) return;
        int fractionDigits = Math.max(0, value.scale());
        int integerDigits = Math.max(0, value.precision() - value.scale());
        if (integerDigits > maxIntegerDigits || fractionDigits > maxFractionDigits) {
            throw new IllegalArgumentException(field + " exceeds the supported numeric precision ("
                    + maxIntegerDigits + " integer and " + maxFractionDigits + " fractional digits)");
        }
    }

    private void requireOptionalLength(String value, String field, int maxLength) {
        if (value != null && value.length() > maxLength) {
            throw new IllegalArgumentException(field + " cannot exceed " + maxLength + " characters");
        }
    }

    private void copyRequest(BuyingIntentRequest request, BuyingIntent intent) {
        intent.setCategory(request.category().trim());
        intent.setRegion(blankToNull(request.region()));
        intent.setNeedText(request.needText().trim());
        intent.setQuantity(request.quantity());
        intent.setQuantityUnit(blankToNull(request.quantityUnit()));
        intent.setBudgetMin(request.budgetMin());
        intent.setBudgetMax(request.budgetMax());
        intent.setCurrency(request.currency() == null || request.currency().isBlank()
                ? "UZS" : request.currency().trim().toUpperCase(Locale.ROOT));
        intent.setExpiresAt(request.expiresAt());
    }

    private BuyingIntentStateException state(BuyingIntent intent, String message) {
        return new BuyingIntentStateException(intent.getStatus().name(), message);
    }

    private BuyingIntentStatus parseStatus(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return BuyingIntentStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unsupported buying-intent status: " + value);
        }
    }

    private void requireOwner(String ownerSub) {
        if (ownerSub == null || ownerSub.isBlank()) {
            throw new IllegalArgumentException("Authenticated buyer subject is required");
        }
    }

    private Set<String> tokenize(String value) {
        if (value == null || value.isBlank()) {
            return Set.of();
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        Set<String> tokens = new LinkedHashSet<>();
        for (String token : normalized.split("[^\\p{L}\\p{N}]+")) {
            if (token.length() >= 2) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(value, max));
    }

    private record ScoredIntent(BuyingIntent intent, int score, int queryMatchCount, List<String> reasons) {
    }
}
