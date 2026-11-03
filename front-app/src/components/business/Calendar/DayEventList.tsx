import { DOT_TONES } from '@/components/business/Calendar/calendarStyles';
import { EventLink } from '@/components/business/Calendar/EventLink';
import { Badge } from '@/components/common/Badge';
import { EmptyState } from '@/components/common/EmptyState';
import { CALENDAR_EVENT_TYPES, type CalendarEvent, type CalendarEventType } from '@/types/calendar';
import { EVENT_TYPE_LABELS, pillText, statusLabel } from '@/utils/calendarUtils';
import { formatDate } from '@/utils/dateUtils';

interface DayEventListProps {
  date: string;
  /** The events of this day only */
  events: readonly CalendarEvent[];
}

function EventDetails({ event }: { event: CalendarEvent }) {
  if (event.kind !== 'COUPON') {
    return null;
  }
  return (
    <p className="text-xs text-ink-soft">
      {event.start === event.end
        ? formatDate(event.start)
        : `${formatDate(event.start)} to ${formatDate(event.end)}`}
      {event.count !== null && ` · used ${event.count} ${event.count === 1 ? 'time' : 'times'}`}
    </p>
  );
}

/**
 * What happens on one day, grouped by type (orders, coupons, restaurants) with a count per group, each event with
 * its details, a status badge where it has one, and the link to the page that has more. The day panel and the
 * Date view both show it.
 */
export function DayEventList({ date, events }: DayEventListProps) {
  if (events.length === 0) {
    return (
      <EmptyState
        title="Nothing happening this day"
        message="No orders, coupon campaigns or new restaurants are on this day."
      />
    );
  }
  return (
    <div className="flex flex-col gap-5">
      {CALENDAR_EVENT_TYPES.map((type) => {
        const group = events.filter((event) => event.type === type);
        if (group.length === 0) {
          return null;
        }
        const headingId = `day-${date}-${type}`;
        return (
          <section key={type} aria-labelledby={headingId}>
            <h3
              id={headingId}
              className="mb-2 flex items-center gap-2 text-sm font-bold tracking-tight"
            >
              {EVENT_TYPE_LABELS[type]}
              <Badge>{groupCount(type, group)}</Badge>
            </h3>
            <ul className="flex flex-col divide-y divide-line rounded-xl ring-1 ring-line">
              {group.map((event) => (
                <li
                  key={event.id}
                  className="flex flex-wrap items-center justify-between gap-x-4 gap-y-1 px-4 py-3"
                >
                  <div className="flex min-w-0 items-start gap-3">
                    <span
                      aria-hidden="true"
                      className={`mt-1.5 size-2.5 shrink-0 rounded-full ${DOT_TONES[event.tone]}`}
                    />
                    <div className="min-w-0">
                      <p className="font-semibold break-words">{pillText(event)}</p>
                      <EventDetails event={event} />
                    </div>
                  </div>
                  <div className="flex items-center gap-3">
                    {event.status && (
                      <Badge tone={event.tone} dot>
                        {statusLabel(event.status)}
                      </Badge>
                    )}
                    <EventLink event={event} date={date} />
                  </div>
                </li>
              ))}
            </ul>
          </section>
        );
      })}
    </div>
  );
}

/** The number next to a group's title: campaigns for coupons, otherwise the orders / restaurants counted. */
function groupCount(type: CalendarEventType, group: readonly CalendarEvent[]): number {
  return type === 'COUPONS'
    ? group.length
    : group.reduce((sum, event) => sum + (event.count ?? 1), 0);
}
