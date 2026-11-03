import type { DeliveryAddress, OrderDetails, OrderStatus } from '@/types/order';
import type { OwnerOrderAction, OwnerOrderFilters } from '@/types/owner';

/** How many orders one page of the owner's list shows. */
export const OWNER_ORDERS_PAGE_SIZE = 20;

export const OWNER_ORDER_TAB_IDS = [
  'new',
  'accepted',
  'preparing',
  'ready',
  'on-the-way',
  'delivered',
  'rejected',
  'cancelled',
] as const;
export type OwnerOrderTabId = (typeof OWNER_ORDER_TAB_IDS)[number];

export interface OwnerOrderTab {
  id: OwnerOrderTabId;
  label: string;
  /** The orders of the tab: the ones in this status */
  status: OrderStatus;
  /** Shown when the tab has no orders (and no date filter is set) */
  emptyTitle: string;
  emptyMessage: string;
  /** Shown when the dates leave the tab with no orders */
  emptyFilteredTitle: string;
}

/**
 * The tabs of the order list, in the order of the kitchen's work. Each one is one status, so "which orders need
 * me" is a single click.
 */
export const OWNER_ORDER_TABS: readonly OwnerOrderTab[] = [
  {
    id: 'new',
    label: 'New',
    status: 'PLACED',
    emptyTitle: 'No new orders right now',
    emptyMessage: 'New orders show up here by themselves.',
    emptyFilteredTitle: 'No new orders on these dates',
  },
  {
    id: 'accepted',
    label: 'Accepted',
    status: 'ACCEPTED',
    emptyTitle: 'No accepted orders waiting',
    emptyMessage: 'Orders you accept wait here until you start preparing them.',
    emptyFilteredTitle: 'No accepted orders on these dates',
  },
  {
    id: 'preparing',
    label: 'Preparing',
    status: 'PREPARING',
    emptyTitle: 'Nothing is being prepared',
    emptyMessage: 'Orders you are cooking are listed here until you mark them ready.',
    emptyFilteredTitle: 'No orders were being prepared on these dates',
  },
  {
    id: 'ready',
    label: 'Ready for pickup',
    status: 'READY_FOR_PICKUP',
    emptyTitle: 'No orders are waiting for a delivery partner',
    emptyMessage: 'Orders marked ready wait here until a delivery partner picks them up.',
    emptyFilteredTitle: 'No orders were ready for pickup on these dates',
  },
  {
    id: 'on-the-way',
    label: 'On the way',
    status: 'OUT_FOR_DELIVERY',
    emptyTitle: 'No orders are on the way',
    emptyMessage: 'Orders picked up by a delivery partner are listed here.',
    emptyFilteredTitle: 'No orders were on the way on these dates',
  },
  {
    id: 'delivered',
    label: 'Delivered',
    status: 'DELIVERED',
    emptyTitle: 'No delivered orders yet',
    emptyMessage: 'Finished orders are listed here.',
    emptyFilteredTitle: 'No orders were delivered on these dates',
  },
  {
    id: 'rejected',
    label: 'Rejected',
    status: 'REJECTED',
    emptyTitle: 'No rejected orders',
    emptyMessage: 'Orders you turned down are listed here.',
    emptyFilteredTitle: 'No rejected orders on these dates',
  },
  {
    id: 'cancelled',
    label: 'Cancelled',
    status: 'CANCELLED',
    emptyTitle: 'No cancelled orders',
    emptyMessage: 'Orders cancelled by the customer or the platform are listed here.',
    emptyFilteredTitle: 'No cancelled orders on these dates',
  },
];

/** The tab named in the address; anything else (missing, old link, typo) opens "New". */
export function ownerOrderTabOf(id: string | undefined): OwnerOrderTab {
  return OWNER_ORDER_TABS.find((tab) => tab.id === id) ?? OWNER_ORDER_TABS[0];
}

export const OWNER_ORDER_SORTS = ['newest', 'oldest'] as const;
export type OwnerOrderSort = (typeof OWNER_ORDER_SORTS)[number];

export const OWNER_ORDER_SORT_LABELS: Record<OwnerOrderSort, string> = {
  newest: 'Newest first',
  oldest: 'Oldest first',
};

/** The new orders come oldest first (the customer who waited longest is served first); every other tab newest first. */
export function defaultOwnerOrderSort(tab: OwnerOrderTab): OwnerOrderSort {
  return tab.id === 'new' ? 'oldest' : 'newest';
}

/** The order named in the address, or the tab's own default (missing, old link, typo). */
export function ownerOrderSortOf(value: string | undefined, tab: OwnerOrderTab): OwnerOrderSort {
  return OWNER_ORDER_SORTS.find((sort) => sort === value) ?? defaultOwnerOrderSort(tab);
}

