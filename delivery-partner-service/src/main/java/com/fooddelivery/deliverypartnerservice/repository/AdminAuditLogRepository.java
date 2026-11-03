package com.fooddelivery.deliverypartnerservice.repository;

import java.util.List;

import com.fooddelivery.deliverypartnerservice.entity.AdminAuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.Repository;

/**
 * Audit records are append-only, so this repository deliberately extends the plain
 * {@link Repository} marker interface: it offers save and read methods, but no update or delete.
 */
public interface AdminAuditLogRepository extends Repository<AdminAuditLog, Long> {

    AdminAuditLog save(AdminAuditLog auditLog);

    Page<AdminAuditLog> findByEntityTypeAndEntityId(String entityType, String entityId, Pageable pageable);

    /** Records after the given id, oldest first (replaying them to the platform-wide audit log). */
    List<AdminAuditLog> findByIdGreaterThanOrderByIdAsc(Long afterId, Pageable pageable);
}
