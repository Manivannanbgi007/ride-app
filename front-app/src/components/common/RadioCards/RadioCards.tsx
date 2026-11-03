import { useId, type ReactNode } from 'react';

import { cn } from '@/utils/classNames';

export interface RadioCardOption<T extends string> {
  value: T;
  label: ReactNode;
  description?: ReactNode;
}

interface RadioCardsProps<T extends string> {
  /** What is being chosen (read out, and shown above the cards) */
  legend: string;
  options: readonly RadioCardOption<T>[];
  value: T | '';
  onChange: (value: T) => void;
  error?: string;
  /** Cards side by side on wide screens */
  columns?: 1 | 2 | 3 | 4;
}

const COLUMNS = {
  1: '',
  2: 'sm:grid-cols-2',
  3: 'sm:grid-cols-3',
  4: 'sm:grid-cols-2 lg:grid-cols-4',
};

/**
 * A choice between a few options shown as cards (an address, a way to pay). Real radio buttons underneath: the
 * arrow keys move between them and screen readers announce the group.
 */
export function RadioCards<T extends string>({
  legend,
  options,
  value,
  onChange,
  error,
  columns = 1,
}: RadioCardsProps<T>) {
  const name = useId();
  const errorId = `${name}-error`;
  return (
    <fieldset aria-describedby={error ? errorId : undefined} className="flex flex-col gap-3">
      <legend className="mb-3 text-sm font-semibold">{legend}</legend>
      <div className={cn('grid gap-3', COLUMNS[columns])}>
        {options.map((option) => {
          const checked = option.value === value;
          return (
            <label
              key={option.value}
              className={cn(
                'flex cursor-pointer items-start gap-3 rounded-xl border p-4 transition-colors',
                checked
                  ? 'border-brand-500 bg-brand-50'
                  : 'border-line bg-surface hover:border-brand-500',
              )}
            >
              <input
                type="radio"
                name={name}
                value={option.value}
                checked={checked}
                onChange={() => onChange(option.value)}
                className="mt-0.5 size-4 accent-brand-500"
              />
              <span className="min-w-0 text-sm">
                <span className="block font-semibold">{option.label}</span>
                {option.description && (
                  <span className="mt-0.5 block text-ink-soft">{option.description}</span>
                )}
              </span>
            </label>
          );
        })}
      </div>
      {error && (
        <p id={errorId} className="text-xs text-danger-500">
          {error}
        </p>
      )}
    </fieldset>
  );
}
