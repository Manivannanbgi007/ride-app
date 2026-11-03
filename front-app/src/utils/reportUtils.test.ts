import { describe, expect, it } from 'vitest';

import { dashboardResponse } from '@/test/fixtures';
import type { AdminNotification } from '@/types/notification';
import type { OrderReport } from '@/types/report';
import { entityTypeLabel, recordLinkOf, serviceLabel } from '@/utils/auditUtils';
import { toCsv } from '@/utils/csvUtils';
import { periodOf } from '@/utils/dateUtils';
import { formatMinutes } from '@/utils/formatUtils';
import { recipientOf } from '@/utils/notificationUtils';
import { orderReportCsv, reportFileName } from '@/utils/reportCsv';
import { reassignFormSchema } from '@/utils/validationUtils';

function orderReport(overrides: Partial<OrderReport> = {}): OrderReport {
  return {
    fromDate: '2026-10-02',
    toDate: '2026-10-08',
    currency: 'INR',
    totalOrders: 12,
    completed: 9,
    cancelled: 2,
    pending: 1,
    averageOrderValue: 412.5,
    byStatus: { DELIVERED: 9, CANCELLED: 2, PREPARING: 1 },
    perDay: dashboardResponse().charts.ordersPerDay,
    byRestaurant: dashboardResponse().charts.restaurantPerformance,
    byCity: [{ city: 'Bengaluru, KA', orders: 12, completed: 9, revenue: 3712.5 }],
    unavailable: [],
    ...overrides,
  };
}

describe('periodOf', () => {
  it('gives the last days, today included', () => {
    expect(periodOf(7, new Date(2026, 9, 8))).toEqual({
      fromDate: '2026-10-02',
      toDate: '2026-10-08',
    });
    expect(periodOf(30, new Date(2026, 2, 1))).toEqual({
      fromDate: '2026-01-31',
      toDate: '2026-03-01',
    });
  });
});

describe('formatMinutes', () => {
  it('keeps long waits readable', () => {
    expect(formatMinutes(12)).toBe('12 min');
    expect(formatMinutes(60)).toBe('1 h');
    expect(formatMinutes(95)).toBe('1 h 35 min');
    expect(formatMinutes(1507)).toBe('1 d 1 h');
    expect(formatMinutes(2880)).toBe('2 d');
  });
});

describe('report CSV', () => {
  it('names the file after the report and its period', () => {
    expect(reportFileName('orders', orderReport())).toBe('orders-report_2026-10-02_2026-10-08.csv');
  });

  it('puts the figures first and each list under its own title', () => {
    const rows = orderReportCsv(orderReport());
    expect(rows[0]).toEqual(['Order report', '2026-10-02 to 2026-10-08']);
    expect(rows).toContainEqual(['Orders', 12]);
    expect(rows).toContainEqual(['By delivery city']);
    // A city with a comma is quoted, so it stays one cell in a spreadsheet
    expect(toCsv(rows)).toContain('"Bengaluru, KA",12,9,3712.5');
  });

  it('leaves unknown figures empty instead of writing 0', () => {
    const rows = orderReportCsv(
      orderReport({ totalOrders: null, byCity: [], unavailable: ['order-service'] }),
    );
    expect(rows).toContainEqual(['Orders', null]);
    expect(toCsv(rows)).toContain('\r\nOrders,\r\n');
  });
});

describe('audit log links', () => {
  it('links a record to its admin page when it has one', () => {
    expect(recordLinkOf('RESTAURANT', '501')).toBe('/admin/restaurants/501');
    expect(recordLinkOf('DELIVERY_PARTNER', '31')).toBe('/admin/deliveries/partners/31');
    expect(recordLinkOf('CATEGORY', '4')).toBe('/admin/categories');
  });

  it('gives no link for "all records" or a type without a page', () => {
    expect(recordLinkOf('RESTAURANT', 'ALL')).toBeNull();
    expect(recordLinkOf('AUDIT_LOG', 'ALL')).toBeNull();
    expect(recordLinkOf('SOMETHING_NEW', '1')).toBeNull();
  });

  it('names services and record types for people', () => {
    expect(serviceLabel('restaurant-service')).toBe('Restaurants');
    expect(serviceLabel('admin-service')).toBe('Admin');
    expect(serviceLabel('future-service')).toBe('future-service');
    expect(entityTypeLabel('MENU_ITEM')).toBe('Menu item');
    expect(entityTypeLabel('LOYALTY_POINTS')).toBe('Loyalty points');
  });
});

describe('notification recipient', () => {
  const notification = (overrides: Partial<AdminNotification>) =>
    ({ recipientUserId: null, restaurantId: null, ...overrides }) as AdminNotification;

  it('is the user, or the restaurant until its owner is known', () => {
    expect(recipientOf(notification({ recipientUserId: 7 }))).toBe('User #7');
    expect(recipientOf(notification({ restaurantId: 3 }))).toBe('Restaurant #3');
    expect(recipientOf(notification({}))).toBe('—');
  });
});

describe('reassign form', () => {
  const valid = (hasPartner: boolean, partnerUserId: string, reason: string) =>
    reassignFormSchema(hasPartner).safeParse({ partnerUserId, reason }).success;

  it('needs a reason, and a partner when the delivery is already in the queue', () => {
    expect(valid(true, '', 'Flat tyre')).toBe(true);
    expect(valid(false, '', 'Speed it up')).toBe(false);
    expect(valid(false, '32', 'Speed it up')).toBe(true);
    expect(valid(true, '32', ' ')).toBe(false);
    expect(valid(true, '32', 'x'.repeat(301))).toBe(false);
  });
});
