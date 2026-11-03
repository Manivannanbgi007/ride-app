import { ChevronRight, CreditCard, MapPin } from 'lucide-react';
import { Link } from 'react-router';

import { ReorderButton } from '@/components/business/MyOrders/ReorderButton';
import { customerStatusTone } from '@/components/business/MyOrders/customerStatusTone';
import { orderItemsLine } from '@/components/business/MyOrders/orderItemsLine';
import { OrderStatusPill } from '@/components/business/MyOrders/OrderStatusPill';
import { RestaurantTile } from '@/components/business/Storefront';
import { PaymentStatusBadge } from '@/components/business/StatusBadges';
import { buttonStyles } from '@/components/common/Button';
import type { OrderStatus, OrderSummary } from '@/types/order';
import { cn } from '@/utils/classNames';
import { customerRouteTo, DEFAULT_CURRENCY, PAYMENT_METHOD_LABELS } from '@/utils/constants';
import { formatDateTime } from '@/utils/dateUtils';
import { formatCurrency } from '@/utils/formatUtils';
import { canTrackLive } from '@/utils/shoppingUtils';

/** Orders that are over: they can be ordered again. */
const FINISHED: readonly OrderStatus[] = ['DELIVERED', 'CANCELLED', 'REJECTED'];

/**
 * One of the customer's orders: the restaurant, when, what was in it, what it came to and how it was paid, where it
 * is now, and what can be done next: "Track" (the live map) while it is being prepared or delivered, "Pay now" while
 * the payment is missing, "Reorder" once it is over, and its page. An order still on its way stands out.
 */
export function MyOrderCard({ order }: { order: OrderSummary }) {
  const active = !FINISHED.includes(order.status);
  const tone = customerStatusTone(order.status);

  return (
    <article
      className={cn(
        'relative flex flex-col gap-4 overflow-hidden rounded-2xl bg-surface p-4 pl-5 shadow-card ring-1 transition-shadow hover:shadow-lg sm:p-5 sm:pl-6',
        active ? tone.ring : 'ring-line/60',
      )}
    >
      <span aria-hidden="true" className={cn('absolute inset-y-0 left-0 w-1.5', tone.stripe)} />
      {/* On a phone the total and status go under the order, so its name and dishes keep the width */}
      <div className="grid grid-cols-[auto_minmax(0,1fr)] items-start gap-x-4 gap-y-3 sm:grid-cols-[auto_minmax(0,1fr)_auto]">
        <RestaurantTile
          id={order.restaurantId}
          name={order.restaurantName}
          className="size-12 shrink-0 rounded-xl text-base sm:size-14 sm:text-lg"
        />
        <div className="min-w-0">
          <h3 className="font-bold">
            <Link
              to={customerRouteTo.orderDetails(order.id)}
              className="rounded-md hover:text-brand-text focus-visible:ring-4 focus-visible:ring-brand-500/30 focus-visible:outline-none"
            >
              {order.restaurantName}
              <span className="sr-only">, order #{order.id}</span>
            </Link>
          </h3>
          <p className="text-sm text-ink-soft">
            Order #{order.id} · {formatDateTime(order.createdAt)}
          </p>
          <p className="mt-1 line-clamp-2 text-sm text-ink">{orderItemsLine(order)}</p>
        </div>
        <div className="col-span-2 flex flex-row-reverse items-center justify-between gap-2 sm:col-span-1 sm:flex-col sm:items-end sm:gap-1.5 sm:text-right">
          <p className="text-lg font-extrabold tabular-nums">
            {formatCurrency(order.totalAmount, DEFAULT_CURRENCY)}
          </p>
          <OrderStatusPill status={order.status} />
        </div>
      </div>

      <div className="flex flex-wrap items-center justify-between gap-3 border-t border-line pt-3">
        <p className="flex flex-wrap items-center gap-2 text-sm text-ink-soft">
          <CreditCard aria-hidden="true" className="size-4" />
          {PAYMENT_METHOD_LABELS[order.paymentMethod]}
          <PaymentStatusBadge status={order.paymentStatus} />
        </p>
        <div className="flex flex-wrap items-center gap-2">
          {canTrackLive(order.status) && (
            <Link
              to={customerRouteTo.orderLiveTracking(order.id)}
              className={buttonStyles('primary', 'sm')}
              aria-label={`Track order #${order.id} on the map`}
            >
              <MapPin aria-hidden="true" className="size-4" />
              Track
            </Link>
          )}
          {order.status === 'PAYMENT_PENDING' && (
            <Link
              to={customerRouteTo.orderPayment(order.id)}
              className={buttonStyles('primary', 'sm')}
            >
              Pay now
            </Link>
          )}
          {!active && <ReorderButton order={order} />}
          <Link
            to={customerRouteTo.orderDetails(order.id)}
            className={buttonStyles('ghost', 'sm')}
            aria-label={`Details of order #${order.id}`}
          >
            Details
            <ChevronRight aria-hidden="true" className="size-4" />
          </Link>
        </div>
      </div>
    </article>
  );
}
