package com.fooddelivery.chatservice.dto.response;

import java.time.Instant;

/**
 * A conversation as one side sees it. {@code unreadCount} is the caller's own: for an admin the participant messages
 * no admin has read, for the participant the support messages he has not read. {@code pinned} and {@code muted}
 * are the calling admin's (always false for a participant).
 */
public record ConversationResponse(
        Long id,
        Participant participant,
        LastMessage lastMessage,
        int unreadCount,
        boolean pinned,
        boolean muted,
        Instant createdAt
) {

    /** {@code online} is only filled for admins, and null when presence is unknown. */
    public record Participant(Long userId, String name, String role, Boolean online) {
    }

    /** {@code read}: the other side has read it. */
    public record LastMessage(String preview, Instant at, boolean fromStaff, boolean read) {
    }
}
