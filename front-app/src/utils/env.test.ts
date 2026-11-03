import { describe, expect, it } from 'vitest';

import { ConfigurationError, OPENSTREETMAP_TILES, readEnvironment } from '@/utils/env';

const complete = {
  VITE_APP_NAME: 'Front-App',
  VITE_APP_ENV: 'UAT',
  VITE_API_BASE_URL: 'https://uat-api.example.com',
  VITE_API_TIMEOUT_MS: '15000',
  VITE_FEATURE_API_LOGGING: 'false',
  VITE_FEATURE_QUERY_DEVTOOLS: 'TRUE',
  VITE_FEATURE_ENVIRONMENT_BADGE: 'true',
  VITE_FEATURE_PAYMENT_TEST_HINTS: 'true',
  VITE_FEATURE_LIVE_UPDATES: 'true',
  VITE_FEATURE_FAKE_LIVE_TRACKING: 'true',
  VITE_FEATURE_DEV_ACCOUNTS: 'true',
  VITE_MAP_TILE_URL: ' https://tiles.example.com/{z}/{x}/{y}.png ',
  VITE_MAP_TILE_ATTRIBUTION: '&copy; Example Maps',
};

describe('readEnvironment', () => {
  it('reads every setting of an environment file', () => {
    expect(readEnvironment(complete)).toEqual({
      appName: 'Front-App',
      environmentName: 'UAT',
      apiBaseUrl: 'https://uat-api.example.com',
      apiTimeoutMs: 15000,
      features: {
        apiLogging: false,
        queryDevtools: true,
        environmentBadge: true,
        paymentTestHints: true,
        liveUpdates: true,
        fakeLiveTracking: true,
        devAccounts: true,
      },
      map: {
        tileUrl: 'https://tiles.example.com/{z}/{x}/{y}.png',
        attribution: '&copy; Example Maps',
      },
    });
  });

  it("uses OpenStreetMap's tiles and credit when no tile server is set (no key needed)", () => {
    const withoutTiles = {
      ...complete,
      VITE_MAP_TILE_URL: undefined,
      VITE_MAP_TILE_ATTRIBUTION: '',
    };

    expect(readEnvironment(withoutTiles).map).toEqual(OPENSTREETMAP_TILES);
  });

  it.each([
    [{ VITE_API_BASE_URL: ' ' }, 'VITE_API_BASE_URL is not set'],
    [{ VITE_API_TIMEOUT_MS: 'soon' }, 'VITE_API_TIMEOUT_MS must be a number'],
  ])('names the wrong setting: %o', (change, message) => {
    expect(() => readEnvironment({ ...complete, ...change })).toThrow(ConfigurationError);
    expect(() => readEnvironment({ ...complete, ...change })).toThrow(message);
  });
});
