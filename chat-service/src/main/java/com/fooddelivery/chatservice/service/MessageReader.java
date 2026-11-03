package com.fooddelivery.chatservice.service;

import java.util.List;

import com.fooddelivery.chatservice.dto.response.MessagePageResponse;
import com.fooddelivery.chatservice.entity.Message;
import com.fooddelivery.chatservice.mapper.ChatMapper;
import com.fooddelivery.chatservice.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

/**
 * Reads a page of a conversation, for both sides. Paging goes by message id ("older than this one"), not by page
 * number: a message that arrives while the reader scrolls back can never shift a page, so there are no duplicates
 * and no gaps.
 */
@Component
@RequiredArgsConstructor
public class MessageReader {

    private final MessageRepository messageRepository;
    private final ChatMapper chatMapper;

    /**
     * @param beforeId only messages with a smaller id; null means from the newest
     * @param limit    page size, already checked (1 to 100)
     * @param search   optional text the message must contain (case-insensitive); null or blank means every message
     */
    public MessagePageResponse page(Long conversationId, Long beforeId, int limit, String search) {
        long before = beforeId == null ? Long.MAX_VALUE : beforeId;
        // One row more than asked tells whether an older page exists, without a count query
        List<Message> newestFirst = messageRepository.findPage(conversationId,
                before, ChatRules.containsPattern(search),
                PageRequest.of(0, limit + 1));
        boolean hasMore = newestFirst.size() > limit;
        List<Message> shown = hasMore ? newestFirst.subList(0, limit) : newestFirst;
        // The oldest message shown is where the next, older page starts
        Long nextBeforeId = hasMore ? shown.getLast().getId() : null;
        return new MessagePageResponse(shown.reversed().stream().map(chatMapper::toResponse).toList(),
                hasMore, nextBeforeId);
    }
}
