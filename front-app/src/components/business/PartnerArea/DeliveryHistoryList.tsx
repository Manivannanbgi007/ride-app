import { ChevronRight, MapPin } from 'lucide-react';
import { Link } from 'react-router';

import { Skeleton } from '@/components/common/Loader';
import type { DeliverySummary } from '@/types/delivery';
import { cn } from '@/utils/classNames';
import { DEFAULT_CURRENCY, partnerRouteTo } from '@/utils/constants';
import { formatTime } from '@/utils/dateUtils';
import { formatCurrency, formatMinutes } from '@/utils/formatUtils';
import { dayLabelOf } from '@/utils/partnerAreaUtils';
import { deliveryMomentOf } from '@/utils/partnerHistoryUtils';

import { PARTNER_DELIVERY_LOOKS } from './partnerDeliveryLooks';
import { PartnerDeliveryStatusPill } from './PartnerDeliveryStatusPill';

/** The columns of a row on a wide screen (the header uses the same): order, route, time, payment, status, arrow. */
const COLUMNS =
  'md:grid-cols-[minmax(0,8rem)_minmax(0,1fr)_minmax(0,8rem)_minmax(0,8rem)_minmax(0,8.5rem)_1rem]';

/** "Bengaluru → Mysuru", or just "Bengaluru" within one city (the parts that are known). */
function routeOf(row: DeliverySummary): string | null {
  const sameCity = !row.pickupCity || !row.dropoffCity || row.pickupCity === row.dropoffCity;
  return sameCity
    ? (row.pickupCity ?? row.dropoffCity)
    : [row.pickupCity, row.dropoffCity].join(' → ');
}

/**
 * How long it took or has been going: "Took 24 min" from the offer to the door, "Ready 12 min ago" while the food
 * waits or travels. Null when it cannot be told (cancelled, or times missing).
 */
function timingOf(row: DeliverySummary): string | null {
  if (row.status === 'DELIVERED' && row.assignedAt && row.deliveredAt) {
    const minutes = Math.round(
      (new Date(row.deliveredAt).getTime() - new Date(row.assignedAt).getTime()) / 60_000,
    );
    if (minutes < 0) {
      return null;
    }
    return minutes < 1 ? 'Took under 1 min' : `Took ${formatMinutes(minutes)}`;
  }
  if (row.minutesSinceReady === null) {
    return null;
  }
  return row.minutesSinceReady < 1
    ? 'Ready just now'
    : `Ready ${formatMinutes(row.minutesSinceReady)} ago`;
}

/** Rows grouped by day, in the order they came. */
function byDay(rows: readonly DeliverySummary[]): [string, DeliverySummary[]][] {
  const groups = new Map<string, DeliverySummary[]>();
  for (const row of rows) {
    const day = dayLabelOf(deliveryMomentOf(row));
    groups.set(day, [...(groups.get(day) ?? []), row]);
  }
  return [...groups.entries()];
}

/** What the partner collects for it: the cash (struck through when the order was cancelled), or "Paid online". */
function Payment({ row }: { row: DeliverySummary }) {
  if ((row.cashToCollect ?? 0) <= 0) {
    return (
      <span className="rounded-full bg-info-100 px-2 py-0.5 text-xs font-semibold text-info-ink">
        Paid online
      </span>
    );
  }
  const cancelled = row.status === 'CANCELLED';
  return (
    <span className="flex flex-col items-end md:items-start">
      <span className="text-[11px] text-ink-soft">{cancelled ? 'Not collected' : 'Cash'}</span>
      <span
        className={cn(
          'text-sm font-extrabold text-ink tabular-nums',
          cancelled && 'font-semibold text-ink-soft line-through',
        )}
      >
        {formatCurrency(row.cashToCollect, DEFAULT_CURRENCY)}
      </span>
    </span>
  );
}

/**
 * One delivery, compact. Wide screen: one line under the column titles. Phone: two lines, the order and its status,
 * then where from and to and the money. The whole row opens the delivery's page.
 */
