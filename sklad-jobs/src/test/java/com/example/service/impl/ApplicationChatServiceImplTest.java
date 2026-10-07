package com.example.service.impl;
import com.example.config.clent.*;
import com.example.entity.*;
import com.example.repository.JobApplicationRepository;
import com.example.utils.SpringSecurityUtil;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import org.springframework.security.access.AccessDeniedException;
import java.time.Instant;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;
class ApplicationChatServiceImplTest {
    JobApplicationRepository repo=mock(JobApplicationRepository.class);
    ChatClient chat=mock(ChatClient.class);
    CompanyClient companies=mock(CompanyClient.class);
    ApplicationChatServiceImpl service=new ApplicationChatServiceImpl(repo,chat,companies);
    JobApplication app;
    MockedStatic<SpringSecurityUtil> auth;
    @BeforeEach void setup(){
        auth=mockStatic(SpringSecurityUtil.class);auth.when(SpringSecurityUtil::getProfileId).thenReturn(25L);
        Vacancy v=new Vacancy();v.setId(100L);v.setCompanyId(42L);
        app=new JobApplication();app.setId(1L);app.setCandidateId(25L);app.setVacancy(v);app.setChatPending(true);
        when(repo.findForChat(1L)).thenReturn(Optional.of(app));
    }
    @AfterEach void close(){auth.close();}
    @Test void ownerOpensAndRetryReusesLinkedChat(){
        when(chat.open(any())).thenReturn(Map.of("threadId",80L));
        assertEquals(80L,service.open(1L).chatThreadId());
        assertEquals("READY",service.open(1L).state());
        assertFalse(app.isChatPending());
        verify(chat,times(1)).open(eq(new com.example.dto.application.JobChatRequest(1L,100L,25L,42L)));
    }
    @Test void foreignUserCannotCreateChat(){
        auth.when(SpringSecurityUtil::getProfileId).thenReturn(26L);
        when(companies.getOwnedCompanyIds(26L)).thenReturn(List.of(99L));
        assertThrows(AccessDeniedException.class,()->service.open(1L));verifyNoInteractions(chat);
    }
    @Test void companyOwnerCanOpen(){
        auth.when(SpringSecurityUtil::getProfileId).thenReturn(26L);
        when(companies.getOwnedCompanyIds(26L)).thenReturn(List.of(42L));
        when(chat.open(any())).thenReturn(Map.of("threadId",80L));
        assertEquals(80L,service.open(1L).chatThreadId());
    }
    @Test void failureRemainsPendingThenRecovers(){
        when(chat.open(any())).thenThrow(mock(feign.FeignException.class)).thenReturn(Map.of("threadId",80L));
        service.deliver(1L);assertTrue(app.isChatPending());assertNull(app.getChatThreadId());
        assertTrue(app.getChatNextAttemptAt().isAfter(Instant.now()));
        service.deliver(1L);verify(chat,times(1)).open(any());
        app.setChatNextAttemptAt(Instant.now().minusSeconds(1));service.deliver(1L);
        assertEquals(80L,app.getChatThreadId());assertFalse(app.isChatPending());
    }
    @Test void missingThreadIdRemainsPending(){
        when(chat.open(any())).thenReturn(Map.of());
        assertEquals("PENDING",service.open(1L).state());
        assertNull(app.getChatThreadId());assertTrue(app.isChatPending());
    }
}