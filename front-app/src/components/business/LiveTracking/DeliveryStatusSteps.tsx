import { Check } from 'lucide-react';

import { ORDER_STEP_LOOKS } from '@/components/business/MyOrders/orderStepStyles';
import type { LiveTrackingPhase } from '@/types/liveTracking';
import type { OrderDetails } from '@/types/order';
import { cn } from '@/utils/classNames';
import { formatTime } from '@/utils/dateUtils';
import { trackingSteps } from '@/utils/liveTrackingUtils';
import type { ProgressStep } from '@/utils/shoppingUtils';

/** How screen readers hear each step's state */
const STEP_STATE_WORDS: Record<ProgressStep['state'], string> = {
  done: ' (done)',
  current: ' (now)',
  upcoming: ' (to come)',
};

/** Half of one of the six columns: where the first marker's centre is, as a share of the width. */
const EDGE = 100 / 12;

interface DeliveryStatusStepsProps {
  order: Pick<OrderDetails, 'status' | 'timeline'>;
  /** Where the live delivery is, when a partner is on the way (it may be a moment ahead of the order) */
  phase?: LiveTrackingPhase;
}

/**
 * Placed -> accepted -> preparing -> ready -> on the way -> delivered, from the order's saved status (the same steps
 * and names as the order page), with the time each step was reached. Never from the simulated ride.
 * Side by side with a green route filling up to the current step on wider screens; one under the other on a phone.
 * Done steps are green with a tick, the current one is in its own colour with a soft pulse, the rest are grey.
 */
export function DeliveryStatusSteps({ order, phase }: DeliveryStatusStepsProps) {
  const steps = trackingSteps(order, phase);
  const current = steps.findIndex((step) => step.state === 'current');
  // How far along the route the order is (0 to 1): up to the current step, or all of it once delivered
  const reached = current >= 0 ? current : steps.length - 1;
  const share = steps.length > 1 ? reached / (steps.length - 1) : 0;

  return (
    <section aria-label="Delivery status" className="relative">
      {/* The route, wider screens: a grey rail with a green fill */}
      <span
        aria-hidden="true"
        className="absolute top-5 hidden h-1 -translate-y-1/2 rounded-full bg-line sm:block"
        style={{ left: `${EDGE}%`, right: `${EDGE}%` }}
      />
      <span
        aria-hidden="true"
        className="absolute top-5 hidden h-1 -translate-y-1/2 rounded-full bg-linear-to-r from-brand-400 to-brand-600 transition-[width] duration-1000 ease-out sm:block"
        style={{ left: `${EDGE}%`, width: `${share * (100 - 2 * EDGE)}%` }}
      />
      {/* The route, phones: the same rail, standing */}
      <span
        aria-hidden="true"
        className="absolute top-5 bottom-5 left-5 w-1 -translate-x-1/2 overflow-hidden rounded-full bg-line sm:hidden"
      >
        <span
          className="block w-full rounded-full bg-linear-to-b from-brand-400 to-brand-600 transition-[height] duration-1000 ease-out"
          style={{ height: `${share * 100}%` }}
        />
      </span>

      <ol className="relative grid grid-cols-1 gap-4 sm:grid-cols-6 sm:gap-2">
        {steps.map(({ status, state, at }) => {
          const look = ORDER_STEP_LOOKS[status];
          const Icon = look?.icon ?? Check;
          return (
            <li
              key={status}
              aria-current={state === 'current' ? 'step' : undefined}
              className="flex items-center gap-3 sm:flex-col sm:gap-2 sm:text-center"
            >
              <span aria-hidden="true" className="relative flex size-10 shrink-0">
                {state === 'current' && (
                  <span
                    className={cn(
                      'absolute inset-0 rounded-full opacity-30 motion-safe:animate-ping',
                      look?.color,
                    )}
                  />
                )}
                <span
                  className={cn(
                    'relative flex size-10 items-center justify-center rounded-full ring-4 ring-surface transition-all duration-500',
                    state === 'done' && 'bg-brand-500 text-on-brand shadow-brand',
                    state === 'current' && cn(look?.color, 'scale-110 shadow-lg'),
                    state === 'upcoming' && 'bg-page text-ink-soft',
                  )}
                >
                  <Icon className="size-[1.125rem]" />
                  {state === 'done' && (
                    <span className="absolute -right-1 -bottom-1 flex size-[1.125rem] items-center justify-center rounded-full bg-surface text-brand-text shadow-sm">
                      <Check className="size-3" strokeWidth={3} />
                    </span>
                  )}
                </span>
              </span>
              <span className="flex min-w-0 flex-1 items-baseline justify-between gap-2 sm:flex-col sm:items-center sm:gap-0.5">
                <span
                  className={cn(
                    'text-sm font-semibold',
                    state === 'current' && 'text-ink',
                    state === 'upcoming' && 'font-medium text-ink-soft',
                  )}
                >
                  {look?.label ?? status}
                  <span className="sr-only">{STEP_STATE_WORDS[state]}</span>
                </span>
                {at && state !== 'upcoming' && (
                  <time dateTime={at} className="text-xs text-ink-soft tabular-nums">
                    {formatTime(at)}
                  </time>
                )}
              </span>
            </li>
          );
        })}
      </ol>
    </section>
  );
}
