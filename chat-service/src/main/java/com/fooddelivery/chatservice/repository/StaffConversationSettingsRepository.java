package com.fooddelivery.chatservice.repository;

import com.fooddelivery.chatservice.entity.StaffConversationSettings;
import com.fooddelivery.chatservice.entity.StaffConversationSettingsId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StaffConversationSettingsRepository
        extends JpaRepository<StaffConversationSettings, StaffConversationSettingsId> {
}
