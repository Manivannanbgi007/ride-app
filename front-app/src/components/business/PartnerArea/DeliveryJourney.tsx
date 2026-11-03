import type { DeliveryStatus } from '@/types/delivery';
import { cn } from '@/utils/classNames';
import { journeyStepOf, PARTNER_JOURNEY } from '@/utils/partnerAreaUtils';

/** Offered, accepted, picked up, delivered: where this delivery is, as a bar of four segments. */
export function DeliveryJourney({ status }: { status: DeliveryStatus }) {
  const reached = journeyStepOf(status);
  return (
    <ol aria-label="Delivery progress" className="grid grid-cols-4 gap-1.5">
      {PARTNER_JOURNEY.map((step, index) => {
        const done = index <= reached;
        const isCurrent = index === reached;
        return (
          <li
            key={step.status}
            aria-current={isCurrent ? 'step' : undefined}
            className="flex flex-col gap-1.5"
          >
            <span
              aria-hidden="true"
              className={cn(
                'relative h-1.5 overflow-hidden rounded-full',
                done ? 'bg-brand-500' : 'bg-line',
              )}
            >
              {isCurrent && index < PARTNER_JOURNEY.length - 1 && (
                <span className="absolute inset-0 animate-shimmer bg-linear-to-r from-transparent via-white/70 to-transparent bg-size-[200%_100%] motion-reduce:hidden" />
              )}
            </span>
            <span
              className={cn(
                'text-[11px] sm:text-xs',
                isCurrent && 'font-bold text-brand-text',
                done && !isCurrent && 'font-semibold',
                !done && 'text-ink-soft',
              )}
            >
              {step.label}
              <span className="sr-only">{done ? ' (done)' : ' (to do)'}</span>
            </span>
          </li>
        );
      })}
    </ol>
  );
}