/** What the API is asked for: the tab's status, the days, and oldest first when asked (newest is its default). */
export function ownerOrderFilters(
  tab: OwnerOrderTab,
  fromDate?: string,
  toDate?: string,
  sort: OwnerOrderSort = defaultOwnerOrderSort(tab),
): OwnerOrderFilters {
  const filters: OwnerOrderFilters = { status: tab.status };
  if (fromDate) {
    filters.fromDate = fromDate;
  }
  if (toDate) {
    filters.toDate = toDate;
  }
  if (sort === 'oldest') {
    filters.sort = 'createdAt,asc';
  }
  return filters;
}

/** What a tab's count is asked for: its status and the same days as the list (the order does not matter). */
export function ownerOrderCountFilters(
  tab: OwnerOrderTab,
  fromDate?: string,
  toDate?: string,
): OwnerOrderFilters {
  const filters = ownerOrderFilters(tab, fromDate, toDate);
  delete filters.sort;
  return filters;
}

/** A day in the address must look like 'YYYY-MM-DD' (a hand-edited link may not); otherwise it is ignored. */
export function isoDayOrUndefined(value: string | undefined): string | undefined {
  return value && /^\d{4}-\d{2}-\d{2}$/.test(value) ? value : undefined;
}

/**
 * The kitchen's next steps for an order in this status. Only the restaurant's own steps: once the order is ready,
 * the delivery partners take over, and finished orders have nothing left to do.
 */
export function ownerOrderActions(status: OrderStatus): readonly OwnerOrderAction[] {
  switch (status) {
    case 'PLACED':
      return ['accept', 'reject'];
    case 'ACCEPTED':
      return ['preparing'];
    case 'PREPARING':
      return ['ready'];
    default:
      return [];
  }
}

export const OWNER_ORDER_ACTION_LABELS: Record<OwnerOrderAction, string> = {
  accept: 'Accept',
  reject: 'Reject',
  preparing: 'Start preparing',
  ready: 'Mark ready',
};

/** The toast after a step worked. */
export function ownerOrderActionDone(action: OwnerOrderAction, orderId: number): string {
  switch (action) {
    case 'accept':
      return `Order #${orderId} accepted.`;
    case 'reject':
      return `Order #${orderId} rejected. The customer is told why.`;
    case 'preparing':
      return `Order #${orderId} is being prepared.`;
    case 'ready':
      return `Order #${orderId} is ready for pickup.`;
  }
}

/** '+91 98765 43210' -> '+919876543210': a tel: link does not need the spaces and dashes. */
export function phoneDigits(phone: string): string {
  return phone.replace(/[^\d+]/g, '');
}

/** The delivery address on one line, without the parts the order does not have; null when there is none. */
export function formatAddress(address: DeliveryAddress | null): string | null {
  if (!address) {
    return null;
  }
  return [address.line1, address.line2, address.city, address.state, address.postalCode]
    .filter(Boolean)
    .join(', ');
}

export interface DeliveryTimes {
  /** When the order reached the kitchen: PLACED (after the payment for an online one), else when it was created */
  placedAt: string;
  /** The last DELIVERED step of the timeline; null when the timeline has none */
  deliveredAt: string | null;
  /** Whole minutes from placed to delivered; null without a delivery time */
  minutes: number | null;
}

/** How long a delivered order took from the kitchen to the door, from its timeline (nothing is estimated). */
export function deliveryTimes(order: Pick<OrderDetails, 'createdAt' | 'timeline'>): DeliveryTimes {
  const placedAt = order.timeline.find((entry) => entry.status === 'PLACED')?.at ?? order.createdAt;
  const deliveredAt =
    order.timeline.filter((entry) => entry.status === 'DELIVERED').at(-1)?.at ?? null;
  const minutes =
    deliveredAt === null
      ? null
      : Math.max(0, Math.round((Date.parse(deliveredAt) - Date.parse(placedAt)) / 60_000));
  return { placedAt, deliveredAt, minutes };
}

export const OWNER_ORDER_MESSAGES = {
  REJECT_REASON_REQUIRED: 'Please tell the customer why you cannot make this order.',
  REJECT_REASON_PLACEHOLDER: 'e.g. We have run out of an ingredient',
  NOT_FOUND_TITLE: 'We could not find this order',
  NOT_FOUND_MESSAGE: 'It may belong to another restaurant, or the link is wrong.',
  BAD_DATES: 'Choose dates that make a valid range to see the orders.',
} as const;
