import { Inbox, type LucideIcon } from 'lucide-react';
import type { ReactNode } from 'react';

interface EmptyStateProps {
  title: string;
  message?: string;
  icon?: LucideIcon;
  /** E.g. a button "Clear filters" */
  action?: ReactNode;
}

/**
 * The API answered, but there is nothing to show ("No orders found"). Not the same as an error. The icon breathes in
 * its soft circle while a ring spreads out around it (motion-reduce: still).
 */
export function EmptyState({ title, message, icon: Icon = Inbox, action }: EmptyStateProps) {
  return (
    <div className="flex flex-col items-center gap-3 px-6 py-10 text-center motion-safe:animate-rise-in">
      <span className="relative flex size-16 items-center justify-center">
        <span
          aria-hidden="true"
          className="absolute inset-0 rounded-full bg-brand-200/60 motion-safe:animate-ping-soft"
        />
        <span className="relative flex size-16 items-center justify-center rounded-full bg-linear-to-br from-brand-50 to-brand-100 text-brand-500 ring-1 ring-brand-200 motion-safe:animate-breathe">
          <Icon aria-hidden="true" className="size-7" />
        </span>
      </span>
      <h3 className="text-base font-semibold">{title}</h3>
      {message && <p className="max-w-sm text-sm text-ink-soft">{message}</p>}
      {action}
    </div>
  );
}
