import { Link } from 'react-router';

import { RefundStatusBadge } from '@/components/business/StatusBadges';
import { Alert } from '@/components/common/Alert';
import { AsyncContent } from '@/components/common/AsyncContent';
import { BackLink } from '@/components/common/BackLink';
import { Card } from '@/components/common/Card';
import { DescriptionList } from '@/components/common/DescriptionList';
import { PageHeader } from '@/components/common/PageHeader';
import { useRefund } from '@/hooks/api/paymentHooks';
import { useIdParam } from '@/hooks/useIdParam';
import { NotFoundPage } from '@/pages/NotFound/NotFoundPage';
import { DEFAULT_CURRENCY, ROUTES, routeTo } from '@/utils/constants';
import { formatDateTime } from '@/utils/dateUtils';
import { ERROR_MESSAGES } from '@/utils/errorMessages';
import { formatCurrency } from '@/utils/formatUtils';

/** One refund: how much, why, who asked, and how it ended. */
export default function RefundDetailsPage() {
  const refundId = useIdParam('refundId');
  return refundId === null ? <NotFoundPage /> : <RefundDetailsView refundId={refundId} />;
}

function RefundDetailsView({ refundId }: { refundId: number }) {
  const refund = useRefund(refundId);

  return (
    <div className="flex flex-col gap-6">
      <BackLink to={ROUTES.refunds}>Back to refunds</BackLink>
      <AsyncContent
        isLoading={refund.isPending}
        error={refund.error}
        errorTitle={ERROR_MESSAGES.REFUND_LOAD_FAILED}
        onRetry={() => void refund.refetch()}
      >
        {refund.data && (
          <>
            <PageHeader
              title={`Refund #${refund.data.id}`}
              subtitle={`${formatCurrency(refund.data.amount, DEFAULT_CURRENCY)} of payment #${refund.data.paymentId}`}
            />
            <Card actions={<RefundStatusBadge status={refund.data.status} />} title="Refund">
              <div className="flex flex-col gap-5">
                {refund.data.failureReason && (
                  <Alert tone="error" title="The refund failed">
                    {refund.data.failureReason}. Nothing was refunded.
                  </Alert>
                )}
                <DescriptionList
                  items={[
                    {
                      label: 'Amount',
                      value: formatCurrency(refund.data.amount, DEFAULT_CURRENCY),
                    },
                    { label: 'Reason', value: refund.data.reason },
                    {
                      label: 'Asked by',
                      value:
                        refund.data.requestedByType === 'SYSTEM'
                          ? 'The system (automatic)'
                          : `Admin #${refund.data.requestedByUserId ?? '?'}`,
                    },
                    {
                      label: 'Payment',
                      value: (
                        <Link
                          to={routeTo.paymentDetails(refund.data.paymentId)}
                          className="text-brand-text hover:underline"
                        >
                          #{refund.data.paymentId}
                        </Link>
                      ),
                    },
                    {
                      label: 'Order',
                      value: (
                        <Link
                          to={routeTo.orderDetails(refund.data.orderId)}
                          className="text-brand-text hover:underline"
                        >
                          #{refund.data.orderId}
                        </Link>
                      ),
                    },
                    { label: 'Provider reference', value: refund.data.providerReference },
                    { label: 'Requested', value: formatDateTime(refund.data.requestedAt) },
                    {
                      label: 'Finished',
                      value: refund.data.completedAt
                        ? formatDateTime(refund.data.completedAt)
                        : 'Not yet',
                    },
                  ]}
                />
              </div>
            </Card>
          </>
        )}
      </AsyncContent>
    </div>
  );
}
