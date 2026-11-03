package com.fooddelivery.adminservice.repository;

import java.time.Instant;
import java.util.Locale;

import com.fooddelivery.adminservice.entity.AuditRecord;
import org.springframework.data.jpa.domain.Specification;

/** Filters for the audit log search; combined with AND. */
public final class AuditRecordSpecifications {

    private AuditRecordSpecifications() {
    }

    public static Specification<AuditRecord> hasValue(String field, Object value) {
        return (root, query, builder) -> builder.equal(root.get(field), value);
    }

    public static Specification<AuditRecord> auditedFrom(Instant from) {
        return (root, query, builder) -> builder.greaterThanOrEqualTo(root.get("auditedAt"), from);
    }

    public static Specification<AuditRecord> auditedBefore(Instant before) {
        return (root, query, builder) -> builder.lessThan(root.get("auditedAt"), before);
    }

    /** Text in the reason, the old value or the new value (not case-sensitive). */
    public static Specification<AuditRecord> mentions(String text) {
        String pattern = "%" + text.toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        return (root, query, builder) -> builder.or(
                builder.like(builder.lower(root.get("reason")), pattern, '\\'),
                builder.like(builder.lower(root.get("oldValue")), pattern, '\\'),
                builder.like(builder.lower(root.get("newValue")), pattern, '\\'));
    }
}
