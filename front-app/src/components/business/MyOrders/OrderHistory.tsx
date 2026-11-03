import { Clock, XCircle, type LucideIcon } from 'lucide-react';

import { ORDER_STEP_LOOKS } from '@/components/business/MyOrders/orderStepStyles';
import type { OrderStatus, TimelineEntry } from '@/types/order';
import { cn } from '@/utils/classNames';
import { CUSTOMER_ACTOR_LABELS, CUSTOMER_ORDER_STATUS_LABELS } from '@/utils/constants';
import { formatDateTime } from '@/utils/dateUtils';

/** Statuses that are not steps of the route get their own marker. */
const EXTRA: Partial<Record<OrderStatus, { icon: LucideIcon; color: string }>> = {
  PAYMENT_PENDING: { icon: Clock, color: 'bg-warning-500 text-on-accent' },
  CANCELLED: { icon: XCircle, color: 'bg-danger-500 text-white' },
  REJECTED: { icon: XCircle, color: 'bg-danger-500 text-white' },
};

/**
 * Everything that happened to the order, newest first, with who did it and the note ("Payment 12 received"). Each
 * entry has the marker of its step (the colour it has on the order's route); the newest is highlighted.
 */
export function OrderHistory({ timeline }: { timeline: readonly TimelineEntry[] }) {
  const newestFirst = [...timeline].reverse();
  return (
    <ol className="relative flex flex-col gap-5">
      <span
        aria-hidden="true"
        className="absolute top-3 bottom-3 left-[0.9375rem] w-0.5 rounded-full bg-line"
      />
      {newestFirst.map((entry, index) => {
        const look = ORDER_STEP_LOOKS[entry.status] ?? EXTRA[entry.status];
        const Icon = look?.icon ?? Clock;
        const newest = index === 0;
        return (
          <li key={`${entry.at}-${entry.status}-${index}`} className="relative flex gap-4">
            <span
              aria-hidden="true"
              className={cn(
                'relative z-10 flex size-8 shrink-0 items-center justify-center rounded-full ring-4 ring-surface',
                look?.color ?? 'bg-page text-ink-soft',
                newest && 'shadow-lg',
              )}
            >
              <Icon className="size-4" />
            </span>
            <div className="min-w-0 pt-0.5">
              <p className="flex flex-wrap items-center gap-2 font-semibold">
                {CUSTOMER_ORDER_STATUS_LABELS[entry.status]}
                {newest && (
                  <span className="rounded-full bg-brand-50 px-2 py-0.5 text-[11px] font-bold text-brand-text-strong">
                    Latest
                  </span>
                )}
              </p>
              <p className="text-xs text-ink-soft">
                {formatDateTime(entry.at)} · by {CUSTOMER_ACTOR_LABELS[entry.actorType]}
              </p>
              {entry.note && <p className="mt-1 text-sm">{entry.note}</p>}
            </div>
          </li>
        );
      })}
    </ol>
  );
}
