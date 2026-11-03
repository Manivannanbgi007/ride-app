import { XCircle } from 'lucide-react';
import { useState } from 'react';
import { Link } from 'react-router';

import { toApiError } from '@/api/apiErrorHandler';
import { PriceSummary } from '@/components/business/Cart';
import {
  DeliveryTracking,
  OrderHistory,
  OrderStatusPill,
  OrderTrackingHero,
} from '@/components/business/MyOrders';
import { RateOrder } from '@/components/business/Review';
import { FoodTypeMark } from '@/components/business/Storefront';
import { Alert } from '@/components/common/Alert';
import { AsyncContent } from '@/components/common/AsyncContent';
import { BackLink } from '@/components/common/BackLink';
import { Button } from '@/components/common/Button';
import { Card } from '@/components/common/Card';
import { DescriptionList } from '@/components/common/DescriptionList';
import { SkeletonLoader } from '@/components/common/Loader';
import { PageHeader } from '@/components/common/PageHeader';
import { ReasonDialog } from '@/components/common/ReasonDialog';
import { useToast } from '@/context/ToastContext';
import {
  useCancelMyOrder,
  useMyOrder,
  useOrderRefreshMs,
  useOrderTracking,
} from '@/hooks/api/myOrderHooks';
import { useIdParam } from '@/hooks/useIdParam';
import { NotFoundPage } from '@/pages/NotFound/NotFoundPage';
import type { FoodType } from '@/types/catalog';
import type { OrderDetails } from '@/types/order';
import { CUSTOMER_CANCELLABLE_STATUSES } from '@/types/shopping';
import {
  CUSTOMER_ORDER_STATUS_LABELS,
  CUSTOMER_ROUTES,
  customerRouteTo,
  PAYMENT_METHOD_LABELS,
  PAYMENT_STATUS_LABELS,
} from '@/utils/constants';
import { formatDateTime } from '@/utils/dateUtils';
import { ERROR_MESSAGES } from '@/utils/errorMessages';
import { formatCurrency } from '@/utils/formatUtils';
import { addressLines, hasDelivery, isFinishedOrder, paymentDeadline } from '@/utils/shoppingUtils';

/**
 * One of the customer's orders: where it stands (step by step), its delivery once the food is ready, what was
 * ordered, the price, and everything that happened. Before the restaurant accepts, the customer can cancel.
 */
export default function OrderPage() {
  const orderId = useIdParam('orderId');
  return orderId === null ? <NotFoundPage /> : <OrderView orderId={orderId} />;
}

function OrderView({ orderId }: { orderId: number }) {
  const refreshMs = useOrderRefreshMs();
  const order = useMyOrder(orderId, (current) =>
    current && !isFinishedOrder(current.status) ? refreshMs : false,
  );

  return (
    <div className="flex flex-col gap-6">
      <BackLink to={CUSTOMER_ROUTES.myOrders}>My orders</BackLink>
      <AsyncContent
        isLoading={order.isPending}
        error={order.error}
        errorTitle={ERROR_MESSAGES.MY_ORDER_LOAD_FAILED}
        onRetry={() => void order.refetch()}
        isRetrying={order.isFetching}
        loading={<SkeletonLoader lines={8} className="rounded-2xl bg-surface p-6" />}
      >
        {order.data && <OrderContents order={order.data} />}
      </AsyncContent>
    </div>
  );
}

function OrderContents({ order }: { order: OrderDetails }) {
  const ended = order.status === 'CANCELLED' || order.status === 'REJECTED';
  return (
    <>
      <PageHeader
        title={`Order #${order.id}`}
        subtitle={`${order.restaurantName} · ${formatDateTime(order.createdAt)}`}
        actions={
          <div className="flex flex-wrap items-center gap-3">
            <OrderStatusPill status={order.status} />
            <CancelOrder order={order} />
          </div>
        }
      />
      <StatusMessage order={order} />
      {!ended && order.status !== 'PAYMENT_PENDING' && <OrderTrackingHero order={order} />}
      {hasDelivery(order.status) && <DeliveryCard order={order} />}
      <RateOrder order={order} />
      <div className="grid gap-6 lg:grid-cols-[1fr_24rem]">
        <div className="flex flex-col gap-6">
          <Card title="What you ordered">
            <ul className="divide-y divide-line">
              {order.items.map((line) => (
                <li key={line.menuItemId} className="flex items-center gap-3 py-3 text-sm">
                  {line.foodType && <FoodTypeMark foodType={line.foodType as FoodType} />}
                  <span className="flex-1">
                    {line.quantity} × {line.name}
                  </span>
                  <span className="font-semibold">
                    {formatCurrency(line.lineTotal, order.price.currency)}
                  </span>
                </li>
              ))}
            </ul>
            {order.notes && (
              <p className="mt-4 text-sm text-ink-soft">Your note: “{order.notes}”</p>
            )}
          </Card>
          <Card title="History">
            <OrderHistory timeline={order.timeline} />
          </Card>
        </div>
        <div className="flex flex-col gap-6">
          <Card title="Price">
            <PriceSummary price={order.price} couponCode={order.couponCode} />
          </Card>
          <Card title="Delivery and payment">
            <DescriptionList
              columns={1}
              items={[
                {
                  label: 'Deliver to',
                  value: order.deliveryAddress && addressLines(order.deliveryAddress).join(', '),
                },
                { label: 'Payment', value: PAYMENT_METHOD_LABELS[order.paymentMethod] },
                { label: 'Payment status', value: PAYMENT_STATUS_LABELS[order.paymentStatus] },
              ]}
            />
          </Card>
        </div>
      </div>
      <Link
        to={CUSTOMER_ROUTES.restaurants}
        className="text-sm font-semibold text-brand-text hover:underline"
      >
        Order something else
      </Link>
    </>
  );
}

