import {
  CircleCheckBig,
  ClipboardList,
  IndianRupee,
  Sparkles,
  Timer,
  type LucideIcon,
} from 'lucide-react';

import type { CustomerOrderSummary } from '@/types/order';
import { cn } from '@/utils/classNames';
import { formatDateTime } from '@/utils/dateUtils';
import { formatCurrency, formatNumber } from '@/utils/formatUtils';

import { PALETTE, type PaletteColour } from './palette';
import { ColourIcon, SectionHeading } from './SectionHeading';

/** A figure on a small tile of its colour's tint: the icon, the figure, what it is and a short note. */
function SummaryTile({
  icon,
  colour,
  value,
  label,
  hint,
}: {
  icon: LucideIcon;
  colour: PaletteColour;
  value: string;
  label: string;
  hint?: string;
}) {
  return (
    <li
      className={cn(
        'flex min-w-0 items-center gap-3 rounded-2xl p-3 ring-1 ring-line/50',
        PALETTE[colour].soft,
      )}
    >
      <ColourIcon icon={icon} colour={colour} />
      <span className="min-w-0">
        <span className="block truncate text-lg leading-tight font-extrabold text-ink tabular-nums">
          {value}
        </span>
        <span className={cn('block text-xs font-semibold', PALETTE[colour].text)}>{label}</span>
        {hint && <span className="block truncate text-[11px] text-ink-soft">{hint}</span>}
      </span>
    </li>
  );
}

/**
 * The customer's orders in numbers, all time (the heading says so): orders placed, the ones going now, the ones
 * delivered, and what the delivered ones cost. Straight from order-service's own totals; nothing is estimated.
 */
export function SummaryCards({ summary }: { summary: CustomerOrderSummary }) {
  const cancelled = summary.cancelledOrders + summary.rejectedOrders;
  return (
    <section aria-labelledby="summary-title" className="flex flex-col gap-3">
      <SectionHeading
        id="summary-title"
        icon={Sparkles}
        colour="sky"
        title={
          <>
            Your orders <span className="font-medium text-ink-soft">· all time</span>
          </>
        }
      />
      <ul className="grid grid-cols-2 gap-2 sm:gap-3 lg:grid-cols-4">
        <SummaryTile
          icon={ClipboardList}
          colour="violet"
          value={formatNumber(summary.totalOrders)}
          label="Orders placed"
          hint={
            summary.lastOrderAt ? `Last one ${formatDateTime(summary.lastOrderAt)}` : 'No order yet'
          }
        />
        <SummaryTile
          icon={Timer}
          colour="sunset"
          value={formatNumber(summary.activeOrders)}
          label="In progress now"
          hint={summary.activeOrders > 0 ? 'Being prepared, delivered or paid' : 'Nothing going'}
        />
        <SummaryTile
          icon={CircleCheckBig}
          colour="green"
          value={formatNumber(summary.deliveredOrders)}
          label="Delivered"
          hint={cancelled > 0 ? `${formatNumber(cancelled)} cancelled or declined` : undefined}
        />
        <SummaryTile
          icon={IndianRupee}
          colour="sky"
          value={formatCurrency(summary.totalSpent, summary.currency)}
          label="Total spent"
          hint="On delivered orders"
        />
      </ul>
    </section>
  );
}
