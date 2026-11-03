import { Link } from 'react-router';

import { OrderStatusBadge, PaymentStatusBadge } from '@/components/business/StatusBadges';
import { Alert } from '@/components/common/Alert';
import { Card } from '@/components/common/Card';
import { DescriptionList } from '@/components/common/DescriptionList';
import type { DeliveryAddress, OrderDetails } from '@/types/order';
import { ACTOR_TYPE_LABELS, PAYMENT_METHOD_LABELS, routeTo } from '@/utils/constants';
import { formatDateTime } from '@/utils/dateUtils';

/** Who ordered where, how it is paid, and where it goes. */
export function OrderOverviewCard({ order }: { order: OrderDetails }) {
  return (
    <Card title="Overview">
      <div className="flex flex-col gap-5">
        {order.cancellationReason && (
          <Alert
            tone="error"
            title={`Cancelled${order.cancelledBy ? ` by ${ACTOR_TYPE_LABELS[order.cancelledBy].toLowerCase()}` : ''}`}
          >
            {order.cancellationReason}
          </Alert>
        )}
        <DescriptionList
          columns={1}
          items={[
            { label: 'Status', value: <OrderStatusBadge status={order.status} /> },
            {
              label: 'Payment',
              value: (
                <span className="flex items-center gap-2">
                  {PAYMENT_METHOD_LABELS[order.paymentMethod]}
                  <PaymentStatusBadge status={order.paymentStatus} />
                </span>
              ),
            },
            {
              label: 'Customer',
              value: (
                <Link
                  to={routeTo.customerDetails(order.customerId)}
                  className="text-brand-text hover:underline"
                >
                  {order.customerName ?? `Customer #${order.customerId}`}
                </Link>
              ),
            },
            { label: 'Phone', value: order.customerPhone },
            { label: 'Restaurant', value: `${order.restaurantName} (#${order.restaurantId})` },
            { label: 'Deliver to', value: formatAddress(order.deliveryAddress) },
            { label: 'Coupon', value: order.couponCode },
            { label: 'Customer note', value: order.notes },
            { label: 'Last change', value: formatDateTime(order.updatedAt) },
          ]}
        />
      </div>
    </Card>
  );
}

function formatAddress(address: DeliveryAddress | null): string | null {
  if (!address) {
    return null;
  }
  return [address.line1, address.line2, address.city, address.postalCode]
    .filter(Boolean)
    .join(', ');
}
