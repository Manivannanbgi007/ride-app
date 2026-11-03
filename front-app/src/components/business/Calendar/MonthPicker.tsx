import { ChevronLeft, ChevronRight } from 'lucide-react';
import { useEffect, useRef, useState, type KeyboardEvent } from 'react';

import { MONTH_NAMES } from '@/utils/calendarUtils';
import { cn } from '@/utils/classNames';

interface MonthPickerProps {
  /** The month and year now shown */
  year: number;
  month: number;
  onPick: (year: number, month: number) => void;
  onClose: () => void;
}

/** The arrow keys' steps on the 4 x 3 grid of months. */
const STEPS: Record<string, number> = { ArrowLeft: -1, ArrowRight: 1, ArrowUp: -4, ArrowDown: 4 };

/**
 * The popover under the calendar's title: a year with previous / next buttons and the twelve months. The current
 * month has the focus on opening; arrow keys move between months, Escape or a click outside closes it.
 */
export function MonthPicker({ year, month, onPick, onClose }: MonthPickerProps) {
  const [shownYear, setShownYear] = useState(year);
  const root = useRef<HTMLDivElement>(null);
  const buttons = useRef<(HTMLButtonElement | null)[]>([]);

  useEffect(() => {
    buttons.current[month]?.focus();
    // Only when it opens
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    const closeOutside = (event: MouseEvent) => {
      if (!root.current?.contains(event.target as Node)) {
        onClose();
      }
    };
    const closeOnEscape = (event: globalThis.KeyboardEvent) => {
      if (event.key === 'Escape') {
        onClose();
      }
    };
    document.addEventListener('mousedown', closeOutside);
    document.addEventListener('keydown', closeOnEscape);
    return () => {
      document.removeEventListener('mousedown', closeOutside);
      document.removeEventListener('keydown', closeOnEscape);
    };
  }, [onClose]);

  const moveWithArrows = (event: KeyboardEvent, index: number) => {
    const step = STEPS[event.key];
    if (step === undefined) {
      return;
    }
    event.preventDefault();
    const next = index + step;
    if (next >= 0 && next < 12) {
      buttons.current[next]?.focus();
    }
  };

  return (
    <div
      ref={root}
      role="dialog"
      aria-label="Choose month and year"
      className="absolute top-full left-0 z-30 mt-2 w-72 max-w-[calc(100vw-2rem)] rounded-2xl bg-surface p-4 shadow-card ring-1 ring-line"
    >
      <div className="mb-3 flex items-center justify-between">
        <button
          type="button"
          aria-label="Previous year"
          onClick={() => setShownYear(shownYear - 1)}
          className="flex size-8 cursor-pointer items-center justify-center rounded-lg text-ink-soft hover:bg-page hover:text-ink focus-visible:ring-2 focus-visible:ring-brand-500 focus-visible:outline-none"
        >
          <ChevronLeft aria-hidden="true" className="size-5" />
        </button>
        <p aria-live="polite" className="text-base font-bold">
          {shownYear}
        </p>
        <button
          type="button"
          aria-label="Next year"
          onClick={() => setShownYear(shownYear + 1)}
          className="flex size-8 cursor-pointer items-center justify-center rounded-lg text-ink-soft hover:bg-page hover:text-ink focus-visible:ring-2 focus-visible:ring-brand-500 focus-visible:outline-none"
        >
          <ChevronRight aria-hidden="true" className="size-5" />
        </button>
      </div>
      <div className="grid grid-cols-3 gap-2">
        {MONTH_NAMES.map((name, index) => {
          const isCurrent = shownYear === year && index === month;
          return (
            <button
              key={name}
              ref={(element) => {
                buttons.current[index] = element;
              }}
              type="button"
              aria-pressed={isCurrent}
              aria-label={`${name} ${shownYear}`}
              onClick={() => onPick(shownYear, index)}
              onKeyDown={(event) => moveWithArrows(event, index)}
              className={cn(
                'cursor-pointer rounded-xl px-2 py-2 text-sm font-semibold transition-colors',
                'focus-visible:ring-2 focus-visible:ring-brand-500 focus-visible:outline-none',
                isCurrent
                  ? 'bg-brand-500 text-on-brand'
                  : 'text-ink hover:bg-brand-50 hover:text-brand-text',
              )}
            >
              {name.slice(0, 3)}
            </button>
          );
        })}
      </div>
    </div>
  );
}
