package com.fooddelivery.notificationservice.channel;

import java.util.Optional;

import com.fooddelivery.notificationservice.client.UserServiceClient.UserContact;
import com.fooddelivery.notificationservice.entity.NotificationChannel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Text messages. Simulated: the message is logged (with a masked phone number). A real SMS gateway
 * would be called here; nothing else in the service would change.
 */
@Slf4j
@Component
public class SmsSender implements NotificationSender {

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.SMS;
    }

    @Override
    public Optional<String> addressOf(UserContact contact) {
        return Optional.ofNullable(contact.phone()).filter(StringUtils::hasText);
    }

    @Override
    public void send(OutgoingMessage message) {
        log.info("SMS sent (simulated). to={} text=\"{}: {}\"", mask(message.address()), message.subject(), message.body());
    }

    /** +919876543210 -> +91******3210 */
    static String mask(String phone) {
        if (phone.length() <= 7) {
            return "****";
        }
        return phone.substring(0, 3) + "*".repeat(phone.length() - 7) + phone.substring(phone.length() - 4);
    }
}
