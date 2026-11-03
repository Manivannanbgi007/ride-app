package com.fooddelivery.notificationservice.service;

import com.fooddelivery.notificationservice.config.AppProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Runs the dispatcher regularly (switched off in tests that run it themselves). */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.notifications.dispatch-enabled", havingValue = "true", matchIfMissing = true)
public class NotificationDispatchScheduler {

    private final NotificationDispatcher notificationDispatcher;
    private final AppProperties appProperties;

    @Scheduled(fixedDelayString = "${app.notifications.dispatch-interval:1s}")
    public void dispatch() {
        int taken;
        do {
            taken = notificationDispatcher.dispatchDue();
        } while (taken == appProperties.notifications().batchSize());
    }
}
