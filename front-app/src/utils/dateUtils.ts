import { DISPLAY_LOCALE } from '@/utils/constants';

/**
 * The API sends calendar days as 'YYYY-MM-DD' (days in the business time zone). They are read as local dates,
 * so a day never shifts to the previous one in a time zone west of UTC.
 */
export function parseIsoDate(isoDate: string): Date {
  const [year, month, day] = isoDate.split('-').map(Number);
  return new Date(year, month - 1, day);
}

/** Date -> 'YYYY-MM-DD' (as the API expects in fromDate / toDate). */
export function toIsoDate(date: Date): string {
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${date.getFullYear()}-${month}-${day}`;
}

/**
 * The last `days` calendar days, today included, as the reports expect them: periodOf(7, 8 Oct) -> 2 Oct - 8 Oct.
 * (The same choice as the dashboard's "Filter Period".)
 */
export function periodOf(
  days: number,
  today: Date = new Date(),
): { fromDate: string; toDate: string } {
  const from = new Date(today.getFullYear(), today.getMonth(), today.getDate() - (days - 1));
  return { fromDate: toIsoDate(from), toDate: toIsoDate(today) };
}

/** '2026-10-08' -> '8 Oct 2026' */
export function formatDate(isoDate: string): string {
  return parseIsoDate(isoDate).toLocaleDateString(DISPLAY_LOCALE, {
    day: 'numeric',
    month: 'short',
    year: 'numeric',
  });
}

/** '2026-10-08' -> '8 Oct' */
export function formatDayMonth(isoDate: string): string {
  return parseIsoDate(isoDate).toLocaleDateString(DISPLAY_LOCALE, {
    day: 'numeric',
    month: 'short',
  });
}

/** '2026-10-08' -> 'Thu, 8 Oct' */
export function formatWeekdayDayMonth(isoDate: string): string {
  return parseIsoDate(isoDate).toLocaleDateString(DISPLAY_LOCALE, {
    weekday: 'short',
    day: 'numeric',
    month: 'short',
  });
}

/** A moment from the API (ISO-8601 instant) -> '8 Oct 2026, 2:05 pm' in the viewer's time zone */
export function formatDateTime(isoInstant: string): string {
  return new Date(isoInstant).toLocaleString(DISPLAY_LOCALE, {
    day: 'numeric',
    month: 'short',
    year: 'numeric',
    hour: 'numeric',
    minute: '2-digit',
  });
}

/** A moment from the API (ISO-8601 instant) -> '2:05 pm' in the viewer's time zone (an arrival time today) */
export function formatTime(isoInstant: string): string {
  return new Date(isoInstant).toLocaleTimeString(DISPLAY_LOCALE, {
    hour: 'numeric',
    minute: '2-digit',
  });
}

/** A time of day 'HH:mm' (24-hour, as the opening hours are stored) -> '9:00 am'; '23:59' -> '11:59 pm' */
export function formatClockTime(time: string): string {
  const [hours, minutes] = time.split(':').map(Number);
  return new Date(2026, 0, 1, hours, minutes).toLocaleTimeString(DISPLAY_LOCALE, {
    hour: 'numeric',
    minute: '2-digit',
  });
}

/** An hour of the day 0-23 -> '12 am', '1 pm' */
export function formatHour(hour: number): string {
  return new Date(2026, 0, 1, hour).toLocaleTimeString(DISPLAY_LOCALE, { hour: 'numeric' });
}

/** 'Jan' ... 'Dec' (index 0 = January) */
export function shortMonthName(monthIndex: number): string {
  return new Date(2000, monthIndex, 1).toLocaleDateString(DISPLAY_LOCALE, { month: 'short' });
}

const RELATIVE_UNITS: readonly { unit: Intl.RelativeTimeFormatUnit; seconds: number }[] = [
  { unit: 'day', seconds: 86_400 },
  { unit: 'hour', seconds: 3_600 },
  { unit: 'minute', seconds: 60 },
];

/** How long ago a moment was: '1 hour ago', '3 days ago'; under a minute 'just now'; over 30 days the date. */
export function formatRelativeTime(isoInstant: string, now: Date = new Date()): string {
  const seconds = Math.floor((now.getTime() - new Date(isoInstant).getTime()) / 1000);
  if (seconds < 60) {
    return 'just now';
  }
  if (seconds >= 30 * 86_400) {
    return formatDate(toIsoDate(new Date(isoInstant)));
  }
  const { unit, seconds: size } =
    RELATIVE_UNITS.find((entry) => seconds >= entry.seconds) ?? RELATIVE_UNITS[2];
  return new Intl.RelativeTimeFormat(DISPLAY_LOCALE, { numeric: 'always' }).format(
    -Math.floor(seconds / size),
    unit,
  );
}

/** A moment from the API -> '8 Oct 2026' (the day in the viewer's time zone) */
export function formatInstantDate(isoInstant: string): string {
  return new Date(isoInstant).toLocaleDateString(DISPLAY_LOCALE, {
    day: 'numeric',
    month: 'short',
    year: 'numeric',
  });
}

/** A moment from the API -> 'Oct 2026' (the month in the viewer's time zone, for "Partner since") */
export function formatMonthYear(isoInstant: string): string {
  return new Date(isoInstant).toLocaleDateString(DISPLAY_LOCALE, {
    month: 'short',
    year: 'numeric',
  });
}

/** A moment from the API -> '09/26' (month/year, for "Customer since") */
export function formatMonthYearShort(isoInstant: string): string {
  const date = new Date(isoInstant);
  const month = String(date.getMonth() + 1).padStart(2, '0');
  return `${month}/${String(date.getFullYear() % 100).padStart(2, '0')}`;
}
