import { ROUTES, routeTo, SERVICE_LABELS } from '@/utils/constants';
import { humanizeEnum } from '@/utils/formatUtils';
import { isWholeNumber } from '@/utils/validationUtils';

/** The record types the services write today (entityType is text: a new one is shown humanized). */
export const AUDIT_ENTITY_TYPE_LABELS: Record<string, string> = {
  USER: 'User',
  RESTAURANT: 'Restaurant',
  CATEGORY: 'Category',
  MENU_ITEM: 'Menu item',
  ORDER: 'Order',
  PAYMENT: 'Payment',
  NOTIFICATION: 'Notification',
  DELIVERY: 'Delivery',
  DELIVERY_PARTNER: 'Delivery partner',
  COUPON: 'Coupon',
  AUDIT_LOG: 'Audit log',
};

/** The service that saved a record, by its screen name ('restaurant-service' -> 'Restaurants'). */
export function serviceLabel(service: string): string {
  return service === 'admin-service' ? 'Admin' : (SERVICE_LABELS[service] ?? service);
}

export function entityTypeLabel(entityType: string): string {
  return AUDIT_ENTITY_TYPE_LABELS[entityType] ?? humanizeEnum(entityType);
}

/** The admin page of each record type (categories have one page for all). */
const RECORD_PAGES: Partial<Record<string, (id: number) => string>> = {
  USER: routeTo.customerDetails,
  RESTAURANT: routeTo.restaurantDetails,
  MENU_ITEM: routeTo.foodDetails,
  ORDER: routeTo.orderDetails,
  PAYMENT: routeTo.paymentDetails,
  NOTIFICATION: routeTo.notificationDetails,
  DELIVERY: routeTo.deliveryDetails,
  DELIVERY_PARTNER: routeTo.partnerDetails,
  COUPON: routeTo.couponDetails,
  CATEGORY: () => ROUTES.categories,
};

/**
 * The admin page of the record an audit entry is about, or null (no such page, or an entityId that is not a
 * single id, such as 'ALL' for "republish everything").
 */
export function recordLinkOf(entityType: string, entityId: string): string | null {
  const page = RECORD_PAGES[entityType];
  return page && isWholeNumber(entityId) ? page(Number(entityId)) : null;
}
