import { Link } from 'react-router';

import { DeliveryStatusBadge } from '@/components/business/StatusBadges';
import { Alert } from '@/components/common/Alert';
import { Card } from '@/components/common/Card';
import { DescriptionList } from '@/components/common/DescriptionList';
import type { Delivery, DeliveryAddress, DeliveryTimelineEntry } from '@/types/delivery';
import { cn } from '@/utils/classNames';
import { DEFAULT_CURRENCY, routeTo, VEHICLE_TYPE_LABELS } from '@/utils/constants';
import { formatDateTime } from '@/utils/dateUtils';
import { formatCurrency, humanizeEnum } from '@/utils/formatUtils';

function formatAddress(address: DeliveryAddress | null): string | null {
  if (!address) {
    return null;
  }
  return [address.line1, address.line2, address.city, address.postalCode]
    .filter(Boolean)
    .join(', ');
}

const when = (moment: string | null) => (moment ? formatDateTime(moment) : 'Not yet');

/** Where the food comes from and goes to, who carries it, the cash, and the key moments. */
export function DeliveryOverviewCard({ delivery }: { delivery: Delivery }) {
  const currency = delivery.currency ?? DEFAULT_CURRENCY;
  const isCash = delivery.paymentMethod === 'CASH_ON_DELIVERY';
  return (
    <Card title="Delivery" actions={<DeliveryStatusBadge status={delivery.status} />}>
      <div className="flex flex-col gap-5">
        {delivery.cancellationReason && (
          <Alert tone="error" title="Cancelled">
            {delivery.cancellationReason}
          </Alert>
        )}
        <DescriptionList
          items={[
            {
              label: 'Order',
              value: (
                <Link
                  to={routeTo.orderDetails(delivery.orderId)}
                  className="text-brand-text hover:underline"
                >
                  #{delivery.orderId}
                </Link>
              ),
            },
            {
              label: 'Partner',
              value: delivery.partnerUserId ? (
                <Link
                  to={routeTo.partnerDetails(delivery.partnerUserId)}
                  className="text-brand-text hover:underline"
                >
                  {delivery.partnerName ?? `#${delivery.partnerUserId}`}
                  {delivery.vehicleType ? ` (${VEHICLE_TYPE_LABELS[delivery.vehicleType]})` : ''}
                </Link>
              ) : (
                'Nobody yet'
              ),
            },
            {
              label: 'Pick up at',
              value: [delivery.restaurantName, formatAddress(delivery.pickupAddress)]
                .filter(Boolean)
                .join(': '),
            },
            {
              label: 'Deliver to',
              value: [delivery.customerName, formatAddress(delivery.dropoffAddress)]
                .filter(Boolean)
                .join(': '),
            },
            { label: 'Customer phone', value: delivery.customerPhone },
            {
              label: 'Payment',
              value: isCash
                ? `Cash: collect ${formatCurrency(delivery.cashToCollect, currency)}`
                : (delivery.paymentMethod ?? null),
            },
            ...(delivery.cashCollected !== null
              ? [
                  {
                    label: 'Cash collected',
                    value: formatCurrency(delivery.cashCollected, currency),
                  },
                ]
              : []),
            { label: 'Food ready', value: formatDateTime(delivery.readyAt) },
            { label: 'Accepted', value: when(delivery.acceptedAt) },
            { label: 'Picked up', value: when(delivery.pickedUpAt) },
            { label: 'Delivered', value: when(delivery.deliveredAt) },
          ]}
        />
      </div>
    </Card>
  );
}

const ACTOR_LABELS = {
  SYSTEM: 'the system',
  DELIVERY_PARTNER: 'the partner',
  ADMIN: 'an admin',
} as const;

/** Every step of the delivery, oldest first: offers, declines, reassignments, pickup, delivery. */
export function DeliveryTimelineCard({ timeline }: { timeline: readonly DeliveryTimelineEntry[] }) {
  return (
    <Card title="History" subtitle="Offers, declines, reassignments, pickup and delivery">
      <ol className="relative flex flex-col gap-5 border-l-2 border-line pl-6">
        {timeline.map((entry, index) => {
          const bad =
            entry.event === 'CANCELLED' || entry.event === 'DECLINED' || entry.event === 'EXPIRED';
          return (
            <li key={`${entry.event}-${entry.at}-${index}`} className="relative">
              <span
                aria-hidden="true"
                className={cn(
                  'absolute top-1 -left-[1.95rem] size-3.5 rounded-full border-2 border-surface',
                  bad
                    ? 'bg-danger-500'
                    : index === timeline.length - 1
                      ? 'bg-brand-500'
                      : 'bg-ink-soft',
                )}
              />
              <p className="font-semibold">{humanizeEnum(entry.event)}</p>
              <p className="text-xs text-ink-soft">
                {formatDateTime(entry.at)} by {ACTOR_LABELS[entry.actorType]}
                {entry.actorUserId ? ` #${entry.actorUserId}` : ''}
              </p>
              {entry.note && <p className="mt-1 text-sm">{entry.note}</p>}
            </li>
          );
        })}
      </ol>
    </Card>
  );
}
