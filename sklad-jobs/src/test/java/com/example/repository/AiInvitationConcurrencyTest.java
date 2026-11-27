package com.example.repository;

import com.example.dto.resume.AiJobsRequests;
import com.example.entity.*;
import com.example.enums.*;
import com.example.exp.AppConflictException;
import com.example.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DataJpaTest(properties={"spring.flyway.enabled=false","spring.jpa.database-platform=org.hibernate.dialect.H2Dialect","spring.jpa.hibernate.ddl-auto=create-drop"})
@ContextConfiguration(classes=AiCandidateDiscoveryTest.Config.class)
class AiInvitationConcurrencyTest {
    @Autowired VacancyRepository vacancies;
    @Autowired ResumeRepository resumes;
    @Autowired ResumeDiscoveryRepository discoveries;
    @Autowired JobInvitationRepository invitations;
    @Autowired JobApplicationRepository applications;
    @Autowired PlatformTransactionManager manager;

    @Test @Transactional(propagation=Propagation.NOT_SUPPORTED)
    void concurrentDifferentRequestsProduceExactlyOnePendingInvitation() throws Exception {
        TransactionTemplate tx=new TransactionTemplate(manager);
        long[] ids=tx.execute(status -> {
            Vacancy vacancy=new Vacancy();vacancy.setBuyerId(77L);vacancy.setPositionName("Java developer");
            vacancy.setRegionId(1L);vacancy.setVacancyStatus(VacancyStatus.PUBLISHED);vacancy.setExperienceLevel(ExperienceLevel.NO_EXPERIENCE);
            vacancy=vacancies.saveAndFlush(vacancy);
            Resume resume=new Resume();resume.setCandidateId(88L);resume.setTitle("Private");resume.setFullName("Private");
            resume.setPhone("+998901111111");resume.setEmail("private@example.com");resume.setRegionId(1L);
            resume=resumes.saveAndFlush(resume);
            ResumeDiscovery setting=new ResumeDiscovery();setting.setResume(resume);setting.setEnabled(true);
            setting.setSpecialty("Java developer");setting.setRegionId(1L);setting.setExperienceLevel(ExperienceLevel.NO_EXPERIENCE);setting.setSkills("Java");
            discoveries.saveAndFlush(setting);
            return new long[]{vacancy.getId(),resume.getId()};
        });
        JobsVacancyAccess access=mock(JobsVacancyAccess.class);when(access.viewer()).thenReturn(77L);
        when(access.owned(ids[0],true,true)).thenAnswer(i -> vacancies.findForApplication(ids[0]).orElseThrow());
        AiCandidateDiscoveryService discovery=new AiCandidateDiscoveryService(resumes,discoveries,vacancies,access);
        JobInvitationService service=new JobInvitationService(invitations,vacancies,resumes,discoveries,applications,access,discovery);
        ExecutorService workers=Executors.newFixedThreadPool(2);
        CountDownLatch ready=new CountDownLatch(2),start=new CountDownLatch(1);
        Callable<String> send=()->{
            ready.countDown();start.await(5,TimeUnit.SECONDS);
            try {return tx.execute(status -> {service.send(new AiJobsRequests.Invitation(ids[1],ids[0],null,UUID.randomUUID()));return "SENT";});}
            catch(AppConflictException expected) {return "DUPLICATE";}
        };
        try {
            Future<String> first=workers.submit(send),second=workers.submit(send);
            assertTrue(ready.await(5,TimeUnit.SECONDS));start.countDown();
            assertEquals(Set.of("SENT","DUPLICATE"),Set.of(first.get(15,TimeUnit.SECONDS),second.get(15,TimeUnit.SECONDS)));
            assertEquals(1,invitations.count());
        } finally {
            workers.shutdownNow();
            tx.executeWithoutResult(status -> {invitations.deleteAll();discoveries.deleteAll();resumes.deleteAll();vacancies.deleteAll();});
        }
    }
}
