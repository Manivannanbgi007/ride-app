import { ExternalLink } from 'lucide-react';
import { Link } from 'react-router';

import { AuditHistory } from '@/components/business/AuditHistory';
import { OrderTimelineList } from '@/components/business/Order/OrderTimeline';
import { AsyncContent } from '@/components/common/AsyncContent';
import { Button, buttonStyles } from '@/components/common/Button';
import { Modal } from '@/components/common/Modal';
import { useOrder, useOrderAuditLogs } from '@/hooks/api/orderHooks';
import { routeTo } from '@/utils/constants';
import { ERROR_MESSAGES } from '@/utils/errorMessages';

interface OrderHistoryDialogProps {
  orderId: number;
  open: boolean;
  onClose: () => void;
}

/**
 * The history of one order, from the order list: every status it went through (when, and who changed it), and what
 * admins did to it (and why). Both are read only when the dialog is open, from the same APIs as the order's page.
 */
export function OrderHistoryDialog({ orderId, open, onClose }: OrderHistoryDialogProps) {
  const order = useOrder(orderId, open);
  const auditLogs = useOrderAuditLogs(orderId, open);

  return (
    <Modal
      open={open}
      onClose={onClose}
      title={`Order #${orderId} history`}
      size="lg"
      footer={
        <>
          <Link to={routeTo.orderDetails(orderId)} className={buttonStyles('outline', 'md')}>
            <ExternalLink aria-hidden="true" className="size-4" />
            Open order
          </Link>
          <Button onClick={onClose}>Close</Button>
        </>
      }
    >
      <div className="flex max-h-[60vh] flex-col gap-6 overflow-y-auto pr-1">
        <section aria-labelledby={`history-status-${orderId}`}>
          <h3 id={`history-status-${orderId}`} className="mb-3 text-sm font-bold">
            Status changes
          </h3>
          <AsyncContent
            isLoading={order.isPending}
            error={order.error}
            errorTitle={ERROR_MESSAGES.ORDER_LOAD_FAILED}
            onRetry={() => void order.refetch()}
            isRetrying={order.isFetching}
          >
            {order.data && <OrderTimelineList timeline={order.data.timeline} />}
          </AsyncContent>
        </section>
        <section aria-labelledby={`history-admin-${orderId}`}>
          <h3 id={`history-admin-${orderId}`} className="mb-3 text-sm font-bold">
            Admin actions
          </h3>
          <AsyncContent
            isLoading={auditLogs.isPending}
            error={auditLogs.error}
            errorTitle={ERROR_MESSAGES.AUDIT_LOAD_FAILED}
            onRetry={() => void auditLogs.refetch()}
            isRetrying={auditLogs.isFetching}
          >
            {auditLogs.data && <AuditHistory entries={auditLogs.data.content} />}
          </AsyncContent>
        </section>
      </div>
    </Modal>
  );
}
