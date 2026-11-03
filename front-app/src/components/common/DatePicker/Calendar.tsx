import { MonthGrid } from '@/components/common/DatePicker/MonthGrid';
import { isWithinLimits } from '@/utils/calendarUtils';
import { cn } from '@/utils/classNames';
import { toIsoDate } from '@/utils/dateUtils';

interface CalendarProps {
  id: string;
  /** What is chosen ("First day"): read out as the dialog's name */
  title: string;
  /** 'YYYY-MM-DD' or '' */
  value: string;
  min?: string;
  max?: string;
  /** A range to tint (the other end of a "from - until" pair) */
  rangeFrom?: string;
  rangeTo?: string;
  /** Opens above the field (not enough room below) / aligned to its right edge */
  openUp: boolean;
  alignRight: boolean;
  onSelect: (value: string) => void;
  /** Absent: no "Clear" button */
  onClear?: () => void;
  onClose: () => void;
}

/**
 * The calendar of the date picker (a small dialog under the field): one month (see MonthGrid for the keyboard),
 * then "Today" and "Clear". Escape closes it.
 */
export function Calendar({
  id,
  title,
  value,
  min,
  max,
  rangeFrom,
  rangeTo,
  openUp,
  alignRight,
  onSelect,
  onClear,
  onClose,
}: CalendarProps) {
  const today = new Date();
  const range = rangeFrom && rangeTo ? ([rangeFrom, rangeTo].sort() as [string, string]) : null;
  const todayAllowed = isWithinLimits(today, min, max);

  return (
    <div
      id={id}
      role="dialog"
      aria-label={title}
      onKeyDown={(event) => {
        if (event.key === 'Escape') {
          event.preventDefault();
          event.stopPropagation();
          onClose();
        }
      }}
      className={cn(
        'absolute z-40 w-80 rounded-2xl bg-surface p-4 shadow-xl ring-1 ring-line motion-safe:animate-dropdown-in',
        openUp ? 'bottom-full mb-2' : 'top-full mt-2',
        alignRight ? 'right-0' : 'left-0',
      )}
    >
      <MonthGrid
        initialDay={value || undefined}
        min={min}
        max={max}
        isSelected={(day) => day === value}
        range={range}
        onSelect={onSelect}
      />

      <div className="mt-3 flex items-center justify-between border-t border-line pt-3">
        <button
          type="button"
          disabled={!todayAllowed}
          onClick={() => onSelect(toIsoDate(today))}
          className="rounded-lg px-2 py-1 text-sm font-semibold text-brand-text hover:bg-brand-50 disabled:cursor-not-allowed disabled:opacity-40"
        >
          Today
        </button>
        {onClear && value && (
          <button
            type="button"
            onClick={onClear}
            className="rounded-lg px-2 py-1 text-sm font-semibold text-ink-soft hover:bg-page hover:text-ink"
          >
            Clear
          </button>
        )}
      </div>
    </div>
  );
}
