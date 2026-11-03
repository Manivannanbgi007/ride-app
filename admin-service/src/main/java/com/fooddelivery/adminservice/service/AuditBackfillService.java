package com.fooddelivery.adminservice.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.fooddelivery.adminservice.client.PlatformClient;
import com.fooddelivery.adminservice.client.PlatformService;
import com.fooddelivery.adminservice.client.RemoteRecords.AuditReplay;
import com.fooddelivery.adminservice.dto.response.AuditBackfillResponse;
import com.fooddelivery.adminservice.service.ParallelCalls.Batch;
import com.fooddelivery.adminservice.service.ParallelCalls.Call;
import com.fooddelivery.adminservice.web.CallerInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Fills the platform-wide audit log with the records the services saved before it existed: every service sends
 * all its records again as events. Safe to repeat (each record is stored once).
 */
@Service
@RequiredArgsConstructor
public class AuditBackfillService {

    static final String ACTION = "AUDIT_LOG_BACKFILLED";
    @SuppressWarnings("java:S1075") // the path of the services' own internal API, not a setting of the environment
    static final String REPUBLISH_PATH = "/api/v1/internal/audit-logs/events/republish";

    private final PlatformClient platformClient;
    private final ParallelCalls parallelCalls;
    private final AuditLogService auditLogService;

    public AuditBackfillResponse backfill(CallerInfo admin, String reason) {
        Batch batch = parallelCalls.batch();
        List<Call<AuditReplay>> calls = new ArrayList<>();
        for (PlatformService service : PlatformService.values()) {
            calls.add(batch.start(service, () -> platformClient.postInternal(service, REPUBLISH_PATH, AuditReplay.class)));
        }
        Map<String, Long> republished = new TreeMap<>();
        for (Call<AuditReplay> call : calls) {
            AuditReplay replay = batch.await(call);
            if (replay != null) {
                republished.put(call.service().serviceName(), replay.republished());
            }
        }
        auditLogService.recordOwn(admin, ACTION, "AUDIT_LOG", "ALL", "republished " + republished
                + (batch.unavailable().isEmpty() ? "" : ", unavailable " + batch.unavailable()), reason);
        return new AuditBackfillResponse(republished, batch.unavailable());
    }
}
