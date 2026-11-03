package com.fooddelivery.chatservice.entity;

import java.io.Serializable;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Key of {@link StaffConversationSettings}: one row per admin and conversation. */
@Embeddable
@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class StaffConversationSettingsId implements Serializable {

    @Column(name = "staff_user_id", nullable = false)
    private Long staffUserId;

    @Column(name = "conversation_id", nullable = false)
    private Long conversationId;
}
