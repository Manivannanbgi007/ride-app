import { Utensils } from 'lucide-react';

import { ErrorMessage } from '@/components/common/ErrorMessage';
import { Skeleton } from '@/components/common/Loader';
import { useCustomerOrderSummary } from '@/hooks/api/orderHooks';
import { ERROR_MESSAGES } from '@/utils/errorMessages';
import { formatMonthYearShort } from '@/utils/dateUtils';
import { formatCurrency, formatNumber } from '@/utils/formatUtils';

interface CustomerSpendCardProps {
  customerId: number;
  customerName: string;
  /** ISO-8601 instant the account was created */
  memberSince: string;
}

const SHELL = 'overflow-hidden rounded-3xl text-white shadow-brand-lg';

/** The green card: what the customer has spent on delivered orders, with their order counts and name. */
export function CustomerSpendCard({
  customerId,
  customerName,
  memberSince,
}: CustomerSpendCardProps) {
  const summary = useCustomerOrderSummary(customerId);

  if (summary.isPending) {
    return (
      <div role="status" aria-label="Loading total spent" className={`${SHELL} bg-brand-600`}>
        <div className="flex flex-col gap-5 p-6">
          <Skeleton className="h-4 w-24 opacity-40" />
          <Skeleton className="h-10 w-40 opacity-40" />
          <Skeleton className="h-4 w-full opacity-40" />
        </div>
        <div className="bg-brand-700 p-6">
          <Skeleton className="h-6 w-32 opacity-40" />
        </div>
      </div>
    );
  }
  if (summary.error) {
    return (
      <div className="rounded-3xl bg-surface shadow-card">
        <ErrorMessage
          title={ERROR_MESSAGES.CUSTOMER_ORDERS_LOAD_FAILED}
          error={summary.error}
          onRetry={() => void summary.refetch()}
          isRetrying={summary.isFetching}
        />
      </div>
    );
  }

  const data = summary.data;
  return (
    <section
      aria-label="Total spent"
      className={`${SHELL} flex flex-col bg-linear-to-br from-brand-600 to-brand-800`}
    >
      <div className="flex flex-1 flex-col gap-4 p-6">
        <p className="text-sm text-white/90">Total spent</p>
        <p className="text-4xl font-extrabold tracking-tight break-words sm:text-5xl">
          {formatCurrency(data.totalSpent, data.currency)}
        </p>
        <p className="mt-auto flex flex-wrap items-center justify-between gap-2 text-sm font-medium text-white/95">
          <span>
            {formatNumber(data.deliveredOrders)} delivered · {formatNumber(data.totalOrders)} orders
          </span>
          <span>Customer since {formatMonthYearShort(memberSince)}</span>
        </p>
      </div>
      <div className="flex items-center justify-between gap-3 bg-brand-800/60 px-6 py-4">
        <div className="min-w-0">
          <p className="text-xs text-white/80">Name</p>
          <p className="truncate text-lg font-semibold">{customerName}</p>
        </div>
        <Utensils aria-hidden="true" className="size-8 shrink-0 text-white/30" />
      </div>
    </section>
  );
}
