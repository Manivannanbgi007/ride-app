import { AxiosError, AxiosHeaders, type InternalAxiosRequestConfig } from 'axios';
import { describe, expect, it } from 'vitest';

import { ApiError, getUserMessage, isRetryable, toApiError } from '@/api/apiErrorHandler';
import { ERROR_MESSAGES } from '@/utils/errorMessages';

const config = {
  method: 'get',
  url: '/api/v1/admin/orders',
  headers: new AxiosHeaders(),
} as InternalAxiosRequestConfig;

function answered(
  status: number,
  data?: unknown,
  headers: Record<string, string> = {},
): AxiosError {
  return new AxiosError(
    'Request failed',
    'ERR_BAD_RESPONSE',
    config,
    {},
    {
      status,
      statusText: '',
      data,
      headers,
      config,
    },
  );
}

describe('toApiError', () => {
  it('shows the business message of a 409 or 422 answer', () => {
    const conflict = toApiError(
      answered(409, { status: 409, detail: 'The coupon code SAVE10 exists already' }),
    );
    const invalid = toApiError(
      answered(422, { detail: 'Add items worth 200.00 more to use this coupon' }),
    );

    expect(conflict).toMatchObject({
      kind: 'conflict',
      status: 409,
      userMessage: 'The coupon code SAVE10 exists already',
    });
    expect(invalid).toMatchObject({
      kind: 'validation',
      userMessage: 'Add items worth 200.00 more to use this coupon',
    });
  });

  it('keeps the invalid fields of a 400 answer for the form', () => {
    const error = toApiError(
      answered(400, {
        detail: 'Invalid request content.',
        errors: { reason: 'must not be blank' },
      }),
    );

    expect(error.kind).toBe('badRequest');
    expect(error.fieldErrors).toEqual({ reason: 'must not be blank' });
  });

  it('never shows the server text of a server error, but keeps the request id', () => {
    const error = toApiError(
      answered(503, { detail: 'order-service is not available right now', requestId: 'req-1' }),
    );

    expect(error).toMatchObject({
      kind: 'unavailable',
      userMessage: ERROR_MESSAGES.SERVICE_UNAVAILABLE,
      requestId: 'req-1',
    });
    expect(error.message).toContain('GET /api/v1/admin/orders failed with 503');
  });

  it('reads the request id from the header when the body has none', () => {
    const error = toApiError(answered(500, undefined, { 'x-request-id': 'req-2' }));

    expect(error).toMatchObject({
      kind: 'server',
      userMessage: ERROR_MESSAGES.SERVER_ERROR,
      requestId: 'req-2',
    });
  });

  it.each([
    [401, 'unauthorized', ERROR_MESSAGES.UNAUTHORIZED],
    [403, 'forbidden', ERROR_MESSAGES.FORBIDDEN],
    [404, 'notFound', ERROR_MESSAGES.NOT_FOUND],
    [429, 'tooManyRequests', ERROR_MESSAGES.TOO_MANY_REQUESTS],
    [502, 'badGateway', ERROR_MESSAGES.BAD_GATEWAY],
    [504, 'gatewayTimeout', ERROR_MESSAGES.GATEWAY_TIMEOUT],
    [418, 'unknown', ERROR_MESSAGES.UNKNOWN],
  ])('maps %i to %s', (status, kind, message) => {
    expect(toApiError(answered(status))).toMatchObject({ kind, userMessage: message });
  });

  it('a 403 shows the access message, not the server text', () => {
    const error = toApiError(answered(403, { detail: 'You are not allowed to use this API.' }));

    expect(error.userMessage).toBe(ERROR_MESSAGES.FORBIDDEN);
  });

  it('no answer at all is a network error', () => {
    const error = toApiError(new AxiosError('Network Error', 'ERR_NETWORK', config));

    expect(error).toMatchObject({ kind: 'network', userMessage: ERROR_MESSAGES.NETWORK_ERROR });
  });

  it('a request that took too long is a timeout', () => {
    const error = toApiError(new AxiosError('timeout of 15000ms exceeded', 'ECONNABORTED', config));

    expect(error).toMatchObject({ kind: 'timeout', userMessage: ERROR_MESSAGES.TIMEOUT });
  });

  it('anything else is unknown, and an ApiError stays as it is', () => {
    const unknown = toApiError(new TypeError('x is undefined'));

    expect(unknown).toBeInstanceOf(ApiError);
    expect(unknown).toMatchObject({ kind: 'unknown', userMessage: ERROR_MESSAGES.UNKNOWN });
    expect(toApiError(unknown)).toBe(unknown);
    expect(getUserMessage('a string')).toBe(ERROR_MESSAGES.UNKNOWN);
  });
});

describe('isRetryable', () => {
  it('tries again only when the connection failed or a gateway timed out', () => {
    expect(isRetryable(new AxiosError('Network Error', 'ERR_NETWORK', config))).toBe(true);
    expect(isRetryable(answered(504))).toBe(true);
    expect(isRetryable(answered(503))).toBe(false);
    expect(isRetryable(answered(409))).toBe(false);
  });
});
