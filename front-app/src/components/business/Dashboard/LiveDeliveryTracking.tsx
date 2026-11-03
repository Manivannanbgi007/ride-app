import {
  ArrowRight,
  Bike,
  Clock,
  PackageCheck,
  RefreshCw,
  Send,
  Store,
  TriangleAlert,
  type LucideIcon,
} from 'lucide-react';
import { Link } from 'react-router';

import { DeliveryStatusBadge } from '@/components/business/StatusBadges';
import { AsyncContent } from '@/components/common/AsyncContent';
import { Card } from '@/components/common/Card';
import { EmptyState } from '@/components/common/EmptyState';
import { SkeletonLoader } from '@/components/common/Loader';
import { useLiveDeliveryBoard } from '@/hooks/api/deliveryHooks';
import type { LiveStatus } from '@/realtime/liveConnection';
import { useLiveStatus } from '@/realtime/liveStatusContext';
import type { DeliveryStatus, DeliverySummary } from '@/types/delivery';
import { cn } from '@/utils/classNames';
import { NOT_AVAILABLE, ROUTES, routeTo } from '@/utils/constants';
import { ERROR_MESSAGES } from '@/utils/errorMessages';
import { formatMinutes, formatNumber, initialsOf } from '@/utils/formatUtils';

/** The stops of a delivery, in the order it passes them, each with its own colour (the route on the card). */
const STAGES: readonly {
  status: DeliveryStatus;
  label: string;
  icon: LucideIcon;
  /** The round stop: colour and, while deliveries are there, a pulse */
  color: string;
  /** How far along the route this stop is, for the progress of a listed delivery (0 to 1) */
  progress: number;
}[] = [
  {
    status: 'PENDING',
    label: 'Waiting',
    icon: Clock,
    color: 'bg-warning-500 text-on-accent',
    progress: 0.1,
  },
  {
    status: 'ASSIGNED',
    label: 'Offered',
    icon: Send,
    color: 'bg-sunset-500 text-white',
    progress: 0.3,
  },
  {
    status: 'ACCEPTED',
    label: 'To pickup',
    icon: Store,
    color: 'bg-info-500 text-white',
    progress: 0.5,
  },
  {
    status: 'PICKED_UP',
    label: 'On the way',
    icon: Bike,
    color: 'bg-berry-700 text-white',
    progress: 0.8,
  },
  {
    status: 'DELIVERED',
    label: 'Delivered',
    icon: PackageCheck,
    color: 'bg-brand-500 text-on-brand',
    progress: 1,
  },
];

/** Waiting for a partner 10 minutes, or travelling 45, counts as late (the same limits as the delivery list). */
const LATE_WAITING_MINUTES = 10;
const LATE_TRAVELLING_MINUTES = 45;

/**
 * "Live Delivery Tracking": where today's deliveries are on their way (waiting for a partner, offered, going to the
 * restaurant, on the road, delivered), how many partners are free or busy, and the newest deliveries with how long
 * they have been going. The figures come from the delivery APIs and are asked again every 30 seconds while the
 * page is open; the card says when it last heard.
 */
