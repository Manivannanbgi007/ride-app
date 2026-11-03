import { ChevronLeft, ChevronRight } from 'lucide-react';
import type { ReactNode } from 'react';

import { DatePicker } from '@/components/common/DatePicker';
import { IconButton } from '@/components/common/IconButton';
import { Select } from '@/components/common/Select';
import { MONTH_NAMES } from '@/utils/calendarUtils';
import { cn } from '@/utils/classNames';
import { parseIsoDate, toIsoDate } from '@/utils/dateUtils';
import {
  ACTIVITY_UNIT_LABELS,
  canGoForward,
  clampToToday,
  rollingDaysOf,
  ROLLING_CHOICES,
  shiftAnchor,
  type ActivityMode,
  type ActivityUnit,
} from '@/utils/orderActivityUtils';

interface OverviewRangeFilterProps {
  mode: ActivityMode;
  /** A day inside the chosen day, week or month ('YYYY-MM-DD') */
  anchor: string;
  /** The business day today ('YYYY-MM-DD'): nothing after it can be chosen */
  today: string;
  onModeChange: (mode: ActivityMode) => void;
  onAnchorChange: (anchor: string) => void;
}

const UNITS: readonly ActivityUnit[] = ['day', 'week', 'month'];

/** How many years back the month choice goes. */
const YEARS_BACK = 5;

/**
 * Which days the overview shows: the last 7, 30 or 90 days, or one calendar day, week (Monday to Sunday) or month.
 * A day, week or month starts at the current one, with arrows and pickers to reach the ones before.
 */
export function OverviewRangeFilter({
  mode,
  anchor,
  today,
  onModeChange,
  onAnchorChange,
}: OverviewRangeFilterProps) {
  const unit = rollingDaysOf(mode) === null ? (mode as ActivityUnit) : null;

  return (
    <div className="flex flex-wrap items-end justify-between gap-3">
      <div role="group" aria-label="Show orders of" className="flex flex-wrap items-center gap-2">
        <Pills>
          {ROLLING_CHOICES.map((choice) => (
            <ModeButton
              key={choice.mode}
              active={mode === choice.mode}
              onClick={() => onModeChange(choice.mode)}
            >
              {`Last ${choice.days} days`}
            </ModeButton>
          ))}
        </Pills>
        <Pills>
          {UNITS.map((option) => (
            <ModeButton key={option} active={mode === option} onClick={() => onModeChange(option)}>
              {ACTIVITY_UNIT_LABELS[option]}
            </ModeButton>
          ))}
        </Pills>
      </div>

      {unit && (
        <div className="flex items-end gap-2">
          <IconButton
            label={`Previous ${unit}`}
            icon={ChevronLeft}
            onClick={() => onAnchorChange(shiftAnchor(unit, anchor, -1, today))}
          />
          {unit === 'month' ? (
            <MonthFields anchor={anchor} today={today} onChange={onAnchorChange} />
          ) : (
            <div className="w-44">
              <DatePicker
                label={unit === 'day' ? 'Day' : 'Any day of the week'}
                value={anchor}
                max={today}
                clearable={false}
                onChange={(day) => day && onAnchorChange(clampToToday(day, today))}
              />
            </div>
          )}
          <IconButton
            label={`Next ${unit}`}
            icon={ChevronRight}
            disabled={!canGoForward(unit, anchor, today)}
            onClick={() => onAnchorChange(shiftAnchor(unit, anchor, 1, today))}
          />
        </div>
      )}
    </div>
  );
}

function Pills({ children }: { children: ReactNode }) {
  return <div className="flex rounded-full bg-page p-1 text-sm font-medium">{children}</div>;
}

function ModeButton({
  active,
  onClick,
  children,
}: {
  active: boolean;
  onClick: () => void;
  children: string;
}) {
  return (
    <button
      type="button"
      aria-pressed={active}
      onClick={onClick}
      className={cn(
        'cursor-pointer rounded-full px-3 py-1.5 whitespace-nowrap transition-colors duration-200',
        'focus-visible:ring-2 focus-visible:ring-brand-500/40 focus-visible:outline-none',
        active ? 'bg-surface text-brand-text-strong shadow-sm' : 'text-ink-soft hover:text-ink',
      )}
    >
      {children}
    </button>
  );
}

/** A month and a year; a month that has not started yet becomes the current one. */
function MonthFields({
  anchor,
  today,
  onChange,
}: {
  anchor: string;
  today: string;
  onChange: (anchor: string) => void;
}) {
  const chosen = parseIsoDate(anchor);
  const thisYear = parseIsoDate(today).getFullYear();
  const years = Array.from({ length: YEARS_BACK + 1 }, (_, index) => thisYear - index);
  if (!years.includes(chosen.getFullYear())) {
    years.push(chosen.getFullYear());
  }

  const choose = (year: number, month: number) =>
    onChange(clampToToday(toIsoDate(new Date(year, month, 1)), today));

  return (
    <div className="flex gap-2">
      <div className="w-36">
        <Select
          label="Month"
          options={MONTH_NAMES.map((name, month) => ({ value: String(month), label: name }))}
          value={String(chosen.getMonth())}
          onChange={(event) => choose(chosen.getFullYear(), Number(event.target.value))}
        />
      </div>
      <div className="w-24">
        <Select
          label="Year"
          options={years.map((year) => ({ value: String(year), label: String(year) }))}
          value={String(chosen.getFullYear())}
          onChange={(event) => choose(Number(event.target.value), chosen.getMonth())}
        />
      </div>
    </div>
  );
}
