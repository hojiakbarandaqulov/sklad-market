package com.example.repository;

import com.example.dto.resume.AiJobsRequests;
import com.example.entity.*;
import com.example.enums.*;
import com.example.exp.*;
import com.example.service.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ContextConfiguration;
import java.time.Instant;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DataJpaTest(properties={"spring.flyway.enabled=false","spring.jpa.database-platform=org.hibernate.dialect.H2Dialect","spring.jpa.hibernate.ddl-auto=create-drop"})
@ContextConfiguration(classes=AiCandidateDiscoveryTest.Config.class)
class AiCandidateDiscoveryTest {
    @Configuration @EnableAutoConfiguration @EntityScan("com.example.entity")
    @EnableJpaRepositories("com.example.repository") static class Config {}
    @Autowired ResumeRepository resumes;
    @Autowired ResumeDiscoveryRepository discoveries;
    @Autowired VacancyRepository vacancies;
    JobsVacancyAccess access;
    AiCandidateDiscoveryService service;
    Vacancy vacancy;

    @BeforeEach void setup() {
        access=mock(JobsVacancyAccess.class); when(access.viewer()).thenReturn(77L);
        service=new AiCandidateDiscoveryService(resumes,discoveries,vacancies,access);
        vacancy=new Vacancy(); vacancy.setPositionName("Java developer"); vacancy.setRegionId(1L);
        vacancy.setBuyerId(77L); vacancy.setVacancyStatus(VacancyStatus.PUBLISHED);
        vacancy.setExperienceLevel(ExperienceLevel.ONE_TO_THREE_YEARS);
        vacancy=vacancies.saveAndFlush(vacancy);
        when(access.owned(vacancy.getId(),false,true)).thenReturn(vacancy);
    }
    Resume resume(long owner,String specialty,long region) {
        Resume r=new Resume(); r.setCandidateId(owner); r.setTitle("Private resume");
        r.setFullName("Never returned"); r.setPhone("+998901111111"); r.setEmail("private@example.com");
        r.setRegionId(region); r.setDesiredPosition(specialty); r.setSkills("Java;SQL");
        r.setWorkExperience("A long private work history with personal data");
        return resumes.saveAndFlush(r);
    }
    ResumeDiscovery enable(Resume r,String specialty,ExperienceLevel experience) {
        ResumeDiscovery d=new ResumeDiscovery(); d.setResume(r); d.setEnabled(true);
        d.setSpecialty(specialty); d.setExperienceLevel(experience); d.setRegionId(r.getRegionId());
        d.setSkills("Java\nSQL"); d.setConsentedAt(Instant.now()); d.setConsentVersion("2026-10-v1");
        return discoveries.saveAndFlush(d);
    }
    @Test void existingPrivateResumesAreNotDiscoverableAndProfileDoesNotInferExperience() {
        Resume own=resume(77,"Java developer",1);
        assertFalse(service.discovery(own.getId()).enabled());
        assertNull(service.matchingProfile(own.getId()).experienceLevel());
        assertTrue(service.candidates(vacancy.getId(),null,1,5).items().isEmpty());
        assertThrows(AppNotFoundException.class,()->service.matchingProfile(resume(88,"Java developer",1).getId()));
    }
    @Test void enablingRequiresOwnerConsentAndStructuredFieldsAndCanPauseWithoutRepeatingThem() {
        Resume own=resume(77,"Java developer",1);
        assertThrows(AppBadException.class,()->service.saveDiscovery(own.getId(),new AiJobsRequests.Discovery(true,false,"Java developer",ExperienceLevel.ONE_TO_THREE_YEARS,1L,List.of("Java"))));
        assertThrows(AppBadException.class,()->service.saveDiscovery(own.getId(),new AiJobsRequests.Discovery(true,true,"Java developer",ExperienceLevel.ONE_TO_THREE_YEARS,2L,List.of("Java"))));
        assertThrows(AppBadException.class,()->service.saveDiscovery(own.getId(),new AiJobsRequests.Discovery(true,true,"Contact private@example.com",ExperienceLevel.ONE_TO_THREE_YEARS,1L,List.of("Java"))));
        assertThrows(AppBadException.class,()->service.saveDiscovery(own.getId(),new AiJobsRequests.Discovery(true,true,"Java developer",ExperienceLevel.ONE_TO_THREE_YEARS,1L,List.of("+998 90 123 45 67"))));
        assertTrue(service.saveDiscovery(own.getId(),new AiJobsRequests.Discovery(true,true,"Java developer",ExperienceLevel.ONE_TO_THREE_YEARS,1L,List.of("Java"))).enabled());
        assertFalse(service.saveDiscovery(own.getId(),new AiJobsRequests.Discovery(false,null,null,null,null,null)).enabled());
    }
    @Test void exactProfessionRegionExperienceAndConsentAllApplyAndRevokedDetailFails() {
        Resume wanted=resume(88,"Java dasturchi",1);
        ResumeDiscovery setting=enable(wanted,"Java dasturchi",ExperienceLevel.THREE_TO_SIX_YEARS);
        enable(resume(89,"JavaScript developer",1),"JavaScript developer",ExperienceLevel.OVER_SIX_YEARS);
        enable(resume(90,"Java developer",2),"Java developer",ExperienceLevel.OVER_SIX_YEARS);
        enable(resume(91,"Java developer",1),"Java developer",ExperienceLevel.NO_EXPERIENCE);
        enable(resume(77,"Java developer",1),"Java developer",ExperienceLevel.OVER_SIX_YEARS);
        Resume privateResume=resume(92,"Java developer",1);
        var result=service.candidates(vacancy.getId(),null,1,5);
        assertEquals(List.of(wanted.getId()),result.items().stream().map(AiCandidateDiscoveryService.Candidate::id).toList());
        assertEquals("Java dasturchi",service.candidate(vacancy.getId(),wanted.getId()).specialty());
        assertThrows(AppNotFoundException.class,()->service.candidate(vacancy.getId(),privateResume.getId()));
        setting.setEnabled(false); discoveries.saveAndFlush(setting);
        assertTrue(service.candidates(vacancy.getId(),null,1,5).items().isEmpty());
        assertThrows(AppNotFoundException.class,()->service.candidate(vacancy.getId(),wanted.getId()));
    }
    @Test void fiveRowsArePaginatedDistinctPeopleAndSearchNeverBroadens() {
        for(long user=80;user<87;user++) enable(resume(user,"Java developer",1),"Java developer",ExperienceLevel.ONE_TO_THREE_YEARS);
        enable(resume(80,"Java developer",1),"Java developer",ExperienceLevel.ONE_TO_THREE_YEARS);
        enable(resume(81,"Accountant",1),"Accountant",ExperienceLevel.ONE_TO_THREE_YEARS);
        enable(resume(82,"Java developer",1),"Java developer",ExperienceLevel.NO_EXPERIENCE);
        var first=service.candidates(vacancy.getId(),null,1,5);
        assertEquals(5,first.items().size()); assertTrue(first.hasMore());
        assertEquals(2,service.candidates(vacancy.getId(),null,2,5).items().size());
        assertTrue(service.candidates(vacancy.getId(),"forklift",1,5).items().isEmpty());
        assertThrows(AppBadException.class,()->service.candidates(vacancy.getId(),"%_!!!",1,5));
        assertThrows(AppBadException.class,()->service.candidates(vacancy.getId(),null,0,5));
    }
    @Test void skillEvidenceRanksBeforeRecencyAndBestEligibleResumeWinsPerPerson() {
        vacancy.setRequirements("Java SQL PostgreSQL"); vacancies.saveAndFlush(vacancy);
        Resume best=resume(88,"Java developer",1);
        ResumeDiscovery bestProfile=enable(best,"Java developer",ExperienceLevel.ONE_TO_THREE_YEARS);
        bestProfile.setSkills("Java\nSQL\nPostgreSQL"); discoveries.saveAndFlush(bestProfile);
        enable(resume(88,"Java developer",1),"Java developer",ExperienceLevel.THREE_TO_SIX_YEARS);
        Resume other=resume(89,"Java developer",1); enable(other,"Java developer",ExperienceLevel.THREE_TO_SIX_YEARS);
        var result=service.candidates(vacancy.getId(),null,1,5);
        assertEquals(2,result.items().size());
        assertEquals(best.getId(),result.items().get(0).id());
        assertEquals(other.getId(),result.items().get(1).id());
    }
    @Test void detailedProfessionalResumeIsAllowlistedAndContactsAreScrubbed() throws Exception {
        Resume r=resume(88,"Java developer",1);
        r.setFileId("private-attachment-uuid");
        r.setWorkExperience("Acme Java engineer; Never returned; private@example.com; +998 90 111 11 11; https://private.example/contact; private-attachment-uuid");
        r.setEducation("University");r.setLanguages("UZ, RU, EN");resumes.saveAndFlush(r);
        enable(r,"Java developer",ExperienceLevel.THREE_TO_SIX_YEARS);
        var detail=service.candidate(vacancy.getId(),r.getId());
        assertTrue(detail.workExperience().contains("Acme Java engineer"));
        assertEquals("University",detail.education());
        String json=new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(detail);
        for(String secret:List.of("Never returned","private@example.com","+998","111 11","private.example","private-attachment-uuid","fullName","fileId","aboutMe"))
            assertFalse(json.contains(secret),secret);
        String summary=new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(service.candidates(vacancy.getId(),null,1,5));
        assertFalse(summary.contains("Acme"));assertFalse(summary.contains("University"));
    }
    @Test void movedOrDeletedResumeIsRemovedAndNativeIsNotWebReact() {
        vacancy.setPositionName("React developer"); vacancies.saveAndFlush(vacancy);
        enable(resume(88,"React Native developer",1),"React Native developer",ExperienceLevel.THREE_TO_SIX_YEARS);
        Resume actual=resume(89,"React developer",1); enable(actual,"React developer",ExperienceLevel.THREE_TO_SIX_YEARS);
        assertEquals(List.of(actual.getId()),service.candidates(vacancy.getId(),null,1,5).items().stream().map(AiCandidateDiscoveryService.Candidate::id).toList());
        actual.setRegionId(2L); resumes.saveAndFlush(actual);
        assertTrue(service.candidates(vacancy.getId(),null,1,5).items().isEmpty());
    }
}
