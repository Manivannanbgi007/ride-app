package com.fooddelivery.chatservice.dto.response;

import java.util.List;

/**
 * The latest messages before a point, oldest first. {@code nextBeforeId} is what to pass as {@code beforeId} to get
 * the page before this one (null when there is none).
 */
public record MessagePageResponse(List<MessageResponse> messages, boolean hasMore, Long nextBeforeId) {
}
