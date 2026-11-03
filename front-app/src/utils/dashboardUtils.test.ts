import { describe, expect, it } from 'vitest';

import { dashboardResponse } from '@/test/fixtures';
import {
  dashboardFigures,
  monthlyRevenueSeries,
  ordersByStatusBars,
  percentOf,
  sumOrdersPerDay,
} from '@/utils/dashboardUtils';

describe('dashboard calculations', () => {
  it('adds up the days of the period', () => {
    const totals = sumOrdersPerDay(dashboardResponse().charts.ordersPerDay);

    expect(totals).toEqual({ orders: 300, delivered: 50, cancelled: 150, revenue: 18591.1 });
  });

  it('gives no percentage when there is nothing to compare with', () => {
    expect(percentOf(1, 4)).toBe(25);
    expect(percentOf(0, 0)).toBeNull();
  });

  it('works out the cards and rates of a dashboard', () => {
    expect(dashboardFigures(dashboardResponse())).toEqual({
      totals: { orders: 300, delivered: 50, cancelled: 150, revenue: 18591.1 },
      deliveredRate: (50 / 300) * 100,
      cancelledRate: 50,
      paymentSuccessRate: 87,
    });
  });

  it('shows order figures as unknown, not zero, when order-service did not answer', () => {
    const figures = dashboardFigures(dashboardResponse({ unavailable: ['order-service'] }));

    expect(figures.totals).toBeNull();
    expect(figures.deliveredRate).toBeNull();
    expect(figures.paymentSuccessRate).toBe(87);
  });

  it('puts the statuses in the order an order moves through them, unknown ones last', () => {
    const bars = ordersByStatusBars({ CANCELLED: 3, NEW_STATUS: 1, PLACED: 5 });

    expect(bars).toEqual([
      { status: 'PLACED', label: 'Placed', count: 5 },
      { status: 'CANCELLED', label: 'Cancelled', count: 3 },
      { status: 'NEW_STATUS', label: 'NEW_STATUS', count: 1 },
    ]);
  });

  it('lines up this year and last year by month; months without a report are null', () => {
    const months = monthlyRevenueSeries(
      [
        {
          label: '2026-10',
          from: '2026-10-01',
          to: '2026-10-08',
          orders: 3,
          completed: 2,
          revenue: 500,
        },
      ],
      [
        {
          label: '2025-01',
          from: '2025-01-01',
          to: '2025-01-31',
          orders: 1,
          completed: 1,
          revenue: 90,
        },
      ],
    );

    expect(months).toHaveLength(12);
    expect(months[0]).toEqual({ month: 'Jan', thisYear: null, lastYear: 90 });
    expect(months[9]).toEqual({ month: 'Oct', thisYear: 500, lastYear: null });
  });
});
