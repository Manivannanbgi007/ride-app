import { Link } from 'react-router';

import { RedemptionStatusBadge } from '@/components/business/StatusBadges';
import { Table, type TableColumn } from '@/components/common/Table';
import type { Redemption } from '@/types/coupon';
import { DEFAULT_CURRENCY, routeTo } from '@/utils/constants';
import { formatDateTime } from '@/utils/dateUtils';
import { formatCurrency } from '@/utils/formatUtils';

const COLUMNS: readonly TableColumn<Redemption>[] = [
  { header: 'Reserved', render: (use) => formatDateTime(use.reservedAt) },
  {
    header: 'Customer',
    render: (use) => (
      <Link to={routeTo.customerDetails(use.customerId)} className="hover:underline">
        #{use.customerId}
      </Link>
    ),
  },
  {
    header: 'Order',
    render: (use) =>
      use.orderId === null ? (
        'None yet'
      ) : (
        <Link to={routeTo.orderDetails(use.orderId)} className="text-brand-text hover:underline">
          #{use.orderId}
        </Link>
      ),
  },
  {
    header: 'Items total',
    align: 'right',
    render: (use) => formatCurrency(use.orderAmount, DEFAULT_CURRENCY),
  },
  {
    header: 'Discount',
    align: 'right',
    render: (use) => formatCurrency(use.discountAmount, DEFAULT_CURRENCY),
  },
  {
    header: 'Status',
    render: (use) => (
      <div className="flex flex-col items-start gap-1">
        <RedemptionStatusBadge status={use.status} />
        {use.releaseReason && <span className="text-xs text-ink-soft">{use.releaseReason}</span>}
      </div>
    ),
  },
];

/** The uses of a coupon, newest first. */
export function RedemptionTable({
  uses,
  isUpdating,
}: {
  uses: readonly Redemption[];
  isUpdating?: boolean;
}) {
  return (
    <Table
      caption="Uses of the coupon"
      columns={COLUMNS}
      rows={uses}
      rowKey={(use) => use.id}
      isUpdating={isUpdating}
    />
  );
}
