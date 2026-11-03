package com.fooddelivery.chatservice.dto.response;

/** The result of an admin opening a conversation: {@code created} is false when it already existed (HTTP 200). */
public record OpenedConversation(ConversationResponse conversation, boolean created) {
}
