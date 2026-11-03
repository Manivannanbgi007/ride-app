import { ChevronRight, ClipboardList, Receipt } from 'lucide-react';
import { Link } from 'react-router';

import {
  customerStatusTone,
  orderItemsLine,
  OrderStatusPill,
  ReorderButton,
} from '@/components/business/MyOrders';
import { RestaurantTile } from '@/components/business/Storefront';
import { AsyncContent } from '@/components/common/AsyncContent';
import { buttonStyles } from '@/components/common/Button';
import { Skeleton } from '@/components/common/Loader';
import { useDashboardOrders } from '@/hooks/api/customerDashboardHooks';
import type { OrderSummary } from '@/types/order';
import { cn } from '@/utils/classNames';
import { CUSTOMER_ROUTES, customerRouteTo, DEFAULT_CURRENCY } from '@/utils/constants';
import { formatDateTime } from '@/utils/dateUtils';
import { formatCurrency } from '@/utils/formatUtils';
import { ERROR_MESSAGES } from '@/utils/errorMessages';

import { SectionHeading, SectionLink } from './SectionHeading';

/** How many finished orders are listed; "View all orders" leads to the full history. */
const SHOWN = 4;

/**
 * "Recent orders": the latest finished orders (the ones still going are in "In progress" above), one compact row
 * each: the restaurant, when and what, the total and how it ended, "Reorder" and its page. The first page of the
 * orders is shared with the rest of the dashboard: one request.
 */
export function RecentOrders() {
  const orders = useDashboardOrders();
  const recent = orders.finished.slice(0, SHOWN);
  const hasAnyOrder = (orders.data?.content.length ?? 0) > 0;

  // Only orders still going so far: "In progress" shows them, and there is nothing finished to list
  if (orders.data && recent.length === 0 && hasAnyOrder) {
    return null;
  }

  return (
    <section aria-labelledby="recent-orders-title" className="flex flex-col gap-3">
      <SectionHeading
        id="recent-orders-title"
        icon={Receipt}
        colour="sunset"
        title="Recent orders"
        subtitle="Order them again in one tap"
      >
        <SectionLink to={CUSTOMER_ROUTES.myOrders} colour="sunset">
          View all orders
        </SectionLink>
      </SectionHeading>
      <AsyncContent
        isLoading={orders.isPending}
        error={orders.error}
        isEmpty={!hasAnyOrder}
        errorTitle={ERROR_MESSAGES.MY_ORDERS_LOAD_FAILED}
        onRetry={() => void orders.refetch()}
        isRetrying={orders.isFetching}
        loading={
          <div className="flex flex-col gap-2">
            {Array.from({ length: 3 }, (_, index) => (
              <Skeleton key={index} className="h-16 rounded-2xl bg-surface" />
            ))}
          </div>
        }
        empty={
          <p className="flex flex-wrap items-center gap-x-3 gap-y-1 rounded-2xl bg-surface px-4 py-3 text-sm text-ink-soft shadow-card ring-1 ring-line/60">
            <ClipboardList aria-hidden="true" className="size-4 shrink-0 text-st-placed-text" />
            No orders yet: your orders appear here as soon as you place one.
            <Link to={CUSTOMER_ROUTES.restaurants} className={buttonStyles('primary', 'sm')}>
              Find food
            </Link>
          </p>
        }
      >
        <ul aria-label="Recent orders" className="flex flex-col gap-2">
          {recent.map((order) => (
            <li key={order.id}>
              <RecentOrderRow order={order} />
            </li>
          ))}
        </ul>
      </AsyncContent>
    </section>
  );
}

/**
 * One finished order in a row: on a wide screen everything on one line; on a phone the total, the status and the
 * actions go on a second line.
 */
function RecentOrderRow({ order }: { order: OrderSummary }) {
  const tone = customerStatusTone(order.status);
  return (
    <article className="relative grid grid-cols-[auto_minmax(0,1fr)_auto] items-center gap-x-3 gap-y-2 overflow-hidden rounded-2xl bg-surface py-2.5 pr-3 pl-4 shadow-card ring-1 ring-line/60 md:grid-cols-[auto_minmax(0,1fr)_auto_auto]">
      <span aria-hidden="true" className={cn('absolute inset-y-0 left-0 w-1', tone.stripe)} />
      <RestaurantTile
        id={order.restaurantId}
        name={order.restaurantName}
        className="size-10 shrink-0 rounded-xl text-sm"
      />
      <div className="min-w-0">
        <h3 className="truncate text-sm font-bold text-ink">
          <Link
            to={customerRouteTo.orderDetails(order.id)}
            className="rounded-md hover:text-brand-text focus-visible:ring-4 focus-visible:ring-brand-500/30 focus-visible:outline-none"
          >
            {order.restaurantName}
            <span className="sr-only">, order #{order.id}</span>
          </Link>
        </h3>
        <p className="truncate text-xs text-ink-soft">
          #{order.id} · {formatDateTime(order.createdAt)} · {orderItemsLine(order)}
        </p>
        {/* On a phone the status sits under the order, so the name and the actions keep their room */}
        <span className="mt-1 block md:hidden">
          <OrderStatusPill status={order.status} />
        </span>
      </div>
      <div className="flex flex-col items-end gap-1">
        <span className="text-sm font-extrabold text-ink tabular-nums">
          {formatCurrency(order.totalAmount, DEFAULT_CURRENCY)}
        </span>
        <span className="hidden md:block">
          <OrderStatusPill status={order.status} />
        </span>
      </div>
      <div className="col-span-3 flex items-center justify-end gap-1 md:col-span-1">
        <ReorderButton order={order} />
        <Link
          to={customerRouteTo.orderDetails(order.id)}
          className={buttonStyles('ghost', 'sm')}
          aria-label={`Details of order #${order.id}`}
        >
          {/* A phone's row has room for the arrow only (the restaurant's name leads there too) */}
          <span className="hidden sm:inline">Details</span>
          <ChevronRight aria-hidden="true" className="size-4" />
        </Link>
      </div>
    </article>
  );
}
