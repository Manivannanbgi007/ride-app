import { ClipboardList } from 'lucide-react';
import { Link } from 'react-router';

import { OrderStatusBadge } from '@/components/business/StatusBadges';
import { AsyncContent } from '@/components/common/AsyncContent';
import { Card } from '@/components/common/Card';
import { EmptyState } from '@/components/common/EmptyState';
import { SkeletonLoader } from '@/components/common/Loader';
import { useOwnerOrders } from '@/hooks/api/ownerHooks';
import { useNow } from '@/hooks/useNow';
import { DEFAULT_CURRENCY, OWNER_ROUTES, ownerRouteTo } from '@/utils/constants';
import { formatDateTime, formatRelativeTime } from '@/utils/dateUtils';
import { formatCurrency } from '@/utils/formatUtils';

/** How many of the latest orders are listed; the rest are on "Orders". */
const SHOWN = 5;
const ALL_STATUSES = {};

/** The latest orders, whatever their status now, newest first; they refresh while the page is open. */
export function RecentOrdersCard({ restaurantId }: { restaurantId: number }) {
  const recent = useOwnerOrders(restaurantId, ALL_STATUSES, 0, SHOWN, true);
  // "12 minutes ago" keeps counting while the owner looks at the page
  useNow();

  return (
    <Card
      title="Latest orders"
      subtitle="Whatever their status, newest first"
      actions={
        <Link
          to={`${OWNER_ROUTES.orders}?tab=delivered`}
          className="rounded-md text-sm font-semibold text-brand-text hover:underline focus-visible:ring-4 focus-visible:ring-brand-500/30 focus-visible:outline-none"
        >
          Order history
        </Link>
      }
    >
      <AsyncContent
        isLoading={recent.isPending}
        error={recent.error}
        isEmpty={recent.data?.content.length === 0}
        errorTitle="Unable to load your latest orders"
        onRetry={() => void recent.refetch()}
        isRetrying={recent.isFetching}
        loading={<SkeletonLoader lines={4} />}
        empty={
          <EmptyState
            icon={ClipboardList}
            title="No orders yet"
            message="Orders appear here as soon as customers place them."
          />
        }
      >
        <ul aria-label="Latest orders" className="-my-3 divide-y divide-line">
          {recent.data?.content.map((order) => (
            <li
              key={order.id}
              className="grid grid-cols-[minmax(0,1fr)_auto] items-center gap-x-3 gap-y-1 py-3 text-sm"
            >
              <div className="flex min-w-0 items-center gap-3">
                <Link
                  to={ownerRouteTo.orderDetails(order.id)}
                  aria-label={`Order #${order.id}`}
                  className="shrink-0 rounded-md font-bold text-brand-text tabular-nums hover:underline focus-visible:ring-4 focus-visible:ring-brand-500/30 focus-visible:outline-none"
                >
                  #{order.id}
                </Link>
                <span
                  className="min-w-0 truncate font-medium text-ink"
                  title={order.customerName ?? undefined}
                >
                  {order.customerName ?? `Customer #${order.customerId}`}
                </span>
              </div>
              <span className="text-right font-semibold text-ink tabular-nums">
                {formatCurrency(order.totalAmount, DEFAULT_CURRENCY)}
              </span>
              <span className="flex min-w-0 flex-wrap items-center gap-2">
                <OrderStatusBadge status={order.status} />
              </span>
              <time
                dateTime={order.createdAt}
                title={formatDateTime(order.createdAt)}
                className="text-right text-xs text-ink-soft"
              >
                {formatRelativeTime(order.createdAt)}
              </time>
            </li>
          ))}
        </ul>
      </AsyncContent>
    </Card>
  );
}
