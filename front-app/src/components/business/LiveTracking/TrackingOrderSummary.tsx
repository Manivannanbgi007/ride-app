import { ChevronDown, CreditCard, MapPin, Store } from 'lucide-react';
import { useState } from 'react';

import { Badge, type BadgeTone } from '@/components/common/Badge';
import type { OrderDetails, PaymentStatus } from '@/types/order';
import { PAYMENT_METHOD_LABELS, PAYMENT_STATUS_LABELS } from '@/utils/constants';
import { formatDateTime } from '@/utils/dateUtils';
import { formatCurrency } from '@/utils/formatUtils';
import { addressLines } from '@/utils/shoppingUtils';

/** Long orders show this many dishes first, the rest on request. */
const FIRST_ITEMS = 3;

const PAYMENT_TONES: Partial<Record<PaymentStatus, BadgeTone>> = {
  PAID: 'success',
  PENDING: 'warning',
  FAILED: 'danger',
  REFUNDED: 'info',
};

type SummaryOrder = Pick<
  OrderDetails,
  | 'id'
  | 'createdAt'
  | 'restaurantName'
  | 'items'
  | 'price'
  | 'paymentMethod'
  | 'paymentStatus'
  | 'deliveryAddress'
>;

/**
 * What the customer ordered, at a glance beside the map: the order number and time, the restaurant, the dishes (a
 * long order folds after three), the total, how it is paid and where it goes.
 */
export function TrackingOrderSummary({ order }: { order: SummaryOrder }) {
  const [showAll, setShowAll] = useState(false);
  const items = showAll ? order.items : order.items.slice(0, FIRST_ITEMS);
  const hidden = order.items.length - FIRST_ITEMS;
  const count = order.items.reduce((sum, item) => sum + item.quantity, 0);
  const currency = order.price.currency;

  return (
    <section
      aria-label="Your order"
      className="rounded-3xl bg-surface p-5 shadow-card motion-safe:animate-page-in"
    >
      <div className="flex items-start justify-between gap-3">
        <div>
          <h2 className="text-base font-bold">Order #{order.id}</h2>
          <p className="text-xs text-ink-soft">
            <time dateTime={order.createdAt}>{formatDateTime(order.createdAt)}</time>
            {' · '}
            {count} {count === 1 ? 'item' : 'items'}
          </p>
        </div>
        <Badge tone={PAYMENT_TONES[order.paymentStatus] ?? 'neutral'}>
          {PAYMENT_STATUS_LABELS[order.paymentStatus]}
        </Badge>
      </div>

      <p className="mt-4 flex items-center gap-3 text-sm font-semibold">
        <span
          aria-hidden="true"
          className="flex size-9 shrink-0 items-center justify-center rounded-xl bg-brand-50 text-brand-text"
        >
          <Store className="size-4" />
        </span>
        <span className="min-w-0 truncate">{order.restaurantName}</span>
      </p>

      <ul aria-label="Ordered dishes" className="mt-4 flex flex-col gap-2.5 text-sm">
        {items.map((item) => (
          <li key={`${item.menuItemId}-${item.name}`} className="flex items-start gap-3">
            <span className="flex h-6 min-w-7 shrink-0 items-center justify-center rounded-lg bg-page px-1.5 text-xs font-bold tabular-nums">
              {item.quantity}×
            </span>
            <span className="min-w-0 flex-1 [overflow-wrap:anywhere]">{item.name}</span>
            <span className="shrink-0 text-ink-soft tabular-nums">
              {formatCurrency(item.lineTotal, currency)}
            </span>
          </li>
        ))}
      </ul>
      {hidden > 0 && (
        <button
          type="button"
          aria-expanded={showAll}
          onClick={() => setShowAll((value) => !value)}
          className="mt-2 inline-flex cursor-pointer items-center gap-1 rounded-lg text-xs font-semibold text-brand-text hover:underline focus-visible:ring-4 focus-visible:ring-brand-500/30 focus-visible:outline-none"
        >
          {showAll ? 'Show fewer' : `Show all ${order.items.length} dishes`}
          <ChevronDown
            aria-hidden="true"
            className={
              showAll ? 'size-3.5 rotate-180 transition-transform' : 'size-3.5 transition-transform'
            }
          />
        </button>
      )}

      <div className="mt-4 flex items-center justify-between border-t border-dashed border-line pt-4">
        <span className="text-sm font-semibold">Total</span>
        <span className="text-lg font-extrabold tabular-nums">
          {formatCurrency(order.price.totalAmount, currency)}
        </span>
      </div>

      <dl className="mt-4 flex flex-col gap-3 text-sm">
        <div>
          <dt className="sr-only">Payment</dt>
          <dd className="flex items-start gap-3">
            <CreditCard aria-hidden="true" className="mt-0.5 size-4 shrink-0 text-ink-soft" />
            {PAYMENT_METHOD_LABELS[order.paymentMethod]}
          </dd>
        </div>
        {order.deliveryAddress && (
          <div>
            <dt className="sr-only">Delivery address</dt>
            <dd className="flex min-w-0 items-start gap-3 [overflow-wrap:anywhere]">
              <MapPin aria-hidden="true" className="mt-0.5 size-4 shrink-0 text-ink-soft" />
              {addressLines(order.deliveryAddress).join(', ')}
            </dd>
          </div>
        )}
      </dl>
    </section>
  );
}
