import { AxiosError, type InternalAxiosRequestConfig } from 'axios';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { apiClient } from '@/api/apiClient';
import { ApiError } from '@/api/apiErrorHandler';
import { axiosInstance } from '@/api/axiosInstance';
import { sessionApi } from '@/api/sessionApi';
import { tokenStore } from '@/api/tokenStore';
import { customerSignedIn, customerSignedOut } from '@/store/slices/sessionSlice';
import { store } from '@/store/store';
import type { LoginResponse } from '@/types/storefront';

describe('axiosInstance', () => {
  const sent: InternalAxiosRequestConfig[] = [];
  const originalAdapter = axiosInstance.defaults.adapter;

  const answerWith = (data: unknown) => {
    axiosInstance.defaults.adapter = (config) => {
      sent.push(config);
      return Promise.resolve({ data, status: 200, statusText: 'OK', headers: {}, config });
    };
  };

  beforeEach(() => {
    sent.length = 0;
    vi.spyOn(console, 'warn').mockImplementation(() => undefined);
  });

  afterEach(() => {
    axiosInstance.defaults.adapter = originalAdapter;
    store.dispatch(customerSignedOut());
    window.history.pushState({}, '', '/');
    vi.restoreAllMocks();
  });

  it("sends every request to the gateway; in the admin area with the admin's token only", async () => {
    window.history.pushState({}, '', '/admin/dashboard');
    answerWith({ unread: 3 });

    const body = await apiClient.get<{ unread: number }>('/api/v1/notifications/unread-count', {
      params: { unreadOnly: true, empty: undefined },
    });

    expect(body).toEqual({ unread: 3 });
    expect(sent[0].baseURL).toBe('http://gateway.test');
    expect(sent[0].headers.get('Authorization')).toBe('Bearer test-admin-token');
    expect(sent[0].headers.get('X-User-Id')).toBeUndefined();
    expect(sent[0].headers.get('X-User-Role')).toBeUndefined();
    expect(sent[0].params).toEqual({ unreadOnly: true, empty: undefined });
  });

  it('sends nothing that names a user for a visitor of the customer area', async () => {
    window.history.pushState({}, '', '/restaurants');
    answerWith({ unread: 0 });

    await apiClient.get('/api/v1/notifications/unread-count');

    expect(sent[0].headers.get('Authorization')).toBeUndefined();
    expect(sent[0].headers.get('X-User-Id')).toBeUndefined();
  });

  it('turns a failed request into an ApiError', async () => {
    axiosInstance.defaults.adapter = (config) =>
      Promise.reject(
        new AxiosError(
          'Request failed',
          'ERR_BAD_REQUEST',
          config,
          {},
          {
            status: 404,
            statusText: 'Not Found',
            data: { detail: 'Order 99 not found' },
            headers: {},
            config,
          },
        ),
      );

    const failure = apiClient.get('/api/v1/admin/orders/99');

    await expect(failure).rejects.toBeInstanceOf(ApiError);
    await expect(failure).rejects.toMatchObject({
      kind: 'notFound',
      userMessage: 'Order 99 not found',
    });
  });
});

