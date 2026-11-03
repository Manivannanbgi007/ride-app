import type { QueryKey } from '@tanstack/react-query';

import type { LiveEvent } from '@/types/live';
import type { ActingUser } from '@/types/user';
import { QUERY_KEYS } from '@/utils/queryKeys';

/**
 * Which cached API data a live event makes out of date. A live event only says "this changed": the screens that
 * show the data read it again through the normal REST APIs, so what each user sees is still decided by the server.
 * Only data that is on screen is read again (React Query leaves the rest to load when it is opened).
 */
export function queryKeysFor(event: LiveEvent, user: ActingUser): QueryKey[] {
  if (user.role === 'PLATFORM_ADMIN') {
    return adminKeysFor(event);
  }
  if (user.role === 'DELIVERY_PARTNER') {
    return partnerKeysFor(event, user);
  }
  if (user.role === 'RESTAURANT_ADMIN') {
    return ownerKeysFor(event, user);
  }
  return personalKeysFor(event, user);
}

/**
 * A restaurant owner hears about the orders of their restaurants (new, paid, cancelled, picked up) and about their
 * restaurants themselves: the order lists, the status counts, the open order and the dashboard's figures of that
 * restaurant are read again (all of them sit under one key), and their notifications.
 */
function ownerKeysFor(event: LiveEvent, user: ActingUser): QueryKey[] {
  const inbox = QUERY_KEYS.inboxAll;
  switch (event.type) {
    case 'ORDER_STATUS_CHANGED':
    case 'ORDER_CANCELLED':
    case 'DELIVERY_STATUS_CHANGED':
      return [
        event.restaurantId === undefined
          ? QUERY_KEYS.owner.allOrders(user.id)
          : QUERY_KEYS.owner.ordersAll(user.id, event.restaurantId),
        inbox,
      ];
    case 'PAYMENT_STATUS_CHANGED':
      // Not sent to owners (payments are the customer's business): only the notifications, should one come
      return [inbox];
    case 'RESTAURANT_CHANGED':
      // The restaurants (header selector, status) and each one's details sit under this key
      return [QUERY_KEYS.owner.restaurants(user.id), inbox];
    case 'CHAT_MESSAGE':
    case 'CHAT_READ':
      return [QUERY_KEYS.chat.mineAll(user.id)];
  }
}

/**
 * A delivery partner hears about the deliveries they are on (an offer, the order cancelled): their current delivery,
 * history and status are read again, and their notifications.
 */
function partnerKeysFor(event: LiveEvent, user: ActingUser): QueryKey[] {
  switch (event.type) {
    case 'DELIVERY_STATUS_CHANGED':
    case 'ORDER_STATUS_CHANGED':
    case 'ORDER_CANCELLED':
      return [QUERY_KEYS.partnerArea.all(user.id), QUERY_KEYS.inboxAll];
    case 'CHAT_MESSAGE':
    case 'CHAT_READ':
      return [QUERY_KEYS.chat.mineAll(user.id)];
    default:
      return [QUERY_KEYS.inboxAll];
  }
}

/** The admin area: the dashboard, the lists and the figures that follow the whole platform. */
function adminKeysFor(event: LiveEvent): QueryKey[] {
  const shared = [QUERY_KEYS.dashboardAll, QUERY_KEYS.reportsAll];
  switch (event.type) {
    case 'ORDER_STATUS_CHANGED':
    case 'ORDER_CANCELLED':
      return [
        ...shared,
        QUERY_KEYS.orders.all,
        QUERY_KEYS.deliveries.all,
        QUERY_KEYS.adminNotifications.all,
      ];
    case 'PAYMENT_STATUS_CHANGED':
      return [...shared, QUERY_KEYS.payments.all, QUERY_KEYS.refunds.all, QUERY_KEYS.orders.all];
    case 'DELIVERY_STATUS_CHANGED':
      return [...shared, QUERY_KEYS.deliveries.all, QUERY_KEYS.partners.all, QUERY_KEYS.orders.all];
    case 'RESTAURANT_CHANGED':
      return [QUERY_KEYS.dashboardAll, QUERY_KEYS.restaurants.all];
    case 'CHAT_MESSAGE':
    case 'CHAT_READ':
      // The inbox, the number on "Chat" and the open conversation's messages
      return [QUERY_KEYS.chat.staffAll];
  }
}

/**
 * A customer: their own orders and the order being tracked, and their notifications (the bell and the inbox), since
 * most of these events also produce one.
 */
function personalKeysFor(event: LiveEvent, user: ActingUser): QueryKey[] {
  const inbox = QUERY_KEYS.inboxAll;
  switch (event.type) {
    case 'ORDER_STATUS_CHANGED':
    case 'ORDER_CANCELLED':
    case 'PAYMENT_STATUS_CHANGED':
    case 'DELIVERY_STATUS_CHANGED':
      return [
        QUERY_KEYS.customer.allOrders(user.id),
        QUERY_KEYS.customer.allTracking(user.id),
        inbox,
      ];
    case 'RESTAURANT_CHANGED':
      return [inbox];
    case 'CHAT_MESSAGE':
    case 'CHAT_READ':
      // The own conversation (its unread count) and its messages
      return [QUERY_KEYS.chat.mineAll(user.id)];
  }
}
