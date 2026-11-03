package com.fooddelivery.chatservice.repository;

import com.fooddelivery.chatservice.entity.Conversation;

/** A conversation with the pin and mute of the admin who asks (false when the admin never set them). */
public record ConversationRow(Conversation conversation, boolean pinned, boolean muted) {
}
