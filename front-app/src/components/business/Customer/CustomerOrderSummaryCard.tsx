import { Card } from '@/components/common/Card';
import type { CustomerOrderSummary } from '@/types/order';
import { cn } from '@/utils/classNames';
import { formatDateTime } from '@/utils/dateUtils';
import { formatCurrency, formatNumber } from '@/utils/formatUtils';

/** A customer's orders in numbers (order-service keeps them). */
export function CustomerOrderSummaryCard({ summary }: { summary: CustomerOrderSummary }) {
  return (
    <Card
      title="Orders"
      subtitle={
        summary.lastOrderAt ? `Last order ${formatDateTime(summary.lastOrderAt)}` : 'No orders yet'
      }
    >
      <dl className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-5">
        <Figure label="Orders" value={formatNumber(summary.totalOrders)} />
        <Figure
          label="In progress"
          value={formatNumber(summary.activeOrders)}
          tone="text-info-500"
        />
        <Figure
          label="Delivered"
          value={formatNumber(summary.deliveredOrders)}
          tone="text-brand-text"
        />
        <Figure
          label="Cancelled or rejected"
          value={formatNumber(summary.cancelledOrders + summary.rejectedOrders)}
          tone="text-danger-500"
        />
        <Figure
          label="Spent (delivered)"
          value={formatCurrency(summary.totalSpent, summary.currency)}
        />
      </dl>
    </Card>
  );
}

function Figure({ label, value, tone }: { label: string; value: string; tone?: string }) {
  return (
    <div className="rounded-xl bg-page px-4 py-3">
      <dt className="text-xs text-ink-soft">{label}</dt>
      <dd className={cn('mt-1 text-xl font-bold break-words', tone)}>{value}</dd>
    </div>
  );
}
