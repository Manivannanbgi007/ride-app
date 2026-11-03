import { BarList, type BarListItem } from '@/components/business/Analytics/BarList';
import { FigureTiles } from '@/components/business/Analytics/FigureTiles';
import {
  OrdersPerDayChart,
  RevenuePeriodsChart,
} from '@/components/business/Analytics/ReportCharts';
import {
  CityTable,
  CustomerRankTable,
  PartnerRankTable,
  PaymentMethodTable,
  RestaurantRankTable,
} from '@/components/business/Analytics/ReportTables';
import { Card } from '@/components/common/Card';
import { DELIVERY_STATUSES } from '@/types/delivery';
import { ORDER_STATUSES } from '@/types/order';
import {
  ORDER_FREQUENCY_BUCKETS,
  type CustomerReport,
  type DeliveryPerformanceReport,
  type OrderReport,
  type RestaurantReport,
  type RevenueReport,
} from '@/types/report';
import { RESTAURANT_STATUSES } from '@/types/restaurant';
import { CHART_COLORS, ORDER_STATUS_COLORS } from '@/utils/chartColors';
import {
  DEFAULT_CURRENCY,
  DELIVERY_STATUS_LABELS,
  ORDER_STATUS_LABELS,
  RESTAURANT_STATUS_LABELS,
} from '@/utils/constants';
import { percentOf } from '@/utils/dashboardUtils';
import { formatCurrency, formatNumber, formatPercent } from '@/utils/formatUtils';

/** Known keys in their usual order with their label, then any key the app does not know yet. */
function barsOf<K extends string>(
  counts: Record<string, number>,
  known: readonly K[],
  labels: Record<K, string>,
  colorOf?: (key: string) => string,
): BarListItem[] {
  const keys = [
    ...known.filter((key) => key in counts),
    ...Object.keys(counts).filter((key) => !known.includes(key as K)),
  ];
  return keys.map((key) => ({
    key,
    label: labels[key as K] ?? key,
    value: counts[key],
    color: colorOf?.(key),
  }));
}

const minutes = (value: number | null) => (value === null ? '—' : `${value} min`);

export function OrderReportView({ report }: { report: OrderReport }) {
  const currency = report.currency ?? DEFAULT_CURRENCY;
  const total = report.totalOrders ?? 0;
  return (
    <>
      <FigureTiles
        figures={[
          { label: 'Orders', value: formatNumber(report.totalOrders) },
          {
            label: 'Delivered',
            value: formatNumber(report.completed),
            hint: formatPercent(percentOf(report.completed ?? 0, total)),
            tone: 'brand',
          },
          {
            label: 'Cancelled or rejected',
            value: formatNumber(report.cancelled),
            hint: formatPercent(percentOf(report.cancelled ?? 0, total)),
            tone: 'danger',
          },
          { label: 'In progress', value: formatNumber(report.pending), tone: 'warning' },
          {
            label: 'Average order value',
            value: formatCurrency(report.averageOrderValue, currency),
            hint: 'Delivered orders',
            tone: 'info',
          },
        ]}
      />
      <div className="grid gap-4 xl:grid-cols-3">
        <Card title="Orders per day" className="xl:col-span-2">
          <OrdersPerDayChart days={report.perDay} />
        </Card>
        <Card title="By status" subtitle="Where the period's orders are now">
          <BarList
            unit="orders"
            items={barsOf(
              report.byStatus,
              ORDER_STATUSES,
              ORDER_STATUS_LABELS,
              (status) => ORDER_STATUS_COLORS[status as keyof typeof ORDER_STATUS_COLORS],
            )}
            emptyTitle="No orders in this period"
          />
        </Card>
      </div>
      <div className="grid gap-4 xl:grid-cols-2">
        <Card title="By restaurant" subtitle="Most revenue first">
          <RestaurantRankTable
            rows={report.byRestaurant}
            currency={currency}
            caption="Orders by restaurant"
          />
        </Card>
        <Card title="By delivery city">
          <CityTable rows={report.byCity} currency={currency} />
        </Card>
      </div>
    </>
  );
}

