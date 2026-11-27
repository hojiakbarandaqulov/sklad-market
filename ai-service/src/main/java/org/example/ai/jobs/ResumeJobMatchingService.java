package org.example.ai.jobs;

import org.example.ai.LocaleNormalizer;
import org.springframework.stereotype.Service;
import java.util.*;

/** Reads an explicitly selected OWN resume projection. Does not save or publish anything. */
@Service
public class ResumeJobMatchingService {
    private final AuthenticatedJobsClient source;
    private final JobMatchingService matching;
    public ResumeJobMatchingService(AuthenticatedJobsClient source, JobMatchingService matching) {
        this.source = source; this.matching = matching;
    }
    public Map<String, Object> recommend(long resumeId, String confirmedExperience, JobCriteria criteria,
            String token, String language) {
        var profile = source.resumeProfile(resumeId, token, language);
        String experience = confirmedExperience == null ? profile.experienceLevel() : confirmedExperience;
        if (profile.desiredPosition() == null || profile.desiredPosition().isBlank() || profile.skills() == null
                || profile.skills().isEmpty() || profile.regionId() == null || profile.regionId() <= 0 || experience == null) {
            var result = new LinkedHashMap<String, Object>(JobMatchingService.profileRequired());
            result.put("message", switch (LocaleNormalizer.normalize(language)) {
                case "uz" -> "Rezyumedagi kasb, ko‘nikmalar, hudud va tajriba darajasini tasdiqlang.";
                case "en" -> "Confirm the occupation, skills, region and experience level from your resume.";
                default -> "Подтвердите профессию, навыки, регион и уровень опыта из своего резюме.";
            });
            return result;
        }
        var safe = new JobPreferences(profile.desiredPosition(), profile.skills(), profile.regionId(), experience,
                null, profile.salaryMin(), null, null).validated();
        return matching.recommendProfile(safe, criteria, language);
    }
}
