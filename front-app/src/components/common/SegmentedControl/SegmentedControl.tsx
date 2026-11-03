import { useId, useRef, type KeyboardEvent } from 'react';

import { cn } from '@/utils/classNames';

export interface SegmentOption<T extends string> {
  id: T;
  label: string;
}

interface SegmentedControlProps<T extends string> {
  /** Read out by screen readers: what the segments switch between */
  label: string;
  options: readonly SegmentOption<T>[];
  value: T;
  onChange: (id: T) => void;
  /** The id of the region the segments control (sets aria-controls); optional */
  panelId?: string;
}

/**
 * A small pill-shaped switch between a few views of the same data (Monthly | Weekly | Daily). Tab semantics: the
 * arrow keys move between the segments, and only the selected one is in the tab order.
 */
export function SegmentedControl<T extends string>({
  label,
  options,
  value,
  onChange,
  panelId,
}: SegmentedControlProps<T>) {
  const baseId = useId();
  const buttons = useRef<(HTMLButtonElement | null)[]>([]);

  const moveWithArrows = (event: KeyboardEvent, index: number) => {
    const step = event.key === 'ArrowRight' ? 1 : event.key === 'ArrowLeft' ? -1 : 0;
    if (step === 0) {
      return;
    }
    event.preventDefault();
    const next = (index + step + options.length) % options.length;
    onChange(options[next].id);
    buttons.current[next]?.focus();
  };

  return (
    <div
      role="tablist"
      aria-label={label}
      className="inline-flex items-center gap-1 rounded-full bg-page p-1"
    >
      {options.map((option, index) => {
        const selected = option.id === value;
        return (
          <button
            key={option.id}
            ref={(element) => {
              buttons.current[index] = element;
            }}
            id={`${baseId}-${option.id}`}
            type="button"
            role="tab"
            aria-selected={selected}
            aria-controls={panelId}
            tabIndex={selected ? 0 : -1}
            onClick={() => onChange(option.id)}
            onKeyDown={(event) => moveWithArrows(event, index)}
            className={cn(
              'cursor-pointer rounded-full px-4 py-1.5 text-sm font-medium transition duration-200',
              'focus-visible:ring-2 focus-visible:ring-brand-500/40 focus-visible:outline-none',
              selected
                ? 'bg-surface text-brand-text-strong shadow-sm'
                : 'text-ink-soft hover:text-ink',
            )}
          >
            {option.label}
          </button>
        );
      })}
    </div>
  );
}
