import {
  ORDER_STATUS_TONES,
  type StatusTone,
} from '@/components/business/OwnerOrders/orderStatusTones';
import type { OrderStatus } from '@/types/order';

/**
 * An order status's colours as the customer sees them: the owner's (one colour per step), except an unpaid order,
 * which is amber, since the customer has to act on it (the owner never sees it, so it is slate there).
 */
export function customerStatusTone(status: OrderStatus): StatusTone {
  return ORDER_STATUS_TONES[status === 'PAYMENT_PENDING' ? 'PREPARING' : status];
}
