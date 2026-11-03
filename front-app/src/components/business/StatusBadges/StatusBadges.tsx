import { Badge, type BadgeTone } from '@/components/common/Badge';
import type { CategoryStatus, MenuItemStatus } from '@/types/catalog';
import type { CouponAvailability, RedemptionStatus } from '@/types/coupon';
import type { DeliveryStatus, PartnerStatus } from '@/types/delivery';
import type { NotificationStatus } from '@/types/notification';
import type { OrderStatus, PaymentStatus } from '@/types/order';
import type { PaymentRecordStatus, RefundStatus } from '@/types/payment';
import type { RestaurantStatus } from '@/types/restaurant';
import type { UserRole, UserStatus } from '@/types/user';
import {
  CATEGORY_STATUS_LABELS,
  COUPON_AVAILABILITY_LABELS,
  DELIVERY_STATUS_LABELS,
  NOTIFICATION_STATUS_LABELS,
  MENU_ITEM_STATUS_LABELS,
  ORDER_STATUS_LABELS,
  PARTNER_STATUS_LABELS,
  PAYMENT_RECORD_STATUS_LABELS,
  PAYMENT_STATUS_LABELS,
  REDEMPTION_STATUS_LABELS,
  REFUND_STATUS_LABELS,
  RESTAURANT_STATUS_LABELS,
  USER_ROLE_LABELS,
  USER_STATUS_LABELS,
} from '@/utils/constants';

/** Every status as a coloured label, the same on every screen: green = fine, red = ended badly, yellow = waiting. */

const ORDER_STATUS_TONES: Record<OrderStatus, BadgeTone> = {
  PAYMENT_PENDING: 'warning',
  PLACED: 'info',
  ACCEPTED: 'info',
  PREPARING: 'warning',
  READY_FOR_PICKUP: 'warning',
  OUT_FOR_DELIVERY: 'info',
  DELIVERED: 'success',
  CANCELLED: 'danger',
  REJECTED: 'danger',
};

const PAYMENT_STATUS_TONES: Record<PaymentStatus, BadgeTone> = {
  PENDING: 'warning',
  PAID: 'success',
  FAILED: 'danger',
  REFUNDED: 'neutral',
};

const MENU_ITEM_STATUS_TONES: Record<MenuItemStatus, BadgeTone> = {
  ACTIVE: 'success',
  DISABLED: 'neutral',
  BLOCKED: 'danger',
};

export function OrderStatusBadge({ status }: { status: OrderStatus }) {
  return (
    <Badge dot tone={ORDER_STATUS_TONES[status]}>
      {ORDER_STATUS_LABELS[status]}
    </Badge>
  );
}

export function PaymentStatusBadge({ status }: { status: PaymentStatus }) {
  return (
    <Badge dot tone={PAYMENT_STATUS_TONES[status]}>
      {PAYMENT_STATUS_LABELS[status]}
    </Badge>
  );
}

export function UserStatusBadge({ status }: { status: UserStatus }) {
  return (
    <Badge dot tone={status === 'ACTIVE' ? 'success' : 'danger'}>
      {USER_STATUS_LABELS[status]}
    </Badge>
  );
}

export function RoleBadge({ role }: { role: UserRole }) {
  return (
    <Badge dot tone={role === 'PLATFORM_ADMIN' ? 'warning' : 'info'}>
      {USER_ROLE_LABELS[role]}
    </Badge>
  );
}

export function MenuItemStatusBadge({ status }: { status: MenuItemStatus }) {
  return (
    <Badge dot tone={MENU_ITEM_STATUS_TONES[status]}>
      {MENU_ITEM_STATUS_LABELS[status]}
    </Badge>
  );
}

const RESTAURANT_STATUS_TONES: Record<RestaurantStatus, BadgeTone> = {
  PENDING: 'warning',
  APPROVED: 'info',
  REJECTED: 'danger',
  ACTIVE: 'success',
  INACTIVE: 'neutral',
  SUSPENDED: 'danger',
};

const COUPON_AVAILABILITY_TONES: Record<CouponAvailability, BadgeTone> = {
  AVAILABLE: 'success',
  SCHEDULED: 'info',
  EXPIRED: 'neutral',
  USED_UP: 'warning',
  INACTIVE: 'neutral',
};

