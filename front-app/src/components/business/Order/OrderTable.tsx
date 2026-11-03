import { ReceiptText, Store } from 'lucide-react';
import { Link } from 'react-router';

import { OrderActionsProvider } from '@/components/business/Order/OrderActionsProvider';
import { OrderActionsMenu, OrderStatusMenu } from '@/components/business/Order/OrderStatusMenu';
import { PaymentMethodLabel } from '@/components/business/Payment/PaymentMethodLabel';
import { ReviewerAvatar } from '@/components/business/Review/ReviewerAvatar';
import { PaymentStatusBadge } from '@/components/business/StatusBadges';
import { Table, type TableColumn } from '@/components/common/Table';
import type { OrderSummary } from '@/types/order';
import { DEFAULT_CURRENCY, routeTo } from '@/utils/constants';
import { formatInstantDate, formatTime } from '@/utils/dateUtils';
import { formatCurrency } from '@/utils/formatUtils';

const COLUMNS: readonly TableColumn<OrderSummary>[] = [
  {
    header: 'Order',
    render: (order) => (
      <div className="flex items-center gap-3">
        <span
          aria-hidden="true"
          className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-brand-50 text-brand-text transition-transform duration-200 motion-safe:[tr:hover_&]:scale-110"
        >
          <ReceiptText className="size-5" />
        </span>
        <div className="min-w-0">
          <Link
            to={routeTo.orderDetails(order.id)}
            className="text-[15px] font-extrabold tracking-tight text-brand-text hover:underline"
          >
            #{order.id}
          </Link>
          <p className="flex max-w-52 items-center gap-1 text-xs text-ink-soft">
            <Store aria-hidden="true" className="size-3.5 shrink-0" />
            <span className="truncate" title={order.restaurantName}>
              {order.restaurantName}
            </span>
          </p>
        </div>
      </div>
    ),
  },
  {
    header: 'Customer',
    render: (order) => (
      <Link
        to={routeTo.customerDetails(order.customerId)}
        className="group/person flex items-center gap-2.5"
      >
        <ReviewerAvatar name={order.customerName ?? ''} size="none" className="size-9 text-xs" />
        <span className="min-w-0">
          <span className="block max-w-44 truncate font-medium group-hover/person:text-brand-text group-hover/person:underline">
            {order.customerName ?? `Customer #${order.customerId}`}
          </span>
          {order.customerName && (
            <span className="block text-xs text-ink-soft">#{order.customerId}</span>
          )}
        </span>
      </Link>
    ),
  },
  {
    header: 'Placed',
    render: (order) => (
      <time dateTime={order.createdAt} className="block whitespace-nowrap">
        <span className="block font-medium">{formatInstantDate(order.createdAt)}</span>
        <span className="block text-xs text-ink-soft">{formatTime(order.createdAt)}</span>
      </time>
    ),
  },
  {
    header: 'Payment',
    render: (order) => (
      <PaymentMethodLabel method={order.paymentMethod}>
        <PaymentStatusBadge status={order.paymentStatus} />
      </PaymentMethodLabel>
    ),
  },
  {
    header: 'Total',
    align: 'right',
    render: (order) => (
      <div className="whitespace-nowrap">
        <p className="font-bold tabular-nums">
          {formatCurrency(order.totalAmount, DEFAULT_CURRENCY)}
        </p>
        <p className="text-xs text-ink-soft">
          {order.itemCount} {order.itemCount === 1 ? 'item' : 'items'}
        </p>
      </div>
    ),
  },
  { header: 'Status', render: (order) => <OrderStatusMenu order={order} /> },
  // Centred: the header sits right above the buttons
  {
    header: 'Actions',
    align: 'center',
    className: 'w-px whitespace-nowrap',
    render: (order) => <OrderActionsMenu order={order} />,
  },
];

interface OrderTableProps {
  orders: readonly OrderSummary[];
  isUpdating?: boolean;
}

/**
 * Orders as a table: the order number (it opens the order) with its restaurant under it, the customer with their
 * initials (it opens the customer), when, how it is paid, the total with the number of items, and the status, which
 * opens the order's actions.
 */
export function OrderTable({ orders, isUpdating }: OrderTableProps) {
  return (
    <OrderActionsProvider>
      <Table
        caption="Orders"
        columns={COLUMNS}
        rows={orders}
        rowKey={(order) => order.id}
        isUpdating={isUpdating}
      />
    </OrderActionsProvider>
  );
}
