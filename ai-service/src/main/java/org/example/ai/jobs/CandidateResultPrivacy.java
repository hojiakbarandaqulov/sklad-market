package org.example.ai.jobs;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import java.util.*;

/** Candidate professional data is live-only: persisted and model-visible payloads contain references. */
@Component
public class CandidateResultPrivacy {
    private final ObjectMapper mapper;
    public CandidateResultPrivacy(ObjectMapper mapper) { this.mapper = mapper; }

    public static Map<String, Object> references(Map<String, Object> result) {
        if (result == null || !"candidate_search".equals(result.get("kind"))) return result;
        var safe = new LinkedHashMap<String, Object>(); safe.put("kind", "candidate_search");
        var ids = new LinkedHashSet<Long>();
        if (result.get("items") instanceof List<?> items) for (Object item : items) {
            if (item instanceof Map<?, ?> value && value.get("id") instanceof Number id && id.longValue() > 0) ids.add(id.longValue());
        }
        long vacancyId = result.get("vacancyId") instanceof Number id ? id.longValue() : 0;
        safe.put("vacancyId", vacancyId); safe.put("items", ids.stream().limit(5).map(id -> Map.of("id", id)).toList());
        safe.put("hasMore", Boolean.TRUE.equals(result.get("hasMore")));
        String query = result.get("query") instanceof String q && q.length() <= 120
                && q.matches("[\\p{L}\\p{N} .+#'’ʻ_\\-]+") && !q.matches(".*(?:\\d[ -]*){7,}.*") ? q.trim() : null;
        if (query != null && !query.isBlank()) safe.put("query", query);
        safe.put("browseUrl", "/jobs/candidates?vacancyId=" + vacancyId + (query == null || query.isBlank() ? ""
                : "&q=" + org.springframework.web.util.UriUtils.encodeQueryParam(query, java.nio.charset.StandardCharsets.UTF_8)));
        if (Boolean.TRUE.equals(result.get("alreadyShown"))) {
            safe.put("alreadyShown", true);
            safe.put("shownCount", result.get("shownCount") instanceof Number n ? Math.max(0, Math.min(5, n.intValue())) : 0);
        }
        safe.put("requiresHydration", true);
        return safe;
    }

    public String restore(String payload) {
        if (payload == null || !payload.contains("candidate_search")) return payload;
        try {
            var envelope = mapper.readValue(payload, new TypeReference<Map<String, Object>>() {});
            if (!(envelope.get("resultSet") instanceof Map<?, ?> result) || !"candidate_search".equals(result.get("kind"))) return payload;
            @SuppressWarnings("unchecked") var original = (Map<String, Object>) result;
            var safe = new LinkedHashMap<String, Object>(references(original));
            // History is already conversation-owner scoped. No candidate profession/location is
            // restored here: clients obtain it only through fresh Jobs-authorized scoped GETs.
            // This avoids one downstream call per old tool message merely to open a chat.
            envelope.put("resultSet", safe);
            return mapper.writeValueAsString(envelope);
        } catch (Exception malformed) { return null; } // Never restore an unchecked candidate snapshot.
    }
}
