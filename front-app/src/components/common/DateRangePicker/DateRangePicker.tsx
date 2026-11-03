import { CalendarRange, Check, ChevronDown, X } from 'lucide-react';
import { useEffect, useId, useRef, useState, type ReactNode } from 'react';
import { createPortal } from 'react-dom';

import { Button } from '@/components/common/Button';
import { MonthGrid } from '@/components/common/DatePicker/MonthGrid';
import { SegmentedControl } from '@/components/common/SegmentedControl';
import { useMediaQuery } from '@/hooks/useMediaQuery';
import { cn } from '@/utils/classNames';
import {
  DATE_PRESETS,
  dayCountLabel,
  daysIn,
  formatRange,
  matchingPreset,
  MAX_RANGE_DAYS,
  presetLabel,
  presetRange,
  rangeProblem,
  type DatePresetId,
  type DateRange,
} from '@/utils/dateRangeUtils';
import { toIsoDate } from '@/utils/dateUtils';

const MODES = [
  { id: 'day', label: 'Single day' },
  { id: 'range', label: 'Date range' },
] as const;
type Mode = (typeof MODES)[number]['id'];

/** The panel's width from the `sm` breakpoint on (px): the ready-made ranges and one month side by side. */
const PANEL_WIDTH = 544;

export interface DateRangePickerProps {
  /** What the days filter ("Overview dates"): the button's name, and its visible label with `showLabel` */
  label: string;
  showLabel?: boolean;
  /** The chosen days; undefined: none (only with `emptyLabel`) */
  value?: DateRange;
  /** The ready-made range the days come from, if they do ("Last 7 days" moves on with the calendar) */
  preset?: DatePresetId;
  /** A ready-made range passes its id; days picked in the calendar pass none */
  onChange: (range: DateRange | undefined, preset?: DatePresetId) => void;
  /** The ready-made ranges offered (default: all) */
  presets?: readonly DatePresetId[];
  /** The last day that can be chosen ('YYYY-MM-DD'; default: today) */
  max?: string;
  maxDays?: number;
  /** Shown while no days are chosen; with it the days can be removed again ("Any day") */
  emptyLabel?: string;
  className?: string;
}

/**
 * A button that shows the chosen days ("Last 7 days · 26 Oct – 1 Nov 2026") and opens a panel to change them: a
 * ready-made range in one click, or one day or a custom range picked in a calendar (the range under the pointer is
 * previewed) and confirmed with "Apply". A range that cannot be used (too long, the days in the future) is
 * explained and cannot be applied. Escape, a click outside or "Cancel" close the panel without a change. On a
 * phone the panel is a sheet at the bottom of the screen.
 */
