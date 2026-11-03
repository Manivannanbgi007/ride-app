import { Bike, CircleCheckBig, MapPinned, MapPinOff, XCircle, type LucideIcon } from 'lucide-react';
import { Link } from 'react-router';

import { toApiError } from '@/api/apiErrorHandler';
import {
  DeliveryPartnerCard,
  DeliveryPartnerPending,
  LiveTrackingMap,
  TrackingHelpCard,
  TrackingHero,
  TrackingOrderSummary,
  TripCard,
} from '@/components/business/LiveTracking';
import { AsyncContent } from '@/components/common/AsyncContent';
import { BackLink } from '@/components/common/BackLink';
import { buttonStyles } from '@/components/common/Button';
import { Skeleton } from '@/components/common/Loader';
import { useLiveTracking, useMyOrder, useOrderRefreshMs } from '@/hooks/api/myOrderHooks';
import { useIdParam } from '@/hooks/useIdParam';
import { NotFoundPage } from '@/pages/NotFound/NotFoundPage';
import type { LiveTracking } from '@/types/liveTracking';
import type { OrderDetails, OrderStatus } from '@/types/order';
import { CUSTOMER_ORDER_STATUS_LABELS, customerRouteTo } from '@/utils/constants';
import { ERROR_MESSAGES } from '@/utils/errorMessages';
import { mapPoints } from '@/utils/liveTrackingUtils';
import { isFinishedOrder } from '@/utils/shoppingUtils';

/** The two columns on wide screens: the map, and the cards beside it */
const COLUMNS = 'grid items-start gap-6 lg:grid-cols-[minmax(0,1fr)_24rem]';
/** The map's place: tall on wide screens (and kept in view while the cards scroll), a good half of a phone */
const MAP_BOX =
  'h-[46vh] min-h-72 sm:h-[28rem] lg:sticky lg:top-24 lg:h-[min(40rem,calc(100dvh-8rem))]';

/**
 * "Track order": where the order stands at a glance (a green band with the status, the arrival time when the
 * delivery gives one, and the order's steps), then a live map of the partner riding to the customer with the
 * partner, the trip, the order and help beside it. Phones get everything in one column, the map first.
 * The status always comes from the saved order (and its delivery), so a reload shows the latest saved step and
 * the page never goes back to an earlier one. Nothing is invented: no position, time or partner that the platform
 * did not send.
 */
export default function LiveTrackingPage() {
  const orderId = useIdParam('orderId');
  return orderId === null ? <NotFoundPage /> : <LiveTrackingView orderId={orderId} />;
}

function LiveTrackingView({ orderId }: { orderId: number }) {
  const refreshMs = useOrderRefreshMs();
  const order = useMyOrder(orderId, (current) =>
    current && !isFinishedOrder(current.status) ? refreshMs : false,
  );
  const ended = order.data?.status === 'CANCELLED' || order.data?.status === 'REJECTED';
  const tracking = useLiveTracking(orderId, !ended);

  // 404: no partner is on the way for this order yet
  const notStarted =
    !tracking.data && tracking.error !== null && toApiError(tracking.error).kind === 'notFound';

  // The order must load. After that, a failed refresh keeps what is on screen: only a live map that never loaded
  // (and is expected) is an error.
  let error: unknown = null;
  let retry = () => void order.refetch();
  if (!order.data) {
    error = order.error;
  } else if (!tracking.data && !notStarted && !ended) {
    error = tracking.error;
    retry = () => void tracking.refetch();
  }

  return (
    <div className="flex flex-col gap-5">
      <BackLink to={customerRouteTo.orderDetails(orderId)}>{`Order #${orderId}`}</BackLink>
      <AsyncContent
        isLoading={order.isPending || (!ended && !notStarted && tracking.isPending)}
        error={error}
        errorTitle={
          order.data
            ? ERROR_MESSAGES.LIVE_TRACKING_LOAD_FAILED
            : ERROR_MESSAGES.MY_ORDER_LOAD_FAILED
        }
        onRetry={retry}
        isRetrying={order.isFetching || tracking.isFetching}
        loading={<LiveTrackingSkeleton />}
      >
        {order.data && (
          <TrackingContent order={order.data} tracking={tracking.data} ended={ended} />
        )}
      </AsyncContent>
    </div>
  );
}

/** The status band, then the map (or why there is none yet) with the cards beside it. */
function TrackingContent({
  order,
  tracking,
  ended,
}: {
  order: OrderDetails;
  tracking: LiveTracking | undefined;
  ended: boolean;
}) {
  if (ended) {
    return (
      <div className={COLUMNS}>
        <OrderEnded order={order} />
        <aside className="flex flex-col gap-5">
          <TrackingOrderSummary order={order} />
          <TrackingHelpCard orderId={order.id} />
        </aside>
      </div>
    );
  }
  return (
    <div className="flex flex-col gap-6">
      <TrackingHero order={order} tracking={tracking} />
      <div className={COLUMNS}>
        {tracking && mapPoints(tracking).length > 0 ? (
          <section
            aria-label="Live map of your delivery"
            className={`overflow-hidden rounded-3xl bg-surface shadow-card ${MAP_BOX}`}
          >
            <LiveTrackingMap tracking={tracking} />
          </section>
        ) : (
          <MapUnavailable order={order} tracking={tracking} />
        )}
        <aside className="flex flex-col gap-5">
          {tracking ? (
            <DeliveryPartnerCard tracking={tracking} />
          ) : (
            <DeliveryPartnerPending status={order.status} />
          )}
          {tracking && <TripCard tracking={tracking} />}
          <TrackingOrderSummary order={order} />
          <TrackingHelpCard orderId={order.id} />
        </aside>
      </div>
    </div>
  );
}