export function RevenueReportView({ report }: { report: RevenueReport }) {
  const currency = report.currency ?? DEFAULT_CURRENCY;
  const online = report.onlinePayments;
  return (
    <>
      <FigureTiles
        figures={[
          {
            label: 'Revenue',
            value: formatCurrency(report.revenue, currency),
            hint: 'Delivered orders',
            tone: 'brand',
          },
          { label: 'Discounts given', value: formatCurrency(report.discounts, currency) },
          {
            label: 'Average order value',
            value: formatCurrency(report.averageOrderValue, currency),
            tone: 'info',
          },
          {
            label: 'Collected online',
            value: formatCurrency(online?.collected, currency),
            hint: 'Card, UPI and wallet',
          },
          {
            label: 'Refunded online',
            value: formatCurrency(online?.refunded, currency),
            tone: 'danger',
          },
        ]}
      />
      <Card title="Revenue" subtitle="Delivered orders, by the day they were placed">
        <RevenuePeriodsChart periods={report.periods} currency={currency} />
      </Card>
      <div className="grid gap-4 xl:grid-cols-2">
        <Card title="By payment method" subtitle="Cash on delivery included">
          <PaymentMethodTable byMethod={report.byPaymentMethod} currency={currency} />
        </Card>
        <Card title="By restaurant" subtitle="Most revenue first">
          <RestaurantRankTable
            rows={report.byRestaurant}
            currency={currency}
            caption="Revenue by restaurant"
          />
        </Card>
      </div>
    </>
  );
}

/** A restaurant status's colour in the "By status" bars: live green, waiting amber, stopped red, the rest grey. */
function restaurantStatusColour(status: string): string {
  switch (status) {
    case 'ACTIVE':
      return CHART_COLORS.brand;
    case 'PENDING':
    case 'APPROVED':
      return CHART_COLORS.warning;
    case 'SUSPENDED':
    case 'REJECTED':
      return CHART_COLORS.danger;
    default:
      return CHART_COLORS.inkSoft;
  }
}

export function RestaurantReportView({ report }: { report: RestaurantReport }) {
  const currency = report.currency ?? DEFAULT_CURRENCY;
  const withoutOrders =
    report.active === null || report.restaurantsWithOrders === null
      ? null
      : Math.max(0, report.active - report.restaurantsWithOrders);
  return (
    <>
      <FigureTiles
        figures={[
          { label: 'Restaurants', value: formatNumber(report.total), hint: 'Right now' },
          { label: 'Active', value: formatNumber(report.active), tone: 'brand' },
          {
            label: 'Waiting for approval',
            value: formatNumber(report.pendingApproval),
            tone: 'warning',
          },
          { label: 'Suspended', value: formatNumber(report.suspended), tone: 'danger' },
          { label: 'New in the period', value: formatNumber(report.newRestaurants), tone: 'info' },
          {
            label: 'With orders',
            value: formatNumber(report.restaurantsWithOrders),
            hint:
              withoutOrders === null
                ? undefined
                : `${formatNumber(withoutOrders)} active without any`,
          },
        ]}
      />
      <div className="grid gap-4 xl:grid-cols-3">
        <Card title="By status" subtitle="Restaurants right now">
          {/* Every status, also the empty ones: the card shows the whole picture at a glance */}
          <BarList
            unit="restaurants"
            items={barsOf(
              {
                ...Object.fromEntries(RESTAURANT_STATUSES.map((status) => [status, 0])),
                ...report.byStatus,
              },
              RESTAURANT_STATUSES,
              RESTAURANT_STATUS_LABELS,
              restaurantStatusColour,
            )}
            emptyTitle="No restaurants yet"
          />
        </Card>
        <Card
          title="Top restaurants"
          subtitle="Most revenue in the period"
          className="xl:col-span-2"
        >
          <RestaurantRankTable
            rows={report.topRestaurants}
            currency={currency}
            caption="Top restaurants"
          />
        </Card>
      </div>
      <div className="grid gap-4 xl:grid-cols-2">
        <Card title="Low performing" subtitle="Restaurants with orders, least revenue first">
          <RestaurantRankTable
            rows={report.lowPerforming}
            currency={currency}
            caption="Low-performing restaurants"
          />
        </Card>
        <Card title="Most cancellations" subtitle="Highest share of cancelled or rejected orders">
          <RestaurantRankTable
            rows={report.mostCancellations}
            currency={currency}
            caption="Restaurants with the most cancellations"
          />
        </Card>
      </div>
    </>
  );
}

