import { CircleCheckBig, Clock, FlaskConical, TriangleAlert } from 'lucide-react';

import { DeliveryStatusSteps } from '@/components/business/LiveTracking/DeliveryStatusSteps';
import { ORDER_STEP_LOOKS } from '@/components/business/MyOrders/orderStepStyles';
import { useNow } from '@/hooks/useNow';
import type { LiveTracking, LiveTrackingPhase } from '@/types/liveTracking';
import type { OrderDetails } from '@/types/order';
import { CUSTOMER_ORDER_STATUS_LABELS } from '@/utils/constants';
import { formatTime } from '@/utils/dateUtils';
import { formatDistance } from '@/utils/geoUtils';
import { isSimulated, trackedOrderStatus } from '@/utils/liveTrackingUtils';

/** A position older than this (the partner's phone went quiet) is flagged as possibly out of date */
const STALE_AFTER_MS = 45_000;

const HEADLINES: Record<LiveTrackingPhase, (partner: string) => string> = {
  TO_RESTAURANT: (partner) => `${partner} is heading to the restaurant`,
  AT_RESTAURANT: (partner) => `${partner} is picking up your food`,
  TO_CUSTOMER: () => 'Your food is on the way',
  DELIVERED: () => 'Your food has arrived',
};

interface TrackingHeroProps {
  order: Pick<OrderDetails, 'id' | 'status' | 'timeline' | 'restaurantName' | 'createdAt'>;
  /** The live delivery, once a partner is on the way */
  tracking?: LiveTracking;
}

/**
 * The top of the tracking page: a green band with where the order stands in one sentence, when the food arrives
 * (only when the delivery says so: never guessed) and the order's steps under it. While no partner is on the way,
 * the band says what is happening from the order's own status.
 */
export function TrackingHero({ order, tracking }: TrackingHeroProps) {
  const status = trackedOrderStatus(order.status, tracking?.phase);
  const look = ORDER_STEP_LOOKS[status];
  const Icon = look?.icon ?? Clock;
  const headline = tracking
    ? HEADLINES[tracking.phase](tracking.partner.name.split(' ')[0])
    : CUSTOMER_ORDER_STATUS_LABELS[status];

  return (
    <section
      aria-label="Order status"
      className="overflow-hidden rounded-3xl bg-surface shadow-card motion-safe:animate-page-in"
    >
      <div className="relative overflow-hidden bg-linear-to-br from-brand-600 via-brand-700 to-brand-900 px-5 py-6 text-white sm:px-8 sm:py-7">
        {/* Soft shapes behind the text */}
        <span
          aria-hidden="true"
          className="absolute -top-20 -right-12 size-56 rounded-full bg-white/10"
        />
        <span
          aria-hidden="true"
          className="absolute -bottom-24 left-1/4 size-52 rounded-full bg-lime-500/15"
        />

        <div className="relative flex flex-col gap-5 sm:flex-row sm:items-center sm:justify-between">
          <div className="flex min-w-0 items-start gap-4">
            <span className="flex size-14 shrink-0 items-center justify-center rounded-2xl bg-white/15 ring-1 ring-white/25 backdrop-blur-sm">
              <Icon aria-hidden="true" className="size-7 motion-safe:animate-pop" />
            </span>
            <div className="min-w-0">
              <h1 className="truncate text-xs font-semibold tracking-wide text-white/80 uppercase">
                Order #{order.id} · {order.restaurantName}
              </h1>
              {/* Read out when the order or the trip enters a new part, not on every new position */}
              <p aria-live="polite" className="mt-1 text-2xl font-bold tracking-tight sm:text-3xl">
                {headline}
              </p>
              {look && <p className="mt-1 max-w-xl text-sm text-white/85">{look.hint}</p>}
            </div>
          </div>
          {tracking ? (
            <ArrivalBlock tracking={tracking} />
          ) : (
            <span className="inline-flex items-center gap-2 self-start rounded-full bg-white/15 px-3 py-1.5 text-xs font-semibold ring-1 ring-white/20 sm:self-center">
              <Clock aria-hidden="true" className="size-3.5" />
              Placed at {formatTime(order.createdAt)}
            </span>
          )}
        </div>
        {tracking && <TrackingNotes tracking={tracking} />}
      </div>

      <div className="px-5 py-6 sm:px-8 sm:py-7">
        <DeliveryStatusSteps order={order} phase={tracking?.phase} />
      </div>
    </section>
  );
}

