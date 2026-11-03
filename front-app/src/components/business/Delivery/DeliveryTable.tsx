import { Link } from 'react-router';

import { DeliveryStatusBadge } from '@/components/business/StatusBadges';
import { Badge } from '@/components/common/Badge';
import { Table, type TableColumn } from '@/components/common/Table';
import type { DeliverySummary } from '@/types/delivery';
import { DEFAULT_CURRENCY, DISPLAY_LOCALE, NOT_AVAILABLE, routeTo } from '@/utils/constants';
import { formatDateTime } from '@/utils/dateUtils';
import { formatCurrency, formatMinutes, initialsOf } from '@/utils/formatUtils';

/** '18 Oct, 11:14 pm' in the viewer's time zone (the full date and time in the tooltip). */
function ShortMoment({ at }: { at: string }) {
  return (
    <time dateTime={at} title={formatDateTime(at)} className="whitespace-nowrap">
      {new Date(at).toLocaleString(DISPLAY_LOCALE, {
        day: 'numeric',
        month: 'short',
        hour: 'numeric',
        minute: '2-digit',
      })}
    </time>
  );
}

/**
 * Minutes since the food was ready, the late ones in red (the "delayed" limits are 10 and 45 minutes); once
 * delivered, how long it took from the food being ready.
 */
function WaitingTime({ delivery }: { delivery: DeliverySummary }) {
  if (delivery.minutesSinceReady === null) {
    if (!delivery.deliveredAt) {
      return NOT_AVAILABLE;
    }
    const took = Math.max(
      0,
      Math.round(
        (new Date(delivery.deliveredAt).getTime() - new Date(delivery.readyAt).getTime()) / 60_000,
      ),
    );
    return (
      <span
        className="whitespace-nowrap text-ink-soft"
        title={formatDateTime(delivery.deliveredAt)}
      >
        Delivered in {took < 1 ? 'under 1 min' : formatMinutes(took)}
      </span>
    );
  }
  const minutes = delivery.minutesSinceReady;
  const waitingForPartner = delivery.status === 'PENDING' || delivery.status === 'ASSIGNED';
  const late = waitingForPartner ? minutes >= 10 : minutes >= 45;
  return <Badge tone={late ? 'danger' : 'neutral'}>{formatMinutes(minutes)}</Badge>;
}

const COLUMNS: readonly TableColumn<DeliverySummary>[] = [
  {
    header: 'Delivery',
    className: 'whitespace-nowrap',
    render: (delivery) => (
      <span className="flex flex-col">
        <Link
          to={routeTo.deliveryDetails(delivery.id)}
          className="font-semibold text-brand-text hover:underline"
        >
          #{delivery.id}
        </Link>
        <Link
          to={routeTo.orderDetails(delivery.orderId)}
          className="text-xs text-ink-soft hover:underline"
        >
          Order #{delivery.orderId}
        </Link>
      </span>
    ),
  },
  {
    header: 'From',
    className: 'max-w-64',
    render: (delivery) => (
      <div className="min-w-0">
        <p className="truncate font-medium" title={delivery.restaurantName ?? undefined}>
          {delivery.restaurantName ?? NOT_AVAILABLE}
        </p>
        <p className="truncate text-xs text-ink-soft">
          {delivery.pickupCity ?? ''}
          {delivery.dropoffCity && delivery.dropoffCity !== delivery.pickupCity
            ? ` to ${delivery.dropoffCity}`
            : ''}
        </p>
      </div>
    ),
  },
  {
    header: 'Partner',
    className: 'whitespace-nowrap',
    render: (delivery) =>
      delivery.partnerUserId ? (
        <span className="flex items-center gap-2">
          <span
            aria-hidden="true"
            className="flex size-7 shrink-0 items-center justify-center rounded-full bg-brand-100 text-[10px] font-bold text-brand-text-strong"
          >
            {initialsOf(delivery.partnerName ?? '')}
          </span>
          <Link to={routeTo.partnerDetails(delivery.partnerUserId)} className="hover:underline">
            {delivery.partnerName ?? `#${delivery.partnerUserId}`}
          </Link>
        </span>
      ) : (
        <span className="text-ink-soft">Nobody yet</span>
      ),
  },
  { header: 'Food ready', render: (delivery) => <ShortMoment at={delivery.readyAt} /> },
  { header: 'Waiting', render: (delivery) => <WaitingTime delivery={delivery} /> },
  {
    header: 'Cash',
    align: 'right',
    className: 'whitespace-nowrap',
    render: (delivery) =>
      delivery.cashToCollect ? (
        <span className="font-semibold tabular-nums">
          {formatCurrency(delivery.cashToCollect, DEFAULT_CURRENCY)}
        </span>
      ) : (
        <Badge tone="info">Paid</Badge>
      ),
  },
  {
    header: 'Status',
    className: 'whitespace-nowrap',
    render: (delivery) => <DeliveryStatusBadge status={delivery.status} />,
  },
];

interface DeliveryTableProps {
  deliveries: readonly DeliverySummary[];
  isUpdating?: boolean;
}

/** Deliveries as a compact table; links to the delivery, its order and its partner. */
export function DeliveryTable({ deliveries, isUpdating }: DeliveryTableProps) {
  return (
    <Table
      compact
      caption="Deliveries"
      columns={COLUMNS}
      rows={deliveries}
      rowKey={(delivery) => delivery.id}
      isUpdating={isUpdating}
    />
  );
}
