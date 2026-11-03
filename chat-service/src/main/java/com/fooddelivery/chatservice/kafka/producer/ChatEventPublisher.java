package com.fooddelivery.chatservice.kafka.producer;

import java.time.Clock;
import java.util.UUID;

import com.fooddelivery.chatservice.entity.Conversation;
import com.fooddelivery.chatservice.entity.Message;
import com.fooddelivery.chatservice.web.RequestIdFilter;
import com.fooddelivery.events.Topics;
import com.fooddelivery.events.chat.ChatMessageSent;
import com.fooddelivery.events.chat.ChatMessagesRead;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/**
 * Publishes the two chat hints through the transactional outbox: saved with the change that caused them, or not at
 * all. They carry ids only; notification-service turns them into live updates and the browser re-reads over REST.
 * The message key is the conversation id, so the events of one conversation stay in order.
 */
@Component
@RequiredArgsConstructor
public class ChatEventPublisher {

    private static final String AGGREGATE_CONVERSATION = "CONVERSATION";

    private final OutboxWriter outboxWriter;
    private final Clock clock;

    /** Must run in a transaction (the outbox requires one). The message must be saved, so it has its id. */
    public void messageSent(Conversation conversation, Message message) {
        ChatMessageSent event = ChatMessageSent.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setOccurredAt(clock.instant())
                .setRequestId(MDC.get(RequestIdFilter.MDC_REQUEST_ID))
                .setConversationId(conversation.getId())
                .setMessageId(message.getId())
                .setParticipantUserId(conversation.getParticipantUserId())
                .setSenderUserId(message.getSenderUserId())
                .setFromStaff(message.isFromStaff())
                .build();
        outboxWriter.append(AGGREGATE_CONVERSATION, conversation.getId(), Topics.CHAT_MESSAGE_SENT,
                String.valueOf(conversation.getId()), event);
    }

    /** Must run in a transaction (the outbox requires one). */
    public void messagesRead(Conversation conversation, boolean readByStaff, long upToMessageId) {
        ChatMessagesRead event = ChatMessagesRead.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setOccurredAt(clock.instant())
                .setRequestId(MDC.get(RequestIdFilter.MDC_REQUEST_ID))
                .setConversationId(conversation.getId())
                .setParticipantUserId(conversation.getParticipantUserId())
                .setReadByStaff(readByStaff)
                .setUpToMessageId(upToMessageId)
                .build();
        outboxWriter.append(AGGREGATE_CONVERSATION, conversation.getId(), Topics.CHAT_MESSAGES_READ,
                String.valueOf(conversation.getId()), event);
    }
}
