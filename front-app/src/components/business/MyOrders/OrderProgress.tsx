import { Check } from 'lucide-react';

import { ORDER_STEP_LOOKS } from '@/components/business/MyOrders/orderStepStyles';
import type { OrderDetails } from '@/types/order';
import { cn } from '@/utils/classNames';
import { formatDateTime } from '@/utils/dateUtils';
import { orderProgress } from '@/utils/shoppingUtils';

/** Half of one of the six columns: where the first marker's centre is, as a share of the width. */
const EDGE = 100 / 12;

/**
 * Placed → Accepted → Preparing → Ready → On the way → Delivered, with the time each step was reached. A green
 * route fills up to the current step (and slides on when the order moves), done steps are green with a tick, and the
 * current one is in its own colour with a pulse. Side by side on wide screens, one under the other on a phone.
 */
export function OrderProgress({ order }: { order: OrderDetails }) {
  const steps = orderProgress(order);
  const current = steps.findIndex((step) => step.state === 'current');
  // How far along the route the order is (0 to 1): up to the current step, or all of it once delivered
  const reached = current >= 0 ? current : steps.length - 1;
  const share = steps.length > 1 ? reached / (steps.length - 1) : 0;

  return (
    <div className="relative">
      {/* The route, wide screens: a grey rail with a green fill */}
      <span
        aria-hidden="true"
        className="absolute top-6 hidden h-1.5 -translate-y-1/2 rounded-full bg-page sm:block"
        style={{ left: `${EDGE}%`, right: `${EDGE}%` }}
      />
      <span
        aria-hidden="true"
        className="absolute top-6 hidden h-1.5 -translate-y-1/2 rounded-full bg-linear-to-r from-brand-400 to-brand-600 transition-[width] duration-1000 ease-out sm:block"
        style={{ left: `${EDGE}%`, width: `${share * (100 - 2 * EDGE)}%` }}
      />
      {/* The route, phones: the same rail, standing */}
      <span
        aria-hidden="true"
        className="absolute top-6 bottom-6 left-6 w-1.5 -translate-x-1/2 rounded-full bg-page sm:hidden"
      >
        <span
          className="block w-full rounded-full bg-linear-to-b from-brand-400 to-brand-600 transition-[height] duration-1000 ease-out"
          style={{ height: `${share * 100}%` }}
        />
      </span>

      <ol
        className="relative grid grid-cols-1 gap-6 sm:grid-cols-6 sm:gap-2"
        aria-label="Order progress"
      >
        {steps.map((step) => {
          const look = ORDER_STEP_LOOKS[step.status];
          const Icon = look?.icon ?? Check;
          return (
            <li
              key={step.status}
              aria-current={step.state === 'current' ? 'step' : undefined}
              className="flex items-center gap-4 sm:flex-col sm:gap-2 sm:text-center"
            >
              <span className="relative flex size-12 shrink-0 items-center justify-center">
                {step.state === 'current' && (
                  <span
                    aria-hidden="true"
                    className={cn(
                      'absolute inset-0 animate-ping rounded-full opacity-25 motion-reduce:hidden',
                      look?.color,
                    )}
                  />
                )}
                <span
                  className={cn(
                    'relative flex size-12 items-center justify-center rounded-full ring-4 ring-surface transition-all duration-500',
                    step.state === 'done' && 'bg-brand-500 text-on-brand shadow-brand',
                    step.state === 'current' && cn(look?.color, 'scale-110 shadow-lg'),
                    step.state === 'upcoming' && 'bg-page text-ink-soft',
                  )}
                >
                  <Icon aria-hidden="true" className="size-5" />
                  {step.state === 'done' && (
                    <span
                      aria-hidden="true"
                      className="absolute -right-1 -bottom-1 flex size-5 items-center justify-center rounded-full bg-surface text-brand-text shadow-sm"
                    >
                      <Check className="size-3.5" strokeWidth={3} />
                    </span>
                  )}
                </span>
              </span>
              <span className="flex flex-col sm:items-center">
                <span
                  className={cn(
                    'text-sm font-semibold',
                    step.state === 'upcoming' && 'text-ink-soft',
                    step.state === 'current' && 'text-ink',
                  )}
                >
                  {look?.label ?? step.status}
                  <span className="sr-only">
                    {step.state === 'done'
                      ? ' (done)'
                      : step.state === 'current'
                        ? ' (now)'
                        : ' (next)'}
                  </span>
                </span>
                {step.at && (
                  <span className="text-xs text-ink-soft">{formatDateTime(step.at)}</span>
                )}
              </span>
            </li>
          );
        })}
      </ol>
    </div>
  );
}
