package com.fooddelivery.chatservice.service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import com.fooddelivery.chatservice.client.UserInfo;
import com.fooddelivery.chatservice.client.UserLookup;
import com.fooddelivery.chatservice.dto.request.SendMessageRequest;
import com.fooddelivery.chatservice.dto.response.ConversationResponse;
import com.fooddelivery.chatservice.dto.response.MessagePageResponse;
import com.fooddelivery.chatservice.dto.response.SentMessage;
import com.fooddelivery.chatservice.entity.Conversation;
import com.fooddelivery.chatservice.exception.ConflictException;
import com.fooddelivery.chatservice.exception.ResourceNotFoundException;
import com.fooddelivery.chatservice.mapper.ChatMapper;
import com.fooddelivery.chatservice.repository.ConversationRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The participant's side (customer, restaurant owner, delivery partner). There is no conversation id anywhere in
 * this API: every method finds the caller's one conversation by his own user id, so he can only ever reach his own.
 * <p>
 * user-service is asked <em>before</em> the database transaction starts, and only for the very first message (the
 * name and role are then copied into the conversation), so no database connection waits on another service and
 * chatting keeps working when user-service is down.
 */
@Slf4j
@Service
public class ParticipantChatService {

    private final ConversationRepository conversationRepository;
    private final MessageReader messageReader;
    private final ChatMessageWriter messageWriter;
    private final UserLookup userLookup;
    private final ChatMapper chatMapper;
    private final Clock clock;
    private final TransactionTemplate writeTransaction;

    public ParticipantChatService(ConversationRepository conversationRepository, MessageReader messageReader,
                                  ChatMessageWriter messageWriter, UserLookup userLookup, ChatMapper chatMapper,
                                  Clock clock, PlatformTransactionManager transactionManager) {
        this.conversationRepository = conversationRepository;
        this.messageReader = messageReader;
        this.messageWriter = messageWriter;
        this.userLookup = userLookup;
        this.chatMapper = chatMapper;
        this.clock = clock;
        this.writeTransaction = new TransactionTemplate(transactionManager);
    }

    /** 404 when the caller never wrote (the UI then shows an empty chat). */
    public ConversationResponse getConversation(Long userId) {
        return conversationRepository.findByParticipantUserId(userId)
                .map(chatMapper::toParticipantResponse)
                .orElseThrow(() -> new ResourceNotFoundException("You have no conversation yet"));
    }

    /** An empty page (not 404) when the caller has no conversation yet. */
    public MessagePageResponse messages(Long userId, Long beforeId, int limit) {
        return conversationRepository.findByParticipantUserId(userId)
                .map(conversation -> messageReader.page(conversation.getId(), beforeId, limit, null))
                .orElseGet(() -> new MessagePageResponse(List.of(), false, null));
    }

    public SentMessage send(Long userId, SendMessageRequest request) {
        SentMessage sent;
        try {
            sent = sendOnce(userId, request);
        } catch (DataIntegrityViolationException e) {
            // Two first messages of the same user at once: the unique key on participant_user_id let one win.
            // Its conversation exists now, so a second pass finds it (and answers a repeated message as a repeat).
            sent = sendOnce(userId, request);
        }
        if (sent.created()) {
            log.info("Message {} saved in conversation {}", sent.message().id(), sent.message().conversationId());
        }
        return sent;
    }

    private SentMessage sendOnce(Long userId, SendMessageRequest request) {
        UserInfo newcomer = conversationRepository.findByParticipantUserId(userId).isPresent()
                ? null
                : lookupParticipant(userId);

        return writeTransaction.execute(status -> {
            // The lock also serializes this caller's own requests, so a retry waits for the original to finish.
            // A new conversation is inserted without locking: a lock on a row that does not exist yet locks a gap,
            // and two such locks deadlock. The unique key decides the race instead (see send).
            Conversation conversation = newcomer == null
                    ? conversationRepository.lockByParticipantUserId(userId)
                            .orElseThrow(() -> new ConflictException("The conversation changed; please try again"))
                    : open(newcomer);
            return messageWriter.append(conversation, request, new ChatMessageWriter.Sender(userId,
                    conversation.getParticipantName(), conversation.getParticipantRole(), false));
        });
    }

    /** The caller read the support team's messages (204; the support team sees the tick turn double). */
    public void markRead(Long userId) {
        writeTransaction.executeWithoutResult(status -> conversationRepository.lockByParticipantUserId(userId)
                .ifPresent(conversation -> messageWriter.markRead(conversation, false)));
    }

    private UserInfo lookupParticipant(Long userId) {
        UserInfo user = userLookup.findUser(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        ChatRules.checkCanBeParticipant(user);
        return user;
    }

    private Conversation open(UserInfo user) {
        Instant now = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
        return conversationRepository.save(new Conversation(user.id(), ChatRules.fitName(user.fullName()),
                user.role(), now));
    }
}