/** Only before the restaurant accepts (order-service refuses later: the kitchen may have started). */
function CancelOrder({ order }: { order: OrderDetails }) {
  const [open, setOpen] = useState(false);
  const cancel = useCancelMyOrder();
  const { showToast } = useToast();

  if (!CUSTOMER_CANCELLABLE_STATUSES.includes(order.status)) {
    return null;
  }
  const paidOnline = order.paymentStatus === 'PAID';

  const submit = async (reason: string) => {
    await cancel.mutateAsync({ orderId: order.id, reason });
    showToast(
      paidOnline
        ? `Order #${order.id} is cancelled. What you paid is refunded.`
        : `Order #${order.id} is cancelled.`,
    );
  };

  return (
    <>
      <Button
        variant="outline"
        size="sm"
        onClick={() => setOpen(true)}
        leftIcon={<XCircle aria-hidden="true" className="size-4" />}
      >
        Cancel order
      </Button>
      <ReasonDialog
        open={open}
        title={`Cancel order #${order.id}?`}
        description={
          paidOnline
            ? 'The restaurant has not accepted it yet. What you paid online is refunded, and a coupon can be used again.'
            : 'The restaurant has not accepted it yet. A coupon you used can be used again.'
        }
        confirmLabel="Cancel order"
        tone="danger"
        reasonLabel="Why are you cancelling?"
        placeholder="Ordered by mistake, changed my mind..."
        requiredMessage="Please tell us why (one line is enough)."
        onSubmit={submit}
        onClose={() => setOpen(false)}
      />
    </>
  );
}

/** The delivery, from the moment the food is ready; refreshed while it is on its way. */
function DeliveryCard({ order }: { order: OrderDetails }) {
  const active = !isFinishedOrder(order.status);
  const refreshMs = useOrderRefreshMs();
  const tracking = useOrderTracking(order.id, true, active ? refreshMs : false);
  // 404 for a moment after "ready": delivery-service creates the delivery from the event
  const notStarted = tracking.error !== null && toApiError(tracking.error).kind === 'notFound';

  return (
    <Card title="Delivery">
      {notStarted ? (
        <p className="text-sm text-ink-soft">
          The delivery starts in a moment, now that the food is ready.
        </p>
      ) : (
        <AsyncContent
          isLoading={tracking.isPending}
          error={tracking.error}
          errorTitle={ERROR_MESSAGES.TRACKING_LOAD_FAILED}
          onRetry={() => void tracking.refetch()}
          isRetrying={tracking.isFetching}
          loading={<SkeletonLoader lines={3} />}
        >
          {tracking.data && <DeliveryTracking tracking={tracking.data} />}
        </AsyncContent>
      )}
    </Card>
  );
}

/** What happens next, in a sentence (and "Pay now" while the payment is missing). */
function StatusMessage({ order }: { order: OrderDetails }) {
  switch (order.status) {
    case 'PAYMENT_PENDING':
      return (
        <Alert tone="warning" title="Waiting for your payment">
          Pay by {formatDateTime(paymentDeadline(order).toISOString())}, or the order is cancelled.{' '}
          <Link to={customerRouteTo.orderPayment(order.id)} className="font-semibold underline">
            Pay now
          </Link>
        </Alert>
      );
    case 'PLACED':
      return (
        <Alert tone="info" title="Order placed">
          The restaurant will confirm your order in a moment. This page updates by itself.
        </Alert>
      );
    case 'CANCELLED':
    case 'REJECTED':
      return (
        <Alert tone="error" title={CUSTOMER_ORDER_STATUS_LABELS[order.status]}>
          {order.cancellationReason ?? 'This order will not be delivered.'}
          {order.paymentStatus === 'PAID' || order.paymentStatus === 'REFUNDED'
            ? ' What you paid online is refunded.'
            : ''}
        </Alert>
      );
    case 'DELIVERED':
      return (
        <Alert tone="success" title="Delivered">
          Enjoy your meal!
        </Alert>
      );
    default:
      return null;
  }
}
