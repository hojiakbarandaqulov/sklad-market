package org.example.ai.matching.service;

import org.example.ai.gateway.GatewayClient;
import org.example.ai.gateway.GatewayNotFoundException;
import org.example.ai.gateway.dto.GatewayEnvelope;
import org.example.ai.gateway.dto.RemoteLeadDto;
import org.example.ai.gateway.dto.RemotePagedResponse;
import org.example.ai.matching.dto.BuyerOpportunity;
import org.example.ai.matching.dto.BuyerOpportunityResult;
import org.example.ai.tool.PlatformLanguage;
import org.example.ai.tool.ToolArgs;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.text.Normalizer;
import java.time.Clock;
import java.time.LocalDate;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class BuyerOpportunityService {

    private static final int PAGE_SIZE = 100;
    private static final int MAX_PAGES = 5;
    private static final Set<String> ALLOWED_STATUSES =
            Set.of("NEW", "VIEWED", "CONTACTED", "CLOSED", "CANCELED");
    private static final Set<String> DEFAULT_STATUSES = Set.of("NEW", "VIEWED", "CONTACTED");

    private final GatewayClient gatewayClient;
    private final Clock clock;

    @Autowired
    public BuyerOpportunityService(GatewayClient gatewayClient) {
        this(gatewayClient, Clock.systemUTC());
    }

    BuyerOpportunityService(GatewayClient gatewayClient, Clock clock) {
        this.gatewayClient = gatewayClient;
        this.clock = clock;
    }

    /**
     * Every candidate comes from lead-service's caller-scoped seller endpoint. This service is
     * deliberately stateless: raw lead data is ranked in memory and never stored or logged.
     */
    public BuyerOpportunityResult recommend(
            String bearerToken,
            String acceptLanguage,
            String query,
            List<String> requestedStatuses,
            Integer requestedLimit) {
        Set<String> statuses = normalizeStatuses(requestedStatuses);
        int limit = Math.max(1, Math.min(requestedLimit == null ? 10 : requestedLimit, 20));
        Instant asOf = clock.instant();
        LeadCandidates candidateSet = fetchCallerScopedSellerLeads(bearerToken, acceptLanguage);
        List<RemoteLeadDto> leads = candidateSet.leads();
        Map<Long, Integer> buyerFrequency = new HashMap<>();
        for (RemoteLeadDto lead : leads) {
            if (lead.buyerId() != null) {
                buyerFrequency.merge(lead.buyerId(), 1, Integer::sum);
            }
        }
        Set<String> queryTokens = tokenize(query);
        List<ScoredLead> ranked = leads.stream()
                .filter(lead -> lead.status() != null && statuses.contains(lead.status().toUpperCase(Locale.ROOT)))
                .map(lead -> score(lead, queryTokens, buyerFrequency.getOrDefault(lead.buyerId(), 1)))
                .filter(scored -> queryTokens.isEmpty() || scored.queryMatches() > 0)
                .sorted(Comparator.comparingInt(ScoredLead::score).reversed()
                        .thenComparing(scored -> parseDate(scored.lead().neededDate()), Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(scored -> scored.lead().id(), Comparator.nullsLast(Comparator.naturalOrder())))
                .limit(limit)
                .toList();

        return new BuyerOpportunityResult(
                ranked.stream().map(this::project).toList(),
                leads.size(),
                candidateSet.totalLeadCount(),
                candidateSet.truncated(),
                asOf,
                "CALLER_SCOPED_SELLER_LEADS",
                "Buyer identity/contact fields, comments, and delivery addresses are excluded. Lead IDs and "
                        + "requested product snapshots remain visible so the seller can open an already-authorized lead.",
                false);
    }

    private LeadCandidates fetchCallerScopedSellerLeads(String bearerToken, String acceptLanguage) {
        List<RemoteLeadDto> all = new ArrayList<>();
        int page = 1;
        int totalPages = 1;
        long reportedTotal = 0;
        boolean metadataSeen = false;
        do {
            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            params.add("page", String.valueOf(page));
            params.add("perPage", String.valueOf(PAGE_SIZE));
            GatewayEnvelope<RemotePagedResponse<RemoteLeadDto>> envelope;
            try {
                envelope = gatewayClient.get(
                        "/api/v1/leads/seller",
                        params,
                        bearerToken,
                        PlatformLanguage.header(acceptLanguage),
                        new ParameterizedTypeReference<GatewayEnvelope<RemotePagedResponse<RemoteLeadDto>>>() {
                        });
            } catch (GatewayNotFoundException e) {
                return new LeadCandidates(List.of(), 0, false);
            }
            RemotePagedResponse<RemoteLeadDto> response = envelope == null ? null : envelope.data();
            if (response == null) {
                break;
            }
            if (response.items() != null) {
                all.addAll(response.items());
            }
            if (response.meta() != null) {
                metadataSeen = true;
                totalPages = Math.max(response.meta().totalPages(), 1);
                reportedTotal = Math.max(reportedTotal, Math.max(response.meta().total(), 0));
            } else {
                totalPages = 1;
            }
            page++;
        } while (page <= totalPages && page <= MAX_PAGES);
        long total = metadataSeen ? Math.max(reportedTotal, all.size()) : all.size();
        boolean truncated = totalPages > MAX_PAGES || total > all.size();
        return new LeadCandidates(List.copyOf(all), total, truncated);
    }

    private ScoredLead score(RemoteLeadDto lead, Set<String> queryTokens, int buyerLeadCount) {
        int score = switch (safeUpper(lead.status())) {
            case "NEW" -> 40;
            case "VIEWED" -> 30;
            case "CONTACTED" -> 20;
            default -> 0;
        };
        List<String> reasons = new ArrayList<>();
        reasons.add(switch (safeUpper(lead.status())) {
            case "NEW" -> "NEW_REQUEST";
            case "VIEWED" -> "VIEWED_REQUEST";
            case "CONTACTED" -> "CONTACTED_REQUEST";
            default -> "HISTORICAL_REQUEST";
        });

        Set<String> documentTokens = tokenize(searchableText(lead));
        int queryMatches = (int) queryTokens.stream().filter(documentTokens::contains).count();
        if (!queryTokens.isEmpty() && queryMatches > 0) {
            score += Math.round(30f * queryMatches / queryTokens.size());
            reasons.add("PRODUCT_OR_NEED_MATCH");
        }

        LocalDate neededDate = parseDate(lead.neededDate());
        if (neededDate != null) {
            long days = ChronoUnit.DAYS.between(LocalDate.now(clock), neededDate);
            if (days >= 0 && days <= 14) {
                score += 15;
                reasons.add("NEEDED_SOON");
            } else if (days > 14 && days <= 30) {
                score += 8;
                reasons.add("NEEDED_WITHIN_30_DAYS");
            }
        }

        boolean hasQuantity = lead.items() != null && lead.items().stream()
                .anyMatch(item -> item.quantity() != null && item.quantity() > 0);
        if (hasQuantity) {
            score += 5;
            reasons.add("QUANTITY_SPECIFIED");
        }
        if (buyerLeadCount > 1) {
            score += Math.min(10, (buyerLeadCount - 1) * 5);
            reasons.add("REPEAT_BUYER_INTEREST");
        }
        return new ScoredLead(lead, Math.min(score, 100), queryMatches, List.copyOf(reasons));
    }

    private BuyerOpportunity project(ScoredLead scored) {
        RemoteLeadDto lead = scored.lead();
        List<BuyerOpportunity.RequestedItem> items = lead.items() == null ? List.of() : lead.items().stream()
                .limit(10)
                .map(item -> new BuyerOpportunity.RequestedItem(
                        item.productId(), ToolArgs.truncate(item.productNameSnapshot(), 160), item.quantity()))
                .toList();
        return new BuyerOpportunity(
                lead.id(), safeUpper(lead.status()), dateText(lead.neededDate()), scored.score(),
                scored.reasons(), items, "VIEW_AUTHORIZED_LEAD", false);
    }

    private Set<String> normalizeStatuses(List<String> statuses) {
        if (statuses == null || statuses.isEmpty()) {
            return DEFAULT_STATUSES;
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String status : statuses) {
            if (status == null || status.isBlank()) {
                continue;
            }
            String upper = status.trim().toUpperCase(Locale.ROOT);
            if (!ALLOWED_STATUSES.contains(upper)) {
                throw new IllegalArgumentException("Unsupported lead status: " + status);
            }
            normalized.add(upper);
        }
        return normalized.isEmpty() ? DEFAULT_STATUSES : Set.copyOf(normalized);
    }

    private String searchableText(RemoteLeadDto lead) {
        StringBuilder builder = new StringBuilder();
        if (lead.comment() != null) {
            builder.append(lead.comment()).append(' ');
        }
        if (lead.items() != null) {
            for (RemoteLeadDto.RemoteLeadItemDto item : lead.items()) {
                if (item.productNameSnapshot() != null) {
                    builder.append(item.productNameSnapshot()).append(' ');
                }
            }
        }
        return builder.toString();
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

    private LocalDate parseDate(Object value) {
        if (value instanceof LocalDate date) {
            return date;
        }
        if (value instanceof String text) {
            try {
                return LocalDate.parse(text);
            } catch (RuntimeException ignored) {
                return null;
            }
        }
        if (value instanceof java.util.Date date) {
            return date.toInstant().atZone(ZoneOffset.UTC).toLocalDate();
        }
        return null;
    }

    private String dateText(Object value) {
        LocalDate parsed = parseDate(value);
        return parsed == null ? null : parsed.toString();
    }

    private String safeUpper(String value) {
        return value == null ? "UNKNOWN" : value.toUpperCase(Locale.ROOT);
    }

    private record ScoredLead(RemoteLeadDto lead, int score, int queryMatches, List<String> reasons) {
    }

    private record LeadCandidates(List<RemoteLeadDto> leads, long totalLeadCount, boolean truncated) {
    }
}
