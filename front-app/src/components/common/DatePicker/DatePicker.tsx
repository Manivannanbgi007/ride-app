import { CalendarDays, X } from 'lucide-react';
import { useEffect, useId, useRef, useState } from 'react';

import { Calendar } from '@/components/common/DatePicker/Calendar';
import { cn } from '@/utils/classNames';
import { formatDate } from '@/utils/dateUtils';

export interface DatePickerProps {
  label?: string;
  /** 'YYYY-MM-DD', or '' for no date */
  value: string;
  /** '' when cleared */
  onChange: (value: string) => void;
  /** The first / last day that may be chosen ('YYYY-MM-DD', included) */
  min?: string;
  max?: string;
  /** Shown while no date is chosen */
  placeholder?: string;
  error?: string;
  hint?: string;
  /** A date can be removed (filters); a required form field passes false */
  clearable?: boolean;
  disabled?: boolean;
  id?: string;
  /** Tints a range in the calendar (the two ends of a "from - until" pair) */
  rangeFrom?: string;
  rangeTo?: string;
  /** Called when the calendar closes (React Hook Form's field.onBlur, for "touched") */
  onBlur?: () => void;
}

/** Room the calendar needs below the field before it opens above it instead (px). */
const CALENDAR_HEIGHT = 390;
const CALENDAR_WIDTH = 320;
/** Gap between the field and the calendar (mt-2 / mb-2) plus a little breathing room (px). */
const CALENDAR_GAP = 12;

/**
 * Where the visible area really starts: the bottom edge of any sticky or fixed header pinned to the top of the
 * window (the owner, customer and admin layouts all have one). Space above that line is covered by the header,
 * so a calendar opened into it would be hidden behind the top menu.
 */
function coveredTopEdge(): number {
  let edge = 0;
  document.querySelectorAll('header').forEach((header) => {
    const { position } = window.getComputedStyle(header);
    if (position !== 'sticky' && position !== 'fixed') {
      return;
    }
    const rect = header.getBoundingClientRect();
    // Only a header that is actually stuck to the top of the window covers anything
    if (rect.top <= 1 && rect.bottom > edge) {
      edge = rect.bottom;
    }
  });
  return edge;
}

/**
 * A date field that opens a calendar instead of the browser's own date box: the same look everywhere, the date
 * written the Indian way ("8 Oct 2026"), today marked, days outside the limits struck out, and a quick "clear".
 */
