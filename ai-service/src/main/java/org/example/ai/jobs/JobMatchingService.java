package org.example.ai.jobs;

import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.*;

/** Shared REST/chat ranking. No LLM, embeddings, conversation writes, persistent cache or resume reads. */
@Service
public class JobMatchingService {
    private final PublicJobsClient client;
    private final JobPreferencesStore profiles;
    public JobMatchingService(PublicJobsClient client, JobPreferencesStore profiles) { this.client = client; this.profiles = profiles; }

    public Map<String, Object> search(JobCriteria requested, String language) {
        JobCriteria safe = requested.validated(true);
        return match("job_search", safe, safe.query(), null, language);
    }
    public Map<String, Object> recommend(String owner, JobCriteria requested, String language) {
        if (owner == null || owner.isBlank()) throw new org.springframework.security.access.AccessDeniedException("Sign in for saved recommendations");
        JobCriteria safe = requested.validated(false);
        var profile = profiles.find(owner).orElse(null);
        if (profile == null) return profileRequired();
        String query = safe.query() == null ? profile.desiredPosition() : safe.query();
        // Selecting a saved category is deliberate; explicit category always overrides it.
        JobCriteria hard = new JobCriteria(safe.query(), safe.regionId() == null ? profile.regionId() : safe.regionId(),
                safe.categoryId() == null ? profile.categoryId() : safe.categoryId(),
                safe.salaryMin() == null ? profile.salaryMin() : safe.salaryMin(), safe.experienceLevel(),
                safe.employmentType() == null ? profile.employmentType() : safe.employmentType(),
                safe.workSchedule() == null ? profile.workSchedule() : safe.workSchedule(), safe.limit());
        return match("job_recommendations", hard, query, profile, language);
    }

    public static Map<String, Object> profileRequired() {
        return Map.of("kind", "job_profile_required", "items", List.of(), "needsProfile", true,
                "hasMore", false, "setupUrl", "/jobs?aiSetup=1", "browseUrl", "/jobs");
    }

