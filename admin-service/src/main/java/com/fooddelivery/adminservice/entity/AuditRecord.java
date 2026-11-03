package com.fooddelivery.adminservice.entity;

import java.time.Instant;

import com.fooddelivery.adminservice.web.CallerInfo;
import com.fooddelivery.events.admin.AdminActionAudited;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

/**
 * One entry of the platform-wide audit log. Immutable: Hibernate never writes changes to it, the repository has no
 * update or delete, and database triggers refuse both.
 */
@Entity
@Immutable
@Table(name = "audit_records")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditRecord {

    public static final String OWN_SERVICE = "admin-service";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_service", nullable = false, length = 50)
    private String sourceService;

    /** Id of the record in its service; null for admin-service's own records. */
    @Column(name = "source_audit_id")
    private Long sourceAuditId;

    @Column(name = "event_id", length = 36)
    private String eventId;

    @Column(name = "admin_user_id", nullable = false)
    private Long adminUserId;

    /** Text, not an enum: every service has its own actions, and new ones must not need a change here. */
    @Column(nullable = false, length = 60)
    private String action;

    @Column(name = "entity_type", nullable = false, length = 50)
    private String entityType;

    @Column(name = "entity_id", nullable = false, length = 64)
    private String entityId;

    @Column(name = "old_value", length = 1000)
    private String oldValue;

    @Column(name = "new_value", length = 1000)
    private String newValue;

    @Column(length = 500)
    private String reason;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "request_id", length = 64)
    private String requestId;

    @Column(name = "audited_at", nullable = false)
    private Instant auditedAt;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    /** A copy of a service's audit record. */
    public static AuditRecord copyOf(AdminActionAudited event, Instant receivedAt) {
        AuditRecord auditRecord = new AuditRecord();
        auditRecord.sourceService = event.getSourceService();
        auditRecord.sourceAuditId = event.getAuditId();
        auditRecord.eventId = event.getEventId();
        auditRecord.adminUserId = event.getAdminUserId();
        auditRecord.action = event.getAction();
        auditRecord.entityType = event.getEntityType();
        auditRecord.entityId = event.getEntityId();
        auditRecord.oldValue = event.getOldValue();
        auditRecord.newValue = event.getNewValue();
        auditRecord.reason = event.getReason();
        auditRecord.ipAddress = event.getIpAddress();
        auditRecord.requestId = event.getRequestId();
        auditRecord.auditedAt = event.getOccurredAt();
        auditRecord.receivedAt = receivedAt;
        return auditRecord;
    }

    /** An action done in admin-service itself. */
    public static AuditRecord own(CallerInfo admin, String action, String entityType, String entityId, String newValue,
                                  String reason, Instant now) {
        AuditRecord auditRecord = new AuditRecord();
        auditRecord.sourceService = OWN_SERVICE;
        auditRecord.adminUserId = admin.userId();
        auditRecord.action = action;
        auditRecord.entityType = entityType;
        auditRecord.entityId = entityId;
        auditRecord.newValue = newValue;
        auditRecord.reason = reason;
        auditRecord.ipAddress = admin.ipAddress();
        auditRecord.requestId = admin.requestId();
        auditRecord.auditedAt = now;
        auditRecord.receivedAt = now;
        return auditRecord;
    }
}
