package org.example.service.impl;

import org.example.dto.ConversationDto;
import org.example.ai.tool.AgentTool;
import org.example.ai.tool.ToolRegistry;
import org.example.dto.MessageDto;
import org.example.dto.PagedResponse;
import org.example.entity.Conversation;
import org.example.entity.Message;
import org.example.entity.MessageRole;
import org.example.exception.AiNotFoundException;
import org.example.repository.ConversationRepository;
import org.example.repository.MessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConversationServiceImplTest {

    private static final String USER_A = "sub-user-a";
    private static final String USER_B = "sub-user-b";

    @Mock
    private ConversationRepository conversationRepository;

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private ToolRegistry toolRegistry;

    private ConversationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ConversationServiceImpl(conversationRepository, messageRepository, toolRegistry);
    }

    @Test
    void create_persistsConversationOwnedByCallingUser() {
        when(conversationRepository.save(any(Conversation.class))).thenAnswer(invocation -> {
            Conversation c = invocation.getArgument(0);
            c.setId(UUID.randomUUID());
            c.setCreatedAt(Instant.now());
            c.setUpdatedAt(Instant.now());
            return c;
        });

        ConversationDto dto = service.create(USER_A, "BUYER", "My chat", "RU");

        ArgumentCaptor<Conversation> captor = ArgumentCaptor.forClass(Conversation.class);
        verify(conversationRepository).save(captor.capture());
        verify(conversationRepository).softDeleteOlderActiveConversations(
                org.mockito.ArgumentMatchers.eq(USER_A),
                org.mockito.ArgumentMatchers.eq(15),
                any(Instant.class));
        assertThat(captor.getValue().getUserSub()).isEqualTo(USER_A);
        assertThat(captor.getValue().getUserRole()).isEqualTo("BUYER");
        assertThat(captor.getValue().getLocale()).isEqualTo("ru");
        assertThat(dto.getTitle()).isEqualTo("My chat");
    }

    @Test
    void getMessages_conversationOwnedByAnotherUser_throwsNotFound() {
        UUID conversationId = UUID.randomUUID();
        when(conversationRepository.findByIdAndUserSubAndDeletedAtIsNull(conversationId, USER_B))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getMessages(USER_B, conversationId, 1, 20, Set.of("BUYER")))
                .isInstanceOf(AiNotFoundException.class);

        verify(messageRepository, never()).findByConversationIdOrderByCreatedAtAsc(any(), any());
    }

    @Test
    void getMessages_includesOwnerScopedStructuredToolPayloadForHistoryCards() {
        UUID conversationId = UUID.randomUUID();
        Conversation conversation = new Conversation();
        conversation.setId(conversationId);
        conversation.setUserSub(USER_A);
        Message tool = new Message();
        tool.setId(UUID.randomUUID());
        tool.setConversationId(conversationId);
        tool.setRole(MessageRole.TOOL);
        tool.setContent("search_businesses completed");
        tool.setToolName("search_businesses");
        tool.setToolPayload("{\"status\":\"ok\",\"resultSet\":{\"kind\":\"business_search\",\"items\":[]}}");
        tool.setCreatedAt(Instant.now());
        when(conversationRepository.findByIdAndUserSubAndDeletedAtIsNull(conversationId, USER_A))
                .thenReturn(Optional.of(conversation));
        when(messageRepository.findByConversationIdOrderByCreatedAtAsc(any(), any()))
                .thenReturn(new PageImpl<>(java.util.List.of(tool)));
        when(toolRegistry.findAllowed("search_businesses", Set.of("BUYER")))
                .thenReturn(Optional.of(org.mockito.Mockito.mock(AgentTool.class)));

        PagedResponse<MessageDto> response = service.getMessages(
                USER_A, conversationId, 1, 20, Set.of("BUYER"));

        assertThat(response.getItems()).singleElement().satisfies(message -> {
            assertThat(message.getToolName()).isEqualTo("search_businesses");
            assertThat(message.getToolPayload()).contains("business_search");
        });
    }

    @Test
    void getMessages_omitsRoleScopedToolPayloadAfterRoleRevocation() {
        UUID conversationId = UUID.randomUUID();
        Conversation conversation = new Conversation();
        conversation.setId(conversationId);
        conversation.setUserSub(USER_A);
        conversation.setUserRole("BUYER");
        Message tool = new Message();
        tool.setId(UUID.randomUUID());
        tool.setConversationId(conversationId);
        tool.setRole(MessageRole.TOOL);
        tool.setContent("recommend_buyers completed");
        tool.setToolName("recommend_buyers");
        tool.setToolPayload("{\"resultSet\":{\"kind\":\"buyer_recommendations\",\"items\":[{\"leadId\":1}]}}");
        tool.setCreatedAt(Instant.now());
        when(conversationRepository.findByIdAndUserSubAndDeletedAtIsNull(conversationId, USER_A))
                .thenReturn(Optional.of(conversation));
        when(messageRepository.findByConversationIdOrderByCreatedAtAsc(any(), any()))
                .thenReturn(new PageImpl<>(java.util.List.of(tool)));
        when(toolRegistry.findAllowed("recommend_buyers", Set.of("BUYER"))).thenReturn(Optional.empty());

        PagedResponse<MessageDto> response = service.getMessages(
                USER_A, conversationId, 1, 20, Set.of("BUYER"));

        assertThat(response.getItems()).singleElement().satisfies(message -> {
            assertThat(message.getToolName()).isEqualTo("recommend_buyers");
            assertThat(message.getToolPayload()).isNull();
        });
    }

    @Test
    void getMessages_deniesWholeConversationWhenAnyCreationRoleWasRevoked() {
        UUID conversationId = UUID.randomUUID();
        Conversation conversation = new Conversation();
        conversation.setId(conversationId);
        conversation.setUserSub(USER_A);
        conversation.setUserRole("BUYER,SELLER");
        when(conversationRepository.findByIdAndUserSubAndDeletedAtIsNull(conversationId, USER_A))
                .thenReturn(Optional.of(conversation));

        assertThatThrownBy(() -> service.getMessages(
                USER_A, conversationId, 1, 20, Set.of("BUYER")))
                .isInstanceOf(AiNotFoundException.class);

        verify(messageRepository, never()).findByConversationIdOrderByCreatedAtAsc(any(), any());
    }

    @Test
    void getMessages_deniesLegacyOrRoleGainedLaterConversationWhenToolRoleWasRevoked() {
        UUID conversationId = UUID.randomUUID();
        Conversation conversation = new Conversation();
        conversation.setId(conversationId);
        conversation.setUserSub(USER_A);
        // Legacy rows contain only one creation-time role, even if SELLER was also present later.
        conversation.setUserRole("BUYER");
        when(conversationRepository.findByIdAndUserSubAndDeletedAtIsNull(conversationId, USER_A))
                .thenReturn(Optional.of(conversation));
        when(messageRepository.findDistinctSuccessfulLegacyToolNamesByConversationId(conversationId))
                .thenReturn(java.util.List.of("get_lead"));
        AgentTool multiPersonaTool = org.mockito.Mockito.mock(AgentTool.class);
        when(multiPersonaTool.allowedRoles()).thenReturn(Set.of("BUYER", "SELLER"));
        when(toolRegistry.findRegistered("get_lead")).thenReturn(Optional.of(multiPersonaTool));

        assertThatThrownBy(() -> service.getMessages(
                USER_A, conversationId, 1, 20, Set.of("BUYER")))
                .isInstanceOf(AiNotFoundException.class);

        verify(messageRepository, never()).findByConversationIdOrderByCreatedAtAsc(any(), any());
    }

    @Test
    void getMessages_deniesWhenExactTurnRoleProvenanceIsNoLongerCurrent() {
        UUID conversationId = UUID.randomUUID();
        Conversation conversation = new Conversation();
        conversation.setId(conversationId);
        conversation.setUserSub(USER_A);
        conversation.setUserRole("BUYER");
        when(conversationRepository.findByIdAndUserSubAndDeletedAtIsNull(conversationId, USER_A))
                .thenReturn(Optional.of(conversation));
        when(messageRepository.findDistinctRequiredRolesByConversationId(conversationId))
                .thenReturn(java.util.List.of("BUYER,SELLER"));

        assertThatThrownBy(() -> service.getMessages(
                USER_A, conversationId, 1, 20, Set.of("BUYER")))
                .isInstanceOf(AiNotFoundException.class);
    }

    @Test
    void getMessages_deniesSuccessfulLegacyToolWhosePolicyIsNoLongerRegistered() {
        UUID conversationId = UUID.randomUUID();
        Conversation conversation = new Conversation();
        conversation.setId(conversationId);
        conversation.setUserSub(USER_A);
        conversation.setUserRole("BUYER");
        when(conversationRepository.findByIdAndUserSubAndDeletedAtIsNull(conversationId, USER_A))
                .thenReturn(Optional.of(conversation));
        when(messageRepository.findDistinctSuccessfulLegacyToolNamesByConversationId(conversationId))
                .thenReturn(java.util.List.of("retired_private_tool"));
        when(toolRegistry.findRegistered("retired_private_tool")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getMessages(
                USER_A, conversationId, 1, 20, Set.of("BUYER")))
                .isInstanceOf(AiNotFoundException.class);
    }

    @Test
    void requireOwned_ownedConversation_returnsEntity() {
        UUID conversationId = UUID.randomUUID();
        Conversation conversation = new Conversation();
        conversation.setId(conversationId);
        conversation.setUserSub(USER_A);
        when(conversationRepository.findByIdAndUserSubAndDeletedAtIsNull(conversationId, USER_A))
                .thenReturn(Optional.of(conversation));

        Conversation result = service.requireOwned(USER_A, conversationId);

        assertThat(result.getId()).isEqualTo(conversationId);
    }

    @Test
    void delete_ownedConversation_softDeletesInsteadOfRemoving() {
        UUID conversationId = UUID.randomUUID();
        Conversation conversation = new Conversation();
        conversation.setId(conversationId);
        conversation.setUserSub(USER_A);
        when(conversationRepository.findByIdAndUserSubAndDeletedAtIsNull(conversationId, USER_A))
                .thenReturn(Optional.of(conversation));
        when(conversationRepository.save(any(Conversation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.delete(USER_A, conversationId);

        ArgumentCaptor<Conversation> captor = ArgumentCaptor.forClass(Conversation.class);
        verify(conversationRepository).save(captor.capture());
        assertThat(captor.getValue().getDeletedAt()).isNotNull();
    }

    @Test
    void delete_conversationOwnedByAnotherUser_throwsNotFoundAndNeverSaves() {
        UUID conversationId = UUID.randomUUID();
        when(conversationRepository.findByIdAndUserSubAndDeletedAtIsNull(conversationId, USER_B))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(USER_B, conversationId)).isInstanceOf(AiNotFoundException.class);

        verify(conversationRepository, never()).save(any());
    }

    @Test
    void list_rejectsOutOfRangePageSize() {
        assertThatThrownBy(() -> service.list(USER_A, 1, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.list(USER_A, 0, 20)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void list_capsVisibleHistoryAtFifteenSessions() {
        when(conversationRepository.findByUserSubAndDeletedAtIsNullOrderByUpdatedAtDesc(
                org.mockito.ArgumentMatchers.eq(USER_A), any(Pageable.class)))
                .thenReturn(new PageImpl<>(java.util.List.of()));

        PagedResponse<ConversationDto> response = service.list(USER_A, 1, 100);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(conversationRepository).findByUserSubAndDeletedAtIsNullOrderByUpdatedAtDesc(
                org.mockito.ArgumentMatchers.eq(USER_A), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(15);
        assertThat(response.getMeta().getPerPage()).isEqualTo(15);
    }
}
