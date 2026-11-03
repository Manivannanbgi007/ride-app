package com.fooddelivery.chatservice.dto.response;

/** The number of conversations with something no admin has read yet (not the number of messages). */
public record UnreadCountResponse(long count) {
}
