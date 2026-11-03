import {
  Banknote,
  Bike,
  CheckCheck,
  CircleX,
  Clock3,
  CreditCard,
  Timer,
  type LucideIcon,
} from 'lucide-react';

import type { Delivery, DeliveryStatus } from '@/types/delivery';
import { cn } from '@/utils/classNames';
import { DEFAULT_CURRENCY } from '@/utils/constants';
import { formatDateTime } from '@/utils/dateUtils';
import { formatCurrency, formatMinutes } from '@/utils/formatUtils';
import { collectsCash, minutesBetween, paidOnlineLabel } from '@/utils/partnerAreaUtils';

type Outcome = 'delivered' | 'cancelled' | 'active';

const LOOKS: Record<Outcome, { tone: string; icon: LucideIcon }> = {
  delivered: { tone: 'from-brand-600 via-brand-700 to-brand-800', icon: CheckCheck },
  cancelled: { tone: 'from-danger-600 to-ruby-600', icon: CircleX },
  active: { tone: 'from-brand-700 via-brand-800 to-brand-900', icon: Bike },
};

/** The status in the partner's words */
const STATUS_WORDS: Record<DeliveryStatus, string> = {
  PENDING: 'Waiting for a partner',
  ASSIGNED: 'New offer',
  ACCEPTED: 'Heading to the restaurant',
  PICKED_UP: 'On the way to the customer',
  DELIVERED: 'Delivered',
  CANCELLED: 'Cancelled',
};

function outcomeOf(status: DeliveryStatus): Outcome {
  if (status === 'DELIVERED') {
    return 'delivered';
  }
  return status === 'CANCELLED' ? 'cancelled' : 'active';
}

interface Fact {
  icon: LucideIcon;
  label: string;
  value: string;
}

/** The few numbers that sum a delivery up: when it ended, how long it took, and the money. */
function factsOf(delivery: Delivery): Fact[] {
  const currency = delivery.currency ?? DEFAULT_CURRENCY;
  const facts: Fact[] = [];
  if (delivery.deliveredAt) {
    facts.push({ icon: Clock3, label: 'Delivered', value: formatDateTime(delivery.deliveredAt) });
    facts.push({
      icon: Timer,
      label: 'Food ready to door',
      value: formatMinutes(minutesBetween(delivery.readyAt, delivery.deliveredAt)),
    });
  } else if (delivery.cancelledAt) {
    facts.push({ icon: Clock3, label: 'Cancelled', value: formatDateTime(delivery.cancelledAt) });
  } else {
    facts.push({ icon: Clock3, label: 'Food ready', value: formatDateTime(delivery.readyAt) });
  }
  if (collectsCash(delivery)) {
    facts.push({
      icon: Banknote,
      label: delivery.cashCollected === null ? 'Cash to collect' : 'Cash collected',
      value: formatCurrency(delivery.cashCollected ?? delivery.cashToCollect, currency),
    });
  } else {
    facts.push({
      icon: CreditCard,
      label: 'Payment',
      value: paidOnlineLabel(delivery.paymentMethod),
    });
  }
  return facts;
}

/**
 * The top of a delivery's page: its outcome in colour (green delivered, red cancelled, deep green while on its way),
 * the order and the trip, and the figures that matter to the partner. Holds the page's only h1.
 */
export function DeliveryOutcomeHero({ delivery }: { delivery: Delivery }) {
  const outcome = outcomeOf(delivery.status);
  const { tone, icon: Icon } = LOOKS[outcome];
  const restaurant = delivery.restaurantName ?? 'The restaurant';
  const customer = delivery.customerName ?? 'the customer';

  return (
    <section
      aria-labelledby="delivery-title"
      className={cn(
        'relative isolate overflow-hidden rounded-3xl bg-linear-to-br p-6 text-white shadow-card sm:p-8',
        tone,
      )}
    >
      <span
        aria-hidden="true"
        className="absolute -top-24 -right-16 -z-10 size-72 rounded-full bg-white/10 blur-2xl"
      />
      <span
        aria-hidden="true"
        className="absolute -bottom-32 left-1/4 -z-10 size-80 rounded-full bg-lime-500/15 blur-3xl"
      />

      <div className="flex items-start justify-between gap-6">
        <div className="flex min-w-0 flex-col items-start gap-2">
          <span className="inline-flex items-center gap-1.5 rounded-full bg-white/15 px-3 py-1 text-xs font-bold backdrop-blur-sm">
            <Icon aria-hidden="true" className="size-3.5" />
            {STATUS_WORDS[delivery.status]}
          </span>
          <h1 id="delivery-title" className="text-3xl font-extrabold tracking-tight sm:text-4xl">
            Delivery #{delivery.id}
          </h1>
          <p className="text-sm text-white/85 sm:text-base">
            Order #{delivery.orderId} · {restaurant} → {customer}
          </p>
        </div>
        <span
          aria-hidden="true"
          className="hidden size-16 shrink-0 items-center justify-center rounded-3xl bg-white/15 ring-1 ring-white/25 backdrop-blur-sm sm:flex"
        >
          <Icon className="size-8" />
        </span>
      </div>

      <dl className="mt-6 grid gap-3 sm:grid-cols-3">
        {factsOf(delivery).map(({ icon: FactIcon, label, value }) => (
          <div
            key={label}
            className="flex items-center gap-3 rounded-2xl bg-white/12 px-4 py-3 backdrop-blur-sm"
          >
            <FactIcon aria-hidden="true" className="size-5 shrink-0 text-white/80" />
            <div className="min-w-0">
              <dt className="text-xs font-medium text-white/75">{label}</dt>
              <dd className="truncate font-bold">{value}</dd>
            </div>
          </div>
        ))}
      </dl>

      {delivery.cancellationReason && (
        <p className="mt-4 rounded-2xl bg-white/15 px-4 py-3 text-sm">
          <span className="font-bold">Why: </span>
          {delivery.cancellationReason}
        </p>
      )}
    </section>
  );
}