export function LiveDeliveryTracking() {
  const connection = useLiveStatus();
  const { summary, latest, partners } = useLiveDeliveryBoard();
  const report = summary.data;
  const deliveries = latest.data?.content ?? [];
  const nothingYet = report?.total === 0 && deliveries.length === 0;
  const updatedAt = Math.max(summary.dataUpdatedAt, latest.dataUpdatedAt);

  return (
    <Card
      title="Live Delivery Tracking"
      subtitle="Where deliveries are now, from the restaurant to the door"
      actions={
        <LiveBadge
          connection={connection}
          updatedAt={updatedAt}
          isRefreshing={summary.isFetching || latest.isFetching}
          hasFailed={summary.isRefetchError || latest.isRefetchError}
        />
      }
    >
      <AsyncContent
        isLoading={summary.isPending || latest.isPending}
        error={summary.error ?? latest.error}
        isEmpty={nothingYet}
        errorTitle={ERROR_MESSAGES.LIVE_DELIVERIES_LOAD_FAILED}
        onRetry={() => {
          void summary.refetch();
          void latest.refetch();
        }}
        isRetrying={summary.isFetching || latest.isFetching}
        loading={<SkeletonLoader lines={6} />}
        empty={
          <EmptyState
            title="No deliveries today yet"
            message="They appear here as soon as a restaurant has an order ready."
          />
        }
      >
        {report && (
          <div className="flex flex-col gap-5">
            {/* One band: where today's deliveries are, then the partners and the late ones */}
            <div className="grid items-center gap-4 rounded-2xl bg-page/70 p-4 ring-1 ring-line/60 lg:grid-cols-[minmax(0,1.5fr)_minmax(0,1fr)]">
              <Route counts={report.byStatus} />
              <Facts
                available={partners.data?.byStatus.AVAILABLE}
                busy={partners.data?.byStatus.BUSY}
                offline={partners.data?.byStatus.OFFLINE}
                delayedNow={report.delayedNow}
                averageMinutes={report.averageDeliveryMinutes}
              />
            </div>
            <DeliveryList deliveries={deliveries} />
          </div>
        )}
      </AsyncContent>
    </Card>
  );
}

/** A pulsing "Live" and when the figures were last fetched; a warning when the latest refresh failed. */
function LiveBadge({
  connection,
  updatedAt,
  isRefreshing,
  hasFailed,
}: {
  connection: LiveStatus;
  updatedAt: number;
  isRefreshing: boolean;
  hasFailed: boolean;
}) {
  // LIVE only while the live connection (WebSocket) is open: updates then arrive the moment they happen. Otherwise
  // the card still refreshes by its own timer, and says so.
  const live = connection === 'open' && !hasFailed;
  const warn = hasFailed || connection === 'connecting' || connection === 'reconnecting';
  const label = hasFailed ? 'OFFLINE' : live ? 'LIVE' : warn ? 'RECONNECTING' : 'AUTO-REFRESH';
  const time =
    updatedAt > 0
      ? new Date(updatedAt).toLocaleTimeString('en-IN', {
          hour: '2-digit',
          minute: '2-digit',
          second: '2-digit',
        })
      : null;
  return (
    <div className="flex items-center gap-3 text-xs">
      <span
        className={cn(
          'flex items-center gap-2 rounded-full px-3 py-1 font-bold tracking-wide',
          warn ? 'bg-warning-50 text-warning-text' : 'bg-surface text-brand-text-strong',
        )}
      >
        <span className="relative flex size-2.5">
          {live && (
            <span className="absolute inline-flex size-full animate-ping rounded-full bg-brand-500/60 motion-reduce:hidden" />
          )}
          <span
            className={cn(
              'relative inline-flex size-2.5 rounded-full',
              warn ? 'bg-warning-500' : live ? 'bg-brand-500' : 'bg-ink-soft',
            )}
          />
        </span>
        {label}
      </span>
      {time && (
        <span className="flex items-center gap-1.5 text-brand-text-strong/80">
          <RefreshCw
            aria-hidden="true"
            className={cn('size-3.5', isRefreshing && 'animate-spin motion-reduce:animate-none')}
          />
          {hasFailed ? `Last update ${time}` : `Updated ${time}`}
        </span>
      )}
    </div>
  );
}

