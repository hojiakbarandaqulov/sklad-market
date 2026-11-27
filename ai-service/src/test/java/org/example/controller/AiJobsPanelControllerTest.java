package org.example.controller;

import org.example.ai.jobs.*;
import org.example.ai.guardrail.RpmRateLimiter;
import org.example.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import java.util.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AiJobsPanelController.class) @Import(SecurityConfig.class)
@TestPropertySource(properties="server.domain=http://localhost")
class AiJobsPanelControllerTest {
    @Autowired MockMvc mvc;
    @MockBean JwtDecoder decoder;
    @MockBean JobsPanelService panel;
    @MockBean JobMatchingService matching;
    @MockBean RpmRateLimiter rpm;
    @Test void guestsCannotTriggerPanelOrDirectSearch() throws Exception {
        mvc.perform(post("/api/v1/ai/jobs/panel").contentType("application/json").content("{\"mode\":\"JOBS\",\"query\":\"driver\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/ai/jobs/search?query=driver")).andExpect(status().isUnauthorized());
        verifyNoInteractions(panel,matching,rpm);
    }
    @Test void panelCapturesCurrentCallerAndDoesNotAcceptAnInjectedIdentity() throws Exception {
        when(rpm.tryConsume(anyString(),eq(20))).thenReturn(true);
        when(panel.search(any(),eq("owner"),anySet(),anyString(),any())).thenReturn(Map.of("kind","job_search","items",List.of()));
        mvc.perform(post("/api/v1/ai/jobs/panel?userSub=someone-else").with(jwt().jwt(j -> j.subject("owner")))
                .contentType("application/json").content("{\"mode\":\"JOBS\",\"query\":\"driver\"}"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"));
        verify(panel).search(any(),eq("owner"),anySet(),anyString(),any());
    }
    @Test void badJsonAndRateDenialDoNotReachMatching() throws Exception {
        mvc.perform(post("/api/v1/ai/jobs/panel").with(jwt()).contentType("application/json").content("{bad"))
                .andExpect(status().isBadRequest());
        when(rpm.tryConsume(anyString(),eq(20))).thenReturn(false);
        mvc.perform(post("/api/v1/ai/jobs/panel").with(jwt()).contentType("application/json").content("{\"mode\":\"JOBS\"}"))
                .andExpect(status().isTooManyRequests());
        verifyNoInteractions(panel,matching);
    }
}
