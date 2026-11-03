import { ArrowRight, Banknote, CreditCard } from 'lucide-react';
import { useId } from 'react';
import { Link } from 'react-router';

import { CountdownRing, HURRY_SECONDS } from '@/components/business/PartnerArea/CountdownRing';
import { DeliveryJourney } from '@/components/business/PartnerArea/DeliveryJourney';
import { DeliveryStepActions } from '@/components/business/PartnerArea/DeliveryStepActions';
import { LiveLocationNote } from '@/components/business/PartnerArea/LiveLocationNote';
import { RouteTimeline, type RouteLeg } from '@/components/business/PartnerArea/RouteTimeline';
import { DeliveryStatusBadge } from '@/components/business/StatusBadges';
import { useNow } from '@/hooks/useNow';
import type { Delivery } from '@/types/delivery';
import { cn } from '@/utils/classNames';
import { DEFAULT_CURRENCY, partnerRouteTo } from '@/utils/constants';
import { formatCurrency } from '@/utils/formatUtils';
import {
  collectsCash,
  formatCountdown,
  nextPartnerStep,
  OFFER_ACCEPT_SECONDS,
  offerSecondsLeft,
  paidOnlineLabel,
} from '@/utils/partnerAreaUtils';

/** The stop the partner is heading to for each next step (an offer is not on its way yet). */
const ACTIVE_LEGS: Record<'accept' | 'pickup' | 'deliver', RouteLeg | null> = {
  accept: null,
  pickup: 'pickup',
  deliver: 'dropoff',
};

interface CurrentDeliveryCardProps {
  delivery: Delivery;
  /** Leave out the link to the delivery's own page (on that page itself) */
  hideDetailsLink?: boolean;
}

/**
 * The delivery a partner is working on, as a job ticket with the one next step: accept (or decline) an offer before
 * the ring runs out, pick up the food, hand it over (confirming the cash for cash on delivery). Every step goes to
 * delivery-service, which checks it again: a step that is no longer possible (the order was cancelled, the offer went
 * to someone else) is explained and the ticket shows what is true now.
 */
export function CurrentDeliveryCard({
  delivery,
  hideDetailsLink = false,
}: CurrentDeliveryCardProps) {
  const now = useNow(1000);
  const titleId = useId();
  const next = nextPartnerStep(delivery.status);
  const secondsLeft = offerSecondsLeft(delivery, now);
  const currency = delivery.currency ?? DEFAULT_CURRENCY;
  const cash = collectsCash(delivery);
  const amount = formatCurrency(delivery.cashToCollect, currency);
  const restaurant = delivery.restaurantName ?? 'the restaurant';
  const customer = delivery.customerName ?? 'the customer';
  const isOffer = delivery.status === 'ASSIGNED';

  return (
    <article
      aria-labelledby={titleId}
      className={cn(
        'overflow-hidden rounded-3xl bg-surface shadow-card',
        isOffer && 'ring-2 ring-brand-500/60',
      )}
    >
      <header
        className={cn(
          'flex items-start justify-between gap-4 border-b border-dashed border-line p-5 sm:p-6',
          isOffer && 'bg-linear-to-br from-brand-50 to-surface',
        )}
      >
        <div className="flex min-w-0 flex-col gap-1">
          <p className="text-xs font-bold tracking-wider text-brand-text uppercase">
            Order #{delivery.orderId}
          </p>
          <h2 id={titleId} className="text-xl font-extrabold tracking-tight sm:text-2xl">
            {isOffer ? 'New delivery offer' : 'Your delivery'}
          </h2>
          <p className="flex items-center gap-1.5 text-sm text-ink-soft">
            {cash ? (
              <Banknote aria-hidden="true" className="size-4 shrink-0" />
            ) : (
              <CreditCard aria-hidden="true" className="size-4 shrink-0" />
            )}
            {cash ? `Cash on delivery · ${amount}` : paidOnlineLabel(delivery.paymentMethod)}
          </p>
        </div>
        {secondsLeft === null ? (
          <DeliveryStatusBadge status={delivery.status} />
        ) : (
          <CountdownRing secondsLeft={secondsLeft} totalSeconds={OFFER_ACCEPT_SECONDS} />
        )}
      </header>

      <div className="flex flex-col gap-6 p-5 sm:p-6">
        <DeliveryJourney status={delivery.status} />

        <LiveLocationNote deliveryId={delivery.id} />

        {secondsLeft !== null && (
          <p
            className={cn(
              'text-sm font-semibold',
              secondsLeft > HURRY_SECONDS ? 'text-ink-soft' : 'text-danger-text',
            )}
          >
            {secondsLeft > 0
              ? `Accept within ${formatCountdown(secondsLeft)}, or the delivery goes to another partner.`
              : 'Time is up: the delivery goes to another partner.'}
          </p>
        )}

        <RouteTimeline delivery={delivery} activeLeg={next ? ACTIVE_LEGS[next] : null} />

        {cash && next !== null && (
          <div className="flex items-center gap-4 rounded-2xl bg-warning-50 p-4 ring-1 ring-warning-500/40">
            <span
              aria-hidden="true"
              className="flex size-11 shrink-0 items-center justify-center rounded-xl bg-warning-500 text-on-accent"
            >
              <Banknote className="size-5" />
            </span>
            <div className="min-w-0">
              <p className="text-xs font-bold tracking-wider text-warning-text uppercase">
                Cash on delivery
              </p>
              <p className="text-sm">
                Collect exactly <strong className="text-base">{amount}</strong> from {customer}.
              </p>
            </div>
          </div>
        )}

        {next === 'pickup' && (
          <p className="rounded-2xl bg-page px-4 py-3 text-sm text-ink-soft">
            Go to {restaurant}. Tap the button once the food is with you: the customer is told it is
            on its way.
          </p>
        )}

        <DeliveryStepActions delivery={delivery} />

        {!hideDetailsLink && (
          <Link
            to={partnerRouteTo.deliveryDetails(delivery.id)}
            className="group inline-flex items-center gap-1.5 self-center text-sm font-semibold text-brand-text hover:underline"
          >
            Delivery details and history
            <ArrowRight
              aria-hidden="true"
              className="size-4 transition-transform group-hover:translate-x-0.5"
            />
          </Link>
        )}
      </div>
    </article>
  );
}
