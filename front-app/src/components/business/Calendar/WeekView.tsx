import { useMemo } from 'react';

import { EventPill } from '@/components/business/Calendar/EventPill';
import { WeekLanes } from '@/components/business/Calendar/WeekLanes';
import type { CalendarEvent } from '@/types/calendar';
import {
  SUNDAY_FIRST_WEEKDAYS,
  WEEK_BAR_LANES,
  dayLabel,
  indexEventsByDay,
  isWeekend,
  layoutWeek,
  weekDays,
} from '@/utils/calendarUtils';
import { cn } from '@/utils/classNames';
import { parseIsoDate } from '@/utils/dateUtils';

interface WeekViewProps {
  /** Any day of the week shown */
  date: string;
  today: string;
  events: readonly CalendarEvent[];
  isPhone: boolean;
  onOpenDay: (date: string) => void;
}

/** The number of a day and its weekday, as a button that opens the day. */
function DayHeading({
  day,
  today,
  count,
  onOpenDay,
  className,
}: {
  day: string;
  today: string;
  count: number;
  onOpenDay: (date: string) => void;
  className?: string;
}) {
  const weekday = SUNDAY_FIRST_WEEKDAYS[parseIsoDate(day).getDay()];
  const isToday = day === today;
  return (
    <button
      type="button"
      aria-label={dayLabel(day, count)}
      aria-current={isToday ? 'date' : undefined}
      onClick={() => onOpenDay(day)}
      className={cn(
        'flex cursor-pointer items-baseline gap-2 rounded-lg px-2 py-1 text-left transition-colors hover:bg-page',
        'focus-visible:ring-2 focus-visible:ring-brand-500 focus-visible:outline-none',
        className,
      )}
    >
      <span
        aria-hidden="true"
        className={cn(
          'text-xl',
          isToday ? 'font-extrabold' : 'font-semibold',
          isWeekend(day) ? 'text-danger-text' : 'text-ink',
        )}
      >
        {parseIsoDate(day).getDate()}
      </span>
      <span aria-hidden="true" className="text-sm text-ink-soft">
        {weekday.short}
      </span>
    </button>
  );
}

/**
 * The week: seven day columns with the coupon bars across them at the top and the day's short events under the
 * bars. On a phone the days are a list, one under the other.
 */
export function WeekView({ date, today, events, isPhone, onOpenDay }: WeekViewProps) {
  const days = useMemo(() => weekDays(date), [date]);
  const layout = useMemo(
    () => layoutWeek(days, events, { maxBarLanes: WEEK_BAR_LANES }),
    [days, events],
  );
  const byDay = useMemo(() => indexEventsByDay(events, days[0], days[6]), [days, events]);

  if (isPhone) {
    return (
      <ul className="flex flex-col gap-3">
        {days.map((day) => {
          const dayEvents = byDay.get(day) ?? [];
          return (
            <li
              key={day}
              className={cn(
                'rounded-xl p-2 ring-1 ring-line',
                day === today && 'bg-brand-50',
                day === date && day !== today && 'bg-info-50',
              )}
            >
              <DayHeading day={day} today={today} count={dayEvents.length} onOpenDay={onOpenDay} />
              <div className="mt-1 flex flex-col gap-1 px-1">
                {dayEvents.length === 0 ? (
                  <p className="px-1 text-xs text-ink-soft">No events</p>
                ) : (
                  dayEvents.map((event) => (
                    <EventPill key={event.id} event={event} onOpen={() => onOpenDay(day)} />
                  ))
                )}
              </div>
            </li>
          );
        })}
      </ul>
    );
  }

  return (
    <div className="overflow-hidden rounded-xl border-t border-l border-line">
      <div className="grid grid-cols-7">
        {days.map((day) => (
          <div
            key={day}
            className={cn(
              'border-r border-b border-line p-2',
              day === today && 'bg-brand-50',
              day === date && day !== today && 'bg-info-50',
            )}
          >
            <DayHeading
              day={day}
              today={today}
              count={byDay.get(day)?.length ?? 0}
              onOpenDay={onOpenDay}
            />
          </div>
        ))}
      </div>
      <div className="relative min-h-72">
        <div aria-hidden="true" className="absolute inset-0 grid grid-cols-7">
          {days.map((day) => (
            <div
              key={day}
              className={cn(
                'border-r border-b border-line',
                day === today && 'bg-brand-50',
                day === date && day !== today && 'bg-info-50',
              )}
            />
          ))}
        </div>
        <WeekLanes showMore layout={layout} onOpenDay={onOpenDay} className="relative py-2" />
      </div>
    </div>
  );
}
