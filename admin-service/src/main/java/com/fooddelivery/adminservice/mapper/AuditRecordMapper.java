package com.fooddelivery.adminservice.mapper;

import com.fooddelivery.adminservice.dto.response.AuditRecordResponse;
import com.fooddelivery.adminservice.entity.AuditRecord;
import org.springframework.stereotype.Component;

@Component
public class AuditRecordMapper {

    public AuditRecordResponse toResponse(AuditRecord auditRecord) {
        return new AuditRecordResponse(auditRecord.getId(), auditRecord.getSourceService(), auditRecord.getSourceAuditId(),
                auditRecord.getAdminUserId(), auditRecord.getAction(), auditRecord.getEntityType(), auditRecord.getEntityId(),
                auditRecord.getOldValue(), auditRecord.getNewValue(), auditRecord.getReason(), auditRecord.getAuditedAt(),
                auditRecord.getIpAddress(), auditRecord.getRequestId());
    }
}
