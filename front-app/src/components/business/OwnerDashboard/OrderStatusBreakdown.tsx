import { ChevronRight } from 'lucide-react';
import { useId } from 'react';
import { Link } from 'react-router';

import { ORDER_STATUS_TONES } from '@/components/business/OwnerOrders';
import type { OrderStatus } from '@/types/order';
import { cn } from '@/utils/classNames';
import { ordersByStatusBars } from '@/utils/dashboardUtils';
import { formatNumber, formatPercent } from '@/utils/formatUtils';
import { stagger } from '@/utils/motion';

/** A status's gradient (the owner's order pages' colours); a status the app does not know yet is slate. */
function fillOf(status: string): string {
  return status in ORDER_STATUS_TONES
    ? ORDER_STATUS_TONES[status as OrderStatus].fill
    : ORDER_STATUS_TONES.CANCELLED.fill;
}

interface OrderStatusBreakdownProps {
  ordersByStatus: Partial<Record<OrderStatus, number>>;
  /** Where a status's orders are listed (its tab of the Orders page, for the same days); none: not a link */
  linkOf?: (status: string) => string | undefined;
  className?: string;
}

/**
 * Where the range's orders are now: one compact row per status that has orders, in the order an order moves
 * through them, with its count, its share of the orders and a thin bar in the status's gradient that grows in.
 * With `linkOf` each row opens the orders behind it.
 */
export function OrderStatusBreakdown({
  ordersByStatus,
  linkOf,
  className,
}: OrderStatusBreakdownProps) {
  const titleId = useId();
  const rows = ordersByStatusBars(ordersByStatus).filter((row) => row.count > 0);
  const total = rows.reduce((sum, row) => sum + row.count, 0);

  return (
    <section aria-labelledby={titleId} className={cn('flex flex-col gap-3', className)}>
      <div className="flex items-baseline justify-between gap-2">
        <h3 id={titleId} className="text-sm font-bold text-ink">
          Orders by status
        </h3>
        <span className="text-xs text-ink-soft">
          {linkOf && rows.length > 0 ? 'Select one to see them' : 'Where they are now'}
        </span>
      </div>
      {rows.length === 0 ? (
        <p className="text-sm text-ink-soft">No orders in this range.</p>
      ) : (
        <ul
          aria-labelledby={titleId}
          className="grid gap-x-6 gap-y-1 sm:grid-cols-2 xl:grid-cols-1"
        >
          {rows.map((row, index) => {
            const share = (row.count / total) * 100;
            const to = linkOf?.(row.status);
            const content = (
              <>
                <span className="flex items-center justify-between gap-3 text-sm">
                  <span className="flex min-w-0 items-center gap-2">
                    <span
                      aria-hidden="true"
                      className={cn('size-2.5 shrink-0 rounded-full', fillOf(row.status))}
                    />
                    <span className="truncate text-ink">{row.label}</span>
                  </span>
                  <span className="flex shrink-0 items-center gap-1 tabular-nums">
                    <span className="font-bold text-ink">{formatNumber(row.count)}</span>
                    <span className="ml-0.5 text-xs text-ink-soft">{formatPercent(share)}</span>
                    {to && (
                      <ChevronRight
                        aria-hidden="true"
                        className="size-3.5 text-ink-soft transition-transform group-hover:translate-x-0.5"
                      />
                    )}
                  </span>
                </span>
                <span
                  aria-hidden="true"
                  className="block h-1.5 overflow-hidden rounded-full bg-chart-track"
                >
                  <span
                    className={cn(
                      'block h-full origin-left rounded-full stagger motion-safe:animate-bar-grow',
                      fillOf(row.status),
                    )}
                    // A small share keeps a visible sliver
                    style={{ width: `${Math.max(share, 3)}%`, ...stagger(index) }}
                  />
                </span>
              </>
            );
            return (
              <li key={row.status}>
                {to ? (
                  <Link
                    to={to}
                    aria-label={`${row.label}: ${formatNumber(row.count)} ${row.count === 1 ? 'order' : 'orders'}, ${formatPercent(share)}. See them`}
                    className="group -mx-2 flex flex-col gap-1.5 rounded-xl px-2 py-1.5 transition hover:bg-page focus-visible:ring-4 focus-visible:ring-brand-500/30 focus-visible:outline-none"
                  >
                    {content}
                  </Link>
                ) : (
                  <span className="flex flex-col gap-1.5 py-1.5">{content}</span>
                )}
              </li>
            );
          })}
        </ul>
      )}
    </section>
  );
}