/** The five stops joined by a dashed line whose dashes run toward "Delivered"; a stop pulses while deliveries wait there. */
function Route({ counts }: { counts: Partial<Record<DeliveryStatus, number>> }) {
  return (
    <ol className="relative grid grid-cols-5 gap-1" aria-label="Today's deliveries by stage">
      <span
        aria-hidden="true"
        className="absolute top-5 right-[10%] left-[10%] h-0.5 animate-route-flow bg-size-[24px_100%] opacity-60"
        style={{
          backgroundImage:
            'repeating-linear-gradient(90deg, var(--color-brand-400) 0 12px, transparent 12px 24px)',
        }}
      />
      {STAGES.map((stage) => {
        const count = counts[stage.status] ?? 0;
        const active = count > 0 && stage.status !== 'DELIVERED';
        return (
          <li
            key={stage.status}
            className="relative flex min-w-0 flex-col items-center gap-2 text-center"
          >
            <span className="relative flex size-10 items-center justify-center">
              {active && (
                <span
                  aria-hidden="true"
                  className={cn(
                    'absolute inset-0 animate-ping rounded-full opacity-30 motion-reduce:hidden',
                    stage.color,
                  )}
                />
              )}
              <span
                className={cn(
                  'relative flex size-10 items-center justify-center rounded-full shadow-card ring-4 ring-surface',
                  stage.color,
                  count === 0 && 'opacity-50',
                )}
              >
                <stage.icon aria-hidden="true" className="size-4.5" />
              </span>
            </span>
            {/* A new number pops in (the key changes with it) */}
            <span key={count} className="animate-pop text-xl leading-none font-bold tracking-tight">
              {formatNumber(count)}
            </span>
            <span className="text-[11px] font-medium text-ink-soft sm:text-xs">{stage.label}</span>
          </li>
        );
      })}
    </ol>
  );
}

/** Partners free, on a delivery and offline, the late deliveries (a link to them) and the average time. */
function Facts({
  available,
  busy,
  offline,
  delayedNow,
  averageMinutes,
}: {
  available?: number;
  busy?: number;
  offline?: number;
  delayedNow: number;
  averageMinutes: number | null;
}) {
  return (
    <div className="flex flex-wrap gap-2 text-sm lg:justify-end">
      <Fact tone="bg-brand-50 text-brand-text-strong" label="Partners free" value={available} />
      <Fact tone="bg-sunset-50 text-sunset-text" label="On a delivery" value={busy} />
      <Fact tone="bg-page text-ink-soft" label="Offline" value={offline} />
      {averageMinutes !== null && (
        <Fact
          tone="bg-info-50 text-info-500"
          label="Average time"
          text={formatMinutes(averageMinutes)}
        />
      )}
      {delayedNow > 0 && (
        <Link
          to={ROUTES.deliveries}
          className="flex items-center gap-2 rounded-full bg-danger-50 px-3 py-1.5 font-semibold text-danger-text transition-colors hover:bg-danger-500 hover:text-white"
        >
          <TriangleAlert aria-hidden="true" className="size-4" />
          {delayedNow} late now
        </Link>
      )}
    </div>
  );
}

function Fact({
  tone,
  label,
  value,
  text,
}: {
  tone: string;
  label: string;
  value?: number;
  text?: string;
}) {
  return (
    <span className={cn('flex items-center gap-2 rounded-full px-3 py-1.5', tone)}>
      <span className="font-bold">
        {text ?? (value === undefined ? NOT_AVAILABLE : formatNumber(value))}
      </span>
      {label}
    </span>
  );
}

/**
 * The newest deliveries, one slim row each: the stage it is at, the order and its route, the partner, a bar along
 * the route, how long it has been going (marked when late) and its status. On a phone each row folds in two.
 */
function DeliveryList({ deliveries }: { deliveries: readonly DeliverySummary[] }) {
  if (deliveries.length === 0) {
    return null;
  }
  return (
    <div className="flex flex-col gap-2">
      <div className="flex items-center justify-between gap-3">
        <h3 className="flex items-center gap-2 text-sm font-bold">
          Latest deliveries
          <span className="rounded-full bg-page px-2 py-0.5 text-xs font-semibold text-ink-soft ring-1 ring-line">
            {deliveries.length}
          </span>
        </h3>
        <Link
          to={ROUTES.deliveries}
          className="flex items-center gap-1 text-xs font-semibold text-brand-text hover:underline"
        >
          View all
          <ArrowRight aria-hidden="true" className="size-3.5" />
        </Link>
      </div>
      <ul className="flex flex-col gap-1.5">
        {deliveries.map((delivery) => (
          <DeliveryRow key={delivery.id} delivery={delivery} />
        ))}
      </ul>
    </div>
  );
}

