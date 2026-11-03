package com.fooddelivery.notificationservice.channel;

import java.util.Optional;

import com.fooddelivery.notificationservice.client.UserServiceClient.UserContact;
import com.fooddelivery.notificationservice.entity.NotificationChannel;

/**
 * Strategy: one implementation per channel. Adding a channel (for example WhatsApp) means one new class;
 * {@link NotificationSenderFactory} picks it up automatically.
 */
public interface NotificationSender {

    NotificationChannel channel();

    /** Where this channel reaches the user; empty if the user has no such address (e.g. no phone number). */
    Optional<String> addressOf(UserContact contact);

    /** Delivers the message, or throws if the channel failed (the dispatcher retries later). */
    void send(OutgoingMessage message);
}
