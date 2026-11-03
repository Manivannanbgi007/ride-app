import type { CalendarEventType, CalendarTone } from '@/types/calendar';

/**
 * Colours of the calendar, from the tokens in index.css only (so light and dark mode follow): a tinted pill with
 * dark text of the same colour, like the status badges. info = blue, success = green, danger = red,
 * warning = yellow, neutral = grey.
 */
export const PILL_TONES: Record<CalendarTone, string> = {
  info: 'bg-info-100 text-info-ink ring-info-500/40',
  success: 'bg-brand-200 text-brand-text-deep ring-brand-500/40',
  danger: 'bg-danger-100 text-danger-ink ring-danger-500/40',
  warning: 'bg-warning-100 text-warning-ink ring-warning-500/50',
  neutral: 'bg-line text-ink ring-ink-soft/30',
};

/** The small round dot of a tone (the legend, the phone's day cells). */
export const DOT_TONES: Record<CalendarTone, string> = {
  info: 'bg-info-500',
  success: 'bg-brand-500',
  danger: 'bg-danger-500',
  warning: 'bg-warning-500',
  neutral: 'bg-ink-soft',
};

/** The colour each type of event has in the legend (the filter chips). */
export const TYPE_TONES: Record<CalendarEventType, CalendarTone> = {
  ORDERS: 'info',
  COUPONS: 'success',
  RESTAURANTS: 'warning',
};
