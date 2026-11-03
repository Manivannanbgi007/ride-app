import type { CustomerOrderActivity, OrderStatus } from '@/types/order';
import { CHART_COLORS } from '@/utils/chartColors';

/**
 * The period's orders by where they stand now, in four groups the customer recognises. Each has its colour (the
 * legend's dot and the gradient's light end), the gradient's deep end, and a light tint for its row (a class name).
 */
export const STATUS_GROUPS = [
  {
    key: 'delivered',
    label: 'Delivered',
    statuses: ['DELIVERED'],
    color: CHART_COLORS.brand,
    to: CHART_COLORS.deepGreen,
    tint: 'bg-st-delivered-soft',
  },
  {
    key: 'inProgress',
    label: 'In progress',
    statuses: ['PLACED', 'ACCEPTED', 'PREPARING', 'READY_FOR_PICKUP', 'OUT_FOR_DELIVERY'],
    color: CHART_COLORS.info,
    to: CHART_COLORS.blue,
    tint: 'bg-st-ready-soft',
  },
  {
    key: 'waiting',
    label: 'Waiting for payment',
    statuses: ['PAYMENT_PENDING'],
    color: CHART_COLORS.warning,
    to: CHART_COLORS.sunset,
    tint: 'bg-st-preparing-soft',
  },
  {
    key: 'cancelled',
    label: 'Cancelled or declined',
    statuses: ['CANCELLED', 'REJECTED'],
    color: CHART_COLORS.danger,
    to: CHART_COLORS.rose,
    tint: 'bg-st-rejected-soft',
  },
] as const satisfies readonly {
  key: string;
  label: string;
  statuses: readonly OrderStatus[];
  color: string;
  to: string;
  tint: string;
}[];

export interface StatusGroupFigure {
  key: (typeof STATUS_GROUPS)[number]['key'];
  label: string;
  color: string;
  to: string;
  tint: string;
  count: number;
  /** Share of the period's orders, 0..100 */
  share: number;
}

/** One day of the period, as the charts draw it. */
export interface ActivityDay {
  date: string;
  orders: number;
  delivered: number;
  cancelled: number;
  /** Placed and not delivered, cancelled or declined (yet) */
  other: number;
  spent: number;
  /** Spent from the first day of the period up to this one */
  spentSoFar: number;
}

/** What the charts and figures of a period show; all of it is read from order-service's totals. */
export interface ActivityFigures {
  orders: number;
  spent: number;
  delivered: number;
  /** What a delivered order cost on average (null without delivered orders) */
  averageOrder: number | null;
  /** Share of the period's orders that were delivered, 0..100 (null without orders) */
  deliveredShare: number | null;
  groups: StatusGroupFigure[];
  days: ActivityDay[];
  /** On how many of the period's days something was ordered */
  daysWithOrders: number;
  /** The day with the most orders (the earliest of equals), null without orders */
  busiestDay: ActivityDay | null;
  /** The day the most was spent (the earliest of equals), null when nothing was spent */
  biggestSpendDay: ActivityDay | null;
}

export function figuresOf(activity: CustomerOrderActivity): ActivityFigures {
  const total = activity.totalOrders;
  const groups = STATUS_GROUPS.map((group) => {
    const count = group.statuses.reduce(
      (sum, status) => sum + (activity.ordersByStatus[status] ?? 0),
      0,
    );
    return {
      key: group.key,
      label: group.label,
      color: group.color,
      to: group.to,
      tint: group.tint,
      count,
      share: total > 0 ? (count / total) * 100 : 0,
    };
  });
  let spentSoFar = 0;
  const days = activity.perDay.map((day) => {
    spentSoFar += day.revenue;
    return {
      date: day.date,
      orders: day.orders,
      delivered: day.completed,
      cancelled: day.cancelled,
      other: Math.max(0, day.orders - day.completed - day.cancelled),
      spent: day.revenue,
      spentSoFar,
    };
  });
  const delivered = groups[0].count;
  const most = (pick: (day: ActivityDay) => number) =>
    days.reduce<ActivityDay | null>(
      (best, day) => (pick(day) > (best ? pick(best) : 0) ? day : best),
      null,
    );
  return {
    orders: total,
    spent: activity.spent,
    delivered,
    averageOrder: delivered > 0 ? activity.spent / delivered : null,
    deliveredShare: total > 0 ? (delivered / total) * 100 : null,
    groups,
    days,
    daysWithOrders: days.filter((day) => day.orders > 0).length,
    busiestDay: most((day) => day.orders),
    biggestSpendDay: most((day) => day.spent),
  };
}

/** The change from `before` to `now` in percent; null when there is nothing to compare with (before was 0). */
export function changeOf(now: number, before: number): number | null {
  return before === 0 ? null : ((now - before) / before) * 100;
}

/**
 * The three parts of a day's orders, bottom to top, as the bars and their legend show them: each bar part is a
 * gradient from its colour (top) to a deeper one; its legend chip has the light tint (a class name).
 */
export const ORDER_SERIES = [
  {
    key: 'delivered',
    label: 'Delivered',
    color: CHART_COLORS.brand,
    to: CHART_COLORS.deepGreen,
    tint: 'bg-st-delivered-soft',
  },
  {
    key: 'other',
    label: 'In progress or waiting',
    color: CHART_COLORS.info,
    to: CHART_COLORS.indigo,
    tint: 'bg-st-ready-soft',
  },
  {
    key: 'cancelled',
    label: 'Cancelled or declined',
    color: CHART_COLORS.danger,
    to: CHART_COLORS.rose,
    tint: 'bg-st-rejected-soft',
  },
] as const;