/**
 * When the food arrives (or arrived). Only the demo simulation has an estimate; a real ride shows how fresh the
 * partner's GPS position is instead, because the platform has no routing service to promise a time.
 */
function ArrivalBlock({ tracking }: { tracking: LiveTracking }) {
  const now = useNow(5_000);
  const box =
    'flex shrink-0 self-start rounded-2xl bg-white/15 ring-1 ring-white/25 backdrop-blur-sm sm:self-center';
  if (tracking.phase === 'DELIVERED') {
    const at = tracking.deliveredAt ?? tracking.estimatedArrival;
    return (
      <div className={`${box} items-center gap-3 px-4 py-3`}>
        <CircleCheckBig aria-hidden="true" className="size-7" />
        <p className="text-lg font-bold">{at ? `Arrived at ${formatTime(at)}` : 'Delivered'}</p>
      </div>
    );
  }
  if (typeof tracking.etaMinutes === 'number' && tracking.estimatedArrival) {
    return (
      <div className={`${box} flex-col px-5 py-3 sm:items-end`}>
        <p className="text-[11px] font-semibold tracking-wide text-white/80 uppercase">
          Arriving in
        </p>
        <p className="flex items-baseline gap-1.5">
          <span className="text-4xl font-extrabold tracking-tight tabular-nums">
            {tracking.etaMinutes}
          </span>
          <span className="text-base font-bold">min</span>
        </p>
        <p className="text-xs text-white/85">
          Arriving around {formatTime(tracking.estimatedArrival)}
          {typeof tracking.remainingMeters === 'number' &&
            ` · ${formatDistance(tracking.remainingMeters)} to go`}
        </p>
      </div>
    );
  }
  return (
    <div className={`${box} items-center gap-3 px-4 py-3`}>
      <span aria-hidden="true" className="relative flex size-3">
        {tracking.rider && (
          <span className="absolute inset-0 rounded-full bg-lime-300 motion-safe:animate-ping" />
        )}
        <span
          className={`relative size-3 rounded-full ${tracking.rider ? 'bg-lime-300' : 'bg-white/50'}`}
        />
      </span>
      <div>
        <p className="text-sm font-bold">
          {tracking.rider ? 'Live location' : 'Location coming up'}
        </p>
        <p className="text-xs text-white/85">
          {tracking.rider
            ? `Updated ${secondsAgo(now, tracking.rider.updatedAt)}`
            : 'From your partner’s phone'}
        </p>
      </div>
    </div>
  );
}

/** "just now", "12 s ago", "3 min ago" */
function secondsAgo(now: number, moment: string): string {
  const seconds = Math.max(0, Math.round((now - Date.parse(moment)) / 1000));
  if (seconds < 5) {
    return 'just now';
  }
  return seconds < 60 ? `${seconds} s ago` : `${Math.round(seconds / 60)} min ago`;
}

/** A simulated ride is labelled as such; a position that stopped updating is flagged. */
function TrackingNotes({ tracking }: { tracking: LiveTracking }) {
  const now = useNow(5_000);
  const simulated = isSimulated(tracking);
  const delivered = tracking.phase === 'DELIVERED';
  const stale =
    !delivered &&
    tracking.rider !== null &&
    now - Date.parse(tracking.rider.updatedAt) > STALE_AFTER_MS;
  if (!simulated && !stale) {
    return null;
  }
  const firstName = tracking.partner.name.split(' ')[0];
  return (
    <div className="relative mt-5 flex flex-col gap-2">
      {simulated && (
        <p className="flex items-start gap-2 text-xs text-white/85">
          <span className="inline-flex shrink-0 items-center gap-1 rounded-full bg-warning-100 px-2.5 py-0.5 text-[11px] font-bold text-warning-ink">
            <FlaskConical aria-hidden="true" className="size-3" />
            Simulated
          </span>
          <span>
            Sample positions and an estimated time for demonstration: not the partner’s real
            location or a promised arrival time.
          </span>
        </p>
      )}
      {stale && (
        <p className="flex items-start gap-2 rounded-xl bg-warning-50 px-3 py-2.5 text-sm text-warning-text">
          <TriangleAlert aria-hidden="true" className="mt-0.5 size-4 shrink-0" />
          {firstName}’s position has not changed for a while. It updates again as soon as their
          phone reconnects.
        </p>
      )}
    </div>
  );
}
