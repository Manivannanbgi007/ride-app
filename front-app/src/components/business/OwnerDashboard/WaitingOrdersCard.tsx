import { Link } from 'react-router';

import {
  OwnerOrderTable,
  OwnerOrderTableLoader,
} from '@/components/business/OwnerOrders/OwnerOrderTable';
import {
  ownerOrderFilters,
  ownerOrderTabOf,
} from '@/components/business/OwnerOrders/ownerOrderUtils';
import { AsyncContent } from '@/components/common/AsyncContent';
import { Card } from '@/components/common/Card';
import { useOwnerOrders } from '@/hooks/api/ownerHooks';
import { OWNER_ROUTES } from '@/utils/constants';
import { formatNumber } from '@/utils/formatUtils';

/** How many of the orders waiting for a decision are listed (the rest are one click away on "Orders"). */
const SHOWN = 5;
// The "New" tab's own filters: the customer who waited longest comes first
const NEW_ORDERS = ownerOrderFilters(ownerOrderTabOf('new'));

/**
 * The orders waiting for the owner's decision, oldest first, each with Accept and Reject (the same rows and steps as
 * the "New" tab). Nothing is shown while no order waits: the "New" card above already says so.
 */
export function WaitingOrdersCard({ restaurantId }: { restaurantId: number }) {
  const waiting = useOwnerOrders(restaurantId, NEW_ORDERS, 0, SHOWN, true);
  const total = waiting.data?.totalElements ?? 0;

  if (waiting.data && total === 0) {
    return null;
  }
  return (
    <Card
      title="Waiting for your decision"
      subtitle={
        total > 0
          ? `${formatNumber(total)} new order${total === 1 ? '' : 's'}${total > SHOWN ? `, the ${SHOWN} oldest shown` : ''}`
          : undefined
      }
      actions={
        <Link
          to={OWNER_ROUTES.orders}
          className="rounded-md text-sm font-semibold text-brand-text hover:underline focus-visible:ring-4 focus-visible:ring-brand-500/30 focus-visible:outline-none"
        >
          All new orders
        </Link>
      }
    >
      <AsyncContent
        isLoading={waiting.isPending}
        error={waiting.error}
        errorTitle="Unable to load the new orders"
        onRetry={() => void waiting.refetch()}
        isRetrying={waiting.isFetching}
        loading={<OwnerOrderTableLoader />}
      >
        <OwnerOrderTable
          restaurantId={restaurantId}
          orders={waiting.data?.content ?? []}
          label="New orders"
        />
      </AsyncContent>
    </Card>
  );
}
