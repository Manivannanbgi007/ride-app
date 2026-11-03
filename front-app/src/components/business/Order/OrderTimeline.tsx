import { Card } from '@/components/common/Card';
import type { TimelineEntry } from '@/types/order';
import { cn } from '@/utils/classNames';
import { ACTOR_TYPE_LABELS, ORDER_STATUS_LABELS } from '@/utils/constants';
import { formatDateTime } from '@/utils/dateUtils';

/** Every step of the order, oldest first: status, who made the change, when, and its note. */
export function OrderTimeline({ timeline }: { timeline: readonly TimelineEntry[] }) {
  return (
    <Card title="Timeline">
      <OrderTimelineList timeline={timeline} />
    </Card>
  );
}

/** The steps without a card around them (the order page's card, and the history pop-up of the order list). */
export function OrderTimelineList({ timeline }: { timeline: readonly TimelineEntry[] }) {
  return (
    <>
      <ol className="relative flex flex-col gap-6 border-l-2 border-line pl-6">
        {timeline.map((entry, index) => {
          const ended = entry.status === 'CANCELLED' || entry.status === 'REJECTED';
          return (
            <li key={`${entry.status}-${entry.at}`} className="relative">
              <span
                aria-hidden="true"
                className={cn(
                  'absolute top-1 -left-[1.95rem] size-3.5 rounded-full border-2 border-surface',
                  ended
                    ? 'bg-danger-500'
                    : index === timeline.length - 1
                      ? 'bg-brand-500'
                      : 'bg-ink-soft',
                )}
              />
              <p className="font-semibold">{ORDER_STATUS_LABELS[entry.status]}</p>
              <p className="text-xs text-ink-soft">
                {formatDateTime(entry.at)} by {ACTOR_TYPE_LABELS[entry.actorType].toLowerCase()}
                {entry.actorUserId ? ` #${entry.actorUserId}` : ''}
              </p>
              {entry.note && <p className="mt-1 text-sm">{entry.note}</p>}
            </li>
          );
        })}
      </ol>
    </>
  );
}
