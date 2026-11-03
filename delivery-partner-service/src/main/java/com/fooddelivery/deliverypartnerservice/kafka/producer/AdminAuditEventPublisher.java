package com.fooddelivery.deliverypartnerservice.kafka.producer;

import java.util.UUID;

import com.fooddelivery.deliverypartnerservice.entity.AdminAuditLog;
import com.fooddelivery.events.Topics;
import com.fooddelivery.events.admin.AdminActionAudited;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Copies audit records to the platform-wide audit log (admin-service) as AdminActionAudited events, through the
 * transactional outbox: the action, its audit record and the event are saved together, or not at all.
 */
@Component
public class AdminAuditEventPublisher {

    private static final String AGGREGATE_AUDIT = "ADMIN_AUDIT";

    private final OutboxWriter outboxWriter;
    private final String serviceName;

    public AdminAuditEventPublisher(OutboxWriter outboxWriter, @Value("${spring.application.name}") String serviceName) {
        this.outboxWriter = outboxWriter;
        this.serviceName = serviceName;
    }

    /** Must run in a transaction (the outbox requires one). The record must be saved, so it has its id. */
    public void actionAudited(AdminAuditLog auditLog) {
        AdminActionAudited event = AdminActionAudited.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setOccurredAt(auditLog.getCreatedAt())
                .setRequestId(auditLog.getRequestId())
                .setSourceService(serviceName)
                .setAuditId(auditLog.getId())
                .setAdminUserId(auditLog.getAdminUserId())
                .setAction(auditLog.getAction().name())
                .setEntityType(auditLog.getEntityType())
                .setEntityId(auditLog.getEntityId())
                .setOldValue(auditLog.getOldValue())
                .setNewValue(auditLog.getNewValue())
                .setReason(auditLog.getReason())
                .setIpAddress(auditLog.getIpAddress())
                .build();
        outboxWriter.append(AGGREGATE_AUDIT, auditLog.getId(), Topics.ADMIN_ACTION_AUDITED,
                auditLog.getEntityType() + ":" + auditLog.getEntityId(), event);
    }
}