export function DatePicker({
  label,
  value,
  onChange,
  min,
  max,
  placeholder = 'Any day',
  error,
  hint,
  clearable = true,
  disabled = false,
  id,
  rangeFrom,
  rangeTo,
  onBlur,
}: DatePickerProps) {
  const generatedId = useId();
  const fieldId = id ?? generatedId;
  const labelId = `${fieldId}-label`;
  const valueId = `${fieldId}-value`;
  const messageId = `${fieldId}-message`;
  const calendarId = `${fieldId}-calendar`;
  const message = error ?? hint;
  const [open, setOpen] = useState(false);
  const [placement, setPlacement] = useState({ openUp: false, alignRight: false });
  const container = useRef<HTMLDivElement>(null);
  const trigger = useRef<HTMLButtonElement>(null);

  const openCalendar = () => {
    const rect = trigger.current?.getBoundingClientRect();
    if (rect) {
      const needed = CALENDAR_HEIGHT + CALENDAR_GAP;
      const roomBelow = window.innerHeight - rect.bottom;
      // Measured from the bottom of the sticky header, not from the top of the window
      const roomAbove = rect.top - coveredTopEdge();
      setPlacement({
        // Open above only when the whole calendar fits there. Otherwise open below: the page can always be
        // scrolled down to reveal it, whereas a calendar under the header could not be reached at the top of the page.
        openUp: roomBelow < needed && roomAbove >= needed,
        alignRight: rect.left + CALENDAR_WIDTH > window.innerWidth,
      });
    }
    setOpen(true);
  };
  const close = (returnFocus: boolean) => {
    setOpen(false);
    onBlur?.();
    if (returnFocus) {
      trigger.current?.focus();
    }
  };

  // A click elsewhere closes the calendar
  useEffect(() => {
    if (!open) {
      return;
    }
    const onMouseDown = (event: MouseEvent) => {
      if (!container.current?.contains(event.target as Node)) {
        setOpen(false);
        onBlur?.();
      }
    };
    document.addEventListener('mousedown', onMouseDown);
    return () => document.removeEventListener('mousedown', onMouseDown);
  }, [open, onBlur]);

  const choose = (date: string) => {
    onChange(date);
    close(true);
  };

  return (
    <div className="flex flex-col gap-1.5">
      {label && (
        <label id={labelId} htmlFor={fieldId} className="text-sm font-medium">
          {label}
        </label>
      )}
      <div
        ref={container}
        className="relative"
        // Tabbing out of the calendar closes it (not a day button that disappears when the month changes)
        onBlur={(event) => {
          const next = event.relatedTarget as Node | null;
          if (open && next && !container.current?.contains(next)) {
            setOpen(false);
            onBlur?.();
          }
        }}
      >
        <button
          ref={trigger}
          id={fieldId}
          type="button"
          disabled={disabled}
          aria-haspopup="dialog"
          aria-expanded={open}
          aria-controls={open ? calendarId : undefined}
          aria-labelledby={label ? `${labelId} ${valueId}` : undefined}
          aria-label={
            label ? undefined : `Choose a date, ${value ? formatDate(value) : placeholder}`
          }
          aria-invalid={error ? true : undefined}
          aria-describedby={message ? messageId : undefined}
          onClick={() => (open ? close(true) : openCalendar())}
          onKeyDown={(event) => {
            if (event.key === 'ArrowDown' && !open) {
              event.preventDefault();
              openCalendar();
            }
          }}
          className={cn(
            'flex h-11 w-full items-center gap-3 rounded-xl border bg-surface pl-4 text-left text-sm transition-colors',
            clearable && value ? 'pr-10' : 'pr-4',
            'focus:border-brand-500 focus:ring-2 focus:ring-brand-100 focus:outline-none',
            'disabled:cursor-not-allowed disabled:bg-page disabled:text-ink-soft',
            error
              ? 'border-danger-500'
              : open
                ? 'border-brand-500'
                : 'border-line hover:border-brand-500',
          )}
        >
          <CalendarDays aria-hidden="true" className="size-5 shrink-0 text-ink-soft" />
          <span id={valueId} className={cn('truncate', !value && 'text-ink-soft')}>
            {value ? formatDate(value) : placeholder}
          </span>
        </button>
        {clearable && value && !disabled && (
          <button
            type="button"
            aria-label={`Clear ${label ?? 'the date'}`}
            onClick={() => {
              onChange('');
              trigger.current?.focus();
            }}
            className="absolute top-1/2 right-2 flex size-7 -translate-y-1/2 items-center justify-center rounded-full text-ink-soft hover:bg-page hover:text-ink"
          >
            <X aria-hidden="true" className="size-4" />
          </button>
        )}
        {open && (
          <Calendar
            id={calendarId}
            title={label ? `Choose: ${label}` : 'Choose a date'}
            value={value}
            min={min}
            max={max}
            rangeFrom={rangeFrom}
            rangeTo={rangeTo}
            openUp={placement.openUp}
            alignRight={placement.alignRight}
            onSelect={choose}
            onClear={clearable ? () => choose('') : undefined}
            onClose={() => close(true)}
          />
        )}
      </div>
      {message && (
        <p id={messageId} className={cn('text-xs', error ? 'text-danger-500' : 'text-ink-soft')}>
          {message}
        </p>
      )}
    </div>
  );
}
