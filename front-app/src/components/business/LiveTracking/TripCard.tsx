import { House, Store, type LucideIcon } from 'lucide-react';

import type { LiveTracking } from '@/types/liveTracking';
import { cn } from '@/utils/classNames';
import { formatTime } from '@/utils/dateUtils';

type StopState = 'done' | 'now' | 'next' | 'later';

/**
 * The trip as two stops on a route: the restaurant (where the partner picks up the food), then the customer. The
 * stop the partner is at, or heading to, is highlighted.
 */
export function TripCard({ tracking }: { tracking: LiveTracking }) {
  const { phase } = tracking;
  const firstName = tracking.partner.name.split(' ')[0];
  const delivered = phase === 'DELIVERED';
  const restaurantState: StopState =
    phase === 'TO_RESTAURANT' ? 'next' : phase === 'AT_RESTAURANT' ? 'now' : 'done';
  const restaurantNote =
    phase === 'TO_RESTAURANT'
      ? `${firstName} picks up your order here`
      : phase === 'AT_RESTAURANT'
        ? `${firstName} is here now`
        : 'Picked up';

  return (
    <section
      aria-label="The trip"
      className="rounded-3xl bg-surface p-5 shadow-card motion-safe:animate-page-in"
    >
      <h2 className="mb-4 text-sm font-bold">The trip</h2>
      <ol className="relative flex flex-col gap-5">
        {/* The road between the two stops: solid once the food is picked up */}
        <span
          aria-hidden="true"
          className={cn(
            'absolute top-10 bottom-10 left-5 -translate-x-1/2 border-l-2',
            restaurantState === 'done'
              ? 'border-solid border-brand-500'
              : 'border-dashed border-line',
          )}
        />
        <TripStop
          icon={Store}
          label={tracking.restaurant.name}
          detail={tracking.restaurant.address}
          state={restaurantState}
          note={restaurantNote}
        />
        <TripStop
          icon={House}
          label="You"
          detail={tracking.dropoff.address}
          state={delivered ? 'done' : phase === 'TO_CUSTOMER' ? 'now' : 'later'}
          note={youNote(tracking, firstName)}
        />
      </ol>
    </section>
  );
}

/** The note under "You": an arrival time only when the delivery gives one (the demo simulation), never a guess. */
function youNote(tracking: LiveTracking, firstName: string): string {
  if (tracking.phase === 'DELIVERED') {
    return `Delivered by ${firstName}`;
  }
  if (tracking.estimatedArrival) {
    return `Arriving around ${formatTime(tracking.estimatedArrival)}`;
  }
  return tracking.phase === 'TO_CUSTOMER' ? `${firstName} is on the way to you` : 'Next stop';
}

/** One stop: a round icon on the road (green once passed, ringed while the partner is there or headed to it). */
function TripStop({
  icon: Icon,
  label,
  detail,
  state,
  note,
}: {
  icon: LucideIcon;
  label: string;
  detail: string;
  state: StopState;
  note: string;
}) {
  return (
    <li className="relative flex gap-4" aria-current={state === 'now' ? 'step' : undefined}>
      <span
        aria-hidden="true"
        className={cn(
          'relative flex size-10 shrink-0 items-center justify-center rounded-full ring-4 ring-surface transition-colors duration-500',
          state === 'done' && 'bg-brand-500 text-on-brand shadow-brand',
          state === 'now' && 'bg-brand-700 text-white shadow-brand',
          state === 'next' && 'bg-brand-50 text-brand-text',
          state === 'later' && 'bg-page text-ink-soft',
        )}
      >
        {state === 'now' && (
          <span className="absolute inset-0 rounded-full bg-brand-500/40 motion-safe:animate-ping" />
        )}
        <Icon className="relative size-[1.125rem]" />
      </span>
      <div className="min-w-0 pt-0.5">
        <p className="font-bold">{label}</p>
        {detail && <p className="text-sm text-ink-soft">{detail}</p>}
        <p
          className={cn(
            'mt-1 text-xs font-semibold',
            state === 'now' ? 'text-brand-text' : 'text-ink-soft',
          )}
        >
          {note}
        </p>
      </div>
    </li>
  );
}
