import { Undo2 } from 'lucide-react';
import { useState } from 'react';

import { AuditHistory } from '@/components/business/AuditHistory';
import {
  PaymentEventsCard,
  PaymentOverviewCard,
  RefundDialog,
  RefundTable,
} from '@/components/business/Payment';
import { AsyncContent } from '@/components/common/AsyncContent';
import { BackLink } from '@/components/common/BackLink';
import { Button } from '@/components/common/Button';
import { Card } from '@/components/common/Card';
import { EmptyState } from '@/components/common/EmptyState';
import { PageHeader } from '@/components/common/PageHeader';
import { useToast } from '@/context/ToastContext';
import { useOrder } from '@/hooks/api/orderHooks';
import { usePayment, usePaymentAuditLogs, useRefundPayment } from '@/hooks/api/paymentHooks';
import { useIdParam } from '@/hooks/useIdParam';
import { NotFoundPage } from '@/pages/NotFound/NotFoundPage';
import { REFUNDABLE_ORDER_STATUSES, type Payment } from '@/types/payment';
import { ORDER_STATUS_LABELS, ROUTES } from '@/utils/constants';
import { formatDateTime } from '@/utils/dateUtils';
import { ERROR_MESSAGES } from '@/utils/errorMessages';
import { formatCurrency } from '@/utils/formatUtils';
import { toPaise } from '@/utils/validationUtils';

/** One payment: its details, refunds, the messages it sent, its history, and "Refund" when allowed. */
export default function PaymentDetailsPage() {
  const paymentId = useIdParam('paymentId');
  return paymentId === null ? <NotFoundPage /> : <PaymentDetailsView paymentId={paymentId} />;
}

function PaymentDetailsView({ paymentId }: { paymentId: number }) {
  const details = usePayment(paymentId);
  const auditLogs = usePaymentAuditLogs(paymentId);

  return (
    <div className="flex flex-col gap-6">
      <BackLink to={ROUTES.payments}>Back to payments</BackLink>
      <AsyncContent
        isLoading={details.isPending}
        error={details.error}
        errorTitle={ERROR_MESSAGES.PAYMENT_LOAD_FAILED}
        onRetry={() => void details.refetch()}
        isRetrying={details.isFetching}
      >
        {details.data && (
          <>
            <PageHeader
              title={`Payment #${details.data.payment.id}`}
              subtitle={`${formatCurrency(details.data.payment.amount, details.data.payment.currency)} for order #${details.data.payment.orderId}, started ${formatDateTime(details.data.payment.createdAt)}`}
              actions={<RefundAction payment={details.data.payment} />}
            />
            <div className="grid gap-6 xl:grid-cols-3">
              <div className="flex flex-col gap-6 xl:col-span-2">
                <PaymentOverviewCard payment={details.data.payment} />
                <Card title="Refunds">
                  {details.data.refunds.length === 0 ? (
                    <EmptyState icon={Undo2} title="Nothing refunded" />
                  ) : (
                    <RefundTable refunds={details.data.refunds} />
                  )}
                </Card>
              </div>
              <div className="flex flex-col gap-6">
                <PaymentEventsCard events={details.data.events} />
                <Card title="Change history" subtitle="Admin actions on this payment">
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
          </>
        )}
      </AsyncContent>
    </div>
  );
}

/**
 * "Refund", offered for a successful payment with money left to refund. payment-service also requires the order
 * to be cancelled, rejected or delivered: the order is looked up to say so before trying.
 */
function RefundAction({ payment }: { payment: Payment }) {
  const { showToast } = useToast();
  const order = useOrder(payment.orderId);
  const refundPayment = useRefundPayment();
  const [isDialogOpen, setDialogOpen] = useState(false);
  const refundable = (toPaise(payment.amount) - toPaise(payment.refundedAmount)) / 100;

  if (payment.status !== 'SUCCESS' || refundable <= 0) {
    return null;
  }
  // While the order is unknown (loading, or order-service down) the server decides
  const orderBlocks =
    order.data !== undefined && !REFUNDABLE_ORDER_STATUSES.includes(order.data.status);

  const refund = async (amount: number | null, reason: string, idempotencyKey: string) => {
    const result = await refundPayment.mutateAsync({
      paymentId: payment.id,
      amount,
      reason,
      idempotencyKey,
    });
    const money = formatCurrency(result.amount, payment.currency);
    if (result.status === 'SUCCESS') {
      showToast(`${money} refunded to the customer.`);
    } else if (result.status === 'FAILED') {
      showToast(
        `The refund of ${money} failed: ${result.failureReason ?? 'the provider refused it'}. Nothing was refunded; you can try again.`,
        'error',
      );
    } else {
      showToast(`The refund of ${money} is being processed.`, 'info');
    }
  };

  return (
    <div className="flex flex-col items-end gap-2">
      <Button
        variant="danger"
        disabled={orderBlocks}
        onClick={() => setDialogOpen(true)}
        leftIcon={<Undo2 aria-hidden="true" className="size-4" />}
      >
        Refund
      </Button>
      {orderBlocks && order.data && (
        <p className="max-w-xs text-right text-xs text-ink-soft">
          Order #{order.data.id} is {ORDER_STATUS_LABELS[order.data.status].toLowerCase()}: it must
          be cancelled, rejected or delivered before it can be refunded.
        </p>
      )}
      {isDialogOpen && (
        <RefundDialog
          payment={payment}
          refundable={refundable}
          onSubmit={refund}
          onClose={() => setDialogOpen(false)}
        />
      )}
    </div>
  );
}
