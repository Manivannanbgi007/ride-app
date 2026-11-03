import { apiClient } from '@/api/apiClient';
import type { PageResponse } from '@/types/api';
import type {
  AuditActionCount,
  AuditBackfillResult,
  AuditLogFilters,
  AuditRecord,
  AuditRecordDetails,
} from '@/types/audit';
import { API_ENDPOINTS } from '@/utils/apiEndpoints';

/** admin-service: the platform-wide audit log (read-only). */
export const auditLogService = {
  searchAuditLogs: (filters: AuditLogFilters, page: number, size: number) =>
    apiClient.get<PageResponse<AuditRecord>>(API_ENDPOINTS.admin.auditLogs.list, {
      params: { ...filters, page, size },
    }),

  /** Which actions the log has, per service, with counts (the filter lists). */
  getActions: () => apiClient.get<AuditActionCount[]>(API_ENDPOINTS.admin.auditLogs.actions),

  getAuditRecord: (auditId: number) =>
    apiClient.get<AuditRecordDetails>(API_ENDPOINTS.admin.auditLogs.details(auditId)),

  /**
   * Asks every service to send its audit records again (the ones made before the platform-wide log existed).
   * Safe to repeat: copies already in the log are skipped. Audited itself.
   */
  backfill: (reason: string) =>
    apiClient.post<AuditBackfillResult>(API_ENDPOINTS.admin.auditLogs.backfill, { reason }),
};
