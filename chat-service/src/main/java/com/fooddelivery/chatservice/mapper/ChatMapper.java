package com.fooddelivery.chatservice.mapper;

import com.fooddelivery.chatservice.dto.response.ConversationResponse;
import com.fooddelivery.chatservice.dto.response.MessageResponse;
import com.fooddelivery.chatservice.entity.Conversation;
import com.fooddelivery.chatservice.entity.Message;
import org.springframework.stereotype.Component;

/** Entity to DTO. */
@Component
public class ChatMapper {

    /** The staff view: unread of the support team, the calling admin's pin and mute, and presence when known. */
    public ConversationResponse toStaffResponse(Conversation conversation, boolean pinned, boolean muted,
                                                Boolean online) {
        return toResponse(conversation, conversation.getUnreadForStaff(), pinned, muted, online);
    }

    /** The participant's view: his unread, no pin, no mute and no presence (he cannot see who is online). */
    public ConversationResponse toParticipantResponse(Conversation conversation) {
        return toResponse(conversation, conversation.getUnreadForParticipant(), false, false, null);
    }

    private ConversationResponse toResponse(Conversation conversation, int unread, boolean pinned, boolean muted,
                                            Boolean online) {
        ConversationResponse.LastMessage last = conversation.getLastMessageAt() == null
                ? null
                : new ConversationResponse.LastMessage(conversation.getLastMessagePreview(),
                        conversation.getLastMessageAt(), conversation.getLastMessageFromStaff(),
                        conversation.isLastMessageRead());
        return new ConversationResponse(
                conversation.getId(),
                new ConversationResponse.Participant(conversation.getParticipantUserId(),
                        conversation.getParticipantName(), conversation.getParticipantRole(), online),
                last,
                unread,
                pinned,
                muted,
                conversation.getCreatedAt());
    }

    public MessageResponse toResponse(Message message) {
        return new MessageResponse(
                message.getId(),
                message.getConversationId(),
                message.getClientMessageId(),
                message.getSenderUserId(),
                message.getSenderName(),
                message.getSenderRole(),
                message.isFromStaff(),
                message.getBody(),
                message.getCreatedAt(),
                message.getReadAt());
    }
}
