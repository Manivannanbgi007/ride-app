import { PaymentStatusBadge } from '@/components/business/StatusBadges';
import { Alert } from '@/components/common/Alert';
import { Card } from '@/components/common/Card';
import { DescriptionList } from '@/components/common/DescriptionList';
import { formatAddress, phoneDigits } from '@/components/business/OwnerOrders/ownerOrderUtils';
import type { OrderDetails } from '@/types/order';
import { ACTOR_TYPE_LABELS, PAYMENT_METHOD_LABELS } from '@/utils/constants';
import { formatDateTime } from '@/utils/dateUtils';

/**
 * Who ordered, where it goes and how it is paid. Like the admin's overview card, but the customer is a name and
 * a phone number to call (an owner has no customer screen to open).
 */
export function OwnerOrderInfoCard({ order }: { order: OrderDetails }) {
  return (
    <Card title="Customer and delivery">
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
            { label: 'Customer', value: order.customerName ?? `Customer #${order.customerId}` },
            {
              label: 'Phone',
              value: order.customerPhone && (
                <a
                  href={`tel:${phoneDigits(order.customerPhone)}`}
                  className="text-brand-text hover:underline"
                >
                  {order.customerPhone}
                </a>
              ),
            },
            { label: 'Deliver to', value: formatAddress(order.deliveryAddress) },
            { label: 'Customer note', value: order.notes },
            {
              label: 'Payment',
              value: (
                <span className="flex flex-wrap items-center gap-2">
                  {PAYMENT_METHOD_LABELS[order.paymentMethod]}
                  <PaymentStatusBadge status={order.paymentStatus} />
                </span>
              ),
            },
            { label: 'Placed', value: formatDateTime(order.createdAt) },
            { label: 'Last change', value: formatDateTime(order.updatedAt) },
          ]}
        />
      </div>
    </Card>
  );
}
