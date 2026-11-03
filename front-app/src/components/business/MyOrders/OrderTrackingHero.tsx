import { MapPin, Radio } from 'lucide-react';
import { Link } from 'react-router';

import { OrderProgress } from '@/components/business/MyOrders/OrderProgress';
import { ORDER_STEP_LOOKS } from '@/components/business/MyOrders/orderStepStyles';
import { Tooltip } from '@/components/common/Tooltip';
import { useNow } from '@/hooks/useNow';
import type { LiveStatus } from '@/realtime/liveConnection';
import { useLiveStatus } from '@/realtime/liveStatusContext';
import type { OrderDetails } from '@/types/order';
import { cn } from '@/utils/classNames';
import { CUSTOMER_ORDER_STATUS_LABELS, customerRouteTo } from '@/utils/constants';
import { formatMinutes } from '@/utils/formatUtils';
import { canTrackLive } from '@/utils/shoppingUtils';

/**
 * The top of a customer's order page: a green band with where the order stands right now, what is happening in a
 * sentence, how long it has been going and whether the page is live, and under it the route of the order
 * (placed to delivered). Updates arrive by themselves (WebSocket), so the band and the route change while the
 * customer watches.
 */
export function OrderTrackingHero({ order }: { order: OrderDetails }) {
  const now = useNow();
  const look = ORDER_STEP_LOOKS[order.status];
  const delivered = order.status === 'DELIVERED';
  const Icon = look?.icon;

  const startedAt = Date.parse(order.createdAt);
  const deliveredAt = delivered
    ? [...order.timeline].reverse().find((entry) => entry.status === 'DELIVERED')?.at
    : undefined;
  const minutes = Math.max(
    0,
    Math.round(((deliveredAt ? Date.parse(deliveredAt) : now) - startedAt) / 60_000),
  );

  return (
    <section className="overflow-hidden rounded-3xl bg-surface shadow-card">
      <div className="relative overflow-hidden bg-linear-to-br from-brand-600 via-brand-700 to-brand-900 px-6 py-6 text-white sm:px-8">
        {/* Soft shapes behind the text */}
        <span
          aria-hidden="true"
          className="absolute -top-16 -right-10 size-52 rounded-full bg-white/10"
        />
        <span
          aria-hidden="true"
          className="absolute -bottom-20 left-1/3 size-48 rounded-full bg-lime-500/15"
        />
        {/* A phone stacks it: the headline, then the live chip and the time; the sentence goes under all of it */}
        <div className="relative flex flex-col gap-4 sm:flex-row sm:items-center sm:gap-5">
          <div className="flex min-w-0 flex-1 items-center gap-4">
            {Icon && (
              <span className="flex size-14 shrink-0 items-center justify-center rounded-2xl bg-white/15 ring-1 ring-white/25 backdrop-blur-sm">
                <Icon aria-hidden="true" className="size-7 motion-safe:animate-pop" />
              </span>
            )}
            <div className="min-w-0 flex-1">
              <p className="truncate text-xs font-semibold tracking-wide text-white/70 uppercase">
                {order.restaurantName}
              </p>
              <h2 className="text-2xl font-bold tracking-tight sm:text-3xl">
                {CUSTOMER_ORDER_STATUS_LABELS[order.status]}
              </h2>
            </div>
          </div>
          <div className="flex flex-wrap items-center gap-2 sm:flex-col sm:items-end">
            {!delivered && <LiveChip />}
            <span className="rounded-full bg-white/15 px-3 py-1 text-xs font-semibold ring-1 ring-white/20">
              {delivered
                ? `Delivered in ${formatMinutes(minutes)}`
                : `Placed ${formatMinutes(minutes)} ago`}
            </span>
          </div>
        </div>
        {look && (
          <p className="relative mt-3 max-w-xl text-sm text-white/85 sm:mt-1 sm:ml-[4.5rem]">
            {look.hint}
          </p>
        )}
        {canTrackLive(order.status) && (
          <Link
            to={customerRouteTo.orderLiveTracking(order.id)}
            className="relative mt-4 inline-flex h-10 items-center gap-2 rounded-full bg-surface px-4 text-sm font-bold text-brand-text-strong shadow-lg hover:bg-brand-50 focus-visible:ring-2 focus-visible:ring-white focus-visible:outline-none sm:ml-[4.5rem]"
          >
            <MapPin aria-hidden="true" className="size-4" />
            Track on map
          </Link>
        )}
      </div>
      <div className="px-4 py-6 sm:px-8 sm:py-8">
        <OrderProgress order={order} />
      </div>
    </section>
  );
}

const CHIPS: Record<'live' | 'reconnecting' | 'auto', { label: string; hint: string }> = {
  live: { label: 'LIVE', hint: 'Updates arrive the moment they happen' },
  reconnecting: { label: 'RECONNECTING', hint: 'Trying to get the live updates back' },
  auto: { label: 'AUTO-REFRESH', hint: 'This page looks for news every few seconds' },
};

function chipFor(status: LiveStatus): keyof typeof CHIPS {
  if (status === 'open') {
    return 'live';
  }
  return status === 'connecting' || status === 'reconnecting' ? 'reconnecting' : 'auto';
}

/** "LIVE" while the live connection is open: pulsing and white; otherwise it says how the page stays up to date. */
function LiveChip() {
  const kind = chipFor(useLiveStatus());
  const { label, hint } = CHIPS[kind];
  return (
    <Tooltip label={hint} icon={Radio} placement="bottom">
      <span
        className={cn(
          'flex items-center gap-2 rounded-full px-3 py-1 text-xs font-bold tracking-wide',
          kind === 'live'
            ? 'bg-surface text-brand-text-strong'
            : 'bg-white/15 text-white ring-1 ring-white/25',
        )}
      >
        <span className="relative flex size-2.5">
          {kind === 'live' && (
            <span className="absolute inline-flex size-full animate-ping rounded-full bg-brand-500/60 motion-reduce:hidden" />
          )}
          <span
            className={cn(
              'relative inline-flex size-2.5 rounded-full',
              kind === 'live'
                ? 'bg-brand-500'
                : kind === 'reconnecting'
                  ? 'bg-warning-500'
                  : 'bg-white/70',
            )}
          />
        </span>
        {label}
      </span>
    </Tooltip>
  );
}
