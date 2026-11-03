package com.fooddelivery.chatservice.service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import com.fooddelivery.chatservice.config.AppProperties;
import com.fooddelivery.chatservice.dto.request.SendMessageRequest;
import com.fooddelivery.chatservice.dto.response.SentMessage;
import com.fooddelivery.chatservice.entity.Conversation;
import com.fooddelivery.chatservice.entity.Message;
import com.fooddelivery.chatservice.kafka.producer.ChatEventPublisher;
import com.fooddelivery.chatservice.mapper.ChatMapper;
import com.fooddelivery.chatservice.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * What changes a conversation, shared by the participant's and the support team's side: adding a message and marking
 * messages read. Both must run in the caller's transaction, with the conversation row locked
 * (ConversationRepository.lockById / lockByParticipantUserId); the event is written to the outbox in that same
 * transaction.
 */
@Component
@RequiredArgsConstructor
public class ChatMessageWriter {

    private final MessageRepository messageRepository;
    private final ChatEventPublisher eventPublisher;
    private final ChatMapper chatMapper;
    private final AppProperties appProperties;
    private final Clock clock;

    /** Who writes: a participant, or an admin writing for the support team. */
    public record Sender(Long userId, String name, String role, boolean fromStaff) {
    }

    /**
     * Saves the message, unless the same clientMessageId was saved before: then that message is returned and nothing
     * else happens (no second counter increment, no second event). The rate limit is checked only for a new message,
     * so retrying never fails because of it.
     */
    public SentMessage append(Conversation conversation, SendMessageRequest request, Sender sender) {
        Optional<Message> existing = messageRepository
                .findByConversationIdAndClientMessageId(conversation.getId(), request.clientMessageId());
        if (existing.isPresent()) {
            return new SentMessage(chatMapper.toResponse(existing.get()), false);
        }

        // Truncated to microseconds, what the database column keeps: the response shows exactly what is stored
        Instant now = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
        AppProperties.RateLimit rateLimit = appProperties.chat().rateLimit();
        ChatRules.checkRateLimit(
                messageRepository.countBySenderUserIdAndCreatedAtAfter(sender.userId(), now.minus(rateLimit.window())),
                rateLimit.maxMessages());

        Message message = messageRepository.save(new Message(conversation.getId(), request.clientMessageId(),
                sender.userId(), sender.name(), sender.role(), sender.fromStaff(), request.body(), now));
        conversation.recordMessage(sender.fromStaff(), ChatRules.preview(request.body()), now);
        eventPublisher.messageSent(conversation, message);
        return new SentMessage(chatMapper.toResponse(message), true);
    }

    /**
     * The staff (or the participant) read the messages of the other side. The event is written only when a message
     * really changed from unread to read, so opening a chat that is already read is silent.
     *
     * @return whether anything changed
     */
    public boolean markRead(Conversation conversation, boolean readByStaff) {
        Instant now = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
        // The side that reads is the other side of the messages it reads
        boolean messagesFromStaff = !readByStaff;
        int changed = messageRepository.markRead(conversation.getId(), messagesFromStaff, now);
        if (readByStaff) {
            conversation.markReadByStaff(now);
        } else {
            conversation.markReadByParticipant(now);
        }
        if (changed == 0) {
            return false;
        }
        long latestId = messageRepository.findLatestIdBySide(conversation.getId(), messagesFromStaff).orElseThrow();
        eventPublisher.messagesRead(conversation, readByStaff, latestId);
        return true;
    }
}
