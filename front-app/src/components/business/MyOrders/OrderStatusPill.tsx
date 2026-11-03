import { Badge, type BadgeTone } from '@/components/common/Badge';
import type { OrderStatus } from '@/types/order';
import { CUSTOMER_ORDER_STATUS_LABELS } from '@/utils/constants';

const TONES: Record<OrderStatus, BadgeTone> = {
  PAYMENT_PENDING: 'warning',
  PLACED: 'info',
  ACCEPTED: 'info',
  PREPARING: 'info',
  READY_FOR_PICKUP: 'info',
  OUT_FOR_DELIVERY: 'info',
  DELIVERED: 'success',
  CANCELLED: 'danger',
  REJECTED: 'danger',
};

/** An order's status in the customer's words ("Waiting for the restaurant", "On the way"). */
export function OrderStatusPill({ status }: { status: OrderStatus }) {
  return <Badge tone={TONES[status]}>{CUSTOMER_ORDER_STATUS_LABELS[status]}</Badge>;
}
