package org.example.ai.jobs;

import org.example.ai.gateway.GatewayUnavailableException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class JobMatchingServiceTest {
    PublicJobsClient client = mock(PublicJobsClient.class);
    JobPreferencesStore profiles = mock(JobPreferencesStore.class);
    JobMatchingService service = new JobMatchingService(client, profiles);

    private PublicJobsClient.Candidate job(long id, String title, long region, String price, String experience) {
        return new PublicJobsClient.Candidate(id, title, "Actual employer", null, new BigDecimal(price), region,
                "Tashkent", "FULL_TIME", "FIVE_TWO", experience, 7L, Instant.parse("2026-10-13T00:00:00Z"),
                "Team role", "Java SQL");
    }
    private JobPreferences profile() { return new JobPreferences("Dasturchi", List.of("Java", "SQL"), 1L,
            "ONE_TO_THREE_YEARS", 7L, new BigDecimal("100"), "FULL_TIME", "FIVE_TWO"); }
    private void available(List<PublicJobsClient.Candidate> first, List<PublicJobsClient.Candidate> finalJobs) {
        when(client.candidates(any(), anyList(), isNull(), any())).thenReturn(first);
        when(client.candidates(any(), anyList(), anyList(), any())).thenReturn(finalJobs);
    }

    @Test void missingProfileIsStructuredAndDoesNotCallJobsOrModel() {
        when(profiles.find("owner")).thenReturn(Optional.empty());
        assertThat(service.recommend("owner", JobCriteria.defaults(5), "en")).containsEntry("kind", "job_profile_required")
                .containsEntry("needsProfile", true).containsEntry("items", List.of());
        verifyNoInteractions(client);
    }
    @Test void multilingualOccupationIsRelevantAndSkillsHaveEvidence() {
        var wanted = job(1, "Разработчик Java", 1, "200", "ONE_TO_THREE_YEARS");
        var unrelated = job(2, "Accountant", 1, "200", "ONE_TO_THREE_YEARS");
        when(profiles.find("owner")).thenReturn(Optional.of(profile()));
        available(List.of(wanted, unrelated, wanted), List.of(wanted));
        var result = service.recommend("owner", JobCriteria.defaults(5), "uz");
        assertThat((List<?>) result.get("items")).hasSize(1);
        var item = (Map<?, ?>) ((List<?>) result.get("items")).get(0);
        assertThat(item.get("url")).isEqualTo("/jobs/1");
        assertThat((List<String>) item.get("reasons")).contains("ROLE_MATCH", "SKILL_MATCH", "REGION_MATCH", "SALARY_MATCH");
        assertThat(item.get("matchedSkills")).isEqualTo(List.of("Java", "SQL"));
        for (String secret : List.of("requirements", "contacts", "score", "matchPercentage")) assertThat(item.containsKey(secret)).isFalse();
    }
    @Test void savedMinimumSalaryRegionAndScheduleAreHardDefaultsAndExplicitRegionOverrides() {
        when(profiles.find("owner")).thenReturn(Optional.of(profile()));
        var belowSalary = job(1, "Developer", 1, "99", "ONE_TO_THREE_YEARS");
        var wrongRegion = job(2, "Developer", 2, "200", "ONE_TO_THREE_YEARS");
        available(List.of(belowSalary, wrongRegion), List.of());
        assertThat((List<?>) service.recommend("owner", JobCriteria.defaults(3), "en").get("items")).isEmpty();
        verify(client).candidates(argThat(c -> c.regionId().equals(1L) && c.salaryMin().compareTo(new BigDecimal("100")) == 0
                && c.workSchedule().equals("FIVE_TWO")), anyList(), isNull(), eq("en"));
        clearInvocations(client);
        service.recommend("owner", new JobCriteria(null, 2L, null, null, null, null, null, 3), "en");
        verify(client).candidates(argThat(c -> c.regionId().equals(2L)), anyList(), isNull(), eq("en"));
    }
    @Test void liveShortlistRecheckExcludesClosedJobsAndMoreExperiencedRequirements() {
        when(profiles.find("owner")).thenReturn(Optional.of(profile()));
        var closedBetweenReads = job(1, "Developer", 1, "200", "ONE_TO_THREE_YEARS");
        var requiresTooMuch = job(2, "Developer", 1, "200", "OVER_SIX_YEARS");
        available(List.of(closedBetweenReads, requiresTooMuch), List.of());
        assertThat((List<?>) service.recommend("owner", JobCriteria.defaults(3), "en").get("items")).isEmpty();
        verify(client).candidates(any(), eq(List.of()), eq(List.of(1L)), eq("en"));
    }
    @Test void publicSearchDoesNotReadPrivatePreferencesAndServiceFailureIsNotEmptyResults() {
        available(List.of(job(1, "Haydovchi", 1, "100", "NO_EXPERIENCE")), List.of(job(1, "Haydovchi", 1, "100", "NO_EXPERIENCE")));
        var result = service.search(new JobCriteria("driver", null, null, null, null, null, null, 3), "en");
        assertThat((List<?>) result.get("items")).hasSize(1); verifyNoInteractions(profiles);
        when(client.candidates(any(), anyList(), isNull(), any())).thenThrow(new GatewayUnavailableException("offline", null));
        assertThatThrownBy(() -> service.search(new JobCriteria("driver", null, null, null, null, null, null, 3), "en"))
                .isInstanceOf(GatewayUnavailableException.class);
    }
    @Test void titleMatchingIsMultilingualButDoesNotPretendJavaIsJavaScript() {
        assertThat(JobText.roleMatches("driver", "Водитель грузовика")).isTrue();
        assertThat(JobText.roleMatches("Buxgalter", "Accountant")).isTrue();
        assertThat(JobText.roleMatches("Teacher", "O'qituvchi")).isTrue();
        assertThat(JobText.roleMatches("Java", "JavaScript")).isFalse();
        assertThat(JobText.roleMatches("Java developer", "Developer", "Java and SQL required")).isTrue();
        assertThat(JobText.roleMatches("Java developer", "Accountant", "Java and SQL preferred")).isFalse();
        assertThat(JobText.roleMatches("forklift driver", "Truck driver", "Truck driving license")).isFalse();
        assertThat(JobText.roleMatches("warehouse forklift driver", "Warehouse driver", "Truck driving license")).isFalse();
        assertThat(JobText.roleMatches("forklift driver", "Driver", "Forklift operation required")).isTrue();
        assertThat(JobText.roleMatches("React developer", "ReactNative developer", "ReactNative required")).isFalse();
        assertThat(JobText.roleMatches("jobs", "Accountant")).isFalse();
        assertThat(JobText.skillMatches("Java", "JavaScript required")).isFalse();
    }
    @Test void preferenceAndQueryBoundsAreValidated() {
        assertThatThrownBy(() -> new JobPreferences("Driver", List.of(""), 1L, "NO_EXPERIENCE", null, null, null, null).validated())
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new JobCriteria("a".repeat(121), null, null, null, null, null, null, 3).validated(true))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> JobCriteria.defaults(100).validated(false)).isInstanceOf(IllegalArgumentException.class);
        var safe = new JobPreferences(" Driver ", List.of(" Excel ", "excel"), 1L, "NO_EXPERIENCE", null, null, null, null).validated();
        assertThat(safe.skills()).containsExactly("Excel");
    }
}
