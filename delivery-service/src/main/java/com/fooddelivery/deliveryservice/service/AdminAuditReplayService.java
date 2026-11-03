package com.fooddelivery.deliveryservice.service;

import java.util.List;

import com.fooddelivery.deliveryservice.entity.AdminAuditLog;
import com.fooddelivery.deliveryservice.kafka.producer.AdminAuditEventPublisher;
import com.fooddelivery.deliveryservice.repository.AdminAuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Sends every audit record of this service again as an AdminActionAudited event, oldest first, so the
 * platform-wide audit log also has the records made before it existed. admin-service stores each record once,
 * so replaying is safe. Changes no data here.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminAuditReplayService {

    private static final int BATCH_SIZE = 200;

    private final AdminAuditLogRepository adminAuditLogRepository;
    private final AdminAuditEventPublisher adminAuditEventPublisher;
    private final TransactionTemplate transactionTemplate;

    /** @return how many records were sent */
    public long republishAll() {
        long afterId = 0;
        long total = 0;
        while (true) {
            long from = afterId;
            // One short transaction per batch: the outbox rows of a batch are saved together
            List<AdminAuditLog> batch = transactionTemplate.execute(transaction -> {
                List<AdminAuditLog> auditLogs = adminAuditLogRepository.findByIdGreaterThanOrderByIdAsc(from,
                        PageRequest.ofSize(BATCH_SIZE));
                auditLogs.forEach(adminAuditEventPublisher::actionAudited);
                return auditLogs;
            });
            if (batch == null || batch.isEmpty()) {
                break;
            }
            total += batch.size();
            afterId = batch.getLast().getId();
        }
        log.info("Audit records replayed to the platform-wide audit log. count={}", total);
        return total;
    }
}
