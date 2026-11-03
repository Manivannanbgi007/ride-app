package com.fooddelivery.chatservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import com.fooddelivery.chatservice.client.UserInfo;
import com.fooddelivery.chatservice.client.UserLookup;
import com.fooddelivery.chatservice.dto.request.SendMessageRequest;
import com.fooddelivery.chatservice.dto.response.MessagePageResponse;
import com.fooddelivery.chatservice.dto.response.MessageResponse;
import com.fooddelivery.chatservice.dto.response.SentMessage;
import com.fooddelivery.chatservice.entity.Conversation;
import com.fooddelivery.chatservice.exception.BusinessRuleException;
import com.fooddelivery.chatservice.exception.DependencyUnavailableException;
import com.fooddelivery.chatservice.exception.ResourceNotFoundException;
import com.fooddelivery.chatservice.mapper.ChatMapper;
import com.fooddelivery.chatservice.repository.ConversationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;

/** The participant's decisions with the repository, user-service and the writer replaced by mocks. */
class ParticipantChatServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-05T10:00:00Z");
    private static final SendMessageRequest REQUEST =
            new SendMessageRequest("Hello", "6f1c3a52-0f4b-4d52-9e56-3d6d3f0f0a11");

    private final ConversationRepository conversationRepository = mock(ConversationRepository.class);
    private final MessageReader messageReader = mock(MessageReader.class);
    private final ChatMessageWriter messageWriter = mock(ChatMessageWriter.class);
    private final UserLookup userLookup = mock(UserLookup.class);
    private ParticipantChatService service;

    @BeforeEach
    void setUp() {
        service = new ParticipantChatService(conversationRepository, messageReader, messageWriter, userLookup,
                new ChatMapper(), Clock.fixed(NOW, ZoneOffset.UTC), mock(PlatformTransactionManager.class));
        when(conversationRepository.save(any(Conversation.class))).thenAnswer(call -> call.getArgument(0));
        when(messageWriter.append(any(), any(), any())).thenReturn(new SentMessage(
                new MessageResponse(1L, 5L, REQUEST.clientMessageId(), 7L, "Asha", "CUSTOMER", false, "Hello", NOW, null),
                true));
    }

    @Test
    void theFirstMessageOpensTheConversationWithANameAndRoleCopiedFromUserService() {
        when(conversationRepository.findByParticipantUserId(7L)).thenReturn(Optional.empty());
        when(userLookup.findUser(7L)).thenReturn(Optional.of(new UserInfo(7L, "Asha Rao", "CUSTOMER")));

        SentMessage sent = service.send(7L, REQUEST);

        assertThat(sent.created()).isTrue();
        ArgumentCaptor<Conversation> saved = ArgumentCaptor.forClass(Conversation.class);
        verify(conversationRepository).save(saved.capture());
        assertThat(saved.getValue().getParticipantUserId()).isEqualTo(7L);
        assertThat(saved.getValue().getParticipantName()).isEqualTo("Asha Rao");
        assertThat(saved.getValue().getParticipantRole()).isEqualTo("CUSTOMER");
        assertThat(saved.getValue().getCreatedAt()).isEqualTo(NOW);
        ArgumentCaptor<ChatMessageWriter.Sender> sender = ArgumentCaptor.forClass(ChatMessageWriter.Sender.class);
        verify(messageWriter).append(any(Conversation.class), any(), sender.capture());
        assertThat(sender.getValue()).isEqualTo(new ChatMessageWriter.Sender(7L, "Asha Rao", "CUSTOMER", false));
    }

    @Test
    void anExistingConversationNeverCallsUserService() {
        Conversation existing = new Conversation(7L, "Asha Rao", "CUSTOMER", NOW);
        when(conversationRepository.findByParticipantUserId(7L)).thenReturn(Optional.of(existing));
        when(conversationRepository.lockByParticipantUserId(7L)).thenReturn(Optional.of(existing));

        service.send(7L, REQUEST);

        verifyNoInteractions(userLookup);
        verify(conversationRepository, never()).save(any());
    }

    @Test
    void whenUserServiceIsDownNothingIsSaved() {
        when(conversationRepository.findByParticipantUserId(7L)).thenReturn(Optional.empty());
        when(userLookup.findUser(7L)).thenThrow(new DependencyUnavailableException("user-service",
                new IllegalStateException("connection refused")));

        assertThatThrownBy(() -> service.send(7L, REQUEST)).isInstanceOf(DependencyUnavailableException.class);

        verify(conversationRepository, never()).save(any());
        verifyNoInteractions(messageWriter);
    }

    @Test
    void anUnknownUserCannotWrite() {
        when(conversationRepository.findByParticipantUserId(7L)).thenReturn(Optional.empty());
        when(userLookup.findUser(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.send(7L, REQUEST)).isInstanceOf(ResourceNotFoundException.class);

        verifyNoInteractions(messageWriter);
    }

    @Test
    void aPlatformAdminCannotOpenAConversationOfHisOwn() {
        when(conversationRepository.findByParticipantUserId(1L)).thenReturn(Optional.empty());
        when(userLookup.findUser(1L)).thenReturn(Optional.of(new UserInfo(1L, "Root", "PLATFORM_ADMIN")));

        assertThatThrownBy(() -> service.send(1L, REQUEST)).isInstanceOf(BusinessRuleException.class);

        verifyNoInteractions(messageWriter);
    }

    @Test
    void withoutAConversationTheMessagePageIsEmptyAndTheConversationIsNotFound() {
        when(conversationRepository.findByParticipantUserId(7L)).thenReturn(Optional.empty());

        MessagePageResponse page = service.messages(7L, null, 30);

        assertThat(page.messages()).isEmpty();
        assertThat(page.hasMore()).isFalse();
        assertThat(page.nextBeforeId()).isNull();
        assertThatThrownBy(() -> service.getConversation(7L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void readingWithoutAConversationDoesNothing() {
        when(conversationRepository.lockByParticipantUserId(7L)).thenReturn(Optional.empty());

        service.markRead(7L);

        verifyNoInteractions(messageWriter);
    }
}
