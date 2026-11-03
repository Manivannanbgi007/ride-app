package com.fooddelivery.adminservice.repository;

import java.util.List;
import java.util.Optional;

import com.fooddelivery.adminservice.entity.AuditRecord;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

/**
 * Audit records can be added and read, never changed or deleted: this repository extends the plain
 * {@link Repository} marker (no inherited save-all, update or delete methods), and searches go through
 * {@link AuditRecordSearch} instead of JpaSpecificationExecutor, which would bring a delete method.
 */
public interface AuditRecordRepository extends Repository<AuditRecord, Long>, AuditRecordSearch {

    AuditRecord save(AuditRecord auditRecord);

    Optional<AuditRecord> findById(Long id);

    boolean existsBySourceServiceAndSourceAuditId(String sourceService, Long sourceAuditId);

    /** Other records written by the same request (one admin request can change several things). */
    List<AuditRecord> findTop20ByRequestIdAndIdNotOrderByAuditedAtAscIdAsc(String requestId, Long id);

    @Query("""
            select a.sourceService as sourceService, a.action as action, count(a) as total
            from AuditRecord a
            group by a.sourceService, a.action
            order by a.sourceService, a.action
            """)
    List<ActionTotal> countPerServiceAndAction();

    interface ActionTotal {
        String getSourceService();

        String getAction();

        long getTotal();
    }
}
