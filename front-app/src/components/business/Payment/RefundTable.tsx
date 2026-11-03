import { Link } from 'react-router';

import { RefundStatusBadge } from '@/components/business/StatusBadges';
import { Table, type TableColumn } from '@/components/common/Table';
import type { Refund } from '@/types/payment';
import { DEFAULT_CURRENCY, routeTo } from '@/utils/constants';
import { formatDateTime } from '@/utils/dateUtils';
import { formatCurrency } from '@/utils/formatUtils';

const COLUMNS: readonly TableColumn<Refund>[] = [
  {
    header: 'Refund',
    render: (refund) => (
      <Link
        to={routeTo.refundDetails(refund.id)}
        className="font-semibold text-brand-text hover:underline"
      >
        #{refund.id}
      </Link>
    ),
  },
  { header: 'Requested', render: (refund) => formatDateTime(refund.requestedAt) },
  {
    header: 'Payment',
    render: (refund) => (
      <Link to={routeTo.paymentDetails(refund.paymentId)} className="hover:underline">
        #{refund.paymentId}
      </Link>
    ),
  },
  {
    header: 'Order',
    render: (refund) => (
      <Link to={routeTo.orderDetails(refund.orderId)} className="hover:underline">
        #{refund.orderId}
      </Link>
    ),
  },
  {
    header: 'Amount',
    align: 'right',
    render: (refund) => (
      <span className="font-semibold">{formatCurrency(refund.amount, DEFAULT_CURRENCY)}</span>
    ),
  },
  {
    header: 'Asked by',
    render: (refund) =>
      refund.requestedByType === 'SYSTEM' ? 'System' : `Admin #${refund.requestedByUserId ?? '?'}`,
  },
  {
    header: 'Status',
    render: (refund) => (
      <div className="flex flex-col items-start gap-1">
        <RefundStatusBadge status={refund.status} />
        {refund.failureReason && (
          <span className="max-w-48 truncate text-xs text-ink-soft" title={refund.failureReason}>
            {refund.failureReason}
          </span>
        )}
      </div>
    ),
  },
];

interface RefundTableProps {
  refunds: readonly Refund[];
  isUpdating?: boolean;
}

/** Refunds as a table, newest first; links to the refund, its payment and its order. */
export function RefundTable({ refunds, isUpdating }: RefundTableProps) {
  return (
    <Table
      caption="Refunds"
      columns={COLUMNS}
      rows={refunds}
      rowKey={(refund) => refund.id}
      isUpdating={isUpdating}
    />
  );
}
