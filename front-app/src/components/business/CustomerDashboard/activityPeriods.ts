import { type DateRange } from '@/utils/dateRangeUtils';
import { periodOf, toIsoDate } from '@/utils/dateUtils';

/** The periods "Your order activity" can show. */
export const ACTIVITY_PERIODS = [
  { id: 'week', label: 'Last 7 days' },
  { id: 'month', label: 'Last 30 days' },
  { id: 'calendar', label: 'This month' },
] as const;
export type ActivityPeriod = (typeof ACTIVITY_PERIODS)[number]['id'];

// Shared with the owner's reports (utils/dateRangeUtils): kept importable from here for the customer dashboard
export { daysIn, previousRangeOf, type DateRange } from '@/utils/dateRangeUtils';

/** The period's first and last day ('YYYY-MM-DD', the customer's calendar, today included). */
export function rangeOf(period: ActivityPeriod, today: Date = new Date()): DateRange {
  if (period === 'week') {
    return periodOf(7, today);
  }
  if (period === 'month') {
    return periodOf(30, today);
  }
  return {
    fromDate: toIsoDate(new Date(today.getFullYear(), today.getMonth(), 1)),
    toDate: toIsoDate(today),
  };
}
