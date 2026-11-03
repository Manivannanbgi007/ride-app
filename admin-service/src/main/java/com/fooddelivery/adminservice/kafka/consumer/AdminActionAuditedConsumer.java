package com.fooddelivery.adminservice.kafka.consumer;

import com.fooddelivery.adminservice.service.AuditLogService;
import com.fooddelivery.adminservice.web.RequestIdFilter;
import com.fooddelivery.events.Topics;
import com.fooddelivery.events.admin.AdminActionAudited;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Copies of the services' audit records, into the platform-wide audit log. */
@Component
@RequiredArgsConstructor
public class AdminActionAuditedConsumer {

    private final AuditLogService auditLogService;

    @KafkaListener(topics = Topics.ADMIN_ACTION_AUDITED)
    public void onActionAudited(AdminActionAudited event) {
        // Log lines carry the request id of the admin's request
        if (event.getRequestId() != null) {
            MDC.put(RequestIdFilter.MDC_REQUEST_ID, event.getRequestId());
        }
        try {
            auditLogService.storeCopy(event);
        } finally {
            MDC.remove(RequestIdFilter.MDC_REQUEST_ID);
        }
    }
}
