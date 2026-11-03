import { CouponBar, EventPill } from '@/components/business/Calendar/EventPill';
import { PILL_TONES } from '@/components/business/Calendar/calendarStyles';
import { Tooltip } from '@/components/common/Tooltip';
import { cn } from '@/utils/classNames';
import { fullDate, type WeekLayout } from '@/utils/calendarUtils';

interface WeekLanesProps {
  layout: WeekLayout;
  /** Opens the details of a day (a pill opens the day it sits on, a bar the first day it shows) */
  onOpenDay: (date: string) => void;
  /** Draws a "+N more" pill for the events that were left out */
  showMore?: boolean;
  /** Its row when it is the same on every day (month view); default: right under the day's pills */
  moreRow?: number;
  className?: string;
}

/**
 * The events of one week row, laid over its seven day columns: every bar and pill sits in a CSS grid cell of the
 * columns it covers and the lane it was given. The layer lets clicks through to the day cells beneath, except
 * on the pills themselves.
 */
export function WeekLanes({
  layout,
  onOpenDay,
  showMore = false,
  moreRow,
  className,
}: WeekLanesProps) {
  const { bars, days } = layout;
  return (
    <div
      className={cn(
        'pointer-events-none grid auto-rows-[1.5rem] grid-cols-7 gap-x-0 gap-y-1 px-1',
        className,
      )}
    >
      {bars.map((bar) => (
        <div
          key={`${bar.event.id}-${bar.startCol}`}
          className="pointer-events-auto min-w-0 px-0.5"
          style={{ gridColumn: `${bar.startCol + 1} / ${bar.endCol + 2}`, gridRow: bar.lane + 1 }}
        >
          <CouponBar
            event={bar.event}
            continuesBefore={bar.continuesBefore}
            continuesAfter={bar.continuesAfter}
            onOpen={() => onOpenDay(days[bar.startCol].date)}
          />
        </div>
      ))}
      {days.flatMap((day, column) =>
        day.pills.map(({ event, lane }) => (
          <div
            key={`${event.id}-${day.date}`}
            className="pointer-events-auto min-w-0 px-0.5"
            style={{ gridColumn: column + 1, gridRow: lane + 1 }}
          >
            <EventPill event={event} onOpen={() => onOpenDay(day.date)} />
          </div>
        )),
      )}
      {showMore &&
        days.map(
          (day, column) =>
            day.hiddenCount > 0 && (
              <div
                key={`more-${day.date}`}
                className="pointer-events-auto min-w-0 px-0.5"
                style={{ gridColumn: column + 1, gridRow: (moreRow ?? day.moreLane) + 1 }}
              >
                <Tooltip
                  label={`${day.hiddenCount} more on ${fullDate(day.date)}`}
                  placement="top"
                  className="w-full"
                >
                  <button
                    type="button"
                    aria-label={`${day.hiddenCount} more events on ${fullDate(day.date)}`}
                    onClick={() => onOpenDay(day.date)}
                    className={cn(
                      'h-6 w-full cursor-pointer rounded-full px-2.5 text-xs font-bold ring-1 ring-inset',
                      'hover:brightness-95 focus-visible:ring-2 focus-visible:ring-brand-500 focus-visible:outline-none',
                      PILL_TONES.warning,
                    )}
                  >
                    +{day.hiddenCount} more
                  </button>
                </Tooltip>
              </div>
            ),
        )}
    </div>
  );
}
