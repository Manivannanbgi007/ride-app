import { Bike, ChevronRight, CreditCard, MapPin } from 'lucide-react';
import { Link } from 'react-router';

import {
  customerStatusTone,
  ORDER_STEP_LOOKS,
  orderItemsLine,
  OrderStatusPill,
} from '@/components/business/MyOrders';
import { RestaurantTile } from '@/components/business/Storefront';
import { buttonStyles } from '@/components/common/Button';
import { useDashboardOrders } from '@/hooks/api/customerDashboardHooks';
import type { OrderSummary } from '@/types/order';
import { cn } from '@/utils/classNames';
import { CUSTOMER_ROUTES, customerRouteTo, DEFAULT_CURRENCY } from '@/utils/constants';
import { formatDateTime } from '@/utils/dateUtils';
import { formatCurrency } from '@/utils/formatUtils';
import { canTrackLive, ORDER_PROGRESS } from '@/utils/shoppingUtils';

import { SectionHeading, SectionLink } from './SectionHeading';

/** How many orders on their way get a card; the others are one link away. */
const SHOWN = 2;

/**
 * "In progress": the orders still going, newest first, each standing out with its progress (placed, accepted,
 * preparing, ready, on the way, delivered), what is happening now, and what to do: "Track" on the live map once
 * the restaurant has accepted, "Pay now" while an online payment is missing. Nothing at all when no order is going.
 * There is no delivery time estimate: the platform does not compute one, so none is shown.
 */
export function ActiveOrders() {
  const { active } = useDashboardOrders();
  if (active.length === 0) {
    return null;
  }
  const shown = active.slice(0, SHOWN);

  return (
    <section aria-labelledby="active-orders-title" className="flex flex-col gap-3">
      <SectionHeading
        id="active-orders-title"
        icon={Bike}
        colour="teal"
        title={active.length === 1 ? 'Your order in progress' : 'Your orders in progress'}
        subtitle="Follow it as it happens"
      >
        {active.length > SHOWN && (
          <SectionLink to={CUSTOMER_ROUTES.myOrders} colour="teal">
            All {active.length} in progress
          </SectionLink>
        )}
      </SectionHeading>
      <ul
        aria-label="Orders in progress"
        className={cn('grid grid-cols-1 gap-3', shown.length > 1 && 'lg:grid-cols-2')}
      >
        {shown.map((order) => (
          <li key={order.id} className="flex min-w-0">
            <ActiveOrderCard order={order} />
          </li>
        ))}
      </ul>
    </section>
  );
}

/** Step n of 6 as a row of short bars: the done ones green, the current one in its status colour. */
function ProgressBars({ order }: { order: OrderSummary }) {
  const current = ORDER_PROGRESS.indexOf(order.status);
  const look = ORDER_STEP_LOOKS[order.status];
  const tone = customerStatusTone(order.status);
  return (
    <div
      role="progressbar"
      aria-valuemin={1}
      aria-valuemax={ORDER_PROGRESS.length}
      aria-valuenow={current + 1}
      aria-valuetext={`Step ${current + 1} of ${ORDER_PROGRESS.length}: ${look?.label ?? ''}`}
      aria-label={`Progress of order #${order.id}`}
      className="flex gap-1"
    >
      {ORDER_PROGRESS.map((status, index) => (
        <span
          key={status}
          aria-hidden="true"
          className={cn(
            'h-1.5 flex-1 rounded-full',
            index < current && 'bg-brand-500',
            index === current && tone.stripe,
            index > current && 'bg-page ring-1 ring-line/60',
          )}
        />
      ))}
    </div>
  );
}

function ActiveOrderCard({ order }: { order: OrderSummary }) {
  const tone = customerStatusTone(order.status);
  const look = ORDER_STEP_LOOKS[order.status];
  const unpaid = order.status === 'PAYMENT_PENDING';
  return (
    <article
      aria-label={`Order #${order.id} from ${order.restaurantName}`}
      className={cn(
        'relative flex w-full min-w-0 flex-col gap-3 overflow-hidden rounded-2xl bg-surface p-4 pl-5 shadow-card ring-2',
        tone.ring,
      )}
    >
      <span aria-hidden="true" className={cn('absolute inset-y-0 left-0 w-1.5', tone.stripe)} />
      <div className="flex items-start gap-3">
        <RestaurantTile
          id={order.restaurantId}
          name={order.restaurantName}
          className="size-11 shrink-0 rounded-xl text-sm"
        />
        <div className="flex min-w-0 flex-1 flex-col items-start gap-1">
          <p className="w-full truncate font-bold text-ink">{order.restaurantName}</p>
          <p className="w-full truncate text-xs text-ink-soft">
            Order #{order.id} · {formatDateTime(order.createdAt)} · {orderItemsLine(order)}
          </p>
          <OrderStatusPill status={order.status} />
        </div>
        <span className="shrink-0 font-extrabold text-ink tabular-nums">
          {formatCurrency(order.totalAmount, DEFAULT_CURRENCY)}
        </span>
      </div>

      {unpaid ? (
        <p className={cn('rounded-xl px-3 py-2 text-sm font-medium', tone.soft, tone.text)}>
          The restaurant gets your order as soon as the payment goes through.
        </p>
      ) : (
        <div className="flex flex-col gap-1.5">
          <ProgressBars order={order} />
          {look && (
            <p className="text-sm text-ink">
              <span className={cn('font-bold', tone.text)}>{look.label}.</span> {look.hint}
            </p>
          )}
        </div>
      )}

      <div className="flex flex-wrap items-center justify-end gap-2">
        {unpaid && (
          <Link
            to={customerRouteTo.orderPayment(order.id)}
            className={buttonStyles('primary', 'sm')}
            aria-label={`Pay now for order #${order.id}`}
          >
            <CreditCard aria-hidden="true" className="size-4" />
            Pay now
          </Link>
        )}
        {canTrackLive(order.status) && (
          <Link
            to={customerRouteTo.orderLiveTracking(order.id)}
            className={buttonStyles('primary', 'sm')}
            aria-label={`Track order #${order.id} on the map`}
          >
            <MapPin aria-hidden="true" className="size-4" />
            Track order
          </Link>
        )}
        <Link
          to={customerRouteTo.orderDetails(order.id)}
          className={buttonStyles('ghost', 'sm')}
          aria-label={`Details of order #${order.id}`}
        >
          Details
          <ChevronRight aria-hidden="true" className="size-4" />
        </Link>
      </div>
    </article>
  );
}
