package com.fooddelivery.deliveryservice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Runs the assignment job regularly (switched off in tests that run it themselves). */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.assignment.enabled", havingValue = "true", matchIfMissing = true)
public class DeliveryAssignmentScheduler {

    private final DeliveryAssignmentService deliveryAssignmentService;

    @Scheduled(fixedDelayString = "${app.assignment.interval:5s}")
    public void assign() {
        deliveryAssignmentService.runAssignmentRound();
    }
}
