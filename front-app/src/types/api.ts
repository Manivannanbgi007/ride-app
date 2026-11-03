/** A paginated list, as every service returns it (PageResponse). */
export interface PageResponse<T> {
  content: T[];
  /** Zero-based page number */
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}

/** The error body of every service (RFC 9457 ProblemDetail, with the services' extra fields). */
export interface ProblemDetail {
  type?: string;
  title?: string;
  status?: number;
  /** Written for people: shown to the user for 4xx answers */
  detail?: string;
  instance?: string;
  /** Invalid fields (400): field name -> message */
  errors?: Record<string, string>;
  requestId?: string;
  /** A machine-readable reason some answers carry (for example WRONG_ACCOUNT_TYPE on a sign-in) */
  code?: string;
}

/** Query-string values of a request; undefined and null values are left out of the URL. */
export type QueryParams = Record<string, string | number | boolean | undefined | null>;

/**
 * One admin action on a record (AuditLogResponse, the same in every service): who changed what, from which value
 * to which, and why.
 */
export interface AuditLogEntry {
  auditId: number;
  adminUserId: number;
  action: string;
  entityType: string;
  entityId: string;
  oldValue: string | null;
  newValue: string | null;
  reason: string | null;
  ipAddress: string | null;
  requestId: string | null;
  timestamp: string;
}
