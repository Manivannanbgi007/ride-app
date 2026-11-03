package com.fooddelivery.userservice.service;

import java.time.Clock;

import com.fooddelivery.userservice.dto.response.AuditLogResponse;
import com.fooddelivery.userservice.dto.response.PageResponse;
import com.fooddelivery.userservice.entity.AdminAuditLog;
import com.fooddelivery.userservice.entity.AuditAction;
import com.fooddelivery.userservice.kafka.producer.AdminAuditEventPublisher;
import com.fooddelivery.userservice.mapper.AuditLogMapper;
import com.fooddelivery.userservice.repository.AdminAuditLogRepository;
import com.fooddelivery.userservice.web.CallerInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminAuditService {

    public static final String ENTITY_TYPE_USER = "USER";

    private final AdminAuditLogRepository adminAuditLogRepository;
    private final AuditLogMapper auditLogMapper;
    private final AdminAuditEventPublisher adminAuditEventPublisher;
    private final Clock clock;

    /**
     * Records an admin action. MANDATORY propagation means this can only run inside the transaction
     * of the action itself: the change and its audit record are saved together, or not at all.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordAction(CallerInfo admin, AuditAction action, String entityType, Object entityId,
                             String oldValue, String newValue, String reason) {
        AdminAuditLog auditLog = new AdminAuditLog(
                admin.userId(),
                action,
                entityType,
                String.valueOf(entityId),
                oldValue,
                newValue,
                reason,
                admin.ipAddress(),
                admin.requestId(),
                clock.instant());

        AdminAuditLog saved = adminAuditLogRepository.save(auditLog);
        // Same transaction: the platform-wide audit log (admin-service) gets a copy through the outbox
        adminAuditEventPublisher.actionAudited(saved);
        log.info("Admin action audited. action={} entityType={} entityId={} adminUserId={}",
                action, entityType, entityId, admin.userId());
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditLogResponse> getAuditLogs(String entityType, Object entityId, Pageable pageable) {
        // Newest first, always: client-supplied sorting is not needed for an audit trail.
        Pageable newestFirst = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));

        return PageResponse.from(adminAuditLogRepository
                .findByEntityTypeAndEntityId(entityType, String.valueOf(entityId), newestFirst)
                .map(auditLogMapper::toResponse));
    }
}
