import type { ReactNode } from 'react';

import { cn } from '@/utils/classNames';

interface CardProps {
  title?: string;
  subtitle?: ReactNode;
  /** Shown at the right of the title (a button, a filter) */
  actions?: ReactNode;
  children: ReactNode;
  className?: string;
  /** A slimmer header band and less padding, for a dense panel such as a chart with its figures */
  compact?: boolean;
}

/**
 * The white rounded panel every section sits in. With a title it gets a header band in the brand's light green
 * (title, subtitle and actions), so each panel's subject stands out; without one it is a plain panel (filters).
 * Nothing is clipped at the corners: calendars and menus opened inside a panel may reach past its edge.
 */
export function Card({ title, subtitle, actions, children, className, compact }: CardProps) {
  const hasHeader = Boolean(title) || Boolean(actions);
  return (
    <section className={cn('rounded-3xl bg-surface shadow-card', !hasHeader && 'p-6', className)}>
      {hasHeader && (
        <header
          className={cn(
            'flex flex-wrap items-center justify-between gap-3 rounded-t-3xl border-b border-line',
            compact ? 'px-4 py-3 sm:px-5' : 'px-6 py-4',
          )}
        >
          <div className="flex min-w-0 items-start gap-3">
            {title && (
              <span
                aria-hidden="true"
                className="mt-1.5 h-4 w-1 shrink-0 rounded-full bg-linear-to-b from-brand-400 to-brand-600"
              />
            )}
            <div className="min-w-0">
              {title && <h2 className="text-base font-bold tracking-tight">{title}</h2>}
              {subtitle && <p className="mt-0.5 text-sm text-ink-soft">{subtitle}</p>}
            </div>
          </div>
          {actions}
        </header>
      )}
      {hasHeader ? <div className={compact ? 'p-4 sm:p-5' : 'p-6'}>{children}</div> : children}
    </section>
  );
}
