import type { ReactNode } from 'react';

import { cn } from '@/utils/classNames';

/**
 * Every tone is a clear background with dark text of the same colour and a fine ring of it, in light and dark mode
 * (the colours are tokens in index.css): green = fine, red = ended badly or off, yellow = waiting, blue = on its way.
 */
const TONES = {
  success: 'bg-brand-200 text-brand-text-deep ring-brand-500/40',
  danger: 'bg-danger-100 text-danger-ink ring-danger-500/40',
  info: 'bg-info-100 text-info-ink ring-info-500/40',
  warning: 'bg-warning-100 text-warning-ink ring-warning-500/50',
  neutral: 'bg-line text-ink ring-ink-soft/30',
};

export type BadgeTone = keyof typeof TONES;

interface BadgeProps {
  tone?: BadgeTone;
  /** A small dot of the badge's colour before the text (statuses have one; counts and names do not) */
  dot?: boolean;
  children: ReactNode;
}

/** A small coloured label: a status, a count, an environment name. */
export function Badge({ tone = 'neutral', dot = false, children }: BadgeProps) {
  return (
    <span
      className={cn(
        'inline-flex items-center gap-1.5 rounded-full px-3 py-1 text-xs leading-none font-semibold whitespace-nowrap ring-1 ring-inset',
        TONES[tone],
      )}
    >
      {dot && <span aria-hidden="true" className="size-1.5 shrink-0 rounded-full bg-current" />}
      {children}
    </span>
  );
}
