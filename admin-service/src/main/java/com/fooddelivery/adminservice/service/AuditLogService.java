package com.fooddelivery.adminservice.service;

import java.time.Clock;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import com.fooddelivery.adminservice.config.AppProperties;
import com.fooddelivery.adminservice.dto.request.AuditLogSearchCriteria;
import com.fooddelivery.adminservice.dto.response.AuditActionCount;
import com.fooddelivery.adminservice.dto.response.AuditRecordDetailsResponse;
import com.fooddelivery.adminservice.dto.response.AuditRecordResponse;
import com.fooddelivery.adminservice.dto.response.PageResponse;
import com.fooddelivery.adminservice.entity.AuditRecord;
import com.fooddelivery.adminservice.exception.ResourceNotFoundException;
import com.fooddelivery.adminservice.mapper.AuditRecordMapper;
import com.fooddelivery.adminservice.repository.AuditRecordRepository;
import com.fooddelivery.adminservice.repository.AuditRecordSpecifications;
import com.fooddelivery.adminservice.validation.SortValidator;
import com.fooddelivery.adminservice.web.CallerInfo;
import com.fooddelivery.events.admin.AdminActionAudited;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** The platform-wide audit log: stores the services' audit records once each, and searches them. */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogService {

    static final List<String> SORTABLE_FIELDS = List.of("auditedAt", "action", "sourceService", "adminUserId", "entityType");

    private final AuditRecordRepository auditRecordRepository;
    private final AuditRecordMapper auditRecordMapper;
    private final AppProperties appProperties;
    private final Clock clock;

    /**
     * Stores a service's audit record. Kafka may deliver an event twice, and services can replay their records:
     * a record already stored (same service and id) is skipped.
     *
     * @return false when it was stored before
     */
    @Transactional
    public boolean storeCopy(AdminActionAudited event) {
        if (auditRecordRepository.existsBySourceServiceAndSourceAuditId(event.getSourceService(), event.getAuditId())) {
            log.debug("Audit record already stored. service={} auditId={}", event.getSourceService(), event.getAuditId());
            return false;
        }
        try {
            auditRecordRepository.save(AuditRecord.copyOf(event, clock.instant()));
        } catch (DataIntegrityViolationException e) {
            // Stored at the same moment by another consumer (unique key on service + id)
            return false;
        }
        log.info("Audit record stored. service={} action={} entityType={} entityId={} adminUserId={}",
                event.getSourceService(), event.getAction(), event.getEntityType(), event.getEntityId(),
                event.getAdminUserId());
        return true;
    }

    /** An action done in admin-service itself (written straight into the log). */
    @Transactional
    public void recordOwn(CallerInfo admin, String action, String entityType, String entityId, String newValue,
                          String reason) {
        auditRecordRepository.save(AuditRecord.own(admin, action, entityType, entityId, newValue, reason, clock.instant()));
        log.info("Admin action audited. action={} adminUserId={}", action, admin.userId());
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditRecordResponse> search(AuditLogSearchCriteria criteria, Pageable pageable) {
        SortValidator.validate(pageable.getSort(), SORTABLE_FIELDS);
        // Newest first unless asked otherwise; the id keeps records of the same moment in a stable order
        Sort sort = (pageable.getSort().isSorted() ? pageable.getSort() : Sort.by(Sort.Direction.DESC, "auditedAt"))
                .and(Sort.by(Sort.Direction.DESC, "id"));
        Pageable page = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);
        return PageResponse.from(auditRecordRepository.search(toSpecification(criteria), page)
                .map(auditRecordMapper::toResponse));
    }

    @Transactional(readOnly = true)
    public AuditRecordDetailsResponse getRecord(Long auditId) {
        AuditRecord auditRecord = auditRecordRepository.findById(auditId)
                .orElseThrow(() -> new ResourceNotFoundException("Audit record not found: " + auditId));
        List<AuditRecordResponse> sameRequest = auditRecord.getRequestId() == null ? List.of()
                : auditRecordRepository.findTop20ByRequestIdAndIdNotOrderByAuditedAtAscIdAsc(auditRecord.getRequestId(),
                        auditRecord.getId()).stream().map(auditRecordMapper::toResponse).toList();
        return new AuditRecordDetailsResponse(auditRecordMapper.toResponse(auditRecord), auditRecord.getReceivedAt(),
                sameRequest);
    }

    /** Which actions the log has, per service (for filter lists). */
    @Transactional(readOnly = true)
    public List<AuditActionCount> getActions() {
        return auditRecordRepository.countPerServiceAndAction().stream()
                .map(row -> new AuditActionCount(row.getSourceService(), row.getAction(), row.getTotal()))
                .toList();
    }

    private Specification<AuditRecord> toSpecification(AuditLogSearchCriteria criteria) {
        List<Specification<AuditRecord>> conditions = new ArrayList<>();
        addEqual(conditions, "sourceService", criteria.service());
        addEqual(conditions, "adminUserId", criteria.adminUserId());
        addEqual(conditions, "action", criteria.action());
        addEqual(conditions, "entityType", criteria.entityType());
        addEqual(conditions, "entityId", criteria.entityId());
        addEqual(conditions, "requestId", criteria.requestId());
        BusinessDateRange range = BusinessDateRange.of(criteria.fromDate(), criteria.toDate(),
                ZoneId.of(appProperties.businessTimeZone()));
        if (range.from() != null) {
            conditions.add(AuditRecordSpecifications.auditedFrom(range.from()));
        }
        if (range.before() != null) {
            conditions.add(AuditRecordSpecifications.auditedBefore(range.before()));
        }
        if (StringUtils.hasText(criteria.q())) {
            conditions.add(AuditRecordSpecifications.mentions(criteria.q().trim()));
        }
        return Specification.allOf(conditions);
    }

    private static void addEqual(List<Specification<AuditRecord>> conditions, String field, Object value) {
        if (value instanceof String text) {
            if (StringUtils.hasText(text)) {
                conditions.add(AuditRecordSpecifications.hasValue(field, text.trim()));
            }
        } else if (value != null) {
            conditions.add(AuditRecordSpecifications.hasValue(field, value));
        }
    }
}
