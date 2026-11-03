import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { ApiError } from '@/api/apiErrorHandler';
import { sessionApi } from '@/api/sessionApi';
import { accessTokenFor, RENEW_BEFORE_MS, renewAccessToken } from '@/api/sessionRefresh';
import { tokenStore } from '@/api/tokenStore';
import { ownerSignedIn, ownerSignedOut } from '@/store/slices/sessionSlice';
import { store } from '@/store/store';
import type { LoginResponse } from '@/types/storefront';

const owner = {
  id: 423,
  role: 'RESTAURANT_ADMIN' as const,
  fullName: 'Test Owner',
  email: 'o@x.com',
};

const answer = (overrides: Partial<LoginResponse> = {}): LoginResponse => ({
  userId: 423,
  email: 'o@x.com',
  fullName: 'Test Owner',
  role: 'RESTAURANT_ADMIN',
  lastLoginAt: null,
  accessToken: 'renewed-token',
  tokenType: 'Bearer',
  expiresIn: 900,
  ...overrides,
});

const failure = (kind: ApiError['kind'], status?: number) =>
  new ApiError({ kind, status, userMessage: kind, technicalMessage: kind });

beforeEach(() => {
  vi.mocked(sessionApi.refresh).mockReset();
  vi.mocked(sessionApi.logout).mockClear();
  vi.spyOn(console, 'warn').mockImplementation(() => undefined);
  vi.spyOn(console, 'info').mockImplementation(() => undefined);
  store.dispatch(ownerSignedIn(owner));
  sessionStorage.setItem('front-app.owner', JSON.stringify(owner));
});

afterEach(() => {
  store.dispatch(ownerSignedOut());
  tokenStore.clear('owner');
  sessionStorage.removeItem('front-app.owner');
  vi.restoreAllMocks();
});

describe('accessTokenFor', () => {
  it('uses the token in memory while it has more than 4 minutes left', async () => {
    tokenStore.set('owner', {
      value: 'fresh',
      expiresAt: Date.now() + RENEW_BEFORE_MS + 60_000,
      userId: 423,
    });

    await expect(accessTokenFor('owner')).resolves.toBe('fresh');
    expect(sessionApi.refresh).not.toHaveBeenCalled();
  });

  it('renews a token about to expire, and keeps the new one in memory', async () => {
    tokenStore.set('owner', { value: 'old', expiresAt: Date.now() + 20_000, userId: 423 });
    vi.mocked(sessionApi.refresh).mockResolvedValue(answer());

    await expect(accessTokenFor('owner')).resolves.toBe('renewed-token');
    expect(sessionApi.refresh).toHaveBeenCalledWith('RESTAURANT_ADMIN');
    expect(tokenStore.get('owner')).toMatchObject({ value: 'renewed-token', userId: 423 });
  });

  it("never sends another person's token: one of a previous account is renewed", async () => {
    tokenStore.set('owner', { value: 'someone-else', expiresAt: Date.now() + 600_000, userId: 99 });
    vi.mocked(sessionApi.refresh).mockResolvedValue(answer());

    await expect(accessTokenFor('owner')).resolves.toBe('renewed-token');
  });

  it('asks nothing for an area nobody is signed in to', async () => {
    store.dispatch(ownerSignedOut());

    await expect(accessTokenFor('owner')).resolves.toBeNull();
    expect(sessionApi.refresh).not.toHaveBeenCalled();
  });
});

describe('renewAccessToken', () => {
  it('renews once for all the requests that need a token at the same time (after a reload)', async () => {
    vi.mocked(sessionApi.refresh).mockResolvedValue(answer());

    const tokens = await Promise.all([
      accessTokenFor('owner'),
      accessTokenFor('owner'),
      accessTokenFor('owner'),
    ]);

    expect(tokens).toEqual(['renewed-token', 'renewed-token', 'renewed-token']);
    expect(sessionApi.refresh).toHaveBeenCalledOnce();
  });

  it('tries once more when another tab renewed the same session at that moment (409)', async () => {
    vi.mocked(sessionApi.refresh)
      .mockRejectedValueOnce(failure('conflict', 409))
      .mockResolvedValueOnce(answer());

    await expect(renewAccessToken('owner')).resolves.toBe('renewed-token');
    expect(sessionApi.refresh).toHaveBeenCalledTimes(2);
  });

  it('signs the area out when the session has ended (401): its pages then ask for a sign-in', async () => {
    tokenStore.set('owner', { value: 'old', expiresAt: Date.now() + 1_000, userId: 423 });
    vi.mocked(sessionApi.refresh).mockRejectedValue(failure('unauthorized', 401));

    await expect(renewAccessToken('owner')).resolves.toBeNull();
    expect(store.getState().session.owner).toBeNull();
    expect(tokenStore.get('owner')).toBeNull();
    expect(sessionStorage.getItem('front-app.owner')).toBeNull();
    // The server ended it already: nothing more to tell it
    expect(sessionApi.logout).not.toHaveBeenCalled();
  });

  it('signs this tab out when another account signed in to the same area in another tab', async () => {
    vi.mocked(sessionApi.refresh).mockResolvedValue(answer({ userId: 500, accessToken: 'theirs' }));

    await expect(renewAccessToken('owner')).resolves.toBeNull();
    expect(store.getState().session.owner).toBeNull();
    expect(tokenStore.get('owner')).toBeNull();
  });

  it('keeps the person signed in when user-service cannot be reached (the next request tries again)', async () => {
    vi.mocked(sessionApi.refresh).mockRejectedValue(failure('network'));

    await expect(renewAccessToken('owner')).resolves.toBeNull();
    expect(store.getState().session.owner).toEqual(owner);
  });
});
