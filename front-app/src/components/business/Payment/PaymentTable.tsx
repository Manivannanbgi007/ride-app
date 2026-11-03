import { Link } from 'react-router';

import { PaymentRecordStatusBadge } from '@/components/business/StatusBadges';
import { Table, type TableColumn } from '@/components/common/Table';
import type { Payment } from '@/types/payment';
import { DISPLAY_LOCALE, PAYMENT_METHOD_LABELS, routeTo } from '@/utils/constants';
import { formatDateTime } from '@/utils/dateUtils';
import { formatCurrency } from '@/utils/formatUtils';

const COLUMNS: readonly TableColumn<Payment>[] = [
  {
    header: 'Payment',
    render: (payment) => (
      <Link
        to={routeTo.paymentDetails(payment.id)}
        className="font-semibold text-brand-text hover:underline"
      >
        #{payment.id}
      </Link>
    ),
  },
  {
    header: 'Started',
    render: (payment) => (
      // '16 Oct, 11:03 pm' in the viewer's time zone; the full date and time in the tooltip
      <time
        dateTime={payment.createdAt}
        title={formatDateTime(payment.createdAt)}
        className="whitespace-nowrap"
      >
        {new Date(payment.createdAt).toLocaleString(DISPLAY_LOCALE, {
          day: 'numeric',
          month: 'short',
          hour: 'numeric',
          minute: '2-digit',
        })}
      </time>
    ),
  },
  {
    header: 'Order',
    render: (payment) => (
      <Link to={routeTo.orderDetails(payment.orderId)} className="hover:underline">
        #{payment.orderId}
      </Link>
    ),
  },
  {
    header: 'Customer',
    render: (payment) => (
      <Link to={routeTo.customerDetails(payment.customerId)} className="hover:underline">
        #{payment.customerId}
      </Link>
    ),
  },
  {
    header: 'Method',
    render: (payment) => (
      <div>
        <p className="font-medium whitespace-nowrap">{PAYMENT_METHOD_LABELS[payment.method]}</p>
        {payment.instrument && (
          <p className="max-w-48 truncate text-xs text-ink-soft" title={payment.instrument}>
            {payment.instrument}
          </p>
        )}
      </div>
    ),
  },
  {
    header: 'Amount',
    align: 'right',
    render: (payment) => (
      <div>
        <p className="font-semibold whitespace-nowrap tabular-nums">
          {formatCurrency(payment.amount, payment.currency)}
        </p>
        {payment.refundedAmount > 0 && (
          <p className="text-xs text-ink-soft">
            {formatCurrency(payment.refundedAmount, payment.currency)} refunded
          </p>
        )}
      </div>
    ),
  },
  {
    header: 'Status',
    render: (payment) => (
      <div className="flex flex-col items-start gap-1">
        <PaymentRecordStatusBadge status={payment.status} />
        {payment.failureReason && (
          <span className="max-w-48 truncate text-xs text-ink-soft" title={payment.failureReason}>
            {payment.failureReason}
          </span>
        )}
      </div>
    ),
  },
];

interface PaymentTableProps {
  payments: readonly Payment[];
  isUpdating?: boolean;
}

/** Payments as a table; the number opens the payment, the order and customer numbers their pages. */
export function PaymentTable({ payments, isUpdating }: PaymentTableProps) {
  return (
    <Table
      compact
      caption="Payments"
      columns={COLUMNS}
      rows={payments}
      rowKey={(payment) => payment.id}
      isUpdating={isUpdating}
    />
  );
}
