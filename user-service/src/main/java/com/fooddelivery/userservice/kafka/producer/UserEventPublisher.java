package com.fooddelivery.userservice.kafka.producer;

import java.time.Clock;
import java.util.UUID;

import com.fooddelivery.events.Topics;
import com.fooddelivery.events.user.UserRegistered;
import com.fooddelivery.userservice.entity.User;
import com.fooddelivery.userservice.web.RequestIdFilter;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/**
 * Builds the user events and writes them to the transactional outbox, in the caller's transaction;
 * {@link OutboxRelay} publishes them to Kafka.
 */
@Component
@RequiredArgsConstructor
public class UserEventPublisher {

    private static final String AGGREGATE_USER = "USER";

    private final OutboxWriter outboxWriter;
    private final Clock clock;

    public void userRegistered(User user) {
        UserRegistered event = UserRegistered.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setOccurredAt(clock.instant())
                .setRequestId(MDC.get(RequestIdFilter.MDC_REQUEST_ID))
                .setUserId(user.getId())
                .setFullName(user.getFullName())
                .setEmail(user.getEmail())
                .setPhone(user.getPhone())
                .setRole(user.getRole().name())
                .build();

        outboxWriter.append(AGGREGATE_USER, user.getId(), Topics.USER_REGISTERED, String.valueOf(user.getId()), event);
    }
}
