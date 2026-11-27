package org.example.ai.jobs;

import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class CandidateMatchingService {
    private final AuthenticatedJobsClient source;
    public CandidateMatchingService(AuthenticatedJobsClient source) { this.source = source; }
    public Map<String, Object> search(long vacancyId, String query, String token, String language) {
        var page = source.candidates(vacancyId, query, 1, 5, token, language);
        var seen = new HashSet<Long>(); var items = new ArrayList<Map<String, Object>>();
        for (var candidate : page.items()) {
            if (candidate.id() != null && candidate.id() > 0 && seen.add(candidate.id())) items.add(card(candidate, vacancyId));
        }
        var data = new LinkedHashMap<String, Object>(); data.put("kind", "candidate_search");
        data.put("items", items); data.put("vacancyId", vacancyId); data.put("hasMore", page.hasMore());
        if (query != null && !query.isBlank()) data.put("query", query);
        data.put("browseUrl", "/jobs/candidates?vacancyId=" + vacancyId
                + (query == null || query.isBlank() ? "" : "&q=" + org.springframework.web.util.UriUtils.encodeQueryParam(query, java.nio.charset.StandardCharsets.UTF_8)));
        return data;
    }
    public static Map<String, Object> card(AuthenticatedJobsClient.Candidate candidate, long vacancyId) {
        var item = new LinkedHashMap<String, Object>(); item.put("id", candidate.id());
        item.put("specialty", JobPreferences.text(candidate.specialty(), 120, "specialty"));
        if (candidate.experienceLevel() != null && JobPreferences.EXPERIENCE_LEVELS.contains(candidate.experienceLevel()))
            item.put("experienceLevel", candidate.experienceLevel());
        if (candidate.regionId() != null && candidate.regionId() > 0) item.put("regionId", candidate.regionId());
        item.put("skills", candidate.skills() == null ? List.of() : candidate.skills().stream().filter(Objects::nonNull)
                .filter(s -> !s.isBlank() && s.length() <= 60).limit(5).toList());
        item.put("reasons", candidate.reasons() == null ? List.of() : candidate.reasons().stream()
                .filter(Set.of("ROLE_MATCH", "SKILL_MATCH", "REGION_MATCH", "EXPERIENCE_MATCH")::contains).limit(4).toList());
        item.put("url", "/jobs/candidates/" + candidate.id() + "?vacancyId=" + vacancyId);
        return item;
    }
}
