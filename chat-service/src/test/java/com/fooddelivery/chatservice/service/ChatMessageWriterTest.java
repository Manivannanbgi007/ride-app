package com.fooddelivery.chatservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import com.fooddelivery.chatservice.config.AppProperties;
import com.fooddelivery.chatservice.dto.request.SendMessageRequest;
import com.fooddelivery.chatservice.dto.response.SentMessage;
import com.fooddelivery.chatservice.entity.Conversation;
import com.fooddelivery.chatservice.entity.Message;
import com.fooddelivery.chatservice.exception.TooManyRequestsException;
import com.fooddelivery.chatservice.kafka.producer.ChatEventPublisher;
import com.fooddelivery.chatservice.mapper.ChatMapper;
import com.fooddelivery.chatservice.repository.MessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** The rules of adding a message and of reading, with the repository and the outbox replaced by mocks. */
class ChatMessageWriterTest {

    private static final Instant NOW = Instant.parse("2026-10-05T10:00:00Z");
    private static final String CLIENT_ID = "6f1c3a52-0f4b-4d52-9e56-3d6d3f0f0a11";
    private static final ChatMessageWriter.Sender PARTICIPANT =
            new ChatMessageWriter.Sender(7L, "Asha", "CUSTOMER", false);
    private static final ChatMessageWriter.Sender STAFF =
            new ChatMessageWriter.Sender(1L, "Ravi", "PLATFORM_ADMIN", true);

    private final MessageRepository messageRepository = mock(MessageRepository.class);
    private final ChatEventPublisher eventPublisher = mock(ChatEventPublisher.class);
    private ChatMessageWriter writer;
    private Conversation conversation;

    @BeforeEach
    void setUp() {
        AppProperties.RemoteService remote = new AppProperties.RemoteService("http://x", Duration.ofSeconds(1),
                Duration.ofSeconds(1));
        AppProperties properties = new AppProperties(remote, remote,
                new AppProperties.Chat(new AppProperties.RateLimit(3, Duration.ofSeconds(60))));
        writer = new ChatMessageWriter(messageRepository, eventPublisher, new ChatMapper(), properties,
                Clock.fixed(NOW, ZoneOffset.UTC));
        conversation = new Conversation(7L, "Asha", "CUSTOMER", NOW.minusSeconds(600));
        ReflectionTestUtils.setField(conversation, "id", 5L);
        when(messageRepository.save(any(Message.class))).thenAnswer(call -> call.getArgument(0));
    }

    private static SendMessageRequest request(String body) {
        return new SendMessageRequest(body, CLIENT_ID);
    }

    @Test
    void aParticipantMessageIsSavedCountedForStaffAndAnnounced() {
        SentMessage sent = writer.append(conversation, request("  Where is my order?  "), PARTICIPANT);

        assertThat(sent.created()).isTrue();
        assertThat(sent.message().body()).isEqualTo("Where is my order?");
        assertThat(sent.message().fromStaff()).isFalse();
        assertThat(sent.message().senderName()).isEqualTo("Asha");
        assertThat(sent.message().createdAt()).isEqualTo(NOW);
        assertThat(sent.message().readAt()).isNull();
        assertThat(conversation.getUnreadForStaff()).isEqualTo(1);
        assertThat(conversation.getUnreadForParticipant()).isZero();
        assertThat(conversation.getLastMessagePreview()).isEqualTo("Where is my order?");
        assertThat(conversation.getLastMessageFromStaff()).isFalse();
        assertThat(conversation.getLastMessageAt()).isEqualTo(NOW);
        verify(eventPublisher).messageSent(eq(conversation), any(Message.class));
    }

    @Test
    void aStaffMessageIsCountedForTheParticipant() {
        writer.append(conversation, request("On its way"), STAFF);
        writer.append(conversation, new SendMessageRequest("Sorry for the wait", "6f1c3a52-0f4b-4d52-9e56-3d6d3f0f0a12"),
                STAFF);

        assertThat(conversation.getUnreadForParticipant()).isEqualTo(2);
        assertThat(conversation.getUnreadForStaff()).isZero();
        assertThat(conversation.getLastMessageFromStaff()).isTrue();
        assertThat(conversation.isLastMessageRead()).isFalse();
    }

    @Test
    void theSameClientMessageIdReturnsTheSavedMessageAndChangesNothing() {
        Message saved = new Message(5L, CLIENT_ID, 7L, "Asha", "CUSTOMER", false, "Hello", NOW.minusSeconds(5));
        when(messageRepository.findByConversationIdAndClientMessageId(5L, CLIENT_ID)).thenReturn(Optional.of(saved));
        // Even at the rate limit: a retry must not fail because of it
        when(messageRepository.countBySenderUserIdAndCreatedAtAfter(anyLong(), any())).thenReturn(99L);

        SentMessage sent = writer.append(conversation, request("Hello"), PARTICIPANT);

        assertThat(sent.created()).isFalse();
        assertThat(sent.message().body()).isEqualTo("Hello");
        assertThat(conversation.getUnreadForStaff()).isZero();
        verify(messageRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void aSenderAtTheRateLimitIsRefusedAndNothingIsSaved() {
        when(messageRepository.countBySenderUserIdAndCreatedAtAfter(7L, NOW.minusSeconds(60))).thenReturn(3L);

        assertThatThrownBy(() -> writer.append(conversation, request("again"), PARTICIPANT))
                .isInstanceOf(TooManyRequestsException.class);

        verify(messageRepository, never()).save(any());
        assertThat(conversation.getUnreadForStaff()).isZero();
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void aSenderBelowTheRateLimitIsAccepted() {
        when(messageRepository.countBySenderUserIdAndCreatedAtAfter(7L, NOW.minusSeconds(60))).thenReturn(2L);

        assertThat(writer.append(conversation, request("ok"), PARTICIPANT).created()).isTrue();
    }

    @Test
    void readingClearsTheCounterAndAnnouncesUpToTheLatestMessage() {
        conversation.recordMessage(false, "hi", NOW.minusSeconds(60));
        conversation.recordMessage(false, "hello?", NOW.minusSeconds(30));
        when(messageRepository.markRead(5L, false, NOW)).thenReturn(2);
        when(messageRepository.findLatestIdBySide(5L, false)).thenReturn(Optional.of(42L));

        boolean changed = writer.markRead(conversation, true);

        assertThat(changed).isTrue();
        assertThat(conversation.getUnreadForStaff()).isZero();
        assertThat(conversation.isLastMessageRead()).isTrue();
        verify(eventPublisher).messagesRead(conversation, true, 42L);
    }

    @Test
    void theParticipantReadsTheMessagesOfStaff() {
        conversation.recordMessage(true, "on its way", NOW.minusSeconds(30));
        when(messageRepository.markRead(5L, true, NOW)).thenReturn(1);
        when(messageRepository.findLatestIdBySide(5L, true)).thenReturn(Optional.of(43L));

        writer.markRead(conversation, false);

        assertThat(conversation.getUnreadForParticipant()).isZero();
        verify(eventPublisher).messagesRead(conversation, false, 43L);
    }

    @Test
    void readingWhenNothingIsUnreadWritesNoEvent() {
        when(messageRepository.markRead(anyLong(), anyBoolean(), any())).thenReturn(0);

        assertThat(writer.markRead(conversation, true)).isFalse();
        assertThat(writer.markRead(conversation, false)).isFalse();

        verifyNoInteractions(eventPublisher);
    }
}
