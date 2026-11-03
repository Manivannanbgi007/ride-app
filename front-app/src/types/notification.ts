/** notification-service: the acting user's in-app inbox, and (admin) every notification the platform sends. */

/** What a notification is about (the text of each type is written by notification-service). */
export const NOTIFICATION_TYPES = [
  'WELCOME',
  'RESTAURANT_SUBMITTED',
  'RESTAURANT_APPROVED',
  'RESTAURANT_REJECTED',
  'RESTAURANT_LIVE',
  'RESTAURANT_DEACTIVATED',
  'RESTAURANT_SUSPENDED',
  'ORDER_PLACED',
  'ORDER_ACCEPTED',
  'ORDER_PREPARING',
  'ORDER_READY',
  'ORDER_OUT_FOR_DELIVERY',
  'ORDER_DELIVERED',
  'ORDER_CANCELLED',
  'PAYMENT_FAILED',
  'REFUND_COMPLETED',
  'NEW_ORDER_FOR_RESTAURANT',
  'ORDER_CANCELLED_FOR_RESTAURANT',
  'PARTNER_ON_THE_WAY',
  'DELIVERY_OFFERED',
  'DELIVERY_CANCELLED_FOR_PARTNER',
] as const;
export type NotificationType = (typeof NOTIFICATION_TYPES)[number];

/** EMAIL (SMTP), SMS (simulated), PUSH (simulated, and the in-app inbox). */
export const NOTIFICATION_CHANNELS = ['EMAIL', 'SMS', 'PUSH'] as const;
export type NotificationChannel = (typeof NOTIFICATION_CHANNELS)[number];

/**
 * PENDING: waiting to be sent or retried. FAILED: given up after the last attempt. SKIPPED: nothing to send it to
 * (an SMS for a user without a phone). Only FAILED and SKIPPED can be resent.
 */
export const NOTIFICATION_STATUSES = ['PENDING', 'SENT', 'FAILED', 'SKIPPED'] as const;
export type NotificationStatus = (typeof NOTIFICATION_STATUSES)[number];

export const RESENDABLE_STATUSES: readonly NotificationStatus[] = ['FAILED', 'SKIPPED'];

/** GET /api/v1/notifications/unread-count: the number on the bell. */
export interface UnreadCountResponse {
  unread: number;
}

/** One notification of the acting user's inbox (always sent; readAt null = unread). */
export interface InboxNotification {
  id: number;
  type: NotificationType;
  title: string;
  message: string;
  orderId: number | null;
  sentAt: string;
  readAt: string | null;
}

/** The admin view of a notification, with how it was delivered. */
export interface AdminNotification {
  id: number;
  type: NotificationType;
  channel: NotificationChannel;
  status: NotificationStatus;
  /** Null for a notification to a restaurant until it is sent (then: the owner) */
  recipientUserId: number | null;
  restaurantId: number | null;
  orderId: number | null;
  /** Email address, phone number or inbox actually used */
  recipientAddress: string | null;
  subject: string;
  body: string;
  attempts: number;
  /** While PENDING: the next try */
  nextAttemptAt: string | null;
  /** The error while retrying or when FAILED, the reason when SKIPPED */
  lastError: string | null;
  sourceEventId: string;
  requestId: string | null;
  createdAt: string;
  sentAt: string | null;
  readAt: string | null;
}

/** The admin notification list's filters (dates: created, 'YYYY-MM-DD', inclusive). */
export interface NotificationFilters {
  status?: NotificationStatus;
  channel?: NotificationChannel;
  type?: NotificationType;
  recipientUserId?: number;
  orderId?: number;
  restaurantId?: number;
  fromDate?: string;
  toDate?: string;
}

/** Notifications of a period (no dates: today) per status and channel; a missing key means 0. */
export interface NotificationSummary {
  fromDate: string;
  toDate: string;
  total: number;
  byStatus: Partial<Record<NotificationStatus, number>>;
  byChannel: Partial<Record<NotificationChannel, number>>;
}
