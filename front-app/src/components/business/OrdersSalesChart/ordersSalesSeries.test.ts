import { describe, expect, it } from 'vitest';

import { busiestDayOf, salesDaysOf, salesReportRows } from './ordersSalesSeries';

describe('the chart days', () => {
  const days = salesDaysOf([
    { date: '2026-10-17', orders: 5, completed: 1, cancelled: 1, revenue: 300 },
    { date: '2026-10-18', orders: 6, completed: 4, cancelled: 2, revenue: 900 },
    { date: '2026-10-19', orders: 6, completed: 6, cancelled: 0, revenue: 1200 },
  ]);

  it('adds the orders still in progress to each day', () => {
    expect(days.map((day) => day.inProgress)).toEqual([3, 0, 0]);
  });

  it('finds the busiest day (the first of a tie), and none without orders', () => {
    expect(busiestDayOf(days)?.date).toBe('2026-10-18');
    expect(busiestDayOf(salesDaysOf([{ ...days[0], orders: 0, completed: 0, cancelled: 0 }]))).toBe(
      undefined,
    );
  });
});

describe('salesReportRows', () => {
  it('writes a header, then one row per day with the orders in progress', () => {
    const rows = salesReportRows(
      salesDaysOf([{ date: '2026-10-19', orders: 5, completed: 2, cancelled: 1, revenue: 640 }]),
    );
    expect(rows).toEqual([
      ['Date', 'Orders', 'Delivered', 'In progress', 'Cancelled or rejected', 'Sales (delivered)'],
      ['2026-10-19', 5, 2, 2, 1, 640],
    ]);
  });
});
