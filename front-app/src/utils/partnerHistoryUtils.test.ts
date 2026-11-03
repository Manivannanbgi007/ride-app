import { describe, expect, it } from 'vitest';

import { deliverySummary } from '@/test/fixtures';
import { filterHistory, localDayOf, summarizeHistory } from '@/utils/partnerHistoryUtils';

/** A delivery delivered at local noon on the given day (so the time zone cannot move it to another day). */
const deliveredOn = (id: number, day: [number, number, number], extra = {}) =>
  deliverySummary({
    id,
    orderId: id + 100,
    status: 'DELIVERED',
    deliveredAt: new Date(day[0], day[1] - 1, day[2], 12, 0).toISOString(),
    ...extra,
  });

describe('localDayOf', () => {
  it('gives the local calendar day', () => {
    expect(localDayOf(new Date(2026, 9, 8, 23, 30).toISOString())).toBe('2026-10-08');
    expect(localDayOf(new Date(2026, 0, 2, 0, 5).toISOString())).toBe('2026-01-02');
  });
});

describe('filterHistory', () => {
  const rows = [
    deliveredOn(1, [2026, 10, 8], { restaurantName: 'Spice Garden' }),
    deliveredOn(2, [2026, 10, 9], { restaurantName: 'Pizza Hub', pickupCity: 'Mysuru' }),
    deliveredOn(3, [2026, 10, 10], { restaurantName: 'Dosa Corner' }),
  ];

  it('keeps everything without filters', () => {
    expect(filterHistory(rows, {})).toHaveLength(3);
  });

  it('searches the order number (with or without #), the restaurant and the city', () => {
    expect(filterHistory(rows, { search: '102' }).map((row) => row.id)).toEqual([2]);
    expect(filterHistory(rows, { search: '#103' }).map((row) => row.id)).toEqual([3]);
    expect(filterHistory(rows, { search: 'spice' }).map((row) => row.id)).toEqual([1]);
    expect(filterHistory(rows, { search: 'mysuru' }).map((row) => row.id)).toEqual([2]);
    expect(filterHistory(rows, { search: 'nothing' })).toEqual([]);
  });

  it('keeps the days in the range, both ends included', () => {
    const inRange = filterHistory(rows, { fromDate: '2026-10-09', toDate: '2026-10-10' });
    expect(inRange.map((row) => row.id)).toEqual([2, 3]);
    expect(filterHistory(rows, { fromDate: '2026-10-10' }).map((row) => row.id)).toEqual([3]);
    expect(filterHistory(rows, { toDate: '2026-10-08' }).map((row) => row.id)).toEqual([1]);
  });

  it('ignores an impossible range', () => {
    expect(filterHistory(rows, { fromDate: '2026-10-10', toDate: '2026-10-08' })).toHaveLength(3);
  });
});

describe('summarizeHistory', () => {
  it('counts outcomes and sums the cash of delivered orders only', () => {
    const summary = summarizeHistory([
      deliveredOn(1, [2026, 10, 8], { cashToCollect: 250 }),
      deliveredOn(2, [2026, 10, 8], { cashToCollect: 0 }),
      deliveredOn(3, [2026, 10, 8], { cashToCollect: null }),
      deliverySummary({ id: 4, status: 'CANCELLED', cashToCollect: 999 }),
      deliverySummary({ id: 5, status: 'PICKED_UP', cashToCollect: 100 }),
    ]);
    expect(summary).toEqual({
      delivered: 3,
      cancelled: 1,
      cashDeliveries: 1,
      cashCollected: 250,
      paidOnline: 2,
    });
  });
});
