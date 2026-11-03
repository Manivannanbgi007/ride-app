import type { LucideIcon } from 'lucide-react';

import { cn } from '@/utils/classNames';
import { formatNumber } from '@/utils/formatUtils';
import { stagger } from '@/utils/motion';

export interface QuickFilter<T extends string> {
  value: T;
  label: string;
  icon?: LucideIcon;
  /** How many records the chip finds, when known */
  count?: number;
}

interface QuickFiltersProps<T extends string> {
  /** Read out by screen readers: what the chips filter ("Order status") */
  label: string;
  items: readonly QuickFilter<T>[];
  value: T;
  onChange: (value: T) => void;
}

/**
 * One-click filters above a list ("All · Delivered · Cancelled ..."): pill buttons, the chosen one filled with the
 * brand gradient. They pop in one after the other and scroll sideways on a phone. Each is a toggle button
 * (aria-pressed), so screen readers say which one is on.
 */
export function QuickFilters<T extends string>({
  label,
  items,
  value,
  onChange,
}: QuickFiltersProps<T>) {
  return (
    <div
      role="group"
      aria-label={label}
      className="-mx-1 flex [scrollbar-width:none] gap-2 overflow-x-auto px-1 pt-0.5 pb-1.5"
    >
      {items.map(({ value: itemValue, label: itemLabel, icon: Icon, count }, index) => {
        const active = itemValue === value;
        return (
          <button
            key={itemValue}
            type="button"
            aria-pressed={active}
            onClick={() => onChange(itemValue)}
            style={stagger(index)}
            className={cn(
              'group inline-flex shrink-0 items-center gap-2 rounded-full py-2 pr-3.5 pl-3 text-sm font-semibold whitespace-nowrap transition duration-200 stagger motion-safe:animate-pop-in',
              'focus-visible:ring-4 focus-visible:ring-brand-500/30 focus-visible:outline-none',
              active
                ? 'bg-linear-to-br from-brand-500 to-brand-700 text-white shadow-brand'
                : 'bg-surface text-ink ring-1 ring-line hover:bg-brand-50 hover:ring-brand-300 motion-safe:hover:-translate-y-0.5',
            )}
          >
            {Icon && (
              <Icon
                aria-hidden="true"
                className={cn(
                  'size-4 transition-transform duration-200 group-hover:scale-110',
                  active ? 'text-white' : 'text-brand-text',
                )}
              />
            )}
            {itemLabel}
            {/* The space keeps "All 1,200" apart for screen readers (a flex row does not show it) */}
            {count !== undefined && ' '}
            {count !== undefined && (
              <span
                className={cn(
                  'min-w-6 rounded-full px-1.5 py-0.5 text-center text-[11px] leading-none font-bold tabular-nums',
                  active ? 'bg-white/25 text-white' : 'bg-brand-50 text-brand-text-strong',
                )}
              >
                {formatNumber(count)}
              </span>
            )}
          </button>
        );
      })}
    </div>
  );
}