function HistoryRow({ row }: { row: DeliverySummary }) {
  const route = routeOf(row);
  const timing = timingOf(row);
  return (
    <li
      className={cn(
        'group relative grid grid-cols-[minmax(0,1fr)_auto] items-center gap-x-3 gap-y-1 overflow-hidden rounded-xl bg-surface py-2.5 pr-3 pl-4 shadow-card ring-1 ring-line/60 transition',
        'focus-within:ring-2 focus-within:ring-brand-500 hover:ring-brand-300',
        COLUMNS,
      )}
    >
      <span
        aria-hidden="true"
        className={cn('absolute inset-y-0 left-0 w-1', PARTNER_DELIVERY_LOOKS[row.status].stripe)}
      />
      <div className="flex min-w-0 items-baseline gap-2 md:col-start-1 md:row-start-1 md:flex-col md:gap-0">
        {/* The whole row opens the delivery: the link is stretched over it */}
        <Link
          to={partnerRouteTo.deliveryDetails(row.id)}
          className="text-sm font-bold text-ink after:absolute after:inset-0 focus-visible:outline-none"
        >
          Order #{row.orderId}
        </Link>
        <span className="text-xs text-ink-soft tabular-nums">
          {formatTime(deliveryMomentOf(row))}
        </span>
      </div>
      <div className="justify-self-end md:col-start-5 md:row-start-1 md:justify-self-start">
        <PartnerDeliveryStatusPill status={row.status} />
      </div>
      <div className="min-w-0 md:col-start-2 md:row-start-1">
        <p className="truncate text-sm font-medium text-ink">
          {row.restaurantName ?? 'Restaurant'}
        </p>
        {route && (
          <p className="flex min-w-0 items-center gap-1 text-xs text-ink-soft">
            <MapPin aria-hidden="true" className="size-3 shrink-0" />
            <span className="truncate">{route}</span>
          </p>
        )}
      </div>
      <p className="hidden text-xs text-ink-soft md:col-start-3 md:row-start-1 md:block">
        {timing ?? '—'}
      </p>
      <div className="justify-self-end md:col-start-4 md:row-start-1 md:justify-self-start">
        <Payment row={row} />
      </div>
      <ChevronRight
        aria-hidden="true"
        className="hidden size-4 text-ink-soft transition-transform group-hover:translate-x-0.5 md:col-start-6 md:row-start-1 md:block"
      />
    </li>
  );
}

interface DeliveryHistoryListProps {
  rows: readonly DeliverySummary[];
  /** Another page is loading: the rows are dimmed */
  isUpdating?: boolean;
}

/**
 * The partner's deliveries as compact rows, grouped by day ("Today", "Yesterday", "8 Oct 2026"), with the column
 * titles on a wide screen; each row opens its delivery's page.
 */
export function DeliveryHistoryList({ rows, isUpdating = false }: DeliveryHistoryListProps) {
  return (
    <div
      aria-busy={isUpdating || undefined}
      className={cn('flex flex-col gap-4 transition-opacity', isUpdating && 'opacity-60')}
    >
      <div
        aria-hidden="true"
        className={cn(
          'hidden gap-x-3 px-4 text-[11px] font-bold tracking-wider text-ink-soft uppercase md:grid',
          COLUMNS,
        )}
      >
        <span>Order</span>
        <span>Restaurant and route</span>
        <span>Time</span>
        <span>Payment</span>
        <span>Status</span>
      </div>
      {byDay(rows).map(([day, dayRows]) => (
        <section key={day} className="flex flex-col gap-2">
          <h2 className="flex items-center gap-2 px-1 text-xs font-bold text-ink-soft">
            {day}
            <span className="rounded-full bg-surface px-2 py-0.5 font-semibold ring-1 ring-line">
              {dayRows.length}
            </span>
            <span aria-hidden="true" className="h-px flex-1 bg-line" />
          </h2>
          <ul className="flex flex-col gap-1.5">
            {dayRows.map((row) => (
              <HistoryRow key={row.id} row={row} />
            ))}
          </ul>
        </section>
      ))}
    </div>
  );
}

/** The rows' shape while the deliveries load. */
export function DeliveryHistoryLoader() {
  return (
    <div role="status" aria-label="Loading" className="flex flex-col gap-1.5">
      {Array.from({ length: 6 }, (_, index) => (
        <div
          key={index}
          className="flex items-center gap-4 rounded-xl bg-surface px-4 py-3 shadow-card"
        >
          <Skeleton className="h-4 w-20" />
          <Skeleton className="h-4 flex-1" />
          <Skeleton className="h-5 w-20 rounded-full" />
        </div>
      ))}
    </div>
  );
}