const FREQUENCY_LABELS: Record<(typeof ORDER_FREQUENCY_BUCKETS)[number], string> = {
  '1': '1 order',
  '2-3': '2 to 3 orders',
  '4-5': '4 to 5 orders',
  '6+': '6 or more',
};

export function CustomerReportView({ report }: { report: CustomerReport }) {
  const currency = report.currency ?? DEFAULT_CURRENCY;
  return (
    <>
      <FigureTiles
        figures={[
          { label: 'Customers', value: formatNumber(report.totalCustomers), hint: 'Accounts now' },
          { label: 'New in the period', value: formatNumber(report.newCustomers), tone: 'info' },
          {
            label: 'Ordered',
            value: formatNumber(report.orderingCustomers),
            hint: 'At least one order',
            tone: 'brand',
          },
          {
            label: 'Did not order',
            value: formatNumber(report.notOrdering),
            hint: 'Inactive in the period',
            tone: 'warning',
          },
          {
            label: 'Deactivated',
            value: formatNumber(report.deactivatedAccounts),
            hint: 'By an admin',
            tone: 'danger',
          },
          {
            label: 'Orders per customer',
            value: report.ordersPerCustomer === null ? '—' : report.ordersPerCustomer.toFixed(2),
            hint: 'Of the ones who ordered',
          },
        ]}
      />
      <div className="grid gap-4 xl:grid-cols-3">
        <Card title="Order frequency" subtitle="Customers by how often they ordered">
          <BarList
            unit="customers"
            items={barsOf(
              report.orderFrequency,
              ORDER_FREQUENCY_BUCKETS,
              FREQUENCY_LABELS,
              () => CHART_COLORS.info,
            )}
            emptyTitle="No orders in this period"
          />
        </Card>
        <Card title="Top customers" subtitle="Most spent in the period" className="xl:col-span-2">
          <CustomerRankTable rows={report.topCustomers} currency={currency} />
        </Card>
      </div>
    </>
  );
}

const DELIVERY_STATUS_COLORS: Record<string, string> = {
  PENDING: CHART_COLORS.warning,
  ASSIGNED: CHART_COLORS.warning,
  ACCEPTED: CHART_COLORS.info,
  PICKED_UP: CHART_COLORS.info,
  DELIVERED: CHART_COLORS.brand,
  CANCELLED: CHART_COLORS.danger,
};

export function DeliveryReportView({ report }: { report: DeliveryPerformanceReport }) {
  const total = report.total ?? 0;
  return (
    <>
      <FigureTiles
        figures={[
          { label: 'Deliveries', value: formatNumber(report.total) },
          {
            label: 'Delivered',
            value: formatNumber(report.completed),
            hint: formatPercent(percentOf(report.completed ?? 0, total)),
            tone: 'brand',
          },
          {
            label: 'Failed',
            value: formatNumber(report.failed),
            hint: 'Cancelled before arriving',
            tone: 'danger',
          },
          {
            label: 'Average delivery',
            value: minutes(report.averageDeliveryMinutes),
            hint: 'Food ready to delivered',
            tone: 'info',
          },
          {
            label: 'Delayed now',
            value: formatNumber(report.delayedNow),
            hint: 'Right now',
            tone: report.delayedNow ? 'danger' : 'ink',
          },
          {
            label: 'Partners online',
            value: formatNumber(report.partnersOnline),
            hint:
              report.waitingForPartner === null
                ? 'Right now'
                : `${formatNumber(report.waitingForPartner)} deliveries waiting for one`,
          },
        ]}
      />
      <div className="grid gap-4 xl:grid-cols-3">
        <Card title="By status" subtitle="Deliveries started in the period">
          <BarList
            unit="deliveries"
            items={barsOf(
              report.byStatus,
              DELIVERY_STATUSES,
              DELIVERY_STATUS_LABELS,
              (status) => DELIVERY_STATUS_COLORS[status] ?? CHART_COLORS.inkSoft,
            )}
            emptyTitle="No deliveries in this period"
          />
        </Card>
        <Card
          title="Top partners"
          subtitle="Most deliveries in the period"
          className="xl:col-span-2"
        >
          <PartnerRankTable rows={report.topPartners} />
        </Card>
      </div>
    </>
  );
}