const NO_MAP: Partial<Record<OrderStatus, { icon: LucideIcon; title: string; message: string }>> = {
  OUT_FOR_DELIVERY: {
    icon: MapPinOff,
    title: 'Live location is not available right now',
    message:
      'Your food is on its way. The map appears as soon as the partner’s position is shared again; this page keeps checking.',
  },
  DELIVERED: {
    icon: CircleCheckBig,
    title: 'Your order was delivered',
    message: 'There is nothing left to follow on the map. Enjoy your meal!',
  },
};

const NO_PARTNER_YET = {
  icon: Bike,
  title: 'No delivery partner on the way yet',
  message:
    'The map shows your order moving as soon as a partner is on the way. This page checks by itself.',
};

/**
 * A partner is on the way, but there is nothing to draw yet: no position from their phone so far, and the addresses
 * have no map coordinates. Never an invented position.
 */
function waitingForPosition(partnerName: string) {
  const firstName = partnerName.split(' ')[0];
  return {
    icon: MapPinned,
    title: `Waiting for ${firstName}’s location`,
    message: `The map appears as soon as ${firstName}’s phone shares their position. This page checks by itself.`,
  };
}

/** Where the map goes while there is no live position: why, on a quiet dotted "map" background. */
function MapUnavailable({ order, tracking }: { order: OrderDetails; tracking?: LiveTracking }) {
  const {
    icon: Icon,
    title,
    message,
  } = tracking && tracking.phase !== 'DELIVERED'
    ? waitingForPosition(tracking.partner.name)
    : (NO_MAP[order.status] ?? NO_PARTNER_YET);
  return (
    <div
      className={`relative flex flex-col items-center justify-center gap-3 overflow-hidden rounded-3xl bg-surface px-6 py-10 text-center shadow-card ${MAP_BOX}`}
    >
      <span
        aria-hidden="true"
        className="absolute inset-0 opacity-70"
        style={{
          backgroundImage: 'radial-gradient(var(--color-line) 1.2px, transparent 1.2px)',
          backgroundSize: '18px 18px',
        }}
      />
      <span aria-hidden="true" className="relative flex size-20 items-center justify-center">
        <span className="absolute inset-0 rounded-full bg-brand-500/15 motion-safe:animate-ping" />
        <span className="relative flex size-16 items-center justify-center rounded-full bg-brand-500 text-on-brand shadow-brand">
          <Icon className="size-7" />
        </span>
      </span>
      <h2 className="relative text-lg font-bold">{title}</h2>
      <p className="relative max-w-sm text-sm text-ink-soft">{message}</p>
      <Link
        to={customerRouteTo.orderDetails(order.id)}
        className={buttonStyles('outline', 'sm', 'relative mt-1')}
      >
        See your order
      </Link>
    </div>
  );
}

/** A cancelled or rejected order has no delivery to follow. */
function OrderEnded({ order }: { order: OrderDetails }) {
  return (
    <section className="flex flex-col items-center gap-3 rounded-3xl bg-surface px-6 py-12 text-center shadow-card motion-safe:animate-page-in">
      <span
        aria-hidden="true"
        className="flex size-16 items-center justify-center rounded-full bg-danger-50 text-danger-text"
      >
        <XCircle className="size-8" />
      </span>
      <h1 className="text-xl font-bold">
        {`This order was ${order.status === 'REJECTED' ? 'declined' : 'cancelled'}`}
      </h1>
      <p className="max-w-md text-sm text-ink-soft">
        {order.cancellationReason ?? CUSTOMER_ORDER_STATUS_LABELS[order.status]}
      </p>
      <Link
        to={customerRouteTo.orderDetails(order.id)}
        className={buttonStyles('outline', 'sm', 'mt-1')}
      >
        See your order
      </Link>
    </section>
  );
}

function LiveTrackingSkeleton() {
  return (
    <div role="status" aria-label="Loading your order" className="flex flex-col gap-6">
      <Skeleton className="h-60 rounded-3xl" />
      <div className={COLUMNS}>
        <Skeleton className="h-[46vh] min-h-72 rounded-3xl sm:h-[28rem] lg:h-[40rem]" />
        <div className="flex flex-col gap-5">
          <Skeleton className="h-28 rounded-3xl" />
          <Skeleton className="h-44 rounded-3xl" />
          <Skeleton className="h-56 rounded-3xl" />
        </div>
      </div>
    </div>
  );
}
