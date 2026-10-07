package com.example.service.impl;
import com.example.config.clent.CompanyClient;
import com.example.dto.ApiResponse;
import com.example.dto.vacancy.*;
import com.example.entity.Vacancy;
import com.example.enums.*;
import com.example.exp.AppNotFoundException;
import com.example.repository.VacancyRepository;
import com.example.service.ResourceBundleService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class VacancyPublicMethodsTest {
    VacancyRepository repository=mock(VacancyRepository.class);
    CompanyClient client=mock(CompanyClient.class);
    ResourceBundleService messages=mock(ResourceBundleService.class);
    VacancyServiceImpl service=new VacancyServiceImpl(repository,client,new ModelMapper(),messages);
    Vacancy vacancy;
    @BeforeEach void setup() {
        vacancy=new Vacancy(); vacancy.setId(100L); vacancy.setCompanyId(42L); vacancy.setVacancyStatus(VacancyStatus.PUBLISHED);
        when(repository.findByIdAndVacancyStatusAndDeletedFalse(100L,VacancyStatus.PUBLISHED)).thenReturn(Optional.of(vacancy));
        CompanySummaryDTO company=new CompanySummaryDTO(); company.setId(42L); company.setName("Example"); company.setSlug("example");
        when(client.getSummary(42L)).thenReturn(company);
    }
    @Test void hiddenContactsAreNotFetchedOrSerialized() throws Exception {
        for (Boolean show : new Boolean[]{false,null}) {
            vacancy.setShowContacts(show);
            var dto=service.getVacancy(100L,AppLanguage.UZ);
            assertNull(dto.getContacts());
            assertFalse(new ObjectMapper().writeValueAsString(dto).contains("\"contacts\""));
        }
        verify(client,never()).getPublicContacts(anyString(),anyString());
    }
    @Test void allowedContactsComeFromCompanyService() {
        vacancy.setShowContacts(true);
        VacancyContactsDTO contacts=new VacancyContactsDTO(); contacts.setPhonePrimary("+998901234567");
        when(client.getPublicContacts("example","UZ")).thenReturn(ApiResponse.successResponse(contacts));
        var dto=service.getVacancy(100L,AppLanguage.UZ);
        assertEquals("+998901234567",dto.getContacts().getPhonePrimary()); assertEquals("Example",dto.getCompanyName());
    }
    @Test void missingVacancyDoesNotCallCompanyService() {
        assertThrows(AppNotFoundException.class,()->service.getVacancy(999L,AppLanguage.UZ));
        verifyNoInteractions(client);
    }
    @Test void listDoesNotFetchContactsAndDeduplicatesCompanyLookups() {
        vacancy.setShowContacts(true);
        when(repository.findAll(any(Specification.class),any(Pageable.class)))
          .thenAnswer(i->new PageImpl<>(List.of(vacancy,vacancy),i.getArgument(1),2));
        var page=service.getVacancies(new VacancyFilter(),AppLanguage.UZ);
        assertEquals(2,page.getTotalElements()); assertNull(page.getContent().get(0).getContacts());
        verify(client,times(1)).getSummary(42L); verify(client,never()).getPublicContacts(anyString(),anyString());
    }
}