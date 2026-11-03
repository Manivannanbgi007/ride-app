import { describe, expect, it } from 'vitest';

import type { Offer } from '@/types/shopping';
import {
  formatCountdown,
  offerEndsAt,
  offerImageOf,
  soonestEnding,
  urgencyOf,
} from '@/utils/offerUtils';

const offer = (code: string, validUntil: string, description: string | null = null): Offer => ({
  code,
  description,
  summary: '20% off',
  minOrderAmount: 0,
  restaurantId: null,
  validUntil,
});

describe('offerUtils', () => {
  it('ends an offer at the last moment of its last day', () => {
    expect(new Date(offerEndsAt('2026-10-11'))).toEqual(new Date(2026, 9, 11, 23, 59, 59, 999));
  });

  it('writes the time left as a clock, with days when there are some', () => {
    expect(formatCountdown(((1 * 60 + 47) * 60 + 23) * 1000)).toBe('01:47:23');
    expect(formatCountdown((2 * 86_400 + 5) * 1000)).toBe('2d 00:00:05');
    expect(formatCountdown(-5)).toBe('00:00:00');
  });

  it('picks the offers still running, the soonest to end first', () => {
    const now = new Date(2026, 9, 11, 12).getTime();
    const offers = [
      offer('LATER', '2026-12-31'),
      offer('FRIDAY', '2026-10-16'),
      offer('OVER', '2026-10-10'),
      offer('TODAY', '2026-10-11'),
    ];

    expect(soonestEnding(offers, now, 3).map((item) => item.code)).toEqual([
      'TODAY',
      'FRIDAY',
      'LATER',
    ]);
    expect(soonestEnding(offers, now, 1).map((item) => item.code)).toEqual(['TODAY']);
  });

  it('grades how pressing an offer is', () => {
    const hour = 3_600_000;
    expect(urgencyOf(5 * hour)).toBe('today');
    expect(urgencyOf(3 * 24 * hour)).toBe('soon');
    expect(urgencyOf(30 * 24 * hour)).toBe('limited');
  });

  it('shows the food an offer names, and always the same picture for the same code', () => {
    expect(offerImageOf(offer('CURRY30', '2026-12-31', 'On all Indian restaurants'))).toBe(
      '/images/categories/north-indian.jpg',
    );
    const plain = offer('WELCOME', '2026-12-31');
    expect(offerImageOf(plain)).toBe(offerImageOf({ ...plain }));
    expect(offerImageOf(plain)).toMatch(/^\/images\/categories\//);
  });
});
