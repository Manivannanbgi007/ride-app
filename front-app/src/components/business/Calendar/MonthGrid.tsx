import { useEffect, useMemo, useRef, type KeyboardEvent } from 'react';

import { DOT_TONES } from '@/components/business/Calendar/calendarStyles';
import { WeekLanes } from '@/components/business/Calendar/WeekLanes';
import type { CalendarEvent } from '@/types/calendar';
import {
  MONTH_BAR_LANES,
  MONTH_PILLS_PER_DAY,
  addIsoDays,
  dayLabel,
  indexEventsByDay,
  isInMonth,
  isWeekend,
  layoutWeek,
  monthGridOf,
  shiftDate,
  SUNDAY_FIRST_WEEKDAYS,
  viewTitle,
  weekDays,
} from '@/utils/calendarUtils';
import { cn } from '@/utils/classNames';
import { parseIsoDate } from '@/utils/dateUtils';

interface MonthGridProps {
  /** The selected day: the month around it is drawn */
  date: string;
  today: string;
  events: readonly CalendarEvent[];
  /** A phone: a compact grid with dots and counts instead of pills */
  isPhone: boolean;
  /** A day was clicked (or Enter pressed on it) */
  onDayClick: (date: string) => void;
  /** An arrow key moved the selection to another day */
  onDayMove: (date: string) => void;
  /** A pill or "+N more" asked for the details of a day */
  onOpenDay: (date: string) => void;
}

/** Day keys of the grid: the move each arrow key makes */
const MOVES: Record<string, number> = { ArrowLeft: -1, ArrowRight: 1, ArrowUp: -7, ArrowDown: 7 };

/**
 * The month as 6 week rows of 7 days, Sunday first. A grid for the keyboard too: one day is in the tab order and the
 * arrow keys move between days (Home / End: the week's first / last day, PageUp / PageDown: another month).
 */
export function MonthGrid({
  date,
  today,
  events,
  isPhone,
  onDayClick,
  onDayMove,
  onOpenDay,
}: MonthGridProps) {
  const root = useRef<HTMLDivElement>(null);
  const focusRequest = useRef<string | null>(null);
  const weeks = useMemo(() => monthGridOf(date), [date]);
  const selected = parseIsoDate(date);
  const [year, month] = [selected.getFullYear(), selected.getMonth()];
  const byDay = useMemo(() => indexEventsByDay(events, weeks[0][0], weeks[5][6]), [events, weeks]);

  // After an arrow key moved the selection, the new day's cell takes the focus (once it is drawn)
  useEffect(() => {
    if (focusRequest.current) {
      root.current?.querySelector<HTMLElement>(`[data-date="${focusRequest.current}"]`)?.focus();
      focusRequest.current = null;
    }
  });

  const move = (event: KeyboardEvent, day: string) => {
    let target: string | null = null;
    if (event.key in MOVES) {
      target = addIsoDays(day, MOVES[event.key]);
    } else if (event.key === 'Home') {
      target = weekDays(day)[0];
    } else if (event.key === 'End') {
      target = weekDays(day)[6];
    } else if (event.key === 'PageUp' || event.key === 'PageDown') {
      target = shiftDate('MONTH', day, event.key === 'PageUp' ? -1 : 1);
    } else if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault();
      onDayClick(day);
      return;
    }
    if (target) {
      event.preventDefault();
      focusRequest.current = target;
      onDayMove(target);
    }
  };

  return (
    <div
      ref={root}
      role="grid"
      aria-label={`Calendar, ${viewTitle('MONTH', date)}`}
      className="overflow-hidden rounded-xl border-t border-l border-line"
    >
      <div role="row" className="grid grid-cols-7">
        {SUNDAY_FIRST_WEEKDAYS.map((weekday) => (
          <div
            key={weekday.long}
            role="columnheader"
            className="border-r border-b border-line px-1 py-3 text-center text-xs font-semibold text-ink-soft sm:text-sm"
          >
            <span className="md:hidden">{weekday.short}</span>
            <span className="hidden md:inline">{weekday.long}</span>
          </div>
        ))}
      </div>
      {weeks.map((week, weekIndex) => {
        const layout = isPhone
          ? null
          : layoutWeek(week, events, {
              maxBarLanes: MONTH_BAR_LANES,
              maxPills: MONTH_PILLS_PER_DAY,
            });
        return (
          <div
            key={week[0]}
            role="row"
            data-week={weekIndex}
            className={cn('relative grid grid-cols-7', isPhone ? 'h-18' : 'h-38')}
          >
            {week.map((day) => {
              const dayEvents = byDay.get(day) ?? [];
              const inMonth = isInMonth(day, year, month);
              const isToday = day === today;
              const isSelected = day === date;
              return (
                <div
                  key={day}
                  role="gridcell"
                  data-date={day}
                  aria-label={dayLabel(day, dayEvents.length)}
                  aria-selected={isSelected}
                  aria-current={isToday ? 'date' : undefined}
                  tabIndex={isSelected ? 0 : -1}
                  onClick={() => onDayClick(day)}
                  onKeyDown={(event) => move(event, day)}
                  className={cn(
                    'relative cursor-pointer border-r border-b border-line p-1.5 transition-colors sm:p-2',
                    'hover:bg-page focus-visible:z-10 focus-visible:ring-2 focus-visible:ring-brand-500 focus-visible:outline-none focus-visible:ring-inset',
                    isToday && 'bg-brand-50 hover:bg-brand-50',
                    isSelected && !isToday && 'bg-info-50 hover:bg-info-50',
                  )}
                >
                  <span
                    aria-hidden="true"
                    className={cn(
                      'inline-block text-sm sm:text-base',
                      isWeekend(day) ? 'text-danger-text' : 'text-ink',
                      isToday || isSelected ? 'font-extrabold' : 'font-semibold',
                      !inMonth && 'opacity-40',
                    )}
                  >
                    {parseIsoDate(day).getDate()}
                  </span>
                  {isPhone && dayEvents.length > 0 && (
                    <span aria-hidden="true" className="mt-1 flex flex-col items-start gap-1">
                      <span className="flex gap-0.5">
                        {dayEvents.slice(0, 3).map((event) => (
                          <span
                            key={event.id}
                            className={cn('size-1.5 rounded-full', DOT_TONES[event.tone])}
                          />
                        ))}
                      </span>
                      <span className="rounded-full bg-brand-100 px-1.5 text-[10px] leading-4 font-bold text-brand-text-deep">
                        {dayEvents.length}
                      </span>
                    </span>
                  )}
                </div>
              );
            })}
            {layout && (
              <WeekLanes
                layout={layout}
                showMore
                moreRow={layout.moreRow}
                onOpenDay={onOpenDay}
                className="absolute inset-x-0 top-9"
              />
            )}
          </div>
        );
      })}
    </div>
  );
}
