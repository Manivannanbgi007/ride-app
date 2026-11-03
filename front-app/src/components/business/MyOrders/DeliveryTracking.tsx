import { Banknote, Bike, Check, Phone } from 'lucide-react';

import { Badge } from '@/components/common/Badge';
import { buttonStyles } from '@/components/common/Button';
import type { DeliveryStatus } from '@/types/delivery';
import type { OrderTracking } from '@/types/shopping';
import { cn } from '@/utils/classNames';
import { CUSTOMER_DELIVERY_STATUS_LABELS, VEHICLE_TYPE_LABELS } from '@/utils/constants';
import { formatDateTime } from '@/utils/dateUtils';
import { formatCurrency, initialsOf } from '@/utils/formatUtils';

/** The four stages the customer follows, and the delivery statuses that belong to each (cancelled: none). */
const STAGES: readonly { label: string; statuses: readonly DeliveryStatus[] }[] = [
  { label: 'Finding a partner', statuses: ['PENDING', 'ASSIGNED'] },
  { label: 'To the restaurant', statuses: ['ACCEPTED'] },
  { label: 'On the way to you', statuses: ['PICKED_UP'] },
  { label: 'Delivered', statuses: ['DELIVERED'] },
];

/**
 * The delivery of the order: who brings it (once a partner accepted) with a way to call them while on the way, where
 * the delivery is in its four stages, the cash to have ready, and every step with its time.
 */
export function DeliveryTracking({ tracking }: { tracking: OrderTracking }) {
  const cancelled = tracking.status === 'CANCELLED';
  const stageIndex = STAGES.findIndex((stage) => stage.statuses.includes(tracking.status));
  const delivered = tracking.status === 'DELIVERED';

  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-wrap items-center gap-4 rounded-2xl bg-linear-to-br from-brand-50 to-surface p-4 ring-1 ring-brand-100">
        <span className="relative flex size-14 shrink-0 items-center justify-center">
          {!tracking.partnerName && !cancelled && (
            <span
              aria-hidden="true"
              className="absolute inset-0 animate-ping rounded-full bg-brand-500/25 motion-reduce:hidden"
            />
          )}
          <span className="relative flex size-14 items-center justify-center rounded-full bg-brand-500 text-lg font-bold text-on-brand shadow-brand ring-4 ring-surface">
            {tracking.partnerName ? (
              initialsOf(tracking.partnerName)
            ) : (
              <Bike aria-hidden="true" className="size-6" />
            )}
          </span>
        </span>
        <div className="min-w-0 flex-1">
          <p className="font-semibold">
            {tracking.partnerName ?? 'Finding a delivery partner'}
            {tracking.vehicleType && (
              <span className="font-normal text-ink-soft">
                {' '}
                · {VEHICLE_TYPE_LABELS[tracking.vehicleType]}
              </span>
            )}
          </p>
          <Badge tone={cancelled ? 'danger' : delivered ? 'success' : 'info'}>
            {CUSTOMER_DELIVERY_STATUS_LABELS[tracking.status]}
          </Badge>
        </div>
        {tracking.partnerPhone && (
          <a href={`tel:${tracking.partnerPhone}`} className={buttonStyles('primary', 'sm')}>
            <Phone aria-hidden="true" className="size-4" />
            Call {tracking.partnerName?.split(' ')[0] ?? 'the partner'}
          </a>
        )}
      </div>

      {!cancelled && (
        <ol aria-label="Delivery stages" className="grid grid-cols-2 gap-2 sm:grid-cols-4">
          {STAGES.map((stage, index) => {
            const done = delivered || index < stageIndex;
            const current = !delivered && index === stageIndex;
            return (
              <li
                key={stage.label}
                aria-current={current ? 'step' : undefined}
                className={cn(
                  'flex items-center gap-2 rounded-xl px-3 py-2.5 text-xs font-semibold transition-colors duration-500',
                  done && 'bg-brand-50 text-brand-text-strong',
                  current && 'bg-brand-500 text-on-brand shadow-brand',
                  !done && !current && 'bg-page text-ink-soft',
                )}
              >
                <span
                  aria-hidden="true"
                  className={cn(
                    'flex size-5 shrink-0 items-center justify-center rounded-full text-[10px]',
                    done && 'bg-brand-500 text-on-brand',
                    current && 'bg-surface text-brand-text-strong',
                    !done && !current && 'bg-surface text-ink-soft',
                  )}
                >
                  {done ? <Check className="size-3" strokeWidth={3} /> : index + 1}
                </span>
                {stage.label}
                <span className="sr-only">{done ? ' (done)' : current ? ' (now)' : ' (next)'}</span>
              </li>
            );
          })}
        </ol>
      )}

      {tracking.cashToCollect > 0 && tracking.status !== 'DELIVERED' && (
        <p className="flex items-center gap-3 rounded-xl bg-warning-50 px-4 py-3 text-sm text-warning-text">
          <Banknote aria-hidden="true" className="size-5 shrink-0" />
          <span>
            Cash on delivery: please have{' '}
            {formatCurrency(tracking.cashToCollect, tracking.currency)} ready.
          </span>
        </p>
      )}

      {tracking.steps.length > 0 && (
        <ol className="relative flex flex-col gap-3 text-sm">
          <span
            aria-hidden="true"
            className="absolute top-2 bottom-2 left-[0.4375rem] w-0.5 rounded-full bg-brand-100"
          />
          {tracking.steps.map((step, index) => (
            <li
              key={`${step.at}-${index}`}
              className="relative flex flex-wrap items-baseline justify-between gap-x-4 gap-y-0.5 pl-7"
            >
              <span
                aria-hidden="true"
                className={cn(
                  'absolute top-1 left-0 size-3.5 rounded-full border-2 border-surface',
                  index === tracking.steps.length - 1
                    ? 'bg-brand-600 ring-4 ring-brand-100'
                    : 'bg-brand-400',
                )}
              />
              <span className={cn(index === tracking.steps.length - 1 && 'font-semibold')}>
                {step.note ?? CUSTOMER_DELIVERY_STATUS_LABELS[step.status]}
              </span>
              <span className="text-xs text-ink-soft">{formatDateTime(step.at)}</span>
            </li>
          ))}
        </ol>
      )}
    </div>
  );
}
