/**
 * What the live connection (WebSocket, notification-service) sends: a small "something changed" hint, never data
 * about people. The application then reads the current data through the REST APIs, which check what the user may see.
 */
export const LIVE_EVENT_TYPES = [
  'ORDER_STATUS_CHANGED',
  'ORDER_CANCELLED',
  'PAYMENT_STATUS_CHANGED',
  'DELIVERY_STATUS_CHANGED',
  'RESTAURANT_CHANGED',
  'CHAT_MESSAGE',
  'CHAT_READ',
] as const;

export type LiveEventType = (typeof LIVE_EVENT_TYPES)[number];

export interface LiveEvent {
  /** The platform event's id: the same event is never sent twice with different ids */
  id: string;
  type: LiveEventType;
  occurredAt: string;
  orderId?: number;
  deliveryId?: number;
  paymentId?: number;
  restaurantId?: number;
  /** The new status of the order, payment, delivery or restaurant */
  status?: string;
  previousStatus?: string;
  /** Chat events: the support conversation (the text is read over REST, never sent here) */
  conversationId?: number;
  /** CHAT_MESSAGE: the new message */
  messageId?: number;
  /** CHAT_MESSAGE: true when the support staff wrote it */
  fromStaff?: boolean;
  /** CHAT_READ: true when the staff read the participant's messages, false for the reverse */
  readByStaff?: boolean;
  /** CHAT_READ: every message up to this one is read */
  upToMessageId?: number;
}

/** HELLO: the connection is accepted. HEARTBEAT: still here. EVENT: something changed. */
export type LiveMessage =
  | { kind: 'HELLO'; at: string }
  | { kind: 'HEARTBEAT'; at: string }
  | { kind: 'EVENT'; at: string; event: LiveEvent };
