package com.example.repository;
import com.example.entity.*;
import com.example.enums.ApplicationStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ContextConfiguration;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;
@DataJpaTest(properties={"spring.flyway.enabled=false","spring.jpa.database-platform=org.hibernate.dialect.H2Dialect","spring.jpa.hibernate.ddl-auto=create-drop"})
@ContextConfiguration(classes=PublicVacancyRepositoryTest.Config.class)
class JobApplicationRepositoryTest {
    @Autowired JobApplicationRepository apps;
    @Autowired VacancyRepository vacancies;
    private JobApplication save(long owner,ApplicationStatus status,boolean deleted){
        Vacancy v=vacancies.save(new Vacancy());
        JobApplication a=new JobApplication();a.setVacancy(v);a.setCandidateId(owner);a.setStatus(status);a.setDeleted(deleted);
        a.setFullName("Test");a.setPhone("+998901234567");a.setEmail("test@example.com");a.setRegionId(1L);a.setResumeId(501L);a.setConsentAcceptedAt(Instant.now());
        return apps.saveAndFlush(a);
    }
    @Test void queriesExcludeForeignAndDeletedAndSupportOptionalStatus(){
        JobApplication mine=save(25,ApplicationStatus.NEW,false);
        save(25,ApplicationStatus.REVIEWED,false);
        JobApplication foreign=save(26,ApplicationStatus.NEW,false);
        JobApplication deleted=save(25,ApplicationStatus.NEW,true);
        assertTrue(apps.existsByVacancyIdAndCandidateIdAndDeletedFalse(mine.getVacancy().getId(),25L));
        assertFalse(apps.existsByVacancyIdAndCandidateIdAndDeletedFalse(mine.getVacancy().getId(),26L));
        assertFalse(apps.existsByVacancyIdAndCandidateIdAndDeletedFalse(deleted.getVacancy().getId(),25L));
        assertTrue(vacancies.findForApplication(mine.getVacancy().getId()).isPresent());
        mine.getVacancy().setDeleted(true);vacancies.saveAndFlush(mine.getVacancy());
        assertTrue(vacancies.findForApplication(mine.getVacancy().getId()).isEmpty());
        assertEquals(2,apps.findMyApplications(25L,null,PageRequest.of(0,20)).getTotalElements());
        assertEquals(1,apps.findMyApplications(25L,ApplicationStatus.NEW,PageRequest.of(0,20)).getTotalElements());
        assertTrue(apps.findByIdAndCandidateIdAndDeletedFalse(foreign.getId(),25L).isEmpty());
        assertTrue(apps.findOwnedForUpdate(deleted.getId(),25L).isEmpty());
        assertTrue(apps.findOwnedForUpdate(mine.getId(),25L).isPresent());
    }
    @Test void resumeAccessRequiresMatchingCandidateApplicationAndCompany() {
        JobApplication application = save(25L, ApplicationStatus.NEW, false);
        application.getVacancy().setCompanyId(7L);
        vacancies.saveAndFlush(application.getVacancy());
        assertTrue(apps.existsResumeApplicationForCompanies(501L, 25L, java.util.List.of(7L)));
        assertFalse(apps.existsResumeApplicationForCompanies(501L, 26L, java.util.List.of(7L)));
        assertFalse(apps.existsResumeApplicationForCompanies(502L, 25L, java.util.List.of(7L)));
        assertFalse(apps.existsResumeApplicationForCompanies(501L, 25L, java.util.List.of(8L)));
        application.setDeleted(true);
        apps.saveAndFlush(application);
        assertFalse(apps.existsResumeApplicationForCompanies(501L, 25L, java.util.List.of(7L)));
        application.setDeleted(false);
        apps.saveAndFlush(application);
        application.getVacancy().setDeleted(true);
        vacancies.saveAndFlush(application.getVacancy());
        assertFalse(apps.existsResumeApplicationForCompanies(501L, 25L, java.util.List.of(7L)));
    }
}