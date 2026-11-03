import { CreditCard, XCircle } from 'lucide-react';
import { useState } from 'react';
import { useNavigate } from 'react-router';

import { AuditHistory } from '@/components/business/AuditHistory';
import { OrderItemsCard, OrderOverviewCard, OrderTimeline } from '@/components/business/Order';
import { AsyncContent } from '@/components/common/AsyncContent';
import { BackLink } from '@/components/common/BackLink';
import { Button } from '@/components/common/Button';
import { Card } from '@/components/common/Card';
import { PageHeader } from '@/components/common/PageHeader';
import { ReasonDialog } from '@/components/common/ReasonDialog';
import { useToast } from '@/context/ToastContext';
import { useCancelOrder, useOrder, useOrderAuditLogs } from '@/hooks/api/orderHooks';
import { useIdParam } from '@/hooks/useIdParam';
import { NotFoundPage } from '@/pages/NotFound/NotFoundPage';
import { useAppDispatch } from '@/store/hooks';
import { paymentFiltersReplaced } from '@/store/slices/paymentListSlice';
import { ADMIN_CANCELLABLE_STATUSES } from '@/types/order';
import { ROUTES } from '@/utils/constants';
import { formatDateTime } from '@/utils/dateUtils';
import { ERROR_MESSAGES } from '@/utils/errorMessages';

/** "Order Detail": one order with its items, timeline and change history; an admin can cancel it. */
export default function OrderDetailsPage() {
  const orderId = useIdParam('orderId');
  return orderId === null ? <NotFoundPage /> : <OrderDetails orderId={orderId} />;
}

function OrderDetails({ orderId }: { orderId: number }) {
  const order = useOrder(orderId);
  const auditLogs = useOrderAuditLogs(orderId);
  const cancelOrder = useCancelOrder();
  const { showToast } = useToast();
  const [isCancelDialogOpen, setCancelDialogOpen] = useState(false);
  const dispatch = useAppDispatch();
  const navigate = useNavigate();

  const showPayments = () => {
    dispatch(paymentFiltersReplaced({ orderId }));
    void navigate(ROUTES.payments);
  };

  const cancel = async (reason: string) => {
    await cancelOrder.mutateAsync({ orderId, reason });
    showToast(`Order #${orderId} was cancelled.`);
  };

  return (
    <div className="flex flex-col gap-6">
      <BackLink to={ROUTES.orders}>Back to orders</BackLink>
      <AsyncContent
        isLoading={order.isPending}
        error={order.error}
        errorTitle={ERROR_MESSAGES.ORDER_LOAD_FAILED}
        onRetry={() => void order.refetch()}
        isRetrying={order.isFetching}
      >
        {order.data && (
          <>
            <PageHeader
              title={`Order #${order.data.id}`}
              subtitle={`Placed ${formatDateTime(order.data.createdAt)} at ${order.data.restaurantName}`}
              actions={
                <div className="flex flex-wrap gap-3">
                  {order.data.paymentMethod !== 'CASH_ON_DELIVERY' && (
                    <Button
                      variant="outline"
                      onClick={showPayments}
                      leftIcon={<CreditCard aria-hidden="true" className="size-4" />}
                    >
                      Payments
                    </Button>
                  )}
                  {ADMIN_CANCELLABLE_STATUSES.includes(order.data.status) && (
                    <Button
                      variant="danger"
                      onClick={() => setCancelDialogOpen(true)}
                      leftIcon={<XCircle aria-hidden="true" className="size-4" />}
                    >
                      Cancel order
                    </Button>
                  )}
                </div>
              }
            />
            <div className="grid gap-6 xl:grid-cols-3">
              <div className="flex flex-col gap-6 xl:col-span-2">
                <OrderItemsCard order={order.data} />
                <OrderTimeline timeline={order.data.timeline} />
              </div>
              <div className="flex flex-col gap-6">
                <OrderOverviewCard order={order.data} />
                <Card title="Change history" subtitle="Admin actions on this order">
                  <AsyncContent
                    isLoading={auditLogs.isPending}
                    error={auditLogs.error}
                    errorTitle={ERROR_MESSAGES.AUDIT_LOAD_FAILED}
                    onRetry={() => void auditLogs.refetch()}
                  >
                    {auditLogs.data && <AuditHistory entries={auditLogs.data.content} />}
                  </AsyncContent>
                </Card>
              </div>
            </div>
            <ReasonDialog
              open={isCancelDialogOpen}
              title={`Cancel order #${orderId}?`}
              description="The customer and the restaurant are told, and an order paid online is refunded in full. This cannot be undone."
              confirmLabel="Cancel order"
              tone="danger"
              onSubmit={cancel}
              onClose={() => setCancelDialogOpen(false)}
            />
          </>
        )}
      </AsyncContent>
    </div>
  );
}
