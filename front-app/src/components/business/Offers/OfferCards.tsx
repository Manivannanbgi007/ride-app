import { CalendarClock, Store, TicketPercent, Timer, Wallet } from 'lucide-react';
import { useId } from 'react';

import { CopyCode } from '@/components/business/Offers/CopyCode';
import { UseOffer } from '@/components/business/Offers/UseOffer';
import { FoodPhoto } from '@/components/common/FoodPhoto';
import { useNow } from '@/hooks/useNow';
import type { Offer } from '@/types/shopping';
import { cn } from '@/utils/classNames';
import { formatDate } from '@/utils/dateUtils';
import {
  formatCountdown,
  offerEndsAt,
  offerImageOf,
  offerMinimumLabel,
  offerScopeLabel,
  urgencyOf,
} from '@/utils/offerUtils';

/**
 * The colour washed over each ticket's picture, taken in turn so neighbours differ. Deep ends of the theme's
 * palette: the white headline on them reads at 4.5:1 or more, in light and dark mode alike.
 */
const TICKET_TONES = [
  'from-brand-900/95 via-brand-800/45',
  'from-st-placed-to/95 via-st-placed-from/45',
  'from-st-accepted-to/95 via-st-accepted-from/45',
  'from-st-out-to/95 via-st-out-from/45',
] as const;

/**
 * How long an offer still runs: a live countdown while it ends within a week (red on its last day, amber before),
 * otherwise its last day.
 */
export function OfferDeadline({ offer, className }: { offer: Offer; className?: string }) {
  const now = useNow(1_000);
  const left = offerEndsAt(offer.validUntil) - now;
  const urgency = urgencyOf(left);

  if (urgency === 'limited') {
    return (
      <span
        className={cn(
          'inline-flex items-center gap-1.5 rounded-full bg-page px-2.5 py-1 font-semibold text-ink-soft ring-1 ring-line',
          className,
        )}
      >
        <CalendarClock aria-hidden="true" className="size-3.5" />
        Valid until {formatDate(offer.validUntil)}
      </span>
    );
  }
  return (
    <span
      className={cn(
        'inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 font-bold',
        urgency === 'today' ? 'bg-danger-100 text-danger-ink' : 'bg-warning-100 text-warning-ink',
        className,
      )}
    >
      <Timer aria-hidden="true" className="size-3.5" />
      {urgency === 'today' ? 'Ends today' : 'Ends in'}
      <span
        role="timer"
        aria-label={`Time left for ${offer.code}`}
        className="font-mono tabular-nums"
      >
        {formatCountdown(left)}
      </span>
    </span>
  );
}

/**
 * A coupon as a ticket: a food picture with what it gives written large across it and where it can be used, a
 * torn-off line, then what it is for, the smallest order, how long it runs, the code to copy and the action.
 */
export function CouponTicket({
  offer,
  index,
  cartHasItems,
}: {
  offer: Offer;
  index: number;
  cartHasItems: boolean;
}) {
  const titleId = useId();
  const ScopeIcon = offer.restaurantId === null ? TicketPercent : Store;

  return (
    <article
      aria-labelledby={titleId}
      className="group relative flex w-full flex-col rounded-3xl bg-surface shadow-card ring-1 ring-line/60 transition duration-300 hover:shadow-xl motion-safe:hover:-translate-y-1"
    >
      <div className="relative h-40 overflow-hidden rounded-t-3xl">
        <FoodPhoto
          sources={[offerImageOf(offer)]}
          className="size-full transition-transform duration-500 group-hover:scale-105 motion-reduce:transition-none"
          fallback={<span className="block size-full bg-brand-700" />}
        />
        <span
          aria-hidden="true"
          className={cn(
            'absolute inset-0 bg-linear-to-t to-transparent',
            TICKET_TONES[index % TICKET_TONES.length],
          )}
        />
        <span className="absolute top-3 left-3 inline-flex items-center gap-1.5 rounded-full bg-white/90 px-2.5 py-1 text-xs font-bold text-slate-900 shadow-sm">
          <ScopeIcon aria-hidden="true" className="size-3.5" />
          {offerScopeLabel(offer)}
        </span>
        <h3
          id={titleId}
          className="absolute inset-x-5 bottom-4 text-3xl leading-tight font-extrabold tracking-tight text-white drop-shadow-sm"
        >
          {offer.summary}
        </h3>
      </div>

      {/* The tear-off line: a dashed rule with a notch cut out at each end */}
      <div aria-hidden="true" className="relative">
        <span className="absolute -top-3 -left-3 size-6 rounded-full bg-page" />
        <span className="absolute -top-3 -right-3 size-6 rounded-full bg-page" />
      </div>

      <div className="flex flex-1 flex-col gap-3 border-t-2 border-dashed border-line p-5">
        {offer.description && (
          <p className="leading-snug font-semibold text-ink">{offer.description}</p>
        )}
        <p className="flex flex-wrap items-center gap-2 text-xs">
          <span className="inline-flex items-center gap-1.5 rounded-full bg-brand-50 px-2.5 py-1 font-semibold text-brand-text-strong">
            <Wallet aria-hidden="true" className="size-3.5" />
            {offerMinimumLabel(offer)}
          </span>
          <OfferDeadline offer={offer} />
        </p>
        <div className="mt-auto flex items-center gap-2 pt-2">
          <CopyCode code={offer.code} className="flex-1" />
          <UseOffer offer={offer} cartHasItems={cartHasItems} />
        </div>
      </div>
    </article>
  );
}
