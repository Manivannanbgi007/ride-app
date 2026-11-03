import axios from 'axios';

import { toApiError } from '@/api/apiErrorHandler';
import {
  ENTITY_TYPES,
  type ActionResolution,
  type AssistantCard,
  type AssistantHistoryMessage,
  type AssistantLink,
  type AssistantReply,
  type EntityItem,
  type EntityType,
  type OrderProposalLine,
  type PendingAction,
  type TableColumn,
} from '@/types/assistant';
import type { UserRole } from '@/types/user';
import { ERROR_MESSAGES } from '@/utils/errorMessages';
import { humanizeEnum } from '@/utils/formatUtils';

// ---------- Safe addresses ----------

/**
 * An address the assistant sent is used only when it is a path inside this application: it starts with one "/",
 * has no "//" anywhere (no protocol-relative or nested URL), no scheme, no backslash and no control character.
 * Anything else is ignored (the assistant's text is data, never a way to leave the application).
 */
export function safeAppPath(path: unknown): string | null {
  if (typeof path !== 'string' || !path.startsWith('/') || path.includes('//')) {
    return null;
  }
  const unsafe = [...path].some((character) => {
    const code = character.charCodeAt(0);
    return character === String.fromCharCode(92) || code <= 0x20 || code === 0x7f;
  });
  return unsafe ? null : path;
}

/** A picture is shown only from a web address or an address of this site. */
export function safeImageUrl(url: unknown): string | null {
  if (typeof url !== 'string') {
    return null;
  }
  const value = url.trim();
  return /^https?:\/\//i.test(value) || (value.startsWith('/') && !value.startsWith('//'))
    ? value
    : null;
}

// ---------- Reading the server's answers defensively ----------

type Json = Record<string, unknown>;

const isObject = (value: unknown): value is Json =>
  typeof value === 'object' && value !== null && !Array.isArray(value);
const asArray = (value: unknown): unknown[] => (Array.isArray(value) ? value : []);
const asString = (value: unknown, fallback = ''): string =>
  typeof value === 'string' ? value : typeof value === 'number' ? String(value) : fallback;
const asNullableString = (value: unknown): string | null => {
  const text = asString(value).trim();
  return text ? text : null;
};
const asNumber = (value: unknown): number | null =>
  typeof value === 'number' && Number.isFinite(value) ? value : null;

export function readLink(value: unknown): AssistantLink | null {
  if (!isObject(value)) {
    return null;
  }
  const path = safeAppPath(value.path);
  const label = asString(value.label).trim();
  return path && label ? { label, path } : null;
}

function readLinks(value: unknown): AssistantLink[] {
  return asArray(value).flatMap((entry) => {
    const link = readLink(entry);
    return link ? [link] : [];
  });
}

function readEntityItem(value: unknown): EntityItem | null {
  if (!isObject(value)) {
    return null;
  }
  const title = asString(value.title).trim();
  if (!title) {
    return null;
  }
  return {
    id: asString(value.id, title),
    title,
    subtitle: asNullableString(value.subtitle),
    badges: asArray(value.badges)
      .map((badge) => asString(badge))
      .filter(Boolean),
    meta: asArray(value.meta).flatMap((entry) =>
      isObject(entry) && asString(entry.label)
        ? [{ label: asString(entry.label), value: asString(entry.value) }]
        : [],
    ),
    imageUrl: safeImageUrl(value.imageUrl),
    link: readLink(value.link),
  };
}

function readCard(value: unknown): AssistantCard | null {
  if (!isObject(value)) {
    return null;
  }
  const title = asString(value.title);
  switch (value.kind) {
    case 'stats':
      return {
        kind: 'stats',
        title,
        stats: asArray(value.stats).flatMap((entry) =>
          isObject(entry)
            ? [
                {
                  label: asString(entry.label),
                  value: asString(entry.value),
                  hint: asNullableString(entry.hint),
                },
              ]
            : [],
        ),
      };
    case 'table': {
      const columns: TableColumn[] = asArray(value.columns).flatMap((entry) =>
        isObject(entry) && asString(entry.key)
          ? [
              {
                key: asString(entry.key),
                label: asString(entry.label, asString(entry.key)),
                align: entry.align === 'right' ? ('right' as const) : ('left' as const),
              },
            ]
          : [],
      );
      const rows = asArray(value.rows).flatMap((row) => {
        if (!isObject(row)) {
          return [];
        }
        const cells: Record<string, string | number | null> = {};
        for (const column of columns) {
          const cell = row[column.key];
          cells[column.key] =
            typeof cell === 'string' || (typeof cell === 'number' && Number.isFinite(cell))
              ? cell
              : null;
        }
        return [cells];
      });
      return { kind: 'table', title, columns, rows };
    }
    case 'entities': {
      const entityType: EntityType = ENTITY_TYPES.includes(value.entityType as EntityType)
        ? (value.entityType as EntityType)
        : 'FOOD';
      return {
        kind: 'entities',
        entityType,
        title,
        items: asArray(value.items).flatMap((item) => {
          const entity = readEntityItem(item);
          return entity ? [entity] : [];
        }),
      };
    }
    default:
      // A kind this version does not know is left out
      return null;
  }
}

