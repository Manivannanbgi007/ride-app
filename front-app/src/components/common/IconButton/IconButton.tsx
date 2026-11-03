import type { LucideIcon } from 'lucide-react';

import { Tooltip } from '@/components/common/Tooltip';
import { cn } from '@/utils/classNames';

interface IconButtonProps {
  /** Read out by screen readers and shown in the tooltip */
  label: string;
  icon: LucideIcon;
  onClick: () => void;
  disabled?: boolean;
  /** danger: turns red on hover (delete) */
  tone?: 'brand' | 'danger';
}

const TONES = {
  brand: 'hover:bg-brand-500 hover:text-on-brand hover:shadow-brand hover:ring-brand-500',
  danger:
    'hover:bg-danger-600 hover:text-white hover:shadow-[0_8px_18px_-8px_rgb(217_59_59/0.7)] hover:ring-danger-600',
};

/** A small square icon button (row actions in a table) with the shared tooltip. */
export function IconButton({
  label,
  icon: Icon,
  onClick,
  disabled = false,
  tone = 'brand',
}: IconButtonProps) {
  return (
    <Tooltip
      label={label}
      icon={Icon}
      className={cn('rounded-lg', disabled && 'cursor-not-allowed')}
    >
      <button
        type="button"
        aria-label={label}
        disabled={disabled}
        onClick={onClick}
        className={cn(
          'flex size-8 items-center justify-center rounded-lg bg-surface text-ink-soft ring-1 ring-line',
          'transition duration-200 motion-safe:hover:-translate-y-0.5 motion-safe:active:translate-y-0 motion-safe:active:scale-95',
          'focus-visible:ring-4 focus-visible:ring-brand-500/30 focus-visible:outline-none',
          TONES[tone],
          'disabled:pointer-events-none disabled:opacity-35',
        )}
      >
        <Icon aria-hidden="true" className="size-4" />
      </button>
    </Tooltip>
  );
}
