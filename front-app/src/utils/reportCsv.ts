import type { RestaurantPoint } from '@/types/dashboard';
import type {
  CustomerReport,
  DeliveryPerformanceReport,
  OrderReport,
  RestaurantReport,
  RevenueReport,
} from '@/types/report';

/**
 * The analytics reports as spreadsheet rows ("Save Report"): the key figures first, then each list under its own
 * title, separated by an empty line. An unknown figure (its service did not answer) is an empty cell, never 0.
 */
type CsvRows = (string | number | null)[][];

const section = (title: string, header: string[], rows: CsvRows): CsvRows => [
  [],
  [title],
  header,
  ...rows,
];

const RESTAURANT_HEADER = [
  'Restaurant #',
  'Restaurant',
  'Orders',
  'Delivered',
  'Revenue',
  'Cancelled %',
];
const restaurantRows = (restaurants: readonly RestaurantPoint[]): CsvRows =>
  restaurants.map((row) => [
    row.restaurantId,
    row.restaurantName,
    row.orders,
    row.completed,
    row.revenue,
    row.cancellationRate,
  ]);

const title = (name: string, report: { fromDate: string; toDate: string }): CsvRows => [
  [name, `${report.fromDate} to ${report.toDate}`],
];

/** 'orders-report_2026-10-01_2026-10-07.csv' */
export function reportFileName(kind: string, report: { fromDate: string; toDate: string }): string {
  return `${kind}-report_${report.fromDate}_${report.toDate}.csv`;
}

export function orderReportCsv(report: OrderReport): CsvRows {
  return [
    ...title('Order report', report),
    ['Orders', report.totalOrders],
    ['Delivered', report.completed],
    ['Cancelled or rejected', report.cancelled],
    ['In progress', report.pending],
    ['Average order value', report.averageOrderValue],
    ['Currency', report.currency],
    ...section(
      'By status',
      ['Status', 'Orders'],
      Object.entries(report.byStatus).map(([status, orders]) => [status, orders]),
    ),
    ...section(
      'Per day',
      ['Date', 'Orders', 'Delivered', 'Cancelled', 'Revenue'],
      report.perDay.map((day) => [day.date, day.orders, day.completed, day.cancelled, day.revenue]),
    ),
    ...section('By restaurant', RESTAURANT_HEADER, restaurantRows(report.byRestaurant)),
    ...section(
      'By delivery city',
      ['City', 'Orders', 'Delivered', 'Revenue'],
      report.byCity.map((city) => [city.city, city.orders, city.completed, city.revenue]),
    ),
  ];
}

export function revenueReportCsv(report: RevenueReport): CsvRows {
  return [
    ...title('Revenue report', report),
    ['Revenue', report.revenue],
    ['Discounts', report.discounts],
    ['Average order value', report.averageOrderValue],
    ['Collected online', report.onlinePayments?.collected ?? null],
    ['Refunded online', report.onlinePayments?.refunded ?? null],
    ['Currency', report.currency],
    ...section(
      `Revenue ${report.groupBy.toLowerCase()} by ${report.groupBy.toLowerCase()}`,
      ['Period', 'From', 'To', 'Orders', 'Delivered', 'Revenue'],
      report.periods.map((period) => [
        period.label,
        period.from,
        period.to,
        period.orders,
        period.completed,
        period.revenue,
      ]),
    ),
    ...section(
      'By payment method',
      ['Method', 'Orders', 'Delivered', 'Revenue'],
      Object.entries(report.byPaymentMethod).map(([method, row]) => [
        method,
        row.orders,
        row.completed,
        row.revenue,
      ]),
    ),
    ...section('By restaurant', RESTAURANT_HEADER, restaurantRows(report.byRestaurant)),
  ];
}

export function restaurantReportCsv(report: RestaurantReport): CsvRows {
  return [
    ...title('Restaurant report', report),
    ['Restaurants', report.total],
    ['Active', report.active],
    ['Inactive', report.inactive],
    ['Suspended', report.suspended],
    ['Waiting for approval', report.pendingApproval],
    ['New in the period', report.newRestaurants],
    ['With orders in the period', report.restaurantsWithOrders],
    ...section(
      'By status (now)',
      ['Status', 'Restaurants'],
      Object.entries(report.byStatus).map(([status, count]) => [status, count]),
    ),
    ...section('Top restaurants', RESTAURANT_HEADER, restaurantRows(report.topRestaurants)),
    ...section('Low performing', RESTAURANT_HEADER, restaurantRows(report.lowPerforming)),
    ...section('Most cancellations', RESTAURANT_HEADER, restaurantRows(report.mostCancellations)),
  ];
}

export function customerReportCsv(report: CustomerReport): CsvRows {
  return [
    ...title('Customer report', report),
    ['Customers', report.totalCustomers],
    ['Active accounts', report.activeAccounts],
    ['Deactivated accounts', report.deactivatedAccounts],
    ['New in the period', report.newCustomers],
    ['Ordered in the period', report.orderingCustomers],
    ['Did not order', report.notOrdering],
    ['Orders per ordering customer', report.ordersPerCustomer],
    ...section(
      'Order frequency',
      ['Orders in the period', 'Customers'],
      Object.entries(report.orderFrequency).map(([bucket, customers]) => [bucket, customers]),
    ),
    ...section(
      'Top customers',
      ['Customer #', 'Customer', 'Orders', 'Delivered', 'Spent'],
      report.topCustomers.map((row) => [
        row.customerId,
        row.customerName,
        row.orders,
        row.completed,
        row.spent,
      ]),
    ),
  ];
}

export function deliveryReportCsv(report: DeliveryPerformanceReport): CsvRows {
  return [
    ...title('Delivery report', report),
    ['Deliveries', report.total],
    ['Delivered', report.completed],
    ['Failed (cancelled)', report.failed],
    ['Average delivery minutes', report.averageDeliveryMinutes],
    ['Delayed now', report.delayedNow],
    ['Partners online now', report.partnersOnline],
    ['Waiting for a partner now', report.waitingForPartner],
    ...section(
      'By status',
      ['Status', 'Deliveries'],
      Object.entries(report.byStatus).map(([status, count]) => [status, count]),
    ),
    ...section(
      'Top partners',
      ['Partner #', 'Partner', 'Delivered', 'Average minutes', 'Offers missed'],
      report.topPartners.map((row) => [
        row.userId,
        row.name,
        row.delivered,
        row.averageDeliveryMinutes,
        row.offersMissed,
      ]),
    ),
  ];
}
