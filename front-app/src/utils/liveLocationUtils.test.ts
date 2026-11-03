import { describe, expect, it } from 'vitest';

import { positionAt } from '@/test/fakeGeolocation';
import {
  isOnTheWay,
  MAX_QUIET_MS,
  MIN_SEND_GAP_MS,
  shouldSendPosition,
  toLocationUpdate,
} from '@/utils/liveLocationUtils';

const HERE = { lat: 12.9716, lng: 77.5946 };
/** About 33 m north */
const NEAR = { lat: 12.9719, lng: 77.5946 };
/** About 11 m north */
const VERY_NEAR = { lat: 12.9717, lng: 77.5946 };

describe('shouldSendPosition', () => {
  const sent = { point: HERE, at: 100_000 };

  it('sends the first position', () => {
    expect(shouldSendPosition(null, HERE, 0)).toBe(true);
  });

  it('never sends sooner than the minimum gap, even after a long move', () => {
    expect(shouldSendPosition(sent, NEAR, sent.at + MIN_SEND_GAP_MS - 1)).toBe(false);
  });

  it('sends a move of 25 m or more after the gap, not a smaller one', () => {
    expect(shouldSendPosition(sent, NEAR, sent.at + MIN_SEND_GAP_MS)).toBe(true);
    expect(shouldSendPosition(sent, VERY_NEAR, sent.at + MIN_SEND_GAP_MS)).toBe(false);
  });

  it('sends after a quiet while even without moving', () => {
    expect(shouldSendPosition(sent, HERE, sent.at + MAX_QUIET_MS - 1)).toBe(false);
    expect(shouldSendPosition(sent, HERE, sent.at + MAX_QUIET_MS)).toBe(true);
  });
});

describe('toLocationUpdate', () => {
  it('rounds the accuracy and the heading, and keeps a full turn under 360', () => {
    expect(
      toLocationUpdate(positionAt({ ...HERE, accuracy: 12.6, heading: 359.7 }).coords),
    ).toEqual({
      latitude: 12.9716,
      longitude: 77.5946,
      accuracyMeters: 13,
      headingDeg: 0,
    });
  });

  it('sends no heading while the phone does not know it, and caps a very rough accuracy', () => {
    expect(
      toLocationUpdate(positionAt({ ...HERE, accuracy: 250_000, heading: Number.NaN }).coords),
    ).toMatchObject({ accuracyMeters: 100_000, headingDeg: null });
    expect(toLocationUpdate(positionAt({ ...HERE, heading: null }).coords).headingDeg).toBeNull();
  });
});

describe('isOnTheWay', () => {
  it('is true from accepting the delivery until handing the food over', () => {
    expect(isOnTheWay('ASSIGNED')).toBe(false);
    expect(isOnTheWay('ACCEPTED')).toBe(true);
    expect(isOnTheWay('PICKED_UP')).toBe(true);
    expect(isOnTheWay('DELIVERED')).toBe(false);
    expect(isOnTheWay('CANCELLED')).toBe(false);
    expect(isOnTheWay(undefined)).toBe(false);
  });
});