function DeliveryRow({ delivery }: { delivery: DeliverySummary }) {
  const stage = STAGES.find((candidate) => candidate.status === delivery.status);
  const cancelled = delivery.status === 'CANCELLED';
  const minutes = delivery.minutesSinceReady;
  const waiting = delivery.status === 'PENDING' || delivery.status === 'ASSIGNED';
  const late =
    minutes !== null && minutes >= (waiting ? LATE_WAITING_MINUTES : LATE_TRAVELLING_MINUTES);

  const StageIcon = stage?.icon ?? Clock;
  const reachedIndex = STAGES.findIndex((candidate) => candidate.status === delivery.status);
  const marker = cancelled ? 'bg-danger-500 text-white' : (stage?.color ?? 'bg-page text-ink-soft');

  return (
    <li className="grid grid-cols-[2.25rem_minmax(0,1fr)_auto] items-center gap-x-3 gap-y-2 rounded-xl bg-page/70 px-3 py-2 ring-1 ring-line/50 transition-colors hover:bg-surface md:grid-cols-[2.25rem_minmax(0,1.3fr)_minmax(0,1fr)_minmax(0,1.4fr)_5.5rem_auto]">
      {/* The stage the delivery is at, in that stage's colour */}
      <span
        aria-hidden="true"
        className={cn('flex size-9 items-center justify-center rounded-xl shadow-sm', marker)}
      >
        <StageIcon className="size-4.5" />
      </span>
      <div className="min-w-0">
        <Link
          to={routeTo.deliveryDetails(delivery.id)}
          className="text-sm font-semibold text-brand-text-strong hover:underline"
        >
          Order #{delivery.orderId}
        </Link>
        <p className="truncate text-xs text-ink-soft">
          {delivery.restaurantName ?? NOT_AVAILABLE}
          {delivery.dropoffCity ? ` to ${delivery.dropoffCity}` : ''}
        </p>
      </div>
      <div className="justify-self-end md:order-last">
        <DeliveryStatusBadge status={delivery.status} />
      </div>
      <span className="col-span-3 flex min-w-0 items-center gap-2 text-xs text-ink-soft md:col-span-1">
        {delivery.partnerName && (
          <span
            aria-hidden="true"
            className="flex size-6 shrink-0 items-center justify-center rounded-full bg-brand-100 text-[10px] font-bold text-brand-text-strong"
          >
            {initialsOf(delivery.partnerName)}
          </span>
        )}
        <span className="truncate">{delivery.partnerName ?? 'No partner yet'}</span>
      </span>
      {/* How far along the route it is, one segment per stop; all red for a cancelled one */}
      <div
        role="progressbar"
        aria-label={`Order #${delivery.orderId} progress`}
        aria-valuemin={0}
        aria-valuemax={100}
        aria-valuenow={Math.round((cancelled ? 0 : (stage?.progress ?? 0)) * 100)}
        className="col-span-2 flex gap-1 md:col-span-1"
      >
        {STAGES.map((candidate, index) => (
          <span
            key={candidate.status}
            className={cn(
              'h-1.5 flex-1 rounded-full transition-colors duration-700',
              cancelled
                ? 'bg-danger-500/70'
                : index <= reachedIndex
                  ? candidate.color.split(' ')[0]
                  : 'bg-line/70',
            )}
          />
        ))}
      </div>
      <span className="justify-self-end text-xs md:justify-self-start">
        {minutes !== null ? (
          <span
            className={cn(
              'rounded-full px-2 py-0.5 font-semibold whitespace-nowrap',
              late ? 'bg-danger-50 text-danger-text' : 'bg-surface text-ink-soft',
            )}
          >
            {formatMinutes(minutes)}
            {late && ' (late)'}
          </span>
        ) : (
          <span className="text-ink-soft">—</span>
        )}
      </span>
    </li>
  );
}
