import { describe, expect, it } from 'vitest';

import {
  deliveryTimes,
  formatAddress,
  isoDayOrUndefined,
  OWNER_ORDER_TABS,
  ownerOrderActionDone,
  ownerOrderActions,
  ownerOrderFilters,
  ownerOrderSortOf,
  ownerOrderTabOf,
  phoneDigits,
} from '@/components/business/OwnerOrders/ownerOrderUtils';
import { ORDER_STATUSES } from '@/types/order';

describe('ownerOrderTabOf', () => {
  it('maps every tab to its own status', () => {
    expect(OWNER_ORDER_TABS.map((tab) => [tab.id, tab.status])).toEqual([
      ['new', 'PLACED'],
      ['accepted', 'ACCEPTED'],
      ['preparing', 'PREPARING'],
      ['ready', 'READY_FOR_PICKUP'],
      ['on-the-way', 'OUT_FOR_DELIVERY'],
      ['delivered', 'DELIVERED'],
      ['rejected', 'REJECTED'],
      ['cancelled', 'CANCELLED'],
    ]);
  });

  it('opens "New" for a missing or unknown tab', () => {
    expect(ownerOrderTabOf(undefined).id).toBe('new');
    expect(ownerOrderTabOf('nonsense').id).toBe('new');
    expect(ownerOrderTabOf('delivered').status).toBe('DELIVERED');
  });
});

describe('ownerOrderFilters', () => {
  it('asks for the new orders oldest first', () => {
    expect(ownerOrderFilters(ownerOrderTabOf('new'))).toEqual({
      status: 'PLACED',
      sort: 'createdAt,asc',
    });
  });

  it('leaves the order to the API (latest first) on the other tabs', () => {
    expect(ownerOrderFilters(ownerOrderTabOf('preparing'))).toEqual({ status: 'PREPARING' });
  });

  it('adds only the days that are set', () => {
    expect(ownerOrderFilters(ownerOrderTabOf('delivered'), '2026-10-01', undefined)).toEqual({
      status: 'DELIVERED',
      fromDate: '2026-10-01',
    });
    expect(ownerOrderFilters(ownerOrderTabOf('delivered'), '2026-10-01', '2026-10-08')).toEqual({
      status: 'DELIVERED',
      fromDate: '2026-10-01',
      toDate: '2026-10-08',
    });
  });
});

describe('isoDayOrUndefined', () => {
  it('keeps a day and drops anything else', () => {
    expect(isoDayOrUndefined('2026-10-08')).toBe('2026-10-08');
    expect(isoDayOrUndefined('08/10/2026')).toBeUndefined();
    expect(isoDayOrUndefined('')).toBeUndefined();
    expect(isoDayOrUndefined(undefined)).toBeUndefined();
  });
});

describe('ownerOrderActions', () => {
  it('gives the kitchen its next step', () => {
    expect(ownerOrderActions('PLACED')).toEqual(['accept', 'reject']);
    expect(ownerOrderActions('ACCEPTED')).toEqual(['preparing']);
    expect(ownerOrderActions('PREPARING')).toEqual(['ready']);
  });

  it('gives nothing once the delivery partners take over, or the order is over', () => {
    const without = ORDER_STATUSES.filter(
      (status) => !['PLACED', 'ACCEPTED', 'PREPARING'].includes(status),
    );
    expect(without).toContain('READY_FOR_PICKUP');
    expect(without).toContain('DELIVERED');
    for (const status of without) {
      expect(ownerOrderActions(status)).toEqual([]);
    }
  });
});

describe('ownerOrderActionDone', () => {
  it('names the order and what happened', () => {
    expect(ownerOrderActionDone('accept', 42)).toBe('Order #42 accepted.');
    expect(ownerOrderActionDone('reject', 42)).toContain('rejected');
    expect(ownerOrderActionDone('preparing', 42)).toBe('Order #42 is being prepared.');
    expect(ownerOrderActionDone('ready', 42)).toBe('Order #42 is ready for pickup.');
  });
});

describe('deliveryTimes', () => {
  const entry = (status: 'PAYMENT_PENDING' | 'PLACED' | 'DELIVERED', at: string) => ({
    status,
    actorType: 'SYSTEM' as const,
    actorUserId: null,
    note: null,
    at,
  });

  it('counts from when the order reached the kitchen to its delivery', () => {
    expect(
      deliveryTimes({
        createdAt: '2026-10-08T10:00:00Z',
        timeline: [
          entry('PAYMENT_PENDING', '2026-10-08T10:00:00Z'),
          entry('PLACED', '2026-10-08T10:05:00Z'),
          entry('DELIVERED', '2026-10-08T10:47:30Z'),
        ],
      }),
    ).toEqual({
      placedAt: '2026-10-08T10:05:00Z',
      deliveredAt: '2026-10-08T10:47:30Z',
      minutes: 43,
    });
  });

  it('uses the creation time without a PLACED step, and says nothing it does not know', () => {
    expect(deliveryTimes({ createdAt: '2026-10-08T10:00:00Z', timeline: [] })).toEqual({
      placedAt: '2026-10-08T10:00:00Z',
      deliveredAt: null,
      minutes: null,
    });
  });
});

describe('formatAddress and phoneDigits', () => {
  it('writes an address on one line without its empty parts', () => {
    expect(
      formatAddress({
        line1: '24 MG Road',
        line2: null,
        city: 'Bengaluru',
        state: null,
        postalCode: '560001',
        country: 'IN',
      }),
    ).toBe('24 MG Road, Bengaluru, 560001');
    expect(formatAddress(null)).toBeNull();
  });

  it('keeps only what a tel: link needs', () => {
    expect(phoneDigits('+91 98765-43210')).toBe('+919876543210');
  });
});

describe('ownerOrderSortOf and the order the API is asked for', () => {
  it("uses the tab's own order unless the address names another", () => {
    expect(ownerOrderSortOf(undefined, ownerOrderTabOf('new'))).toBe('oldest');
    expect(ownerOrderSortOf(undefined, ownerOrderTabOf('delivered'))).toBe('newest');
    expect(ownerOrderSortOf('oldest', ownerOrderTabOf('delivered'))).toBe('oldest');
    expect(ownerOrderSortOf('sideways', ownerOrderTabOf('new'))).toBe('oldest');
  });

  it('asks for oldest first only when chosen (newest is the API default)', () => {
    expect(ownerOrderFilters(ownerOrderTabOf('new'), undefined, undefined, 'newest')).toEqual({
      status: 'PLACED',
    });
    expect(ownerOrderFilters(ownerOrderTabOf('delivered'), undefined, undefined, 'oldest')).toEqual(
      {
        status: 'DELIVERED',
        sort: 'createdAt,asc',
      },
    );
  });
});
