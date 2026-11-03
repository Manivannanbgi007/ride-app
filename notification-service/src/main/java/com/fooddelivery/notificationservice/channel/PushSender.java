package com.fooddelivery.notificationservice.channel;

import java.util.Optional;

import com.fooddelivery.notificationservice.client.UserServiceClient.UserContact;
import com.fooddelivery.notificationservice.entity.NotificationChannel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Mobile push. Simulated: logged. The notification is also the user's in-app inbox entry
 * (GET /api/v1/notifications), which the web app shows with an unread counter.
 */
@Slf4j
@Component
public class PushSender implements NotificationSender {

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.PUSH;
    }

    @Override
    public Optional<String> addressOf(UserContact contact) {
        return Optional.of("inbox:user-" + contact.id());
    }

    @Override
    public void send(OutgoingMessage message) {
        log.info("Push sent (simulated). to={} title=\"{}\"", message.address(), message.subject());
    }
}
