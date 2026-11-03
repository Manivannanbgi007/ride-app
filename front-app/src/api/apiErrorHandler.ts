import axios from 'axios';

import type { ProblemDetail } from '@/types/api';
import { REQUEST_ID_HEADER } from '@/utils/apiEndpoints';
import { ERROR_MESSAGES } from '@/utils/errorMessages';

/** What went wrong, in the terms the screens care about. */
export type ApiErrorKind =
  | 'network'
  | 'timeout'
  | 'cancelled'
  | 'badRequest'
  | 'unauthorized'
  | 'forbidden'
  | 'notFound'
  | 'conflict'
  | 'validation'
  | 'tooManyRequests'
  | 'server'
  | 'badGateway'
  | 'unavailable'
  | 'gatewayTimeout'
  | 'unknown';

/** HTTP status -> kind, and the message shown when the server sent none that can be shown. */
const BY_STATUS: Record<number, { kind: ApiErrorKind; message: string }> = {
  400: { kind: 'badRequest', message: ERROR_MESSAGES.BAD_REQUEST },
  401: { kind: 'unauthorized', message: ERROR_MESSAGES.UNAUTHORIZED },
  403: { kind: 'forbidden', message: ERROR_MESSAGES.FORBIDDEN },
  404: { kind: 'notFound', message: ERROR_MESSAGES.NOT_FOUND },
  409: { kind: 'conflict', message: ERROR_MESSAGES.CONFLICT },
  422: { kind: 'validation', message: ERROR_MESSAGES.VALIDATION },
  429: { kind: 'tooManyRequests', message: ERROR_MESSAGES.TOO_MANY_REQUESTS },
  500: { kind: 'server', message: ERROR_MESSAGES.SERVER_ERROR },
  502: { kind: 'badGateway', message: ERROR_MESSAGES.BAD_GATEWAY },
  503: { kind: 'unavailable', message: ERROR_MESSAGES.SERVICE_UNAVAILABLE },
  504: { kind: 'gatewayTimeout', message: ERROR_MESSAGES.GATEWAY_TIMEOUT },
};

/**
 * For these answers the services write their `detail` for people (business rules such as "Add items worth
 * 200.00 more to use this coupon"), so it is shown. Server errors (5xx) never show the server's text.
 */
const SHOWS_SERVER_DETAIL: ReadonlySet<ApiErrorKind> = new Set([
  'badRequest',
  'notFound',
  'conflict',
  'validation',
]);

interface ApiErrorOptions {
  kind: ApiErrorKind;
  status?: number;
  userMessage: string;
  fieldErrors?: Record<string, string>;
  requestId?: string;
  /** The server's machine-readable reason, when it sent one */
  code?: string;
  technicalMessage: string;
  cause?: unknown;
}

/**
 * Every failed API call ends as an ApiError (see axiosInstance). `message` is the technical text for the log;
 * the screen shows `userMessage`.
 */
export class ApiError extends Error {
  readonly kind: ApiErrorKind;
  readonly status: number | undefined;
  /** Safe to show on the screen */
  readonly userMessage: string;
  /** Invalid fields from the server: field name -> message (forms show them next to the fields) */
  readonly fieldErrors: Record<string, string>;
  /** Quote this id when reporting a problem: it finds the request in every service's logs */
  readonly requestId: string | undefined;
  /** The server's machine-readable reason (ProblemDetail `code`), e.g. WRONG_ACCOUNT_TYPE */
  readonly code: string | undefined;

  constructor(options: ApiErrorOptions) {
    super(options.technicalMessage, { cause: options.cause });
    this.name = 'ApiError';
    this.kind = options.kind;
    this.status = options.status;
    this.userMessage = options.userMessage;
    this.fieldErrors = options.fieldErrors ?? {};
    this.requestId = options.requestId;
    this.code = options.code;
  }
}

/** Anything thrown while calling an API (Axios error, ApiError, or something unexpected) -> ApiError. */
export function toApiError(error: unknown): ApiError {
  if (error instanceof ApiError) {
    return error;
  }
  if (!axios.isAxiosError(error)) {
    return new ApiError({
      kind: 'unknown',
      userMessage: ERROR_MESSAGES.UNKNOWN,
      technicalMessage: error instanceof Error ? error.message : String(error),
      cause: error,
    });
  }

  const request = `${error.config?.method?.toUpperCase() ?? 'REQUEST'} ${error.config?.url ?? ''}`;
  if (axios.isCancel(error)) {
    return new ApiError({
      kind: 'cancelled',
      userMessage: ERROR_MESSAGES.UNKNOWN,
      technicalMessage: `${request} was cancelled`,
      cause: error,
    });
  }
  if (error.code === 'ECONNABORTED' || error.code === 'ETIMEDOUT') {
    return new ApiError({
      kind: 'timeout',
      userMessage: ERROR_MESSAGES.TIMEOUT,
      technicalMessage: `${request} timed out: ${error.message}`,
      cause: error,
    });
  }
  if (!error.response) {
    // No answer at all: server down, no network, or the browser blocked it (CORS)
    return new ApiError({
      kind: 'network',
      userMessage: ERROR_MESSAGES.NETWORK_ERROR,
      technicalMessage: `${request} got no answer: ${error.message}`,
      cause: error,
    });
  }

  const status = error.response.status;
  const data: unknown = error.response.data;
  const problem = isProblemDetail(data) ? data : undefined;
  const known = BY_STATUS[status] ?? {
    kind: status >= 500 ? 'server' : 'unknown',
    message: status >= 500 ? ERROR_MESSAGES.SERVER_ERROR : ERROR_MESSAGES.UNKNOWN,
  };
  const detail = problem?.detail?.trim();
  const headerRequestId: unknown = error.response.headers[REQUEST_ID_HEADER];

  return new ApiError({
    kind: known.kind,
    status,
    userMessage: detail && SHOWS_SERVER_DETAIL.has(known.kind) ? detail : known.message,
    fieldErrors: problem?.errors,
    requestId:
      problem?.requestId ?? (typeof headerRequestId === 'string' ? headerRequestId : undefined),
    code: typeof problem?.code === 'string' ? problem.code : undefined,
    technicalMessage: `${request} failed with ${status}: ${detail ?? error.message}`,
    cause: error,
  });
}

/** The message to show for any error (API or not). */
export function getUserMessage(error: unknown): string {
  return toApiError(error).userMessage;
}

/** Worth one more automatic try: the connection failed or timed out, or a gateway had a hiccup. */
export function isRetryable(error: unknown): boolean {
  const { kind } = toApiError(error);
  return (
    kind === 'network' || kind === 'timeout' || kind === 'badGateway' || kind === 'gatewayTimeout'
  );
}

function isProblemDetail(data: unknown): data is ProblemDetail {
  return typeof data === 'object' && data !== null && ('detail' in data || 'title' in data);
}
