package com.example.service.impl;

import com.example.config.clent.CompanyClient;
import com.example.dto.vacancy.VacancyCreateBuyer;
import com.example.entity.Vacancy;
import com.example.enums.*;
import com.example.exp.AppBadException;
import com.example.repository.VacancyRepository;
import com.example.service.ResourceBundleService;
import com.example.utils.SpringSecurityUtil;
import org.junit.jupiter.api.Test;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.*;
import org.springframework.security.access.AccessDeniedException;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class BuyerVacancyServiceTest {
    final VacancyRepository repository = mock(VacancyRepository.class);
    final CompanyClient companies = mock(CompanyClient.class);
    final ResourceBundleService messages = mock(ResourceBundleService.class);
    final VacancyServiceImpl service = new VacancyServiceImpl(repository, companies, new ModelMapper(), messages);

    @Test void creationStoresJwtBuyerAndListReturnsTheSavedVacancy() {
        Vacancy[] saved = new Vacancy[1];
        when(repository.save(any())).thenAnswer(call -> {
            saved[0] = call.getArgument(0);
            saved[0].setId(71L);
            assertEquals(25L,saved[0].getBuyerId());
            assertNull(saved[0].getCompanyId());
            assertEquals(VacancyStatus.DRAFT,saved[0].getVacancyStatus());
            return saved[0];
        });
        when(repository.findByBuyerIdAndDeletedFalse(eq(25L),any(Pageable.class)))
            .thenAnswer(call -> new PageImpl<>(List.of(saved[0]),call.getArgument(1),1));
        var request = new VacancyCreateBuyer(); request.setPositionName("Operator");
        try (var security = mockStatic(SpringSecurityUtil.class)) {
            security.when(SpringSecurityUtil::getProfileId).thenReturn(25L);
            assertEquals(71L, service.createVacancyBuyer(request,AppLanguage.UZ).getId());
            var page = service.getMyVacancy(1,20,AppLanguage.UZ);
            assertEquals(71L,page.getContent().get(0).getId());
            assertEquals(1,page.getTotalElements());
            verifyNoInteractions(companies);
        }
    }
    @Test void emptyListAndOutOfRangePageAreSuccessfulPages() {
        when(repository.findByBuyerIdAndDeletedFalse(eq(25L),any(Pageable.class)))
            .thenAnswer(call -> new PageImpl<>(List.of(),call.getArgument(1),0));
        try (var security = mockStatic(SpringSecurityUtil.class)) {
            security.when(SpringSecurityUtil::getProfileId).thenReturn(25L);
            for(int pageNumber : new int[]{1,3}) {
                var page = service.getMyVacancy(pageNumber,20,AppLanguage.UZ);
                assertTrue(page.isEmpty());
                assertEquals(pageNumber - 1,page.getNumber());
                assertEquals(0,page.getTotalElements());
            }
        }
    }
    @Test void invalidPaginationDoesNotQueryDatabase() {
        try (var security = mockStatic(SpringSecurityUtil.class)) {
            security.when(SpringSecurityUtil::getProfileId).thenReturn(25L);
            for(int[] page : new int[][]{{0,20},{1,0},{1,101}}) {
                assertThrows(AppBadException.class, () -> service.getMyVacancy(page[0],page[1],AppLanguage.UZ));
            }
            verifyNoInteractions(repository);
        }
    }
    @Test void missingProfileCannotCreateOrListOtherUsersVacancies() {
        try (var security = mockStatic(SpringSecurityUtil.class)) {
            security.when(SpringSecurityUtil::getProfileId).thenReturn(null);
            assertThrows(AccessDeniedException.class, () -> service.createVacancyBuyer(new VacancyCreateBuyer(),AppLanguage.UZ));
            assertThrows(AccessDeniedException.class, () -> service.getMyVacancy(1,20,AppLanguage.UZ));
            verifyNoInteractions(repository);
        }
    }
}