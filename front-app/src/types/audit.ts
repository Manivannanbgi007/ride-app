/**
 * admin-service: the platform-wide audit log, a copy of every audited admin action of every service.
 * Read-only: records can never be changed or deleted.
 */

/** One record (AuditRecordResponse). */
export interface AuditRecord {
  /** Id in the platform-wide log */
  auditId: number;
  /** The service that saved it ('restaurant-service', ...) */
  service: string;
  /** Its id in that service (null for admin-service's own records) */
  sourceAuditId: number | null;
  adminUserId: number;
  /** 'RESTAURANT_SUSPENDED', ... (text: every service has its own actions) */
  action: string;
  /** 'RESTAURANT', 'ORDER', ... */
  entityType: string;
  entityId: string;
  oldValue: string | null;
  newValue: string | null;
  reason: string | null;
  /** When the admin acted */
  timestamp: string;
  ipAddress: string | null;
  requestId: string | null;
}

/** A record with the others of the same admin request (an order cancelled with its refund, ...). */
export interface AuditRecordDetails {
  record: AuditRecord;
  /** When the copy reached the platform-wide log */
  receivedAt: string;
  /** Other records with the same requestId (at most 20), oldest first */
  sameRequest: AuditRecord[];
}

/** How many records one action of one service has (for the filter lists). */
export interface AuditActionCount {
  service: string;
  action: string;
  records: number;
}

/** The audit log's filters, all optional (dates: when the admin acted, 'YYYY-MM-DD', inclusive). */
export interface AuditLogFilters {
  service?: string;
  action?: string;
  entityType?: string;
  entityId?: string;
  adminUserId?: number;
  requestId?: string;
  /** Text in the reason, old value or new value */
  q?: string;
  fromDate?: string;
  toDate?: string;
  /** 'auditedAt,desc' (newest first), ... (AUDIT_LOG_SORT_OPTIONS) */
  sort?: string;
}

/** POST /audit-logs/backfill: how many records each service sent again, and which could not be asked. */
export interface AuditBackfillResult {
  republished: Record<string, number>;
  unavailable: string[];
}
