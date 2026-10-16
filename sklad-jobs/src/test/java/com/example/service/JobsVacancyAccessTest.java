package com.example.service;

import com.example.config.clent.CompanyClient;
import com.example.entity.Vacancy;
import com.example.enums.VacancyStatus;
import com.example.exp.*;
import com.example.repository.VacancyRepository;
import com.example.utils.SpringSecurityUtil;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class JobsVacancyAccessTest {
    VacancyRepository repository=mock(VacancyRepository.class);
    CompanyClient companies=mock(CompanyClient.class);
    JobsVacancyAccess service=new JobsVacancyAccess(repository,companies);
    MockedStatic<SpringSecurityUtil> security;
    Vacancy vacancy;
    @BeforeEach void setup() {
        security=mockStatic(SpringSecurityUtil.class); security.when(SpringSecurityUtil::getProfileId).thenReturn(77L);
        vacancy=new Vacancy();vacancy.setId(42L);vacancy.setVacancyStatus(VacancyStatus.PUBLISHED);
        when(repository.findByIdAndDeletedFalse(42L)).thenReturn(Optional.of(vacancy));
    }
    @AfterEach void cleanup() {security.close();}
    @Test void actualBuyerOrCompanyOwnerOnlyNotRoleLabels() {
        vacancy.setBuyerId(77L);
        assertSame(vacancy,service.owned(42L,false,true));verifyNoInteractions(companies);
        vacancy.setBuyerId(66L);vacancy.setCompanyId(7L);
        when(companies.getOwnedCompanyIds(77L)).thenReturn(List.of(8L));
        assertThrows(AppNotFoundException.class,()->service.owned(42L,false,true));
        when(companies.getOwnedCompanyIds(77L)).thenReturn(List.of(7L));
        assertSame(vacancy,service.owned(42L,false,true));
        vacancy.setVacancyStatus(VacancyStatus.CLOSED);
        assertThrows(AppConflictException.class,()->service.owned(42L,false,true));
    }
    @Test void guestsCannotQueryOwnership() {
        security.when(SpringSecurityUtil::getProfileId).thenReturn(null);
        assertThrows(AuthenticationCredentialsNotFoundException.class,()->service.owned(42L,false,true));
        verifyNoInteractions(repository,companies);
    }
}
