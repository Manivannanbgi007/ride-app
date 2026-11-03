import { RotateCcw } from 'lucide-react';
import type { ButtonHTMLAttributes } from 'react';

import { cn } from '@/utils/classNames';

interface ClearFiltersButtonProps extends Omit<
  ButtonHTMLAttributes<HTMLButtonElement>,
  'type' | 'children'
> {
  /** md: beside the filter boxes (as tall as they are); sm: inside a "nothing found" message */
  size?: 'sm' | 'md';
}

const SIZES = {
  sm: { button: 'h-9 gap-2 pr-3.5 pl-1', tile: 'size-7' },
  md: { button: 'h-11 gap-2.5 pr-4 pl-1.5', tile: 'size-8' },
};

/**
 * "Clear filters": a soft green button with a reset arrow in a white tile. On hover it lifts, the tile turns green
 * and the arrow turns once around; it presses in on click. Nothing moves for people who asked for less motion.
 */
export function ClearFiltersButton({ size = 'md', className, ...props }: ClearFiltersButtonProps) {
  return (
    <button
      type="button"
      {...props}
      className={cn(
        'group/clear inline-flex shrink-0 items-center rounded-xl border text-sm font-semibold whitespace-nowrap select-none',
        'border-brand-200 bg-brand-50 text-brand-text-strong',
        'transition-[translate,scale,box-shadow,background-color,border-color] duration-200 ease-out',
        'enabled:hover:border-brand-500 enabled:hover:bg-surface enabled:hover:shadow-brand',
        'enabled:motion-safe:hover:-translate-y-0.5 enabled:motion-safe:active:translate-y-0 enabled:motion-safe:active:scale-[0.97]',
        'focus-visible:ring-4 focus-visible:ring-brand-500/30 focus-visible:outline-none',
        'disabled:cursor-not-allowed disabled:opacity-50',
        SIZES[size].button,
        className,
      )}
    >
      <span
        className={cn(
          'flex items-center justify-center rounded-lg bg-surface text-brand-text shadow-sm ring-1 ring-brand-200',
          'transition-colors duration-200',
          'group-enabled/clear:group-hover/clear:bg-brand-500 group-enabled/clear:group-hover/clear:text-on-brand group-enabled/clear:group-hover/clear:ring-brand-500',
          SIZES[size].tile,
        )}
      >
        <RotateCcw
          aria-hidden="true"
          className="size-4 transition-transform duration-500 ease-out group-enabled/clear:group-hover/clear:motion-safe:-rotate-360"
        />
      </span>
      Clear filters
    </button>
  );
}
