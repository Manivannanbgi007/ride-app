package com.fooddelivery.adminservice.repository;

import com.fooddelivery.adminservice.entity.AuditRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

/** Read-only search over audit records (a repository fragment, implemented by {@link AuditRecordSearchImpl}). */
public interface AuditRecordSearch {

    Page<AuditRecord> search(Specification<AuditRecord> specification, Pageable pageable);
}
