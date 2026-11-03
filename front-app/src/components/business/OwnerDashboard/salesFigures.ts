import type { SalesSummary } from '@/types/owner';
import {
  presetIdOf,
  presetLabel,
  presetRange,
  rangeProblem,
  type DatePresetId,
  type DateRange,
} from '@/utils/dateRangeUtils';
import { toIsoDate } from '@/utils/dateUtils';

/** The overview's days when the address names none. */
export const DEFAULT_SALES_PRESET: DatePresetId = '7';

/** The days the overview (and the top dishes) show, as read from the address. */
export interface SalesFilter {
  range: DateRange;
  /** The ready-made range the days are ("Last 7 days"); none: days picked in the calendar */
  preset?: DatePresetId;
  /** Why the days in the address could not be used; the default range is shown instead */
  problem?: string;
}

/** The overview's part of the address. */
export interface SalesFilterParams {
  /** A ready-made range: '7', 'today', 'last-month'... */
  range?: string;
  /** Days picked in the calendar, 'YYYY-MM-DD' (one day: both the same) */
  from?: string;
  to?: string;
}

/**
 * The days the overview shows, from the address: `?range=30` (a ready-made range: a shared link keeps meaning "the
 * last 30 days") or `?from=2026-10-01&to=2026-10-07` (days picked in the calendar; one day when both are the
 * same). Nothing: the last 7 days. Days that cannot be used (an old or edited link) are not asked for: the reason
 * is returned and the last 7 days are shown instead.
 */
export function salesFilterOf(params: SalesFilterParams, today: Date = new Date()): SalesFilter {
  const fallback = {
    range: presetRange(DEFAULT_SALES_PRESET, today),
    preset: DEFAULT_SALES_PRESET,
  };
  if (params.from !== undefined || params.to !== undefined) {
    const range = { fromDate: params.from, toDate: params.to };
    const problem = rangeProblem(range, toIsoDate(today));
    return problem ? { ...fallback, problem } : { range: range as DateRange };
  }
  if (params.range === undefined) {
    return fallback;
  }
  const preset = presetIdOf(params.range);
  return preset
    ? { range: presetRange(preset, today), preset }
    : { ...fallback, problem: `"${params.range}" is not a range this page knows.` };
}

/**
 * The address for a choice of the date range picker: a ready-made range by its name (none for the default), days
 * picked in the calendar by their dates.
 */
export function salesFilterParams(range: DateRange, preset?: DatePresetId): SalesFilterParams {
  if (preset) {
    return { range: preset === DEFAULT_SALES_PRESET ? undefined : preset };
  }
  return { from: range.fromDate, to: range.toDate };
}

/** The default range's name, for "Showing the last 7 days instead". */
export const DEFAULT_SALES_LABEL = presetLabel(DEFAULT_SALES_PRESET);

/** The headline figures of a range, all from the platform's own totals (nothing is estimated). */
export interface SalesFigures {
  /** The value of the delivered orders (the "revenue" of the restaurant) */
  sales: number;
  orders: number;
  delivered: number;
  /** Cancelled or rejected */
  cancelled: number;
  /** Placed and not finished yet: waiting, accepted, preparing, ready or on the way */
  inProgress: number;
  /** The average delivered order; null when nothing was delivered */
  averageOrder: number | null;
  /** The share of the orders that were cancelled or rejected (0-100); null without orders */
  cancelledShare: number | null;
}

export function salesFiguresOf(summary: SalesSummary): SalesFigures {
  const delivered = summary.ordersByStatus.DELIVERED ?? 0;
  const cancelled =
    (summary.ordersByStatus.CANCELLED ?? 0) + (summary.ordersByStatus.REJECTED ?? 0);
  return {
    sales: summary.deliveredOrdersValue,
    orders: summary.totalOrders,
    delivered,
    cancelled,
    inProgress: Math.max(0, summary.totalOrders - delivered - cancelled),
    averageOrder: delivered > 0 ? summary.deliveredOrdersValue / delivered : null,
    cancelledShare: summary.totalOrders > 0 ? (cancelled / summary.totalOrders) * 100 : null,
  };
}

/** The change from `before` to `now` in percent; null when there was nothing before (no percentage of zero). */
export function percentChange(now: number, before: number): number | null {
  return before === 0 ? null : ((now - before) / before) * 100;
}
