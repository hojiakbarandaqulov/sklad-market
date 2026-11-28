package com.example.service;

import com.example.dto.resume.AiJobsRequests;
import com.example.entity.ResumeDiscovery;
import com.example.entity.Vacancy;
import com.example.enums.ExperienceLevel;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public interface AiCandidateDiscoveryService {
    Discovery discovery(Long resumeId);

    Discovery saveDiscovery(Long resumeId, AiJobsRequests.Discovery request);

    MatchingProfile matchingProfile(Long resumeId);

    Map<String, Object> ownedVacancies();

    CandidatePage candidates(Long vacancyId, String q, int page, int perPage);

    CandidateDetail candidate(Long vacancyId, Long resumeId);

    ResumeDiscovery requireEligibleLocked(Vacancy vacancy, Long resumeId, long inviterId);

    record Discovery(boolean enabled, String specialty, ExperienceLevel experienceLevel,
            Long regionId, List<String> skills) {}

    record MatchingProfile(Long resumeId, String desiredPosition, List<String> skills,
            Long regionId, ExperienceLevel experienceLevel, BigDecimal salaryMin) {}

    record Candidate(Long id, String specialty, ExperienceLevel experienceLevel, Long regionId,
            List<String> skills, List<String> reasons) {}

    record CandidateDetail(Long id, String specialty, ExperienceLevel experienceLevel, Long regionId,
            List<String> skills,List<String> reasons,String workExperience,String education,String languages) {}

    record CandidatePage(List<Candidate> items, int page, int perPage, boolean hasMore, Long vacancyId) {}

    record OwnedVacancy(Long id, String positionName, Long regionId, ExperienceLevel experienceLevel) {}
}
