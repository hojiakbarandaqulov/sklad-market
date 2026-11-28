package com.example.controller;

import com.example.enums.AppLanguage;
import com.example.enums.JobType;
import com.example.service.VacancyService;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.List;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class VacancyJobTypeFilterTest {
    @Test void filterBindsBothEnumValuesAndKeepsTheDefaultUnfiltered() throws Exception {
        var service = mock(VacancyService.class);
        when(service.getVacancies(any(), any())).thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));
        var mvc = MockMvcBuilders.standaloneSetup(new PublicVacancyController(service)).build();
        for (JobType type : JobType.values()) {
            mvc.perform(get("/api/v1/vacancies").param("jobType", type.name()).param("regionId", "1"))
                    .andExpect(status().isOk());
            verify(service).getVacancies(argThat(filter -> filter.getJobType() == type && Long.valueOf(1).equals(filter.getRegionId())), eq(AppLanguage.UZ));
        }
        mvc.perform(get("/api/v1/vacancies")).andExpect(status().isOk());
        verify(service).getVacancies(argThat(filter -> filter.getJobType() == null), eq(AppLanguage.UZ));
    }

    @Test void invalidEnumDoesNotReachTheService() throws Exception {
        var service = mock(VacancyService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new PublicVacancyController(service)).build();
        mvc.perform(get("/api/v1/vacancies").param("jobType", "INVALID"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
}
