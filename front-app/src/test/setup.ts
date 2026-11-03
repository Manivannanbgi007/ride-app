import '@testing-library/jest-dom/vitest';

import { cleanup } from '@testing-library/react';
import { afterEach, vi } from 'vitest';

import { tokenStore } from '@/api/tokenStore';

// Until a test says otherwise, an administrator is signed in to the admin area (the pages of /admin act as the admin,
// as they did when the environment's settings signed the admin in). It is read when the session slice is loaded, so it
// is the starting session of every store the tests make; tests of the sign-in and of visitors pass their own session.
sessionStorage.setItem(
  'front-app.admin',
  JSON.stringify({
    id: 1,
    role: 'PLATFORM_ADMIN',
    fullName: 'Platform Administrator',
    email: 'admin@fooddelivery.local',
  }),
);
// ... with a valid access token in memory (as after a sign-in), so a request of the admin area needs no renewal
tokenStore.set('admin', {
  value: 'test-admin-token',
  expiresAt: Number.MAX_SAFE_INTEGER,
  userId: 1,
});

// The sign-in session calls (they send cookies to user-service) never reach the network: a test that needs an
// answer sets it. Until then a renewal fails as if the server could not be reached (the person stays signed in).
vi.mock('@/api/sessionApi', async () => {
  const { ApiError } = await import('@/api/apiErrorHandler');
  const unreachable = () =>
    Promise.reject(
      new ApiError({
        kind: 'network',
        userMessage: 'No connection',
        technicalMessage: 'The tests do not reach user-service',
      }),
    );
  return {
    sessionApi: {
      login: vi.fn(unreachable),
      refresh: vi.fn(unreachable),
      logout: vi.fn(() => Promise.resolve()),
    },
  };
});

// Every test starts with an empty page
afterEach(() => {
  cleanup();
});

// jsdom has no ResizeObserver; the charts (ResponsiveContainer) need one
class ResizeObserverStub {
  observe = vi.fn();
  unobserve = vi.fn();
  disconnect = vi.fn();
}
vi.stubGlobal('ResizeObserver', ResizeObserverStub);

// jsdom does not scroll (the customer area starts each page at the top)
window.scrollTo = vi.fn();

// Pages show ratings next to restaurants and dishes; unless a test says otherwise nothing has been rated yet,
// and no test reaches the network by accident
vi.mock('@/services/ratingService', () => ({
  ratingService: {
    getRestaurantRatings: vi.fn().mockResolvedValue([]),
    getDishRatings: vi.fn().mockResolvedValue([]),
    getRestaurantRating: vi.fn().mockResolvedValue({
      id: 0,
      average: null,
      count: 0,
      distribution: { '1': 0, '2': 0, '3': 0, '4': 0, '5': 0 },
    }),
    getDishRating: vi.fn().mockResolvedValue({
      id: 0,
      average: null,
      count: 0,
      distribution: { '1': 0, '2': 0, '3': 0, '4': 0, '5': 0 },
    }),
    getRestaurantReviews: vi.fn().mockResolvedValue({
      content: [],
      page: 0,
      size: 5,
      totalElements: 0,
      totalPages: 0,
      first: true,
      last: true,
    }),
    getDishReviews: vi.fn().mockResolvedValue({
      content: [],
      page: 0,
      size: 5,
      totalElements: 0,
      totalPages: 0,
      first: true,
      last: true,
    }),
  },
}));
