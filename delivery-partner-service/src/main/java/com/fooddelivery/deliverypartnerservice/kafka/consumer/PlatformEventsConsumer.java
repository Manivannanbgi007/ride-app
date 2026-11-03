package com.fooddelivery.deliverypartnerservice.kafka.consumer;

import com.fooddelivery.deliverypartnerservice.service.PartnerRegistrationService;
import com.fooddelivery.deliverypartnerservice.service.WorkStatusService;
import com.fooddelivery.deliverypartnerservice.web.RequestIdFilter;
import com.fooddelivery.events.Topics;
import com.fooddelivery.events.delivery.DeliveryPartnerStatusChanged;
import com.fooddelivery.events.user.UserRegistered;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * New partners (UserRegistered) and what they are doing (DeliveryPartnerStatusChanged from delivery-service).
 * Thin: each event is handed to a service. A failure (for example the database is down) is retried, then the
 * event goes to the dead-letter topic (see KafkaConfig).
 */
@Component
@RequiredArgsConstructor
public class PlatformEventsConsumer {

    private final PartnerRegistrationService partnerRegistrationService;
    private final WorkStatusService workStatusService;

    @KafkaListener(topics = Topics.USER_REGISTERED)
    public void onUserRegistered(UserRegistered event) {
        withRequestId(event.getRequestId(), () -> partnerRegistrationService.onUserRegistered(event));
    }

    @KafkaListener(topics = Topics.DELIVERY_PARTNER_STATUS_CHANGED)
    public void onPartnerStatusChanged(DeliveryPartnerStatusChanged event) {
        withRequestId(event.getRequestId(), () -> workStatusService.onPartnerStatusChanged(event));
    }

    /** Log lines of the event carry the request id of the request that started it. */
    private static void withRequestId(String requestId, Runnable work) {
        if (requestId != null) {
            MDC.put(RequestIdFilter.MDC_REQUEST_ID, requestId);
        }
        try {
            work.run();
        } finally {
            MDC.remove(RequestIdFilter.MDC_REQUEST_ID);
        }
    }
}
