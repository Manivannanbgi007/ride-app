package com.fooddelivery.adminservice.dto.request;

import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

/** Audit log filters (all optional, combined with AND). Dates are calendar days in the business time zone. */
public record AuditLogSearchCriteria(

        @Schema(description = "Service that saved the record", example = "restaurant-service")
        @Size(max = 50) String service,

        @Schema(description = "Admin who acted", example = "1")
        Long adminUserId,

        @Schema(example = "RESTAURANT_SUSPENDED")
        @Size(max = 60) String action,

        @Schema(example = "RESTAURANT")
        @Size(max = 50) String entityType,

        @Schema(description = "Id of the entity (use with entityType)", example = "501")
        @Size(max = 64) String entityId,

        @Schema(description = "X-Request-Id of the admin's request", example = "abc-123")
        @Size(max = 64) String requestId,

        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,

        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,

        @Schema(description = "Text in the reason, old value or new value", example = "policy")
        @Size(max = 100) String q
) {
}
