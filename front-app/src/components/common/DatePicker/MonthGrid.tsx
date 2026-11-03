import { ChevronLeft, ChevronRight } from 'lucide-react';
import { useEffect, useId, useRef, useState, type KeyboardEvent } from 'react';

import { Tooltip } from '@/components/common/Tooltip';
import {
  addDays,
  addMonths,
  clampToLimits,
  endOfWeek,
  isSameDay,
  isWithinLimits,
  longDate,
  MONTH_NAMES,
  monthGrid,
  startOfWeek,
  WEEKDAYS,
} from '@/utils/calendarUtils';
import { cn } from '@/utils/classNames';
import { parseIsoDate, toIsoDate } from '@/utils/dateUtils';

export interface MonthGridProps {
  /** The day shown and focused first ('YYYY-MM-DD'; none: today) */
  initialDay?: string;
  /** The first / last day that may be chosen ('YYYY-MM-DD', included) */
  min?: string;
  max?: string;
  /** Whether a day is chosen: drawn filled (one day, or both ends of a range) */
  isSelected: (day: string) => boolean;
  /** The days of a range, both ends included and in order: tinted as one band */
  range?: readonly [string, string] | null;
  onSelect: (day: string) => void;
  /** The pointer moved onto a day (null: off the days), so a range picker can show the range it would make */
  onHover?: (day: string | null) => void;
}

/** Years offered in the year list: around today, widened to the limits and the shown year. */
function yearRange(viewYear: number, min?: string, max?: string): number[] {
  const thisYear = new Date().getFullYear();
  const first = min ? Number(min.slice(0, 4)) : Math.min(thisYear - 10, viewYear);
  const last = max ? Number(max.slice(0, 4)) : Math.max(thisYear + 10, viewYear);
  return Array.from({ length: last - first + 1 }, (_, index) => first + index);
}

/** The band of a range behind a day: full in the middle, half at each end (nothing for a range of one day). */
function bandOf(day: string, range: readonly [string, string] | null | undefined): string | false {
  if (!range || range[0] === range[1] || day < range[0] || day > range[1]) {
    return false;
  }
  // A see-through brand tint: light green on a white card, a deep green glow on a dark one
  if (day === range[0]) {
    return 'bg-linear-to-r from-transparent from-50% to-brand-500/15 to-50%';
  }
  if (day === range[1]) {
    return 'bg-linear-to-l from-transparent from-50% to-brand-500/15 to-50%';
  }
  return 'bg-brand-500/15';
}

/**
 * One month of a calendar: the month and year (arrows and lists), then the days, Monday first. Shared by the date
 * picker and the date range picker. Keyboard, as in the WAI-ARIA date picker pattern: arrows move a day / a week,
 * Home / End to the week's start / end, Page Up / Page Down a month (with Shift a year), Enter or Space chooses.
 * The focused day takes the keyboard focus when the grid opens. Days outside the limits cannot be chosen.
 */
