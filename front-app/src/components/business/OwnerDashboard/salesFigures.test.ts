import { describe, expect, it } from 'vitest';

import type { SalesSummary } from '@/types/owner';

import { percentChange, salesFiguresOf, salesFilterOf, salesFilterParams } from './salesFigures';

const summary = (overrides: Partial<SalesSummary> = {}): SalesSummary => ({
  restaurantId: 1,
  fromDate: '2026-10-13',
  toDate: '2026-10-19',
  totalOrders: 10,
  ordersByStatus: { DELIVERED: 4, PLACED: 2, PREPARING: 2, CANCELLED: 1, REJECTED: 1 },
  acceptedOrdersValue: 1500,
  deliveredOrdersValue: 1000,
  currency: 'INR',
  ...overrides,
});

// Sunday 19 October 2026
const today = new Date(2026, 9, 19);

describe('salesFilterOf', () => {
  it('shows the last 7 days when the address names none', () => {
    expect(salesFilterOf({}, today)).toEqual({
      range: { fromDate: '2026-10-13', toDate: '2026-10-19' },
      preset: '7',
    });
  });

  it('takes a ready-made range of the address, worked out from today', () => {
    expect(salesFilterOf({ range: 'last-month' }, today)).toEqual({
      range: { fromDate: '2026-09-01', toDate: '2026-09-30' },
      preset: 'last-month',
    });
    expect(salesFilterOf({ range: 'today' }, today).range).toEqual({
      fromDate: '2026-10-19',
      toDate: '2026-10-19',
    });
  });

  it('takes days picked in the calendar, one day included', () => {
    expect(salesFilterOf({ from: '2026-10-01', to: '2026-10-09' }, today)).toEqual({
      range: { fromDate: '2026-10-01', toDate: '2026-10-09' },
    });
    expect(salesFilterOf({ from: '2026-10-05', to: '2026-10-05' }, today).range).toEqual({
      fromDate: '2026-10-05',
      toDate: '2026-10-05',
    });
  });

  it('says why days of the address cannot be used, and shows the last 7 days instead', () => {
    const week = { fromDate: '2026-10-13', toDate: '2026-10-19' };
    expect(salesFilterOf({ from: '2026-10-09', to: '2026-10-01' }, today)).toEqual({
      range: week,
      preset: '7',
      problem: 'The last day is before the first day.',
    });
    expect(salesFilterOf({ from: '2026-10-18', to: '2026-10-25' }, today).problem).toBe(
      'The days cannot be in the future.',
    );
    expect(salesFilterOf({ from: '2025-01-01', to: '2026-10-19' }, today).problem).toBe(
      'Choose at most 366 days.',
    );
    expect(salesFilterOf({ from: '2026-02-30', to: '2026-03-02' }, today).problem).toBe(
      'These are not real dates.',
    );
    expect(salesFilterOf({ from: '2026-10-01' }, today).problem).toBe(
      'Choose the first and the last day.',
    );
    expect(salesFilterOf({ range: '12' }, today)).toEqual({
      range: week,
      preset: '7',
      problem: '"12" is not a range this page knows.',
    });
  });
});

describe('salesFilterParams', () => {
  it('keeps a ready-made range by its name (none for the default) and picked days by their dates', () => {
    const days = { fromDate: '2026-10-01', toDate: '2026-10-09' };
    expect(salesFilterParams(days, '30')).toEqual({ range: '30' });
    expect(salesFilterParams(days, '7')).toEqual({ range: undefined });
    expect(salesFilterParams(days)).toEqual({ from: '2026-10-01', to: '2026-10-09' });
  });
});

describe('salesFiguresOf', () => {
  it("works the figures out from the summary's own totals", () => {
    expect(salesFiguresOf(summary())).toEqual({
      sales: 1000,
      orders: 10,
      delivered: 4,
      cancelled: 2,
      inProgress: 4,
      averageOrder: 250,
      cancelledShare: 20,
    });
  });

  it('has no average when nothing was delivered, and no share without orders', () => {
    const figures = salesFiguresOf(
      summary({ totalOrders: 1, ordersByStatus: { PLACED: 1 }, deliveredOrdersValue: 0 }),
    );
    expect(figures.averageOrder).toBeNull();
    expect(figures.inProgress).toBe(1);
    expect(
      salesFiguresOf(summary({ totalOrders: 0, ordersByStatus: {} })).cancelledShare,
    ).toBeNull();
  });
});

describe('percentChange', () => {
  it('is the change in percent, and null when there was nothing before', () => {
    expect(percentChange(125, 100)).toBe(25);
    expect(percentChange(50, 200)).toBe(-75);
    expect(percentChange(5, 0)).toBeNull();
  });
});
