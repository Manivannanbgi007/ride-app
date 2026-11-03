import { DISPLAY_LOCALE } from '@/utils/constants';
import { parseIsoDate, periodOf, toIsoDate } from '@/utils/dateUtils';

/**
 * Date ranges for the filters and reports: calendar days ('YYYY-MM-DD'), both ends included, in the viewer's
 * calendar (the same days the APIs' fromDate / toDate take). Everything here is pure; "today" is passed in.
 */

/** A period: its first and last day ('YYYY-MM-DD', both included). */
export interface DateRange {
  fromDate: string;
  toDate: string;
}

/** The longest range the reports accept (order-service answers 400 above 366 days). */
export const MAX_RANGE_DAYS = 366;

/** The ready-made ranges of the date range picker, in the order they are offered. */
export const DATE_PRESETS = [
  { id: 'today', label: 'Today' },
  { id: 'yesterday', label: 'Yesterday' },
  { id: '7', label: 'Last 7 days' },
  { id: '30', label: 'Last 30 days' },
  { id: '90', label: 'Last 90 days' },
  { id: 'this-month', label: 'This month' },
  { id: 'last-month', label: 'Last month' },
] as const;
export type DatePresetId = (typeof DATE_PRESETS)[number]['id'];

const DAY_MS = 86_400_000;

/** One day as a range of its own. */
export function singleDay(date: string): DateRange {
  return { fromDate: date, toDate: date };
}

/** The days of a ready-made range, seen from `today`. */
export function presetRange(preset: DatePresetId, today: Date = new Date()): DateRange {
  const year = today.getFullYear();
  const month = today.getMonth();
  switch (preset) {
    case 'today':
      return singleDay(toIsoDate(today));
    case 'yesterday':
      return singleDay(toIsoDate(new Date(year, month, today.getDate() - 1)));
    case 'this-month':
      return { fromDate: toIsoDate(new Date(year, month, 1)), toDate: toIsoDate(today) };
    case 'last-month':
      // Day 0 of this month is the last day of the month before
      return {
        fromDate: toIsoDate(new Date(year, month - 1, 1)),
        toDate: toIsoDate(new Date(year, month, 0)),
      };
    default:
      return periodOf(Number(preset), today);
  }
}

/** '7' -> '7' (a known ready-made range); anything else -> undefined. */
export function presetIdOf(value: string | null | undefined): DatePresetId | undefined {
  return DATE_PRESETS.find((preset) => preset.id === value)?.id;
}

export function presetLabel(preset: DatePresetId): string {
  return DATE_PRESETS.find((entry) => entry.id === preset)?.label ?? '';
}

/**
 * The ready-made range these days are today, if any (dates picked by hand, or kept in a link, that happen to be
 * "Last 7 days"). The first match wins: a single day today is "Today", not "This month" on the 1st.
 */
export function matchingPreset(
  range: DateRange,
  today: Date = new Date(),
  presets: readonly DatePresetId[] = DATE_PRESETS.map((preset) => preset.id),
): DatePresetId | undefined {
  return presets.find((preset) => {
    const days = presetRange(preset, today);
    return days.fromDate === range.fromDate && days.toDate === range.toDate;
  });
}

/** How many days a period has, both ends included. */
export function daysIn(range: DateRange): number {
  const span = parseIsoDate(range.toDate).getTime() - parseIsoDate(range.fromDate).getTime();
  // Rounded: a day across a daylight-saving change is 23 or 25 hours long
  return Math.round(span / DAY_MS) + 1;
}

/** The period of the same length just before this one, to compare with: 12-18 Oct -> 5-11 Oct. */
export function previousRangeOf(range: DateRange): DateRange {
  const days = daysIn(range);
  const from = parseIsoDate(range.fromDate);
  const day = (offset: number) =>
    toIsoDate(new Date(from.getFullYear(), from.getMonth(), from.getDate() + offset));
  return { fromDate: day(-days), toDate: day(-1) };
}

/** A real calendar day written 'YYYY-MM-DD' ('2026-02-30' is not one). */
export function isIsoDay(value: string | undefined): value is string {
  if (!value || !/^\d{4}-\d{2}-\d{2}$/.test(value)) {
    return false;
  }
  return toIsoDate(parseIsoDate(value)) === value;
}

/**
 * What is wrong with a range, in words for the owner; undefined when it can be used. A range must have both days,
 * the last one not before the first, none after `lastAllowed` (today: there are no orders tomorrow), and at most
 * `maxDays` days.
 */
export function rangeProblem(
  range: Partial<DateRange>,
  lastAllowed: string,
  maxDays: number = MAX_RANGE_DAYS,
): string | undefined {
  const { fromDate, toDate } = range;
  if (!fromDate || !toDate) {
    return 'Choose the first and the last day.';
  }
  if (!isIsoDay(fromDate) || !isIsoDay(toDate)) {
    return 'These are not real dates.';
  }
  if (toDate < fromDate) {
    return 'The last day is before the first day.';
  }
  if (toDate > lastAllowed) {
    return 'The days cannot be in the future.';
  }
  if (daysIn({ fromDate, toDate }) > maxDays) {
    return `Choose at most ${maxDays} days.`;
  }
  return undefined;
}

/** '1 day', '7 days' */
export function dayCountLabel(days: number): string {
  return days === 1 ? '1 day' : `${days.toLocaleString(DISPLAY_LOCALE)} days`;
}

/**
 * A range in words, as short as it stays clear: 'Sat, 1 Nov 2026' (one day), '3 – 9 Oct 2026' (one month),
 * '26 Oct – 1 Nov 2026' (one year), '28 Dec 2025 – 3 Jan 2026'.
 */
export function formatRange(range: DateRange): string {
  const from = parseIsoDate(range.fromDate);
  const to = parseIsoDate(range.toDate);
  const write = (date: Date, parts: Intl.DateTimeFormatOptions) =>
    date.toLocaleDateString(DISPLAY_LOCALE, parts);
  const full: Intl.DateTimeFormatOptions = { day: 'numeric', month: 'short', year: 'numeric' };

  if (range.fromDate === range.toDate) {
    return `${write(from, { weekday: 'short' })}, ${write(from, full)}`;
  }
  if (from.getFullYear() !== to.getFullYear()) {
    return `${write(from, full)} – ${write(to, full)}`;
  }
  if (from.getMonth() !== to.getMonth()) {
    return `${write(from, { day: 'numeric', month: 'short' })} – ${write(to, full)}`;
  }
  return `${from.getDate()} – ${write(to, full)}`;
}
