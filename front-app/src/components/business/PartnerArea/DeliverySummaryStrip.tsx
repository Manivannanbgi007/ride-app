import { Banknote, CreditCard, PackageCheck, PackageX, type LucideIcon } from 'lucide-react';

import { Skeleton } from '@/components/common/Loader';
import { cn } from '@/utils/classNames';
import { DEFAULT_CURRENCY } from '@/utils/constants';
import { formatCurrency, formatNumber } from '@/utils/formatUtils';
import type { HistorySummary } from '@/utils/partnerHistoryUtils';

interface Figure {
  label: string;
  value: string;
  hint: string;
  icon: LucideIcon;
  /** The icon tile's colours */
  tone: string;
}

function figuresOf(summary: HistorySummary): Figure[] {
  return [
    {
      label: 'Delivered',
      value: formatNumber(summary.delivered),
      hint: 'completed orders',
      icon: PackageCheck,
      tone: 'bg-brand-100 text-brand-text-strong',
    },
    {
      label: 'Cash collected',
      value: formatCurrency(summary.cashCollected, DEFAULT_CURRENCY),
      hint: `${formatNumber(summary.cashDeliveries)} cash ${summary.cashDeliveries === 1 ? 'order' : 'orders'}`,
      icon: Banknote,
      tone: 'bg-warning-100 text-warning-ink',
    },
    {
      label: 'Paid online',
      value: formatNumber(summary.paidOnline),
      hint: 'nothing to collect',
      icon: CreditCard,
      tone: 'bg-info-100 text-info-ink',
    },
    {
      label: 'Cancelled',
      value: formatNumber(summary.cancelled),
      hint: 'not delivered',
      icon: PackageX,
      tone: 'bg-danger-50 text-danger-text',
    },
  ];
}

/** The partner's totals for the deliveries shown (their search and dates): one card, four figures. */
export function DeliverySummaryStrip({ summary }: { summary: HistorySummary }) {
  return (
    <section aria-label="Summary">
      <dl className="grid grid-cols-2 gap-px overflow-hidden rounded-2xl bg-line shadow-card ring-1 ring-line/60 lg:grid-cols-4">
        {figuresOf(summary).map(({ label, value, hint, icon: Icon, tone }) => (
          <div
            key={label}
            className="flex items-center gap-2.5 bg-surface px-3 py-2.5 sm:gap-3 sm:px-4"
          >
            <span
              aria-hidden="true"
              className={cn('flex size-9 shrink-0 items-center justify-center rounded-xl', tone)}
            >
              <Icon className="size-4.5" />
            </span>
            <div className="min-w-0">
              <dt className="text-xs font-semibold text-ink-soft">{label}</dt>
              <dd className="text-lg leading-tight font-extrabold tracking-tight tabular-nums sm:truncate">
                {value}
              </dd>
              <dd className="text-[11px] text-ink-soft sm:truncate">{hint}</dd>
            </div>
          </div>
        ))}
      </dl>
    </section>
  );
}

/** The strip's shape while the deliveries load. */
export function DeliverySummaryLoader() {
  return (
    <div role="status" aria-label="Loading" className="grid grid-cols-2 gap-2 lg:grid-cols-4">
      {Array.from({ length: 4 }, (_, index) => (
        <Skeleton key={index} className="h-16 rounded-2xl" />
      ))}
    </div>
  );
}