const REDEMPTION_STATUS_TONES: Record<RedemptionStatus, BadgeTone> = {
  RESERVED: 'warning',
  APPLIED: 'info',
  REDEEMED: 'success',
  RELEASED: 'neutral',
  EXPIRED: 'neutral',
};

const PAYMENT_RECORD_STATUS_TONES: Record<PaymentRecordStatus, BadgeTone> = {
  INITIATED: 'warning',
  PROCESSING: 'warning',
  SUCCESS: 'success',
  FAILED: 'danger',
  REFUNDED: 'neutral',
};

const REFUND_STATUS_TONES: Record<RefundStatus, BadgeTone> = {
  REQUESTED: 'warning',
  PROCESSING: 'warning',
  SUCCESS: 'success',
  FAILED: 'danger',
};

export function RestaurantStatusBadge({ status }: { status: RestaurantStatus }) {
  return (
    <Badge dot tone={RESTAURANT_STATUS_TONES[status]}>
      {RESTAURANT_STATUS_LABELS[status]}
    </Badge>
  );
}

export function CategoryStatusBadge({ status }: { status: CategoryStatus }) {
  return (
    <Badge dot tone={status === 'ACTIVE' ? 'success' : 'neutral'}>
      {CATEGORY_STATUS_LABELS[status]}
    </Badge>
  );
}

/** Whether a coupon can be used today (status, dates and uses together). */
export function CouponAvailabilityBadge({ availability }: { availability: CouponAvailability }) {
  return (
    <Badge dot tone={COUPON_AVAILABILITY_TONES[availability]}>
      {COUPON_AVAILABILITY_LABELS[availability]}
    </Badge>
  );
}

export function RedemptionStatusBadge({ status }: { status: RedemptionStatus }) {
  return (
    <Badge dot tone={REDEMPTION_STATUS_TONES[status]}>
      {REDEMPTION_STATUS_LABELS[status]}
    </Badge>
  );
}

export function PaymentRecordStatusBadge({ status }: { status: PaymentRecordStatus }) {
  return (
    <Badge dot tone={PAYMENT_RECORD_STATUS_TONES[status]}>
      {PAYMENT_RECORD_STATUS_LABELS[status]}
    </Badge>
  );
}

export function RefundStatusBadge({ status }: { status: RefundStatus }) {
  return (
    <Badge dot tone={REFUND_STATUS_TONES[status]}>
      {REFUND_STATUS_LABELS[status]}
    </Badge>
  );
}

export function StockBadge({ available }: { available: boolean }) {
  return (
    <Badge dot tone={available ? 'success' : 'warning'}>
      {available ? 'In stock' : 'Sold out'}
    </Badge>
  );
}

const DELIVERY_STATUS_TONES: Record<DeliveryStatus, BadgeTone> = {
  PENDING: 'warning',
  ASSIGNED: 'warning',
  ACCEPTED: 'info',
  PICKED_UP: 'info',
  DELIVERED: 'success',
  CANCELLED: 'danger',
};

const PARTNER_STATUS_TONES: Record<PartnerStatus, BadgeTone> = {
  OFFLINE: 'neutral',
  AVAILABLE: 'success',
  BUSY: 'info',
  SUSPENDED: 'danger',
};

export function DeliveryStatusBadge({ status }: { status: DeliveryStatus }) {
  return (
    <Badge dot tone={DELIVERY_STATUS_TONES[status]}>
      {DELIVERY_STATUS_LABELS[status]}
    </Badge>
  );
}

export function PartnerStatusBadge({ status }: { status: PartnerStatus }) {
  return (
    <Badge dot tone={PARTNER_STATUS_TONES[status]}>
      {PARTNER_STATUS_LABELS[status]}
    </Badge>
  );
}

const NOTIFICATION_STATUS_TONES: Record<NotificationStatus, BadgeTone> = {
  PENDING: 'warning',
  SENT: 'success',
  FAILED: 'danger',
  SKIPPED: 'neutral',
};

export function NotificationStatusBadge({ status }: { status: NotificationStatus }) {
  return (
    <Badge dot tone={NOTIFICATION_STATUS_TONES[status]}>
      {NOTIFICATION_STATUS_LABELS[status]}
    </Badge>
  );
}
