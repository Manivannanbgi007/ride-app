package com.fooddelivery.notificationservice.channel;

import java.util.Optional;

import com.fooddelivery.notificationservice.client.UserServiceClient.UserContact;
import com.fooddelivery.notificationservice.config.AppProperties;
import com.fooddelivery.notificationservice.entity.NotificationChannel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** Plain-text email over SMTP (Mailpit in development: http://localhost:8025 shows every message). */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmailSender implements NotificationSender {

    private final JavaMailSender mailSender;
    private final AppProperties appProperties;

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.EMAIL;
    }

    @Override
    public Optional<String> addressOf(UserContact contact) {
        return Optional.ofNullable(contact.email()).filter(StringUtils::hasText);
    }

    @Override
    public void send(OutgoingMessage message) {
        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setFrom(appProperties.notifications().from());
        mail.setTo(message.address());
        mail.setSubject(message.subject());
        mail.setText(greeting(message) + message.body() + "\n\n-- The Food Delivery team");
        mailSender.send(mail);
        log.info("Email sent. to={} subject={}", message.address(), message.subject());
    }

    private static String greeting(OutgoingMessage message) {
        return StringUtils.hasText(message.recipientName()) ? "Hi " + message.recipientName() + ",\n\n" : "";
    }
}