describe('axiosInstance: access tokens', () => {
  const sent: InternalAxiosRequestConfig[] = [];
  const originalAdapter = axiosInstance.defaults.adapter;
  const priya = {
    id: 7,
    role: 'CUSTOMER' as const,
    fullName: 'Priya Sharma',
    email: 'p@example.com',
  };
  const renewed: LoginResponse = {
    userId: 7,
    email: 'p@example.com',
    fullName: 'Priya Sharma',
    role: 'CUSTOMER',
    lastLoginAt: null,
    accessToken: 'renewed-token',
    tokenType: 'Bearer',
    expiresIn: 900,
  };
  const sessionEnded = new ApiError({
    kind: 'unauthorized',
    status: 401,
    userMessage: 'Your session has ended. Please sign in again.',
    technicalMessage: 'HTTP 401',
  });

  /** Answers the requests with these statuses in turn (200 once they are used up). */
  const answerWithStatuses = (...statuses: number[]) => {
    axiosInstance.defaults.adapter = (config) => {
      sent.push(config);
      const status = statuses.shift() ?? 200;
      if (status === 200) {
        return Promise.resolve({ data: {}, status, statusText: 'OK', headers: {}, config });
      }
      return Promise.reject(
        new AxiosError(
          'Refused',
          'ERR_BAD_REQUEST',
          config,
          {},
          {
            status,
            statusText: 'Refused',
            data: { title: 'Refused' },
            headers: {},
            config,
          },
        ),
      );
    };
  };

  beforeEach(() => {
    sent.length = 0;
    vi.spyOn(console, 'warn').mockImplementation(() => undefined);
    vi.mocked(sessionApi.refresh).mockClear();
    window.history.pushState({}, '', '/orders');
    store.dispatch(customerSignedIn(priya));
    tokenStore.set('customer', {
      value: 'current-token',
      expiresAt: Date.now() + 600_000,
      userId: 7,
    });
  });

  afterEach(() => {
    axiosInstance.defaults.adapter = originalAdapter;
    store.dispatch(customerSignedOut());
    tokenStore.clear('customer');
    window.history.pushState({}, '', '/');
    vi.restoreAllMocks();
  });

  it("sends the area's access token as a Bearer token, and no identity headers", async () => {
    answerWithStatuses(200);

    await apiClient.get('/api/v1/orders');

    expect(sent[0].headers.get('Authorization')).toBe('Bearer current-token');
    expect(sent[0].headers.get('X-User-Id')).toBeUndefined();
    expect(sessionApi.refresh).not.toHaveBeenCalled();
  });

  it('renews a token about to expire before sending the request', async () => {
    tokenStore.set('customer', { value: 'old-token', expiresAt: Date.now() + 10_000, userId: 7 });
    vi.mocked(sessionApi.refresh).mockResolvedValueOnce(renewed);
    answerWithStatuses(200);

    await apiClient.get('/api/v1/orders');

    expect(sessionApi.refresh).toHaveBeenCalledWith('CUSTOMER');
    expect(sent[0].headers.get('Authorization')).toBe('Bearer renewed-token');
  });

  it('sends a refused request once more with a renewed token', async () => {
    vi.mocked(sessionApi.refresh).mockResolvedValueOnce(renewed);
    answerWithStatuses(401, 200);

    await expect(apiClient.post('/api/v1/orders', { addressId: 21 })).resolves.toEqual({});

    expect(sent).toHaveLength(2);
    expect(sent[1].headers.get('Authorization')).toBe('Bearer renewed-token');
    expect(sent[1].data).toBe(JSON.stringify({ addressId: 21 }));
    expect(sessionApi.refresh).toHaveBeenCalledOnce();
  });

  it('does not ask again when the renewed token is refused too', async () => {
    vi.mocked(sessionApi.refresh).mockResolvedValueOnce(renewed);
    answerWithStatuses(401, 401);

    await expect(apiClient.get('/api/v1/orders')).rejects.toMatchObject({ kind: 'unauthorized' });

    expect(sent).toHaveLength(2);
    expect(sessionApi.refresh).toHaveBeenCalledOnce();
  });

  it('signs the area out when its session has ended', async () => {
    vi.mocked(sessionApi.refresh).mockRejectedValueOnce(sessionEnded);
    answerWithStatuses(401);

    await expect(apiClient.get('/api/v1/orders')).rejects.toMatchObject({ kind: 'unauthorized' });

    expect(store.getState().session.customer).toBeNull();
    expect(tokenStore.get('customer')).toBeNull();
  });

  it('sends no token for a visitor, and never renews one', async () => {
    store.dispatch(customerSignedOut());
    answerWithStatuses(401);

    await expect(apiClient.get('/api/v1/cart')).rejects.toMatchObject({ kind: 'unauthorized' });

    expect(sent[0].headers.has('Authorization')).toBe(false);
    expect(sessionApi.refresh).not.toHaveBeenCalled();
  });

  it('keeps neither the token nor the request body in a failed request (passwords, for one)', async () => {
    answerWithStatuses(422);

    const failure: unknown = await apiClient
      .put('/api/v1/users/me/password', { currentPassword: 'Secret123', newPassword: 'Secret456' })
      .catch((error: unknown) => error);

    expect(failure).toBeInstanceOf(ApiError);
    const cause = (failure as ApiError).cause as AxiosError;
    expect(cause.config?.headers.has('Authorization')).toBe(false);
    expect(cause.config?.data).toBeUndefined();
    expect(JSON.stringify(failure)).not.toContain('Secret123');
  });
});
