import { useMemo } from 'react';

import type { CalendarEvent } from '@/types/calendar';
import {
  MONTH_NAMES,
  SUNDAY_FIRST_WEEKDAYS,
  dayLabel,
  indexEventsByDay,
  intensityLevel,
} from '@/utils/calendarUtils';
import { cn } from '@/utils/classNames';
import { toIsoDate } from '@/utils/dateUtils';

interface YearViewProps {
  /** Any day of the year shown */
  date: string;
  today: string;
  /** The events of the whole year */
  events: readonly CalendarEvent[];
  /** A month's name was clicked: its first day */
  onOpenMonth: (firstDay: string) => void;
  onOpenDay: (date: string) => void;
}

/** A day with events is tinted the stronger the more events it has. */
const INTENSITY_STYLES = [
  '',
  'bg-brand-100 text-brand-text-deep',
  'bg-brand-300 text-brand-text-deep',
  'bg-brand-500 text-on-accent',
];

/** The year: twelve small months, each day tinted by how much happens on it. */
export function YearView({ date, today, events, onOpenMonth, onOpenDay }: YearViewProps) {
  const year = Number(date.slice(0, 4));
  const byDay = useMemo(
    () => indexEventsByDay(events, `${year}-01-01`, `${year}-12-31`),
    [events, year],
  );

  return (
    <div className="grid gap-6 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
      {MONTH_NAMES.map((name, month) => {
        const firstDay = new Date(year, month, 1);
        const daysInMonth = new Date(year, month + 1, 0).getDate();
        return (
          <section
            key={name}
            aria-label={`${name} ${year}`}
            className="rounded-2xl p-3 ring-1 ring-line"
          >
            <h3 className="mb-2">
              <button
                type="button"
                onClick={() => onOpenMonth(toIsoDate(firstDay))}
                className="cursor-pointer rounded-lg px-2 py-1 text-sm font-bold hover:bg-page hover:text-brand-text focus-visible:ring-2 focus-visible:ring-brand-500 focus-visible:outline-none"
              >
                {name}
              </button>
            </h3>
            <div className="grid grid-cols-7 gap-0.5 text-center">
              {SUNDAY_FIRST_WEEKDAYS.map((weekday) => (
                <span
                  key={weekday.long}
                  aria-hidden="true"
                  className="pb-1 text-[10px] font-semibold text-ink-soft"
                >
                  {weekday.short.slice(0, 1)}
                </span>
              ))}
              {Array.from({ length: firstDay.getDay() }, (_, blank) => (
                <span key={`blank-${blank}`} />
              ))}
              {Array.from({ length: daysInMonth }, (_, index) => {
                const day = toIsoDate(new Date(year, month, index + 1));
                const count = byDay.get(day)?.length ?? 0;
                return (
                  <button
                    key={day}
                    type="button"
                    aria-label={dayLabel(day, count)}
                    aria-current={day === today ? 'date' : undefined}
                    onClick={() => onOpenDay(day)}
                    className={cn(
                      'aspect-square cursor-pointer rounded-md text-xs font-medium text-ink transition hover:ring-2 hover:ring-brand-400',
                      'focus-visible:ring-2 focus-visible:ring-brand-500 focus-visible:outline-none',
                      INTENSITY_STYLES[intensityLevel(count)],
                      day === today && 'font-extrabold ring-2 ring-brand-600',
                    )}
                  >
                    {index + 1}
                  </button>
                );
              })}
            </div>
          </section>
        );
      })}
    </div>
  );
}
