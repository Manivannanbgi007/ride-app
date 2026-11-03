import { toApiError } from '@/api/apiErrorHandler';
import { OrderItemsCard, OrderTimeline } from '@/components/business/Order';
import {
  DishPhoto,
  OWNER_ORDER_MESSAGES,
  OwnerOrderActions,
  OwnerOrderInfoCard,
  useDishLooks,
} from '@/components/business/OwnerOrders';
import { OrderStatusBadge } from '@/components/business/StatusBadges';
import { AsyncContent } from '@/components/common/AsyncContent';
import { BackLink } from '@/components/common/BackLink';
import { EmptyState } from '@/components/common/EmptyState';
import { PageHeader } from '@/components/common/PageHeader';
import { useOwnerOrder } from '@/hooks/api/ownerHooks';
import { useIdParam } from '@/hooks/useIdParam';
import { useOwnerRestaurant } from '@/layouts/owner/ownerRestaurantContext';
import { NotFoundPage } from '@/pages/NotFound/NotFoundPage';
import { ORDER_STATUS_LABELS, OWNER_ROUTES } from '@/utils/constants';
import { formatDateTime } from '@/utils/dateUtils';
import { ERROR_MESSAGES } from '@/utils/errorMessages';

/** "Order #42": what to cook, for whom and where it goes, the timeline, and the kitchen's next step. */
export default function OwnerOrderDetailsPage() {
  const orderId = useIdParam('orderId');
  return orderId === null ? <NotFoundPage /> : <OwnerOrderDetails orderId={orderId} />;
}

function OwnerOrderDetails({ orderId }: { orderId: number }) {
  const { restaurantId } = useOwnerRestaurant();
  // live: a change made elsewhere (the customer cancels, a delivery partner picks it up) shows without reloading
  const order = useOwnerOrder(restaurantId, orderId, true);
  // The dishes' photos, from the restaurant's menu
  const looks = useDishLooks(restaurantId);
  // Only a first load that failed replaces the page: a failed refresh keeps the order on screen
  const loadError = order.data ? null : order.error;
  const isMissing = loadError !== null && toApiError(loadError).kind === 'notFound';

  return (
    <div className="flex flex-col gap-6">
      <BackLink to={OWNER_ROUTES.orders}>Back to orders</BackLink>
      {isMissing ? (
        <EmptyState
          title={OWNER_ORDER_MESSAGES.NOT_FOUND_TITLE}
          message={OWNER_ORDER_MESSAGES.NOT_FOUND_MESSAGE}
        />
      ) : (
        <AsyncContent
          isLoading={order.isPending}
          error={loadError}
          errorTitle={ERROR_MESSAGES.ORDER_LOAD_FAILED}
          onRetry={() => void order.refetch()}
          isRetrying={order.isFetching}
        >
          {order.data && (
            <>
              <PageHeader
                title={`Order #${order.data.id}`}
                subtitle={`Placed ${formatDateTime(order.data.createdAt)}`}
                actions={
                  <div className="flex flex-wrap items-center gap-3">
                    <OrderStatusBadge status={order.data.status} />
                    <OwnerOrderActions
                      orderId={order.data.id}
                      status={order.data.status}
                      size="md"
                    />
                  </div>
                }
              />
              {/* Read out when the status changes (the owner acts here, or the order moves elsewhere) */}
              <p role="status" className="sr-only">
                Order #{order.data.id} is now {ORDER_STATUS_LABELS[order.data.status]}.
              </p>
              <div className="grid gap-6 xl:grid-cols-3">
                <div className="flex flex-col gap-6 xl:col-span-2">
                  <OrderItemsCard
                    order={order.data}
                    itemPhoto={(line) => (
                      <DishPhoto
                        look={looks.get(line.menuItemId)}
                        className="size-11 shrink-0 rounded-xl"
                      />
                    )}
                  />
                  <OrderTimeline timeline={order.data.timeline} />
                </div>
                <div className="flex flex-col gap-6">
                  <OwnerOrderInfoCard order={order.data} />
                </div>
              </div>
            </>
          )}
        </AsyncContent>
      )}
    </div>
  );
}
