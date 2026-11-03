import { useId } from 'react';

import type { PaymentOutcome } from '@/types/dashboard';
import { cn } from '@/utils/classNames';
import type { DashboardFigures } from '@/utils/dashboardUtils';
import { formatNumber, formatPercent } from '@/utils/formatUtils';
import { PALETTE } from '@/utils/palette';

interface OrderRatesPanelProps {
  figures: DashboardFigures;
  payments: PaymentOutcome | null;
  className?: string;
}

/**
 * "Order rates" of the chosen days: how many orders were delivered or cancelled, and how many online payments went
 * through; each as a percentage with a thin gradient bar and the counts it comes from ("8 of 20 orders").
 */
export function OrderRatesPanel({ figures, payments, className }: OrderRatesPanelProps) {
  const titleId = useId();
  const { totals } = figures;
  const ofOrders = (count: number) =>
    totals
      ? `${formatNumber(count)} of ${counted(totals.orders, 'order')}`
      : 'Orders not available';
  const attempted = payments ? payments.successful + payments.failed : 0;

  return (
    <section aria-labelledby={titleId} className={cn('flex flex-col gap-3', className)}>
      <div className="flex items-baseline justify-between gap-2">
        <h3 id={titleId} className="text-sm font-bold text-ink">
          Order rates
        </h3>
        <span className="text-xs text-ink-soft">Share of these days</span>
      </div>
      <ul aria-labelledby={titleId} className="grid gap-x-6 gap-y-3 sm:grid-cols-3 xl:grid-cols-1">
        <RateRow
          label="Delivered"
          rate={figures.deliveredRate}
          detail={ofOrders(totals?.delivered ?? 0)}
          fill={PALETTE.green.line}
        />
        <RateRow
          label="Cancelled or rejected"
          rate={figures.cancelledRate}
          detail={ofOrders(totals?.cancelled ?? 0)}
          fill={PALETTE.rose.line}
        />
        <RateRow
          label="Payment success"
          rate={figures.paymentSuccessRate}
          detail={
            payments
              ? `${formatNumber(payments.successful)} of ${counted(attempted, 'online payment')}`
              : 'Payments not available'
          }
          fill={PALETTE.sky.line}
        />
      </ul>
    </section>
  );
}

/** '1 order', '26 orders' */
function counted(count: number, noun: string): string {
  return `${formatNumber(count)} ${noun}${count === 1 ? '' : 's'}`;
}

function RateRow({
  label,
  rate,
  detail,
  fill,
}: {
  label: string;
  /** Percent; null when there is nothing to divide (no orders, no payments) or the figures are unknown */
  rate: number | null;
  detail: string;
  /** The bar's gradient (a class name) */
  fill: string;
}) {
  return (
    <li className="flex flex-col gap-1.5">
      <div className="flex items-baseline justify-between gap-3">
        <span className="truncate text-sm text-ink">{label}</span>
        <span className="text-base font-extrabold text-ink tabular-nums">
          {formatPercent(rate)}
        </span>
      </div>
      <span aria-hidden="true" className="h-1.5 overflow-hidden rounded-full bg-chart-track">
        <span
          className={cn('block h-full rounded-full', fill)}
          style={{ width: `${rate ?? 0}%` }}
        />
      </span>
      <span className="text-xs text-ink-soft">{detail}</span>
    </li>
  );
}
