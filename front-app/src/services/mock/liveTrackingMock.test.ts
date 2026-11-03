import { beforeEach, describe, expect, it, vi } from 'vitest';

import { ApiError } from '@/api/apiErrorHandler';
import { deliveryService } from '@/services/deliveryService';
import {
  SIMULATION_TIMING,
  liveTrackingMock,
  simulateLiveTracking,
} from '@/services/mock/liveTrackingMock';
import type { OrderTracking } from '@/types/shopping';
import { distanceMeters } from '@/utils/geoUtils';

vi.mock('@/services/deliveryService', () => ({ deliveryService: { getOrderTracking: vi.fn() } }));

const ACCEPTED_AT = Date.parse('2026-10-13T13:46:00Z');
const { toRestaurantMs, toCustomerMs } = SIMULATION_TIMING;
const iso = (ms: number) => new Date(ms).toISOString();

/** The saved delivery of order 25, as delivery-service answers GET /api/v1/delivery/orders/25 */
function delivery(overrides: Partial<OrderTracking> = {}): OrderTracking {
  return {
    orderId: 25,
    deliveryId: 11,
    status: 'ACCEPTED',
    restaurantName: 'Biryani House 29b67fdf',
    dropoffAddress: {
      line1: '45 Sample Road',
      line2: null,
      city: 'Bengaluru',
      state: 'Karnataka',
      postalCode: '560001',
      country: 'India',
    },
    partnerName: 'Ravi 29b67fdf',
    partnerPhone: '+919810917494',
    vehicleType: 'MOTORCYCLE',
    cashToCollect: 0,
    currency: 'INR',
    readyAt: iso(ACCEPTED_AT - 60_000),
    acceptedAt: iso(ACCEPTED_AT),
    pickedUpAt: null,
    deliveredAt: null,
    cancelledAt: null,
    steps: [],
    ...overrides,
  };
}

const PICKED_UP_AT = ACCEPTED_AT + 40_000;
const pickedUp = delivery({ status: 'PICKED_UP', pickedUpAt: iso(PICKED_UP_AT) });
const DELIVERED_AT = PICKED_UP_AT + 180_000;
const delivered = delivery({
  status: 'DELIVERED',
  pickedUpAt: iso(PICKED_UP_AT),
  deliveredAt: iso(DELIVERED_AT),
});

describe('the simulated ride follows the real delivery', () => {
  it('takes the phase from the saved delivery status, not from a clock in the browser', () => {
    expect(simulateLiveTracking(delivery(), ACCEPTED_AT + 1_000).phase).toBe('TO_RESTAURANT');
    expect(simulateLiveTracking(pickedUp, PICKED_UP_AT + 1_000).phase).toBe('TO_CUSTOMER');
    expect(simulateLiveTracking(delivered, DELIVERED_AT + 1_000).phase).toBe('DELIVERED');
  });

  it('waits at the restaurant until the food is really picked up, however long that takes', () => {
    const anHourLater = simulateLiveTracking(delivery(), ACCEPTED_AT + 3_600_000);
    expect(anHourLater.phase).toBe('AT_RESTAURANT');
    expect(anHourLater.rider.location).toEqual(anHourLater.restaurant.location);
  });

  it('never shows "delivered" before the real delivery: it stops just short of the door', () => {
    const late = simulateLiveTracking(pickedUp, PICKED_UP_AT + 10 * toCustomerMs);
    expect(late.phase).toBe('TO_CUSTOMER');
    expect(late.remainingMeters).toBeGreaterThan(0);
    expect(late.etaMinutes).toBeGreaterThanOrEqual(1);
  });

  it('gives the same answer after a reload (nothing is kept in the browser)', () => {
    const moment = PICKED_UP_AT + 30_000;
    expect(simulateLiveTracking(pickedUp, moment)).toEqual(simulateLiveTracking(pickedUp, moment));
  });

  it('once delivered: nothing left to ride, and the real moment it arrived', () => {
    const done = simulateLiveTracking(delivered, DELIVERED_AT + 600_000);
    expect(done.etaMinutes).toBe(0);
    expect(done.remainingMeters).toBe(0);
    expect(done.rider.location).toEqual(done.dropoff.location);
    expect(done.estimatedArrival).toBe(iso(DELIVERED_AT));
  });

  it('names the real partner, restaurant and address (only the positions are samples)', () => {
    const ride = simulateLiveTracking(pickedUp, PICKED_UP_AT + 1_000);
    expect(ride.source).toBe('SIMULATED');
    expect(ride.partner).toEqual({
      name: 'Ravi 29b67fdf',
      phone: '+919810917494',
      vehicleType: 'MOTORCYCLE',
    });
    expect(ride.restaurant.name).toBe('Biryani House 29b67fdf');
    expect(ride.dropoff.address).toBe('45 Sample Road, Bengaluru');
  });

  it('shows the road ahead: to the restaurant first, then only to the customer', () => {
    const before = simulateLiveTracking(delivery(), ACCEPTED_AT + toRestaurantMs / 2);
    expect(before.pathToRestaurant.length).toBeGreaterThan(1);
    expect(before.pathToRestaurant[0]).toEqual(before.rider.location);
    expect(before.pathToCustomer[0]).toEqual(before.restaurant.location);

    const after = simulateLiveTracking(pickedUp, PICKED_UP_AT + toCustomerMs / 2);
    expect(after.pathToRestaurant).toEqual([]);
    expect(after.pathToCustomer[0]).toEqual(after.rider.location);
    expect(after.pathToCustomer.at(-1)).toEqual(after.dropoff.location);
  });

  it('counts the time and distance down, moving in small steps between two refreshes', () => {
    let previous = simulateLiveTracking(pickedUp, PICKED_UP_AT);
    for (let elapsed = 3_000; elapsed <= toCustomerMs; elapsed += 3_000) {
      const next = simulateLiveTracking(pickedUp, PICKED_UP_AT + elapsed);
      expect(next.remainingMeters).toBeLessThanOrEqual(previous.remainingMeters);
      expect(next.etaMinutes).toBeLessThanOrEqual(previous.etaMinutes);
      expect(distanceMeters(previous.rider.location, next.rider.location)).toBeLessThan(250);
      previous = next;
    }
  });
});

describe('liveTrackingMock.getLiveTracking', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('reads the real delivery of the order', async () => {
    vi.mocked(deliveryService.getOrderTracking).mockResolvedValue(pickedUp);

    const ride = await liveTrackingMock.getLiveTracking(25);

    expect(deliveryService.getOrderTracking).toHaveBeenCalledWith(25);
    expect(ride.orderId).toBe(25);
    expect(ride.phase).toBe('TO_CUSTOMER');
  });

  it.each(['PENDING', 'ASSIGNED', 'CANCELLED'] as const)(
    'answers 404 while no partner is on the way (%s), like the real endpoint',
    async (status) => {
      vi.mocked(deliveryService.getOrderTracking).mockResolvedValue(delivery({ status }));

      await expect(liveTrackingMock.getLiveTracking(25)).rejects.toMatchObject({
        kind: 'notFound',
        status: 404,
      });
    },
  );

  it('passes delivery-service errors on (404 before the food is ready, a failure)', async () => {
    const notReady = new ApiError({
      kind: 'notFound',
      status: 404,
      userMessage: 'Not found',
      technicalMessage: 'no delivery yet',
    });
    vi.mocked(deliveryService.getOrderTracking).mockRejectedValue(notReady);

    await expect(liveTrackingMock.getLiveTracking(25)).rejects.toBe(notReady);
  });
});