export function DateRangePicker({
  label,
  showLabel = false,
  value,
  preset,
  onChange,
  presets = DATE_PRESETS.map((entry) => entry.id),
  max,
  maxDays = MAX_RANGE_DAYS,
  emptyLabel,
  className,
}: DateRangePickerProps) {
  const panelId = useId();
  const labelId = useId();
  const today = new Date();
  const lastDay = max ?? toIsoDate(today);
  const [open, setOpen] = useState(false);
  const [alignRight, setAlignRight] = useState(false);
  const [mode, setMode] = useState<Mode>('range');
  // The days being picked: applied only with "Apply"
  const [draft, setDraft] = useState<Partial<DateRange>>({});
  const [hovered, setHovered] = useState<string | null>(null);
  const container = useRef<HTMLDivElement>(null);
  const trigger = useRef<HTMLButtonElement>(null);
  const panel = useRef<HTMLDivElement>(null);
  // On a phone the panel is a sheet over the whole page: it is placed at the end of the page (a portal), so no
  // block of the page can cover it
  const isPhone = !useMediaQuery('(min-width: 40rem)');
  const inPlaceOrSheet = (node: ReactNode) => (isPhone ? createPortal(node, document.body) : node);
  /** Inside the picker: its button or its panel (which is elsewhere in the page on a phone) */
  const isInside = (node: Node | null) =>
    Boolean(node && (container.current?.contains(node) || panel.current?.contains(node)));

  // What the button says: the ready-made range's name (or the one the days happen to be), then the days; without
  // days, only "Any day"
  const shownPreset = value ? (preset ?? matchingPreset(value, today, presets)) : undefined;
  const heading = value
    ? shownPreset
      ? presetLabel(shownPreset)
      : value.fromDate === value.toDate
        ? 'Single day'
        : `Custom · ${dayCountLabel(daysIn(value))}`
    : undefined;
  const daysText = value ? formatRange(value) : (emptyLabel ?? 'Choose the days');

  const openPanel = () => {
    const rect = trigger.current?.getBoundingClientRect();
    if (rect) {
      setAlignRight(rect.left + PANEL_WIDTH > window.innerWidth - 8);
    }
    setDraft(value ?? {});
    setMode(value && value.fromDate === value.toDate ? 'day' : 'range');
    setHovered(null);
    setOpen(true);
  };
  const close = (returnFocus: boolean) => {
    setOpen(false);
    if (returnFocus) {
      trigger.current?.focus();
    }
  };

  // A click elsewhere closes the panel
  useEffect(() => {
    if (!open) {
      return;
    }
    const onMouseDown = (event: MouseEvent) => {
      const target = event.target as Node;
      if (!container.current?.contains(target) && !panel.current?.contains(target)) {
        setOpen(false);
      }
    };
    document.addEventListener('mousedown', onMouseDown);
    return () => document.removeEventListener('mousedown', onMouseDown);
  }, [open]);

  const pickDay = (day: string) => {
    if (mode === 'day') {
      setDraft({ fromDate: day, toDate: day });
    } else if (!draft.fromDate || draft.toDate || day < draft.fromDate) {
      // A new range starts (also when the day is before the first one)
      setDraft({ fromDate: day });
    } else {
      setDraft({ fromDate: draft.fromDate, toDate: day });
    }
  };
  const changeMode = (next: Mode) => {
    setMode(next);
    if (next === 'day' && draft.fromDate) {
      setDraft({ fromDate: draft.fromDate, toDate: draft.fromDate });
    }
  };
  const choosePreset = (id: DatePresetId) => {
    onChange(presetRange(id, today), id);
    close(true);
  };
  const apply = () => {
    if (draft.fromDate && draft.toDate) {
      onChange({ fromDate: draft.fromDate, toDate: draft.toDate });
      close(true);
    }
  };

  const complete = Boolean(draft.fromDate && draft.toDate);
  const problem = complete ? rangeProblem(draft, lastDay, maxDays) : undefined;
  // The band in the calendar: the chosen range, or the one the pointer would make
  const band: [string, string] | null =
    draft.fromDate && draft.toDate
      ? [draft.fromDate, draft.toDate]
      : draft.fromDate && hovered && hovered >= draft.fromDate
        ? [draft.fromDate, hovered]
        : null;

  return (
    <div
      ref={container}
      className={cn('relative flex flex-col gap-1.5', className)}
      // Tabbing out of the panel closes it
      onBlur={(event) => {
        const next = event.relatedTarget as Node | null;
        if (open && next && !isInside(next)) {
          setOpen(false);
        }
      }}
    >
      {showLabel && (
        <span id={labelId} className="text-sm font-medium">
          {label}
        </span>
      )}
      <div className="flex items-center gap-1.5">
        <button
          ref={trigger}
          type="button"
          aria-haspopup="dialog"
          aria-expanded={open}
          aria-controls={open ? panelId : undefined}
          aria-label={`${label}: ${value ? `${heading}, ${daysText}` : daysText}`}
          onClick={() => (open ? close(true) : openPanel())}
          className={cn(
            'group flex h-11 min-w-0 flex-1 items-center gap-3 rounded-xl border bg-surface pr-3 pl-2 text-left shadow-sm transition',
            'focus-visible:border-brand-500 focus-visible:ring-4 focus-visible:ring-brand-500/20 focus-visible:outline-none',
            open
              ? 'border-brand-500 ring-4 ring-brand-500/15'
              : 'border-line hover:border-brand-500',
          )}
        >
          <span
            aria-hidden="true"
            className="flex size-8 shrink-0 items-center justify-center rounded-lg bg-brand-50 text-brand-text-strong transition-transform group-hover:scale-105"
          >
            <CalendarRange className="size-4.5" />
          </span>
          <span className="flex min-w-0 flex-1 flex-col leading-tight">
            {heading && (
              <span className="truncate text-[11px] font-bold tracking-wide text-brand-text uppercase">
                {heading}
              </span>
            )}
            <span
              className={cn(
                'truncate text-sm font-semibold',
                value ? 'text-ink' : 'font-medium text-ink-soft',
              )}
            >
              {daysText}
            </span>
          </span>
          <ChevronDown
            aria-hidden="true"
            className={cn(
              'size-4 shrink-0 text-ink-soft transition-transform',
              open && 'rotate-180',
            )}
          />
        </button>
        {emptyLabel && value && (
          <button
            type="button"
            aria-label={`Clear ${label.toLowerCase()}`}
            onClick={() => {
              onChange(undefined);
              trigger.current?.focus();
            }}
            className="flex size-9 shrink-0 items-center justify-center rounded-full text-ink-soft transition hover:bg-page hover:text-ink focus-visible:ring-4 focus-visible:ring-brand-500/30 focus-visible:outline-none"
          >
            <X aria-hidden="true" className="size-4" />
          </button>
        )}
      </div>

      {open &&
        inPlaceOrSheet(
          <>
            {/* On a phone the sheet dims the page behind it; a tap there closes it */}
            <div
              aria-hidden="true"
              onClick={() => close(false)}
              className="fixed inset-0 z-50 bg-slate-950/40 motion-safe:animate-page-in sm:hidden"
            />
            <div
              ref={panel}
              id={panelId}
              role="dialog"
              aria-label={`Choose: ${label}`}
              onKeyDown={(event) => {
                if (event.key === 'Escape') {
                  event.preventDefault();
                  event.stopPropagation();
                  close(true);
                }
              }}
              className={cn(
                'z-50 flex flex-col rounded-3xl bg-surface shadow-2xl ring-1 ring-line motion-safe:animate-dropdown-in',
                // A sheet at the bottom of a phone, a panel under the button from `sm` on
                'fixed inset-x-2 bottom-2 max-h-[calc(100dvh-1rem)] overflow-y-auto',
                'sm:absolute sm:inset-x-auto sm:top-full sm:bottom-auto sm:mt-2 sm:max-h-none sm:w-[34rem] sm:overflow-visible',
                alignRight ? 'sm:right-0' : 'sm:left-0',
              )}
            >
              <div className="flex flex-col sm:flex-row">
                <div className="border-b border-line p-3 sm:w-44 sm:shrink-0 sm:border-r sm:border-b-0">
                  <p className="mb-2 hidden px-2 text-xs font-bold tracking-wide text-ink-soft uppercase sm:block">
                    Quick ranges
                  </p>
                  <ul
                    aria-label="Quick ranges"
                    className="flex [scrollbar-width:none] gap-2 overflow-x-auto sm:flex-col sm:gap-0.5"
                  >
                    {emptyLabel && (
                      <PresetButton
                        label={emptyLabel}
                        active={!value}
                        onClick={() => {
                          onChange(undefined);
                          close(true);
                        }}
                      />
                    )}
                    {DATE_PRESETS.filter((entry) => presets.includes(entry.id)).map((entry) => (
                      <PresetButton
                        key={entry.id}
                        label={entry.label}
                        active={value !== undefined && shownPreset === entry.id}
                        onClick={() => choosePreset(entry.id)}
                      />
                    ))}
                  </ul>
                </div>

                <div className="flex min-w-0 flex-1 flex-col gap-3 p-4">
                  <div className="flex justify-center">
                    <SegmentedControl
                      label="Pick"
                      options={MODES}
                      value={mode}
                      onChange={changeMode}
                    />
                  </div>
                  <MonthGrid
                    initialDay={draft.toDate ?? draft.fromDate ?? lastDay}
                    max={lastDay}
                    isSelected={(day) => day === draft.fromDate || day === draft.toDate}
                    range={band}
                    onSelect={pickDay}
                    onHover={mode === 'range' ? setHovered : undefined}
                  />
                </div>
              </div>

              <div className="flex flex-wrap items-center justify-between gap-3 border-t border-line px-4 py-3">
                <p aria-live="polite" className="min-w-0 text-sm">
                  {problem ? (
                    <span role="alert" className="font-semibold text-danger-text">
                      {problem}
                    </span>
                  ) : complete ? (
                    <span className="flex items-center gap-1.5 font-semibold text-ink">
                      <Check aria-hidden="true" className="size-4 text-brand-text" />
                      {formatRange(draft as DateRange)}
                      <span className="font-normal text-ink-soft">
                        · {dayCountLabel(daysIn(draft as DateRange))}
                      </span>
                    </span>
                  ) : (
                    <span className="text-ink-soft">
                      {mode === 'day'
                        ? 'Pick a day.'
                        : draft.fromDate
                          ? 'Now pick the last day.'
                          : 'Pick the first day.'}
                    </span>
                  )}
                </p>
                <div className="flex gap-2">
                  <Button variant="ghost" size="sm" onClick={() => close(true)}>
                    Cancel
                  </Button>
                  <Button size="sm" disabled={!complete || Boolean(problem)} onClick={apply}>
                    Apply
                  </Button>
                </div>
              </div>
            </div>
          </>,
        )}
    </div>
  );
}

function PresetButton({
  label,
  active,
  onClick,
}: {
  label: string;
  active: boolean;
  onClick: () => void;
}) {
  return (
    <li className="shrink-0">
      <button
        type="button"
        aria-pressed={active}
        onClick={onClick}
        className={cn(
          'flex w-full items-center justify-between gap-2 rounded-full px-3 py-1.5 text-left text-sm font-medium whitespace-nowrap transition sm:rounded-lg sm:py-2',
          'focus-visible:ring-2 focus-visible:ring-brand-500/40 focus-visible:outline-none',
          active
            ? 'bg-brand-500 text-on-brand shadow-sm'
            : 'bg-page text-ink hover:bg-brand-50 hover:text-brand-text-strong sm:bg-transparent',
        )}
      >
        {label}
        {active && <Check aria-hidden="true" className="hidden size-4 sm:block" />}
      </button>
    </li>
  );
}
