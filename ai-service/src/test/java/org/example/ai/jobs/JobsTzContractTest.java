package org.example.ai.jobs;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.ai.audit.ToolAuditSanitizer;
import org.example.ai.error.AiChatException;
import org.example.ai.guardrail.*;
import org.example.ai.provider.*;
import org.example.ai.tool.ToolResult;
import org.example.exception.AiNotFoundException;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class JobsTzContractTest {
    ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    AuthenticatedJobsClient source = mock(AuthenticatedJobsClient.class);
    JobMatchingService jobs = mock(JobMatchingService.class);
    ResumeJobMatchingService resumes = mock(ResumeJobMatchingService.class);
    CandidateMatchingService candidates = mock(CandidateMatchingService.class);
    PublicJobsClient publicJobs = mock(PublicJobsClient.class);
    ChatModelProvider provider = mock(ChatModelProvider.class);
    AiChatRateLimitService rpm = mock(AiChatRateLimitService.class);
    AiRoleQuotaService quotas = mock(AiRoleQuotaService.class);
    TokenBudgetGuard budget = mock(TokenBudgetGuard.class);
    UsageLedgerService usage = mock(UsageLedgerService.class);
    JobsPanelService panel = new JobsPanelService(jobs,resumes,candidates,source,publicJobs,provider,rpm,quotas,budget,usage,mapper,"gemini-2.5-flash");

    @Test void fiveResultBudgetDeduplicatesAcrossToolsAndLeavesMarketplaceUntouched() {
        var cap = new JobsTurnResultBudget();
        var first = ToolResult.ok(Map.of("kind","job_search","items",List.of(Map.of("id",1),Map.of("id",2),Map.of("id",3))));
        assertThat((List<?>)cap.apply(first).data().get("items")).hasSize(3);
        var next = cap.apply(ToolResult.ok(Map.of("kind","job_recommendations","items",List.of(Map.of("id",3),Map.of("id",4),Map.of("id",5),Map.of("id",6)))));
        assertThat(next.data().get("items")).isEqualTo(List.of(Map.of("id",4),Map.of("id",5)));
        assertThat(cap.apply(first).data()).containsEntry("alreadyShown",true);
        var market = ToolResult.ok(Map.of("kind","business_search","items",List.of(Map.of("id",99))));
        assertThat(cap.apply(market)).isSameAs(market);
        assertThatThrownBy(() -> JobCriteria.defaults(6).validated(false)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void candidatePersistenceAndHistoryNeverRestoreProfessionalSnapshots() throws Exception {
        var result = Map.<String,Object>of("kind","candidate_search","vacancyId",7L,"items",List.of(Map.of(
                "id",11L,"specialty","Private Java role","skills",List.of("SQL"),"regionId",9L,"phone","secret")),"hasMore",true);
        var safe = ToolAuditSanitizer.sanitizeResultSetForPersistence(mapper,result);
        assertThat(mapper.writeValueAsString(safe)).doesNotContain("Private Java", "SQL", "regionId", "secret");
        assertThat(safe).containsEntry("requiresHydration",true);
        String legacy = mapper.writeValueAsString(Map.of("status","ok","resultSet",result));
        String restored = new CandidateResultPrivacy(mapper).restore(legacy);
        assertThat(restored).contains("requiresHydration").doesNotContain("Private Java", "SQL", "secret");
    }

    @Test void emptySelectedVacancyRequestDoesNotCallProviderOrConsumeLlmQuota() {
        when(source.ownedVacancies("jwt","en")).thenReturn(List.of(new AuthenticatedJobsClient.OwnedVacancy(7L,"Java developer")));
        when(candidates.search(7L,null,"jwt","en")).thenReturn(Map.of("kind","candidate_search","items",List.of()));
        panel.search(new JobsPanelService.Request("CANDIDATES","",null,7L,null),"owner",Set.of("SELLER"),"jwt","en");
        verify(candidates).search(7L,null,"jwt","en");
        verifyNoInteractions(provider,rpm,budget,quotas,usage);
    }

    @Test void candidateReferenceMetadataPreservesSafeFiltersAndAlreadyShownMeaning() {
        var result = Map.<String,Object>of("kind","candidate_search","vacancyId",7L,"items",List.of(),
                "query","Java SQL","alreadyShown",true,"shownCount",5);
        assertThat(CandidateResultPrivacy.references(result)).containsEntry("query","Java SQL")
                .containsEntry("alreadyShown",true).containsEntry("shownCount",5)
                .containsEntry("browseUrl","/jobs/candidates?vacancyId=7&q=Java%20SQL");
    }

    @Test void foreignVacancyCannotTriggerProviderOrMatching() {
        when(source.ownedVacancies("jwt","en")).thenReturn(List.of());
        assertThatThrownBy(() -> panel.search(new JobsPanelService.Request("CANDIDATES","Java",null,9L,null),"owner",Set.of("SELLER"),"jwt","en"))
                .isInstanceOf(AiNotFoundException.class);
        verifyNoInteractions(provider,candidates,quotas);
    }

    @Test void missingModeAndMissingExperienceAreValidationErrorsNotNullPointerFailures() {
        assertThatThrownBy(() -> panel.search(new JobsPanelService.Request(null,"driver",null,null,null),"owner",Set.of(),"jwt","en"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new JobPreferences("Driver",List.of("Driving"),1L,null,null,null,null,null).validated())
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(provider,source,quotas);
    }

    @Test void providerInterpretationIsSingleChargedAndHasNoConversationOrResumeBody() {
        when(rpm.tryConsume("owner")).thenReturn(true); when(budget.hasRemainingBudget("owner")).thenReturn(true);
        when(provider.generateStructured(any())).thenReturn(new StructuredCompletionResult(filters("Java developer","",""),new TokenUsage(12,8,20)));
        when(jobs.search(any(),eq("en"))).thenReturn(Map.of("kind","job_search","items",List.of()));
        panel.search(new JobsPanelService.Request("JOBS","Find Java developer jobs",null,null,null),"owner",Set.of("BUYER"),"jwt","en");
        verify(quotas).consume("owner",Set.of("BUYER")); verify(provider,times(1)).generateStructured(argThat(r -> r.maxOutputTokens()==400 && r.images().isEmpty() && Objects.equals(r.reasoningBudgetTokens(),0)));
        verify(usage).recordUsage("owner",12,8); verifyNoInteractions(source,resumes);
    }

    @Test void unsupportedAndAmbiguousConstraintsNeverSilentlyBroadenSearch() {
        when(rpm.tryConsume("owner")).thenReturn(true); when(budget.hasRemainingBudget("owner")).thenReturn(true);
        when(provider.generateStructured(any())).thenReturn(new StructuredCompletionResult(filters("driver","","maximum salary"),new TokenUsage(1,1,2)));
        assertThat(panel.search(new JobsPanelService.Request("JOBS","Driver under 5m salary",null,null,null),"owner",Set.of(),"jwt","en"))
                .containsEntry("kind","job_clarification");
        verifyNoInteractions(jobs,candidates,resumes);
    }

    @Test void exhaustedQuotaPreventsProviderCall() {
        when(rpm.tryConsume("owner")).thenReturn(true); when(budget.hasRemainingBudget("owner")).thenReturn(true);
        doThrow(new AiChatException(org.example.ai.error.AiErrorCode.RATE_LIMITED,"limit")).when(quotas).consume("owner",Set.of());
        assertThatThrownBy(() -> panel.search(new JobsPanelService.Request("JOBS","driver",null,null,null),"owner",Set.of(),"jwt","en"))
                .isInstanceOf(AiChatException.class);
        verifyNoInteractions(provider,jobs);
    }

    @Test void incompleteOwnResumeRequiresConfirmationWithoutPublishingOrSaving() {
        var real = new ResumeJobMatchingService(source,jobs);
        when(source.resumeProfile(1L,"jwt","en")).thenReturn(new AuthenticatedJobsClient.ResumeProfile(1L,"Java developer",List.of("Java"),2L,null,null));
        assertThat(real.recommend(1L,null,JobCriteria.defaults(5),"jwt","en")).containsEntry("kind","job_profile_required");
        verifyNoInteractions(jobs);
        real.recommend(1L,"NO_EXPERIENCE",JobCriteria.defaults(5),"jwt","en");
        verify(jobs).recommendProfile(argThat(p -> p.experienceLevel().equals("NO_EXPERIENCE")),any(),eq("en"));
    }

    private String filters(String title,String region,String unsupported) {
        try { return mapper.writeValueAsString(Map.of("occupation",title,"region",region,"salaryMin","","experienceLevel","",
                "employmentType","","workSchedule","","unsupported",unsupported,"clarification",false)); }
        catch(Exception e) { throw new RuntimeException(e); }
    }
}
