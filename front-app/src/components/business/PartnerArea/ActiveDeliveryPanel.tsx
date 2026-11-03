import {
  ArrowRight,
  Banknote,
  CreditCard,
  House,
  Navigation,
  Phone,
  Store,
  Timer,
  type LucideIcon,
} from 'lucide-react';
import { Link } from 'react-router';

import { DeliveryStepActions } from '@/components/business/PartnerArea/DeliveryStepActions';
import { useNow } from '@/hooks/useNow';
import type { Delivery, DeliveryAddress } from '@/types/delivery';
import { cn } from '@/utils/classNames';
import { DEFAULT_CURRENCY, partnerRouteTo } from '@/utils/constants';
import { formatCurrency } from '@/utils/formatUtils';
import {
  collectsCash,
  formatCountdown,
  formatDeliveryAddress,
  mapsLinkOf,
  nextPartnerStep,
  offerSecondsLeft,
  paidOnlineLabel,
} from '@/utils/partnerAreaUtils';

import { PARTNER_DELIVERY_LOOKS } from './partnerDeliveryLooks';
import { PartnerDeliveryStatusPill } from './PartnerDeliveryStatusPill';

const SMALL_LINK =
  'inline-flex h-8 items-center gap-1 rounded-full px-3 text-xs font-semibold transition-colors focus-visible:ring-4 focus-visible:ring-brand-500/30 focus-visible:outline-none';

/** One stop, compact: what and where, with "Navigate" (and "Call" for the customer). */
function Stop({
  icon: Icon,
  kicker,
  name,
  address,
  phone,
  isNext,
}: {
  icon: LucideIcon;
  kicker: string;
  name: string | null;
  address: DeliveryAddress | null;
  phone?: string | null;
  /** The stop the partner is heading to now */
  isNext: boolean;
}) {
  const text = formatDeliveryAddress(address);
  const maps = mapsLinkOf(address);
  return (
    <li
      className={cn(
        'flex min-w-0 gap-3 rounded-xl p-2.5',
        isNext ? 'bg-brand-50 ring-1 ring-brand-300' : 'bg-page',
      )}
    >
      <span
        aria-hidden="true"
        className={cn(
          'flex size-9 shrink-0 items-center justify-center rounded-lg',
          isNext ? 'bg-brand-500 text-on-brand' : 'bg-surface text-ink-soft',
        )}
      >
        <Icon className="size-4" />
      </span>
      <div className="flex min-w-0 flex-1 flex-col gap-0.5">
        <p className="flex items-center gap-2 text-[11px] font-bold tracking-wider text-ink-soft uppercase">
          {kicker}
          {isNext && (
            <span className="rounded-full bg-brand-500 px-1.5 text-[10px] tracking-normal text-on-brand normal-case">
              Next stop
            </span>
          )}
        </p>
        {name && <p className="truncate text-sm font-bold text-ink">{name}</p>}
        <p className="line-clamp-2 text-xs text-ink-soft">{text ?? 'Address on its way'}</p>
        {(maps ?? phone) && (
          <div className="mt-1 flex flex-wrap gap-1.5">
            {maps && (
              <a
                href={maps}
                target="_blank"
                rel="noreferrer"
                aria-label={`Navigate to ${name ?? kicker.toLowerCase()} (opens in a new tab)`}
                className={cn(SMALL_LINK, 'bg-surface text-brand-text-strong ring-1 ring-line')}
              >
                <Navigation aria-hidden="true" className="size-3.5" />
                Navigate
              </a>
            )}
            {phone && (
              <a
                href={`tel:${phone}`}
                aria-label={`Call ${name ?? 'the customer'}`}
                className={cn(SMALL_LINK, 'bg-surface text-info-ink ring-1 ring-line')}
              >
                <Phone aria-hidden="true" className="size-3.5" />
                Call
              </a>
            )}
          </div>
        )}
      </div>
    </li>
  );
}

/**
 * The delivery being worked on, at the top of "My deliveries", compact: the order and its status (with the time left
 * to accept an offer), what to collect, the two stops (the next one marked, each with "Navigate"), and the next
 * step's buttons, the same as on the "Today" ticket. "Details" opens its page with the whole history.
 */
export function ActiveDeliveryPanel({ delivery }: { delivery: Delivery }) {
  const now = useNow(1000);
  const look = PARTNER_DELIVERY_LOOKS[delivery.status];
  const next = nextPartnerStep(delivery.status);
  const secondsLeft = offerSecondsLeft(delivery, now);
  const cash = collectsCash(delivery);
  const amount = formatCurrency(delivery.cashToCollect, delivery.currency ?? DEFAULT_CURRENCY);

  return (
    <section
      aria-label={`Delivery in progress: order #${delivery.orderId}`}
      className={cn(
        'relative overflow-hidden rounded-2xl bg-surface shadow-card ring-2',
        look.ring,
      )}
    >
      <span aria-hidden="true" className={cn('absolute inset-y-0 left-0 w-1.5', look.stripe)} />
      <div className="flex flex-col gap-3 p-4 pl-5">
        <div className="flex flex-wrap items-center gap-x-3 gap-y-1.5">
          <h2 className="text-base font-extrabold text-ink">Order #{delivery.orderId}</h2>
          <PartnerDeliveryStatusPill status={delivery.status} />
          {secondsLeft !== null && (
            <span
              className={cn(
                'inline-flex items-center gap-1 text-xs font-semibold tabular-nums',
                secondsLeft > 30 ? 'text-ink-soft' : 'text-danger-text',
              )}
            >
              <Timer aria-hidden="true" className="size-3.5" />
              {secondsLeft > 0
                ? `Accept within ${formatCountdown(secondsLeft)}`
                : 'Time is up: it goes to another partner'}
            </span>
          )}
          <span
            className={cn(
              'ml-auto inline-flex items-center gap-1.5 rounded-full px-2.5 py-0.5 text-xs font-bold',
              cash ? 'bg-warning-100 text-warning-ink' : 'bg-info-100 text-info-ink',
            )}
          >
            {cash ? (
              <Banknote aria-hidden="true" className="size-3.5" />
            ) : (
              <CreditCard aria-hidden="true" className="size-3.5" />
            )}
            {cash ? `Collect ${amount}` : paidOnlineLabel(delivery.paymentMethod)}
          </span>
        </div>

        <ol aria-label="Route" className="grid gap-2 md:grid-cols-2">
          <Stop
            icon={Store}
            kicker="Pickup"
            name={delivery.restaurantName}
            address={delivery.pickupAddress}
            isNext={next === 'pickup'}
          />
          <Stop
            icon={House}
            kicker="Drop-off"
            name={delivery.customerName}
            address={delivery.dropoffAddress}
            phone={delivery.customerPhone}
            isNext={next === 'deliver'}
          />
        </ol>

        <div className="flex flex-wrap items-center justify-between gap-3">
          <DeliveryStepActions delivery={delivery} compact />
          <Link
            to={partnerRouteTo.deliveryDetails(delivery.id)}
            aria-label={`Details of order #${delivery.orderId}`}
            className="group ml-auto inline-flex items-center gap-1 rounded-md text-sm font-semibold text-brand-text hover:underline focus-visible:ring-4 focus-visible:ring-brand-500/30 focus-visible:outline-none"
          >
            Details
            <ArrowRight
              aria-hidden="true"
              className="size-4 transition-transform group-hover:translate-x-0.5"
            />
          </Link>
        </div>
      </div>
    </section>
  );
}
