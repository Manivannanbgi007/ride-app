/** admin-service: GET /api/v1/admin/calendar (CalendarResponse). Days are plain 'YYYY-MM-DD' in `timeZone`. */

export const CALENDAR_EVENT_TYPES = ['ORDERS', 'COUPONS', 'RESTAURANTS'] as const;
export type CalendarEventType = (typeof CALENDAR_EVENT_TYPES)[number];

export type CalendarEventKind =
  'ORDERS_PLACED' | 'ORDERS_DELIVERED' | 'ORDERS_CANCELLED' | 'COUPON' | 'RESTAURANTS_REGISTERED';

export type CalendarTone = 'info' | 'success' | 'danger' | 'warning' | 'neutral';

export interface CalendarEvent {
  id: string;
  type: CalendarEventType;
  kind: CalendarEventKind;
  /** What the event says: "12 orders", a coupon's code */
  title: string;
  /** First and last day, both included (equal for a one-day event; a coupon may reach beyond the requested range) */
  start: string;
  end: string;
  /** Orders in a day, restaurants registered, times a coupon was used */
  count: number | null;
  /** A coupon's status ('ACTIVE', 'INACTIVE', 'EXPIRED', ...) */
  status: string | null;
  tone: CalendarTone;
  ref: { couponId: number } | null;
}

export interface CalendarResponse {
  from: string;
  to: string;
  timeZone: string;
  /** Event types that could not be loaded, e.g. ['COUPONS'] */
  unavailable: string[];
  events: CalendarEvent[];
}

export const CALENDAR_VIEWS = ['DATE', 'WEEK', 'MONTH', 'YEAR'] as const;
export type CalendarView = (typeof CALENDAR_VIEWS)[number];
