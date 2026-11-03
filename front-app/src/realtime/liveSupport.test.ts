import { QueryClient } from '@tanstack/react-query';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { InvalidationBatcher } from '@/realtime/invalidationBatcher';
import { queryKeysFor } from '@/realtime/liveInvalidation';
import { liveUrl } from '@/realtime/liveUrl';
import type { LiveEvent } from '@/types/live';
import { QUERY_KEYS } from '@/utils/queryKeys';

const event = (overrides: Partial<LiveEvent>): LiveEvent => ({
  id: 'e1',
  type: 'ORDER_STATUS_CHANGED',
  occurredAt: '2026-10-09T10:00:00Z',
  orderId: 5,
  ...overrides,
});

describe('liveUrl', () => {
  it('uses the gateway address with ws:// or wss://, and the access token in the query', () => {
    expect(liveUrl('http://localhost:8080', 'header.claims.signature')).toBe(
      'ws://localhost:8080/ws/v1/live?access_token=header.claims.signature',
    );
    expect(liveUrl('https://api.example.com/', 'a+b/c=')).toBe(
      'wss://api.example.com/ws/v1/live?access_token=a%2Bb%2Fc%3D',
    );
  });
});

describe('queryKeysFor', () => {
  const admin = { id: 1, role: 'PLATFORM_ADMIN' } as const;
  const customer = { id: 7, role: 'CUSTOMER' } as const;

  it('refreshes the dashboard, the figures and the order lists for an admin when an order changes', () => {
    const keys = queryKeysFor(event({ type: 'ORDER_STATUS_CHANGED' }), admin);

    expect(keys).toContainEqual(QUERY_KEYS.dashboardAll);
    expect(keys).toContainEqual(QUERY_KEYS.reportsAll);
    expect(keys).toContainEqual(QUERY_KEYS.orders.all);
    expect(keys).toContainEqual(QUERY_KEYS.deliveries.all);
  });

  it('refreshes payments and refunds for an admin when a payment changes', () => {
    const keys = queryKeysFor(event({ type: 'PAYMENT_STATUS_CHANGED' }), admin);

    expect(keys).toContainEqual(QUERY_KEYS.payments.all);
    expect(keys).toContainEqual(QUERY_KEYS.refunds.all);
  });

  it('refreshes deliveries, partners and the live card for an admin when a delivery changes', () => {
    const keys = queryKeysFor(event({ type: 'DELIVERY_STATUS_CHANGED' }), admin);

    expect(keys).toContainEqual(QUERY_KEYS.deliveries.all);
    expect(keys).toContainEqual(QUERY_KEYS.partners.all);
    expect(keys).toContainEqual(QUERY_KEYS.dashboardAll);
  });

  it('refreshes restaurants for an admin when a restaurant changes', () => {
    expect(queryKeysFor(event({ type: 'RESTAURANT_CHANGED' }), admin)).toContainEqual(
      QUERY_KEYS.restaurants.all,
    );
  });

  it("refreshes only the customer's own orders, tracking and notifications", () => {
    for (const type of [
      'ORDER_STATUS_CHANGED',
      'ORDER_CANCELLED',
      'PAYMENT_STATUS_CHANGED',
      'DELIVERY_STATUS_CHANGED',
    ] as const) {
      const keys = queryKeysFor(event({ type }), customer);

      expect(keys).toEqual([
        QUERY_KEYS.customer.allOrders(7),
        QUERY_KEYS.customer.allTracking(7),
        QUERY_KEYS.inboxAll,
      ]);
    }
  });

  it('never refreshes the admin area for somebody who is not an admin', () => {
    const keys = JSON.stringify(queryKeysFor(event({ type: 'ORDER_STATUS_CHANGED' }), customer));

    expect(keys).not.toContain('"admin"');
  });

  describe('for a restaurant owner', () => {
    const owner = { id: 3, role: 'RESTAURANT_ADMIN' } as const;

    it("refreshes that restaurant's order lists, counts, open order and figures when one of its orders changes", () => {
      for (const type of [
        'ORDER_STATUS_CHANGED',
        'ORDER_CANCELLED',
        'DELIVERY_STATUS_CHANGED',
      ] as const) {
        expect(queryKeysFor(event({ type, restaurantId: 12 }), owner)).toEqual([
          QUERY_KEYS.owner.ordersAll(3, 12),
          QUERY_KEYS.inboxAll,
        ]);
      }
    });

    it("refreshes the orders of all the owner's restaurants when an event does not say which", () => {
      expect(queryKeysFor(event({ type: 'ORDER_STATUS_CHANGED' }), owner)).toContainEqual(
        QUERY_KEYS.owner.allOrders(3),
      );
    });

    it("refreshes the owner's restaurants when one of them changes", () => {
      expect(
        queryKeysFor(event({ type: 'RESTAURANT_CHANGED', restaurantId: 12 }), owner),
      ).toContainEqual(QUERY_KEYS.owner.restaurants(3));
    });

    it("never refreshes a customer's data (the owner's own orders were missed that way)", () => {
      const keys = JSON.stringify(
        queryKeysFor(event({ type: 'ORDER_STATUS_CHANGED', restaurantId: 12 }), owner),
      );

      expect(keys).not.toContain('"customer"');
    });
  });
});

describe('InvalidationBatcher', () => {
  let queryClient: QueryClient;
  let invalidate: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    vi.useFakeTimers();
    queryClient = new QueryClient();
    invalidate = vi.spyOn(queryClient, 'invalidateQueries').mockResolvedValue();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('reads the same data once, however many events asked for it in the moment', () => {
    const batcher = new InvalidationBatcher(queryClient, 400);

    batcher.add([QUERY_KEYS.dashboardAll, QUERY_KEYS.orders.all]);
    batcher.add([QUERY_KEYS.dashboardAll]);
    vi.advanceTimersByTime(200);
    batcher.add([QUERY_KEYS.dashboardAll, QUERY_KEYS.deliveries.all]);
    expect(invalidate).not.toHaveBeenCalled();
    vi.advanceTimersByTime(200);

    expect(invalidate).toHaveBeenCalledTimes(3);
    expect(invalidate).toHaveBeenCalledWith({ queryKey: QUERY_KEYS.dashboardAll });
    expect(invalidate).toHaveBeenCalledWith({ queryKey: QUERY_KEYS.orders.all });
    expect(invalidate).toHaveBeenCalledWith({ queryKey: QUERY_KEYS.deliveries.all });
  });

  it('starts a new moment after each refresh', () => {
    const batcher = new InvalidationBatcher(queryClient, 400);
    batcher.add([QUERY_KEYS.orders.all]);
    vi.advanceTimersByTime(400);

    batcher.add([QUERY_KEYS.orders.all]);
    vi.advanceTimersByTime(400);

    expect(invalidate).toHaveBeenCalledTimes(2);
  });

  it('reads everything on screen again after a lost connection', () => {
    const batcher = new InvalidationBatcher(queryClient, 400);
    batcher.add([QUERY_KEYS.orders.all]);

    batcher.addEverything();
    vi.advanceTimersByTime(400);

    expect(invalidate).toHaveBeenCalledTimes(1);
    expect(invalidate).toHaveBeenCalledWith(undefined);
  });

  it('refreshes nothing once cancelled (the user left or changed)', () => {
    const batcher = new InvalidationBatcher(queryClient, 400);
    batcher.add([QUERY_KEYS.orders.all]);

    batcher.cancel();
    vi.advanceTimersByTime(1_000);

    expect(invalidate).not.toHaveBeenCalled();
  });
});