export function readCards(value: unknown): AssistantCard[] {
  return asArray(value).flatMap((entry) => {
    const card = readCard(entry);
    return card ? [card] : [];
  });
}

const MAX_CHANGE_ROWS = 12;

/** A change other than an order: a title, what changes, an optional note and the Confirm button's text. */
function readChangeAction(value: Json, actionId: string, expiresAt: string): PendingAction | null {
  const details = value.details;
  const kind = asString(value.kind);
  if (!isObject(details) || !kind || kind === 'PLACE_ORDER') {
    return null;
  }
  const title = asString(details.title).trim();
  const confirmLabel = asString(details.confirmLabel).trim();
  if (!title || !confirmLabel) {
    return null;
  }
  const rows = asArray(details.rows)
    .flatMap((row) =>
      isObject(row) && asString(row.label).trim() && asString(row.value).trim()
        ? [{ label: asString(row.label).trim(), value: asString(row.value).trim() }]
        : [],
    )
    .slice(0, MAX_CHANGE_ROWS);
  return {
    actionId,
    kind,
    expiresAt,
    details: { title, rows, note: asNullableString(details.note), confirmLabel },
  };
}

export function readPendingAction(value: unknown): PendingAction | null {
  if (!isObject(value) || !asString(value.actionId)) {
    return null;
  }
  const expiresAt = asString(value.expiresAt);
  if (!expiresAt || Number.isNaN(Date.parse(expiresAt))) {
    return null;
  }
  if (!isObject(value.summary)) {
    return readChangeAction(value, asString(value.actionId), expiresAt);
  }
  const summary = value.summary;
  const items: OrderProposalLine[] = asArray(summary.items).flatMap((entry) =>
    isObject(entry)
      ? [
          {
            name: asString(entry.name),
            quantity: asNumber(entry.quantity) ?? 1,
            unitPrice: asNumber(entry.unitPrice),
            lineTotal: asNumber(entry.lineTotal),
          },
        ]
      : [],
  );
  return {
    actionId: asString(value.actionId),
    kind: 'PLACE_ORDER',
    expiresAt,
    summary: {
      restaurantName: asString(summary.restaurantName),
      items,
      deliveryAddress: asString(summary.deliveryAddress),
      paymentMethod: asString(summary.paymentMethod),
      itemsTotal: asNumber(summary.itemsTotal),
      discount: asNumber(summary.discount),
      deliveryFee: asNumber(summary.deliveryFee),
      tax: asNumber(summary.tax),
      total: asNumber(summary.total),
      currency: asString(summary.currency, 'INR') || 'INR',
      notes: asNullableString(summary.notes),
      warnings: asArray(summary.warnings)
        .map((warning) => asString(warning))
        .filter(Boolean),
    },
  };
}

export function readReply(value: unknown): AssistantReply {
  const reply = isObject(value) ? value : {};
  return {
    text: asString(reply.text),
    cards: readCards(reply.cards),
    links: readLinks(reply.links),
    pendingAction: readPendingAction(reply.pendingAction),
    toolsUsed: asArray(reply.toolsUsed)
      .map((tool) => asString(tool))
      .filter(Boolean),
  };
}

export function readHistoryMessage(value: unknown): AssistantHistoryMessage | null {
  if (!isObject(value) || (value.role !== 'USER' && value.role !== 'ASSISTANT')) {
    return null;
  }
  return {
    id: asString(value.id),
    role: value.role,
    text: asString(value.text),
    cards: readCards(value.cards),
    links: readLinks(value.links),
    pendingAction: readPendingAction(value.pendingAction),
    at: asNullableString(value.at),
  };
}

export { asArray as readArray, asNumber as readNumber, asString as readString, isObject };

// ---------- Labels ----------

const ROLE_LABELS: Partial<Record<UserRole, string>> = {
  PLATFORM_ADMIN: 'Administrator',
  RESTAURANT_ADMIN: 'Restaurant owner',
  CUSTOMER: 'Customer',
  DELIVERY_PARTNER: 'Delivery partner',
};

export function assistantRoleLabel(role: UserRole): string {
  return ROLE_LABELS[role] ?? humanizeEnum(role);
}

