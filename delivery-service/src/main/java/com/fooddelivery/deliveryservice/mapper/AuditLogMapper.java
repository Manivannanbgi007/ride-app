package com.fooddelivery.deliveryservice.mapper;

import com.fooddelivery.deliveryservice.dto.response.AuditLogResponse;
import com.fooddelivery.deliveryservice.entity.AdminAuditLog;
import org.springframework.stereotype.Component;

@Component
public class AuditLogMapper {

    public AuditLogResponse toResponse(AdminAuditLog auditLog) {
        return new AuditLogResponse(
                auditLog.getId(),
                auditLog.getAdminUserId(),
                auditLog.getAction(),
                auditLog.getEntityType(),
                auditLog.getEntityId(),
                auditLog.getOldValue(),
                auditLog.getNewValue(),
                auditLog.getReason(),
                auditLog.getIpAddress(),
                auditLog.getRequestId(),
                auditLog.getCreatedAt());
    }
}