    private Map<String, Object> match(String kind, JobCriteria hard, String query, JobPreferences profile, String language) {
        List<String> terms = JobText.terms(query);
        if (terms.isEmpty() && hard.categoryId() == null) return result(kind, query, List.of(), 0, false, hard);
        List<PublicJobsClient.Candidate> candidates = client.candidates(hard, terms, null, language);
        Map<Long, Ranked> unique = new LinkedHashMap<>();
        for (var candidate : candidates) {
            Ranked ranked = rank(candidate, hard, query, profile);
            if (ranked != null) unique.putIfAbsent(candidate.id(), ranked);
        }
        List<Ranked> ranked = unique.values().stream().sorted(Comparator.comparingInt(Ranked::score).reversed()
                .thenComparing(r -> r.job().publishedAt(), Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(r -> r.job().id(), Comparator.reverseOrder())).toList();
        List<Long> selected = ranked.stream().limit(hard.limit()).map(r -> r.job().id()).toList();
        if (selected.isEmpty()) return result(kind, query, List.of(), candidates.size(), false, hard);
        // One bounded final DB re-read excludes jobs closed/deleted since candidate selection.
        Map<Long, PublicJobsClient.Candidate> live = new HashMap<>();
        client.candidates(hard, List.of(), selected, language).forEach(job -> live.put(job.id(), job));
        List<Map<String, Object>> cards = new ArrayList<>();
        for (Ranked selection : ranked.stream().limit(hard.limit()).toList()) {
            var job = live.get(selection.job().id());
            Ranked current = job == null ? null : rank(job, hard, query, profile);
            if (current != null) cards.add(card(current));
        }
        return result(kind, query, cards, candidates.size(), ranked.size() > selected.size(), hard);
    }

    private record Ranked(PublicJobsClient.Candidate job, int score, List<String> reasons, List<String> matchedSkills) {}
    private Ranked rank(PublicJobsClient.Candidate job, JobCriteria hard, String query, JobPreferences profile) {
        if (job == null || job.id() == null || job.id() <= 0 || job.positionName() == null || job.positionName().isBlank()) return null;
        if (job.experienceLevel() != null && !JobPreferences.EXPERIENCE_LEVELS.contains(job.experienceLevel())) return null;
        // Defense in depth: do not trust even a successful public response to widen explicit filters.
        if (hard.regionId() != null && !hard.regionId().equals(job.regionId())
                || hard.categoryId() != null && !hard.categoryId().equals(job.categoryId())
                || hard.salaryMin() != null && (job.price() == null || job.price().compareTo(hard.salaryMin()) < 0)
                || hard.experienceLevel() != null && !hard.experienceLevel().equals(job.experienceLevel())
                || !optionalEquals(hard.employmentType(), job.employmentType()) || !optionalEquals(hard.workSchedule(), job.workSchedule())) return null;
        boolean role = JobText.roleMatches(query, job.positionName(), job.shortDescription() + " " + job.requirements());
        // A real selected category can be the target only when no meaningful title was provided.
        if (!role && !(JobText.words(query).isEmpty() && hard.categoryId() != null)) return null;
        List<String> reasons = new ArrayList<>();
        if (role) reasons.add("ROLE_MATCH");
        int score = role ? 100 : 50;
        List<String> skills = profile == null ? List.of() : profile.skills().stream()
                .filter(skill -> JobText.skillMatches(skill, job.positionName() + " " + job.shortDescription() + " " + job.requirements())).limit(5).toList();
        if (!skills.isEmpty()) { reasons.add("SKILL_MATCH"); score += skills.size() * 8; }
        Long wantedRegion = hard.regionId() != null ? hard.regionId() : profile == null ? null : profile.regionId();
        if (wantedRegion != null && wantedRegion.equals(job.regionId())) { reasons.add("REGION_MATCH"); score += 25; }
        String experience = hard.experienceLevel() != null ? hard.experienceLevel() : profile == null ? null : profile.experienceLevel();
        if (experience != null && experience.equals(job.experienceLevel())) { reasons.add("EXPERIENCE_MATCH"); score += 15; }
        // Do not recommend jobs requiring more experience than the profile says unless the user explicitly requests that level.
        if (profile != null && hard.experienceLevel() == null && job.experienceLevel() != null
                && JobPreferences.EXPERIENCE_LEVELS.indexOf(job.experienceLevel()) > JobPreferences.EXPERIENCE_LEVELS.indexOf(profile.experienceLevel())) return null;
        var salary = hard.salaryMin() != null ? hard.salaryMin() : profile == null ? null : profile.salaryMin();
        if (salary != null && job.price() != null && job.price().compareTo(salary) >= 0) { reasons.add("SALARY_MATCH"); score += 10; }
        if (profile != null && profile.employmentType() != null && optionalEquals(profile.employmentType(), job.employmentType())) score += 5;
        if (profile != null && profile.workSchedule() != null && optionalEquals(profile.workSchedule(), job.workSchedule())) score += 5;
        return new Ranked(job, score, List.copyOf(reasons), skills);
    }

    private static boolean optionalEquals(String required, String actual) { return required == null || actual != null && required.trim().equalsIgnoreCase(actual.trim()); }
    private static Map<String, Object> card(Ranked ranked) {
        var job = ranked.job(); var item = new LinkedHashMap<String, Object>();
        item.put("id", job.id()); item.put("positionName", job.positionName()); item.put("url", "/jobs/" + job.id());
        put(item, "companyName", job.companyName()); put(item, "companyLogo", job.companyLogo()); put(item, "price", job.price());
        put(item, "regionId", job.regionId()); put(item, "address", job.address()); put(item, "employmentType", job.employmentType());
        put(item, "workSchedule", job.workSchedule()); put(item, "experienceLevel", job.experienceLevel()); put(item, "categoryId", job.categoryId());
        put(item, "publishedAt", job.publishedAt()); item.put("reasons", ranked.reasons()); item.put("matchedSkills", ranked.matchedSkills());
        return item;
    }
    private static void put(Map<String, Object> map, String key, Object value) { if (value != null) map.put(key, value); }
    private static Map<String, Object> result(String kind, String query, List<Map<String, Object>> items, int candidates, boolean hasMore, JobCriteria hard) {
        var result = new LinkedHashMap<String, Object>(); result.put("kind", kind); result.put("items", items);
        put(result, "query", query); result.put("totalCandidates", candidates); result.put("hasMore", hasMore);
        result.put("setupUrl", "/jobs?aiSetup=1");
        var url = UriComponentsBuilder.fromPath("/jobs");
        if (query != null && !query.isBlank()) url.queryParam("q", query);
        if (hard.regionId() != null) url.queryParam("regionId", hard.regionId());
        if (hard.categoryId() != null) url.queryParam("categoryId", hard.categoryId());
        if (hard.salaryMin() != null) url.queryParam("salaryMin", hard.salaryMin());
        if (hard.experienceLevel() != null) url.queryParam("experienceLevel", hard.experienceLevel());
        if (hard.employmentType() != null) url.queryParam("employmentType", hard.employmentType());
        if (hard.workSchedule() != null) url.queryParam("workSchedule", hard.workSchedule());
        result.put("browseUrl", url.build().encode().toUriString()); return result;
    }
}