export function assistantGreeting(role: UserRole): string {
  switch (role) {
    case 'PLATFORM_ADMIN':
      return 'Hello! Ask me about orders, deliveries and restaurants on the platform.';
    case 'RESTAURANT_ADMIN':
      return 'Hello! Ask me about your restaurant: sales, orders, popular items and offers.';
    case 'CUSTOMER':
      return 'Hello! I can help you find food, check your orders and prepare a new one.';
    case 'DELIVERY_PARTNER':
      return 'Hello! Ask me about your current delivery, where to go, the cash to collect and your deliveries.';
    default:
      return 'Hello! How can I help you today?';
  }
}

/** The names the assistant's tools go by, as people say them. */
const TOOL_LABELS: Record<string, string> = {
  admin_order_stats: 'Order statistics',
  admin_list_orders: 'Orders',
  admin_top_restaurants: 'Top restaurants',
  admin_delivery_summary: 'Delivery summary',
  admin_delayed_deliveries: 'Delayed deliveries',
  admin_pending_restaurants: 'Pending restaurants',
  owner_my_restaurants: 'Your restaurants',
  owner_sales_summary: 'Sales summary',
  owner_orders: 'Orders',
  owner_top_items: 'Popular items',
  owner_delivery_partners: 'Delivery partners',
  owner_menu: 'Menu',
  current_offers: 'Offers',
  search_restaurants: 'Restaurant search',
  search_foods: 'Food search',
  restaurant_menu: 'Restaurant menu',
  my_addresses: 'Your addresses',
  my_orders: 'Your orders',
  my_order_status: 'Order status',
  prepare_order: 'Order preparation',
  partner_current_delivery: 'Current delivery',
  partner_delivery_details: 'Delivery details',
  partner_my_deliveries: 'Your deliveries',
  partner_my_stats: 'Your figures',
  prepare_cancel_order: 'Order cancellation',
  owner_prepare_order_step: 'Order update',
  owner_prepare_item_availability: 'Menu availability',
  partner_prepare_delivery_step: 'Delivery update',
  admin_prepare_cancel_order: 'Order cancellation',
  admin_prepare_reassign_delivery: 'Delivery reassignment',
};

export function toolLabel(tool: string): string {
  const known = TOOL_LABELS[tool];
  if (known) {
    return known;
  }
  const words = tool.replace(/[_-]+/g, ' ').trim().toLowerCase();
  return words ? words.charAt(0).toUpperCase() + words.slice(1) : tool;
}

/** "Order statistics, Orders" (each name once). */
export function toolsUsedText(tools: string[]): string {
  return [...new Set(tools.map(toolLabel))].join(', ');
}

// ---------- Times and names in the conversation ----------

/** "10:42 PM" for today, "13 Oct, 10:42 PM" for an earlier day (in the browser's language and time zone). */
export function formatMessageTime(at: number, now: number = Date.now()): string {
  const date = new Date(at);
  const time = date.toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' });
  if (date.toDateString() === new Date(now).toDateString()) {
    return time;
  }
  return `${date.toLocaleDateString([], { day: 'numeric', month: 'short' })}, ${time}`;
}

/** The first name to greet, when the signed-in user's name is known (the admin session may not have it). */
export function firstNameOf(user: object): string | null {
  const fullName = 'fullName' in user && typeof user.fullName === 'string' ? user.fullName : '';
  return fullName.trim().split(/\s+/)[0] || null;
}

// ---------- Countdown ----------

/** 523000 -> '8:43' (rounded up, so "0:00" means over). */
export function formatCountdown(remainingMs: number): string {
  const seconds = Math.max(0, Math.ceil(remainingMs / 1000));
  return `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, '0')}`;
}

// ---------- Remembering the panel's size (this browser) ----------

const MAXIMIZED_KEY = 'front-app.assistant.maximized';

/** Whether the panel opens as a large window (the user's last choice), else as the side sheet. */
export function loadAssistantMaximized(): boolean {
  try {
    return localStorage.getItem(MAXIMIZED_KEY) === 'on';
  } catch {
    return false;
  }
}

export function saveAssistantMaximized(on: boolean): void {
  try {
    localStorage.setItem(MAXIMIZED_KEY, on ? 'on' : 'off');
  } catch {
    // Without storage the panel simply opens as the side sheet next time
  }
}

// ---------- Remembering the conversation (per signed-in user, for this browser tab) ----------

const conversationKey = (userId: number) => `front-app.assistant.conversation.${userId}`;

export function loadConversationId(userId: number): string | null {
  try {
    return sessionStorage.getItem(conversationKey(userId)) || null;
  } catch {
    return null;
  }
}

export function saveConversationId(userId: number, conversationId: string | null): void {
  try {
    if (conversationId) {
      sessionStorage.setItem(conversationKey(userId), conversationId);
    } else {
      sessionStorage.removeItem(conversationKey(userId));
    }
  } catch {
    // Without storage a refresh simply starts a new conversation
  }
}

// ---------- Errors ----------

