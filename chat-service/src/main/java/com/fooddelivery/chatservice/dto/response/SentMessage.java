package com.fooddelivery.chatservice.dto.response;

/** The result of sending: {@code created} is false when the same clientMessageId had been saved before (HTTP 200). */
public record SentMessage(MessageResponse message, boolean created) {
}
