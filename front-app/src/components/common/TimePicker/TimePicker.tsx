import { Clock } from 'lucide-react';
import { useEffect, useId, useRef, useState, type KeyboardEvent } from 'react';

import { cn } from '@/utils/classNames';
import { formatClockTime, formatHour } from '@/utils/dateUtils';

/** The panel's width (px), to keep it inside the window. */
const PANEL_WIDTH = 248;

const HOURS = Array.from({ length: 24 }, (_, hour) => hour);
const pad = (value: number) => String(value).padStart(2, '0');

export interface TimePickerProps {
  /** What the time is ("Monday opens at"): read out with the time */
  label: string;
  /** 'HH:mm', 24-hour */
  value: string;
  onChange: (value: string) => void;
  disabled?: boolean;
  /** Minutes offered (every 5 by default); the value's own minute is always offered too */
  minuteStep?: number;
}

/**
 * A time field that opens a small panel instead of the browser's own time box: the hour ("9 am") and the minute
 * in two scrolling columns, the chosen ones filled. A click changes the time at once; Escape, "Done" or a click
 * outside close the panel. In each column the arrow keys move between the choices.
 */
export function TimePicker({
  label,
  value,
  onChange,
  disabled = false,
  minuteStep = 5,
}: TimePickerProps) {
  const labelId = useId();
  const valueId = useId();
  const panelId = useId();
  const [open, setOpen] = useState(false);
  const [alignRight, setAlignRight] = useState(false);
  const container = useRef<HTMLDivElement>(null);
  const trigger = useRef<HTMLButtonElement>(null);
  const [hour, minute] = value.split(':').map(Number);
  const minutes = [
    ...new Set([
      ...Array.from({ length: Math.ceil(60 / minuteStep) }, (_, index) => index * minuteStep),
      minute,
    ]),
  ].sort((a, b) => a - b);

  const openPanel = () => {
    const rect = trigger.current?.getBoundingClientRect();
    if (rect) {
      setAlignRight(rect.left + PANEL_WIDTH > window.innerWidth - 8);
    }
    setOpen(true);
  };
  const close = () => {
    setOpen(false);
    trigger.current?.focus();
  };

  // A click elsewhere closes the panel
  useEffect(() => {
    if (!open) {
      return;
    }
    const onMouseDown = (event: MouseEvent) => {
      if (!container.current?.contains(event.target as Node)) {
        setOpen(false);
      }
    };
    document.addEventListener('mousedown', onMouseDown);
    return () => document.removeEventListener('mousedown', onMouseDown);
  }, [open]);

  return (
    <div
      ref={container}
      className="relative"
      onBlur={(event) => {
        const next = event.relatedTarget as Node | null;
        if (open && next && !container.current?.contains(next)) {
          setOpen(false);
        }
      }}
    >
      <span id={labelId} hidden>
        {label}
      </span>
      <button
        ref={trigger}
        type="button"
        disabled={disabled}
        aria-haspopup="dialog"
        aria-expanded={open}
        aria-controls={open ? panelId : undefined}
        aria-labelledby={`${labelId} ${valueId}`}
        onClick={() => (open ? close() : openPanel())}
        className={cn(
          'flex h-10 min-w-[7.5rem] items-center gap-2 rounded-xl border bg-surface px-3 text-sm font-semibold text-ink tabular-nums transition',
          'focus-visible:border-brand-500 focus-visible:ring-2 focus-visible:ring-brand-100 focus-visible:outline-none',
          'disabled:cursor-not-allowed disabled:opacity-50',
          open ? 'border-brand-500 ring-2 ring-brand-100' : 'border-line hover:border-brand-500',
        )}
      >
        <Clock aria-hidden="true" className="size-4 shrink-0 text-ink-soft" />
        <span id={valueId}>{formatClockTime(value)}</span>
      </button>

      {open && (
        <div
          id={panelId}
          role="dialog"
          aria-label={label}
          onKeyDown={(event) => {
            if (event.key === 'Escape') {
              event.preventDefault();
              event.stopPropagation();
              close();
            }
          }}
          style={{ width: PANEL_WIDTH }}
          className={cn(
            'absolute top-full z-40 mt-2 rounded-2xl bg-surface p-3 shadow-xl ring-1 ring-line motion-safe:animate-dropdown-in',
            alignRight ? 'right-0' : 'left-0',
          )}
        >
          <div className="grid grid-cols-2 gap-2">
            <Column
              label="Hour"
              options={HOURS.map((option) => ({ value: option, text: formatHour(option) }))}
              selected={hour}
              onPick={(next) => onChange(`${pad(next)}:${pad(minute)}`)}
            />
            <Column
              label="Minute"
              options={minutes.map((option) => ({ value: option, text: `:${pad(option)}` }))}
              selected={minute}
              onPick={(next) => onChange(`${pad(hour)}:${pad(next)}`)}
            />
          </div>
          <div className="mt-2 flex items-center justify-between border-t border-line pt-2">
            <span className="text-sm font-bold text-ink tabular-nums">
              {formatClockTime(value)}
            </span>
            <button
              type="button"
              onClick={close}
              className="rounded-lg px-3 py-1.5 text-sm font-semibold text-brand-text hover:bg-brand-50"
            >
              Done
            </button>
          </div>
        </div>
      )}
    </div>
  );
}

interface ColumnProps {
  label: string;
  options: readonly { value: number; text: string }[];
  selected: number;
  onPick: (value: number) => void;
}

/** One scrolling column of choices; the chosen one is filled, in view and the only one in the tab order. */
function Column({ label, options, selected, onPick }: ColumnProps) {
  const buttons = useRef<(HTMLButtonElement | null)[]>([]);
  const selectedIndex = Math.max(
    0,
    options.findIndex((option) => option.value === selected),
  );

  // The chosen time is in view when the panel opens
  useEffect(() => {
    buttons.current[selectedIndex]?.scrollIntoView?.({ block: 'center' });
    // Only when the panel opens: a click keeps the column where the owner scrolled it
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const moveWithKeys = (event: KeyboardEvent, index: number) => {
    const step = event.key === 'ArrowDown' ? 1 : event.key === 'ArrowUp' ? -1 : 0;
    if (step === 0) {
      return;
    }
    event.preventDefault();
    const next = Math.min(options.length - 1, Math.max(0, index + step));
    onPick(options[next].value);
    buttons.current[next]?.focus();
  };

  return (
    <div className="flex min-w-0 flex-col gap-1">
      <span className="px-1 text-[11px] font-bold tracking-wide text-ink-soft uppercase">
        {label}
      </span>
      <div
        role="group"
        aria-label={label}
        className="flex max-h-52 [scrollbar-width:thin] flex-col gap-0.5 overflow-y-auto overscroll-contain rounded-xl bg-page p-1"
      >
        {options.map((option, index) => {
          const isSelected = option.value === selected;
          return (
            <button
              key={option.value}
              ref={(element) => {
                buttons.current[index] = element;
              }}
              type="button"
              aria-pressed={isSelected}
              tabIndex={index === selectedIndex ? 0 : -1}
              onClick={() => onPick(option.value)}
              onKeyDown={(event) => moveWithKeys(event, index)}
              className={cn(
                'shrink-0 rounded-lg px-2 py-1.5 text-sm tabular-nums transition-colors',
                'focus-visible:ring-2 focus-visible:ring-brand-500 focus-visible:outline-none',
                isSelected
                  ? 'bg-brand-500 font-bold text-on-brand'
                  : 'text-ink hover:bg-surface hover:text-brand-text-strong',
              )}
            >
              {option.text}
            </button>
          );
        })}
      </div>
    </div>
  );
}