export interface AssistantErrorInfo {
  title: string;
  message: string;
  /** Worth sending the same question again */
  retryable: boolean;
  /** The server's own words, only kept for 409 and 410 (written for people) */
  serverMessage: string | null;
  status: number | undefined;
}

function responseOf(error: unknown) {
  const cause = toApiError(error).cause;
  return axios.isAxiosError(cause) ? cause.response : undefined;
}

function retryAfterSeconds(error: unknown): number | null {
  const header: unknown = responseOf(error)?.headers?.['retry-after'];
  const seconds = typeof header === 'string' || typeof header === 'number' ? Number(header) : NaN;
  return Number.isFinite(seconds) && seconds > 0 ? Math.ceil(seconds) : null;
}

function serverDetail(error: unknown): string | null {
  const data: unknown = responseOf(error)?.data;
  if (isObject(data)) {
    const detail = asString(data.detail).trim() || asString(data.message).trim();
    return detail ? detail.slice(0, 300) : null;
  }
  return null;
}

/** Any failure of a call to the assistant -> what the thread shows (never raw text of a 5xx). */
export function describeAssistantError(error: unknown): AssistantErrorInfo {
  const api = toApiError(error);
  const base = { serverMessage: null, status: api.status };
  if (api.kind === 'cancelled') {
    return {
      ...base,
      title: ERROR_MESSAGES.ASSISTANT_CANCELLED,
      message: ERROR_MESSAGES.ASSISTANT_CANCELLED_DETAIL,
      retryable: true,
    };
  }
  switch (api.status) {
    case 429: {
      const waitSeconds = retryAfterSeconds(error);
      const detail = serverDetail(error);
      return {
        ...base,
        serverMessage: detail,
        title: ERROR_MESSAGES.ASSISTANT_BUSY,
        message: `${detail ?? ERROR_MESSAGES.ASSISTANT_BUSY_DETAIL}${
          waitSeconds ? ` Please wait ${waitSeconds} seconds.` : ''
        }`,
        retryable: true,
      };
    }
    case 503:
      return {
        ...base,
        title: ERROR_MESSAGES.ASSISTANT_UNAVAILABLE,
        message: ERROR_MESSAGES.ASSISTANT_UNAVAILABLE_DETAIL,
        retryable: true,
      };
    case 504:
      return {
        ...base,
        title: ERROR_MESSAGES.ASSISTANT_TIMEOUT,
        message: ERROR_MESSAGES.ASSISTANT_TIMEOUT_DETAIL,
        retryable: true,
      };
    case 401:
    case 403:
      return {
        ...base,
        title: ERROR_MESSAGES.ASSISTANT_NOT_AVAILABLE,
        message: ERROR_MESSAGES.ASSISTANT_NOT_AVAILABLE_DETAIL,
        retryable: false,
      };
    default:
  }
  if (api.kind === 'timeout') {
    return {
      ...base,
      title: ERROR_MESSAGES.ASSISTANT_TIMEOUT,
      message: ERROR_MESSAGES.ASSISTANT_TIMEOUT_DETAIL,
      retryable: true,
    };
  }
  if (api.kind === 'network') {
    return {
      ...base,
      title: ERROR_MESSAGES.ASSISTANT_NETWORK,
      message: ERROR_MESSAGES.NETWORK_ERROR,
      retryable: true,
    };
  }
  return {
    ...base,
    serverMessage:
      api.status === 409 || api.status === 410 || api.status === 422 ? serverDetail(error) : null,
    title: ERROR_MESSAGES.ASSISTANT_FAILED,
    message: ERROR_MESSAGES.ASSISTANT_FAILED_DETAIL,
    retryable: true,
  };
}

/**
 * What a failed confirm or cancel means for the proposal. null = a hiccup (the card stays usable and an alert
 * says so); otherwise the proposal is over and the card shows how. A refusal (409, 422) shows the platform's reason,
 * for example "the restaurant has already accepted it".
 */
export function resolutionAfterActionError(
  error: unknown,
  action: 'confirm' | 'cancel',
  subject: 'order' | 'change' = 'order',
): ActionResolution | null {
  const info = describeAssistantError(error);
  const order = subject === 'order';
  switch (info.status) {
    case 410:
      return { status: 'expired' };
    case 409:
    case 422:
      return action === 'cancel'
        ? { status: 'cancelled' }
        : {
            status: 'failed',
            message:
              info.serverMessage ??
              (order
                ? ERROR_MESSAGES.ASSISTANT_ORDER_CANCELLED
                : ERROR_MESSAGES.ASSISTANT_CHANGE_REFUSED),
          };
    case 404:
    case 403:
      return {
        status: 'failed',
        message: order
          ? ERROR_MESSAGES.ASSISTANT_ORDER_FAILED
          : ERROR_MESSAGES.ASSISTANT_CHANGE_FAILED,
      };
    default:
      return null;
  }
}
