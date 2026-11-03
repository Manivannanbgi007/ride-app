import {
  BellRing,
  CheckCheck,
  ChefHat,
  CircleCheck,
  CircleX,
  History,
  PackageCheck,
  Repeat,
  TimerOff,
  X,
  type LucideIcon,
} from 'lucide-react';

import type { DeliveryEventType, DeliveryTimelineEntry } from '@/types/delivery';
import { cn } from '@/utils/classNames';
import { formatDateTime, formatTime } from '@/utils/dateUtils';
import { formatMinutes } from '@/utils/formatUtils';
import { minutesBetween } from '@/utils/partnerAreaUtils';

type Tone = 'brand' | 'neutral' | 'danger';

/** Each step in the partner's words, with its icon; refusals and cancellations in red */
const STEPS: Record<DeliveryEventType, { icon: LucideIcon; title: string; tone: Tone }> = {
  CREATED: { icon: ChefHat, title: 'Food ready', tone: 'neutral' },
  ASSIGNED: { icon: BellRing, title: 'Offered', tone: 'neutral' },
  ACCEPTED: { icon: CircleCheck, title: 'Accepted', tone: 'brand' },
  DECLINED: { icon: X, title: 'Declined', tone: 'danger' },
  EXPIRED: { icon: TimerOff, title: 'Offer expired', tone: 'danger' },
  REASSIGNED: { icon: Repeat, title: 'Reassigned', tone: 'neutral' },
  PICKED_UP: { icon: PackageCheck, title: 'Picked up', tone: 'brand' },
  DELIVERED: { icon: CheckCheck, title: 'Delivered', tone: 'brand' },
  CANCELLED: { icon: CircleX, title: 'Cancelled', tone: 'danger' },
};

const BUBBLES: Record<Tone, string> = {
  brand: 'bg-brand-500 text-on-brand shadow-brand',
  neutral: 'bg-brand-50 text-brand-text-strong',
  danger: 'bg-danger-50 text-danger-text',
};

/** The same day as the step before: only the time is shown */
function sameDay(a: string, b: string): boolean {
  return new Date(a).toDateString() === new Date(b).toDateString();
}

/**
 * The delivery step by step, oldest first, as the partner lived it: an icon per step, delivery-service's note, the
 * time, and how long the step took since the one before ("+7 min" on the line between them).
 */
export function PartnerDeliveryTimeline({
  timeline,
}: {
  timeline: readonly DeliveryTimelineEntry[];
}) {
  return (
    <section
      aria-labelledby="delivery-history"
      className="rounded-3xl bg-surface p-5 shadow-card sm:p-6"
    >
      <div className="flex items-center gap-3">
        <span
          aria-hidden="true"
          className="flex size-9 items-center justify-center rounded-xl bg-brand-50 text-brand-text-strong"
        >
          <History className="size-4.5" />
        </span>
        <div>
          <h2 id="delivery-history" className="text-base font-bold tracking-tight">
            History
          </h2>
          <p className="text-sm text-ink-soft">Every step, with the time it took</p>
        </div>
      </div>

      {timeline.length === 0 ? (
        <p className="mt-5 text-sm text-ink-soft">The steps appear here as they happen.</p>
      ) : (
        <ol className="mt-6 flex flex-col">
          {timeline.map((entry, index) => {
            const { icon: Icon, title, tone } = STEPS[entry.event];
            const previous = index > 0 ? timeline[index - 1] : null;
            const next = index < timeline.length - 1 ? timeline[index + 1] : null;
            const tookToNext = next ? minutesBetween(entry.at, next.at) : 0;
            const isLast = next === null;
            return (
              <li key={`${entry.event}-${entry.at}-${index}`} className="relative flex gap-4">
                {/* The line to the next step, with the time it took */}
                {!isLast && (
                  <span
                    aria-hidden="true"
                    className="absolute top-11 bottom-0 left-5 w-0.5 -translate-x-1/2 rounded-full bg-line"
                  />
                )}
                <span
                  aria-hidden="true"
                  className={cn(
                    'relative z-10 flex size-10 shrink-0 items-center justify-center rounded-2xl',
                    BUBBLES[tone],
                    isLast && tone !== 'danger' && 'ring-4 ring-brand-500/20',
                  )}
                >
                  <Icon className="size-5" />
                </span>
                <div className={cn('flex min-w-0 flex-1 flex-col gap-0.5', !isLast && 'pb-6')}>
                  <div className="flex flex-wrap items-baseline justify-between gap-x-3">
                    <p className={cn('font-bold', tone === 'danger' && 'text-danger-text')}>
                      {title}
                    </p>
                    <time dateTime={entry.at} className="text-xs font-semibold text-ink-soft">
                      {previous && sameDay(previous.at, entry.at)
                        ? formatTime(entry.at)
                        : formatDateTime(entry.at)}
                    </time>
                  </div>
                  {/* A note that only repeats the step's name ("Delivered") adds nothing */}
                  {entry.note && entry.note.toLowerCase() !== title.toLowerCase() && (
                    <p className="text-sm text-ink-soft">{entry.note}</p>
                  )}
                  {!isLast && tookToNext > 0 && (
                    <p className="mt-2 inline-flex w-fit items-center rounded-full bg-page px-2.5 py-0.5 text-[11px] font-semibold text-ink-soft">
                      +{formatMinutes(tookToNext)} to the next step
                    </p>
                  )}
                </div>
              </li>
            );
          })}
        </ol>
      )}
    </section>
  );
}
