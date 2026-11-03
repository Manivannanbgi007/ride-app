import { AxiosError, type InternalAxiosRequestConfig } from 'axios';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { ApiError } from '@/api/apiErrorHandler';
import { sessionApi, sessionAxios } from '@/api/sessionApi';

// The real calls (test/setup.ts replaces them for every other test)
vi.unmock('@/api/sessionApi');

describe('sessionApi', () => {
  const sent: InternalAxiosRequestConfig[] = [];
  const originalAdapter = sessionAxios.defaults.adapter;

  afterEach(() => {
    sessionAxios.defaults.adapter = originalAdapter;
    sent.length = 0;
  });

  it("lets the browser send and keep the area's cookie, and names the area", async () => {
    sessionAxios.defaults.adapter = (config) => {
      sent.push(config);
      return Promise.resolve({ data: {}, status: 204, statusText: '', headers: {}, config });
    };

    await sessionApi.logout('DELIVERY_PARTNER');

    expect(sent[0].url).toBe('/api/v1/auth/logout');
    expect(sent[0].withCredentials).toBe(true);
    expect(sent[0].data).toBe(JSON.stringify({ role: 'DELIVERY_PARTNER' }));
    expect(sent[0].headers.has('Authorization')).toBe(false);
  });

  it('refuses a sign-in to another area with its code, and keeps no trace of the password', async () => {
    sessionAxios.defaults.adapter = (config) =>
      Promise.reject(
        new AxiosError(
          'Forbidden',
          'ERR_BAD_REQUEST',
          config,
          {},
          {
            status: 403,
            statusText: 'Forbidden',
            data: {
              title: 'Forbidden',
              detail: 'This account cannot sign in here.',
              code: 'WRONG_ACCOUNT_TYPE',
            },
            headers: {},
            config,
          },
        ),
      );

    const error: unknown = await sessionApi
      .login('priya@example.com', 'Eater1234', 'RESTAURANT_ADMIN')
      .catch((failure: unknown) => failure);

    expect(error).toBeInstanceOf(ApiError);
    expect(error).toMatchObject({ kind: 'forbidden', code: 'WRONG_ACCOUNT_TYPE' });
    expect((error as ApiError).cause).toBeUndefined();
    expect(JSON.stringify(error)).not.toContain('Eater1234');
  });
});