export function MonthGrid({
  initialDay,
  min,
  max,
  isSelected,
  range,
  onSelect,
  onHover,
}: MonthGridProps) {
  const captionId = useId();
  const today = new Date();
  const start = clampToLimits(initialDay ? parseIsoDate(initialDay) : today, min, max);
  const [focused, setFocused] = useState(start);
  const [view, setView] = useState({ year: start.getFullYear(), month: start.getMonth() });
  // After opening and after keyboard moves the focused day takes the keyboard focus; not after a click on the
  // month buttons or lists (the focus stays there)
  const [focusDay, setFocusDay] = useState(true);
  const dayButtons = useRef(new Map<string, HTMLButtonElement>());

  useEffect(() => {
    if (focusDay) {
      dayButtons.current.get(toIsoDate(focused))?.focus();
    }
  }, [focused, focusDay]);

  const show = (date: Date, moveFocus: boolean) => {
    const target = clampToLimits(date, min, max);
    setFocused(target);
    setView({ year: target.getFullYear(), month: target.getMonth() });
    setFocusDay(moveFocus);
  };

  const onGridKey = (event: KeyboardEvent) => {
    const moves: Record<string, () => Date> = {
      ArrowLeft: () => addDays(focused, -1),
      ArrowRight: () => addDays(focused, 1),
      ArrowUp: () => addDays(focused, -7),
      ArrowDown: () => addDays(focused, 7),
      Home: () => startOfWeek(focused),
      End: () => endOfWeek(focused),
      PageUp: () => addMonths(focused, event.shiftKey ? -12 : -1),
      PageDown: () => addMonths(focused, event.shiftKey ? 12 : 1),
    };
    const move = moves[event.key] as (() => Date) | undefined;
    if (move) {
      event.preventDefault();
      show(move(), true);
    }
  };

  const weeks = Array.from({ length: 6 }, (_, week) =>
    monthGrid(view.year, view.month).slice(week * 7, week * 7 + 7),
  );
  // The month and year lists keep the day of the month (31 Jan -> February: its last day)
  const changeView = (year: number, month: number) =>
    show(
      addMonths(focused, (year - focused.getFullYear()) * 12 + month - focused.getMonth()),
      false,
    );

  return (
    <div>
      <div className="mb-3 flex items-center justify-between gap-2">
        <Tooltip label="Previous month" icon={ChevronLeft} placement="top">
          <button
            type="button"
            aria-label="Previous month"
            onClick={() => show(addMonths(focused, -1), false)}
            className="flex size-9 items-center justify-center rounded-full text-ink hover:bg-page"
          >
            <ChevronLeft aria-hidden="true" className="size-5" />
          </button>
        </Tooltip>
        <div id={captionId} className="flex items-center gap-1" aria-live="polite">
          <select
            aria-label="Month"
            value={view.month}
            onChange={(event) => changeView(view.year, Number(event.target.value))}
            className="cursor-pointer rounded-lg bg-transparent px-1 py-1 text-sm font-bold hover:bg-page focus:ring-2 focus:ring-brand-100 focus:outline-none"
          >
            {MONTH_NAMES.map((name, month) => (
              <option key={name} value={month}>
                {name}
              </option>
            ))}
          </select>
          <select
            aria-label="Year"
            value={view.year}
            onChange={(event) => changeView(Number(event.target.value), view.month)}
            className="cursor-pointer rounded-lg bg-transparent px-1 py-1 text-sm font-bold hover:bg-page focus:ring-2 focus:ring-brand-100 focus:outline-none"
          >
            {yearRange(view.year, min, max).map((year) => (
              <option key={year} value={year}>
                {year}
              </option>
            ))}
          </select>
        </div>
        <Tooltip label="Next month" icon={ChevronRight} placement="top">
          <button
            type="button"
            aria-label="Next month"
            onClick={() => show(addMonths(focused, 1), false)}
            className="flex size-9 items-center justify-center rounded-full text-ink hover:bg-page"
          >
            <ChevronRight aria-hidden="true" className="size-5" />
          </button>
        </Tooltip>
      </div>

      <table
        role="grid"
        aria-labelledby={captionId}
        className="w-full border-collapse"
        onKeyDown={onGridKey}
        onMouseLeave={() => onHover?.(null)}
      >
        <thead>
          <tr>
            {WEEKDAYS.map((weekday) => (
              <th
                key={weekday.long}
                scope="col"
                abbr={weekday.long}
                className="pb-1 text-center text-xs font-semibold text-ink-soft"
              >
                {weekday.short}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {weeks.map((week) => (
            <tr key={toIsoDate(week[0])}>
              {week.map((day) => {
                const iso = toIsoDate(day);
                const selected = isSelected(iso);
                const allowed = isWithinLimits(day, min, max);
                const inMonth = day.getMonth() === view.month;
                const isToday = isSameDay(day, today);
                return (
                  <td
                    key={iso}
                    aria-selected={selected}
                    className={cn('px-0 py-0.5 text-center', bandOf(iso, range))}
                  >
                    <button
                      ref={(element) => {
                        if (element) {
                          dayButtons.current.set(iso, element);
                        } else {
                          dayButtons.current.delete(iso);
                        }
                      }}
                      type="button"
                      tabIndex={isSameDay(day, focused) ? 0 : -1}
                      disabled={!allowed}
                      aria-label={longDate(day)}
                      aria-current={isToday ? 'date' : undefined}
                      onClick={() => onSelect(iso)}
                      onMouseEnter={() => onHover?.(allowed ? iso : null)}
                      onKeyDown={(event) => {
                        if (event.key === 'Enter' || event.key === ' ') {
                          event.preventDefault();
                          onSelect(iso);
                        }
                      }}
                      className={cn(
                        'mx-auto flex size-9 items-center justify-center rounded-full text-sm transition-colors',
                        'focus-visible:ring-2 focus-visible:ring-brand-500 focus-visible:outline-none',
                        selected && 'bg-brand-500 font-bold text-on-brand hover:bg-brand-400',
                        !selected && allowed && 'hover:bg-brand-100',
                        !selected && isToday && 'font-bold text-brand-text ring-1 ring-brand-500',
                        !selected && !inMonth && 'text-ink-soft',
                        !allowed && 'cursor-not-allowed text-ink-soft/50 line-through',
                      )}
                    >
                      {day.getDate()}
                    </button>
                  </td>
                );
              })}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
