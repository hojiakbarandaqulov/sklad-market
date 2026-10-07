package com.example.service.impl;

import com.example.config.clent.CompanyClient;
import com.example.controller.JobApplicationController;
import com.example.entity.*;
import com.example.enums.*;
import com.example.repository.*;
import com.example.service.*;
import com.example.utils.SpringSecurityUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.modelmapper.ModelMapper;
import org.springframework.context.annotation.*;
import org.springframework.data.domain.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class BuyerIncomingApplicationsTest {
    final VacancyRepository vacancies=mock(VacancyRepository.class);
    final JobApplicationRepository applications=mock(JobApplicationRepository.class);
    final CompanyClient companies=mock(CompanyClient.class);
    final JobApplicationServiceImpl service=new JobApplicationServiceImpl(applications,mock(ResumeRepository.class),vacancies,
        mock(ResourceBundleService.class),new ModelMapper(),new ObjectMapper(),companies);

    Vacancy vacancy(Long buyer, Long company) {
        Vacancy v=new Vacancy(); v.setId(42L); v.setBuyerId(buyer); v.setCompanyId(company);
        when(vacancies.findByIdAndDeletedFalse(42L)).thenReturn(Optional.of(v));
        return v;
    }
    @Test void buyerReadsIncomingResumeIdsForOwnVacancy() {
        Vacancy vacancy=vacancy(77L,null);
        JobApplication application=new JobApplication(); application.setId(9001L); application.setVacancy(vacancy);
        application.setCandidateId(25L); application.setResumeId(501L); application.setFullName("Candidate");
        when(applications.findByVacancyIdAndDeletedFalseAndStatus(eq(42L),eq(ApplicationStatus.NEW),any(Pageable.class)))
            .thenAnswer(call->new PageImpl<>(List.of(application),call.getArgument(2),1));
        try(var security=mockStatic(SpringSecurityUtil.class)) {
            security.when(SpringSecurityUtil::getProfileId).thenReturn(77L);
            var page=service.getApplicationVacancy(42L,GetNewApplicationStatus.NEW,1,20);
            assertEquals(9001L,page.getContent().get(0).getId());
            assertEquals(501L,page.getContent().get(0).getResumeId());
            verifyNoInteractions(companies);
        }
    }
    @Test void anotherBuyerCannotReadApplicants() {
        vacancy(78L,null);
        try(var security=mockStatic(SpringSecurityUtil.class)) {
            security.when(SpringSecurityUtil::getProfileId).thenReturn(77L);
            assertThrows(AccessDeniedException.class,()->service.getApplicationVacancy(42L,GetNewApplicationStatus.NEW,1,20));
            verifyNoInteractions(applications,companies);
        }
    }
    @Test void sellerCompanyOwnershipStillRequired() {
        vacancy(null,7L);
        when(companies.getOwnedCompanyIds(77L)).thenReturn(List.of(7L));
        when(applications.findByVacancyIdAndDeletedFalseAndStatus(eq(42L),eq(ApplicationStatus.NEW),any(Pageable.class)))
            .thenAnswer(call->new PageImpl<>(List.of(),call.getArgument(2),0));
        try(var security=mockStatic(SpringSecurityUtil.class)) {
            security.when(SpringSecurityUtil::getProfileId).thenReturn(77L);
            assertTrue(service.getApplicationVacancy(42L,GetNewApplicationStatus.NEW,1,20).isEmpty());
            verify(companies).getOwnedCompanyIds(77L);
        }
    }
    @Configuration @EnableMethodSecurity(proxyTargetClass=true)
    static class Config {
        @Bean JobApplicationService service() { return mock(JobApplicationService.class); }
        @Bean JobApplicationController controller(JobApplicationService service) { return new JobApplicationController(service); }
    }
    @Test void buyerCanReadIncomingListButCannotChangeApplicationStatus() {
        try(var context=new AnnotationConfigApplicationContext(Config.class)) {
            var controller=context.getBean(JobApplicationController.class);
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("buyer","",List.of(new SimpleGrantedAuthority("ROLE_BUYER"))));
            assertDoesNotThrow(()->controller.getApplicationVacancy(42L,GetNewApplicationStatus.NEW,1,20,AppLanguage.UZ));
            assertThrows(AccessDeniedException.class,()->controller.applicationResponse(9001L,ApplicationResponseStatus.ACCEPTED,AppLanguage.UZ));
        } finally { SecurityContextHolder.clearContext(); }
    }
}