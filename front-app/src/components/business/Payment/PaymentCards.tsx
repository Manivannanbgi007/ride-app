import { Link } from 'react-router';

import { PaymentRecordStatusBadge } from '@/components/business/StatusBadges';
import { Alert } from '@/components/common/Alert';
import { Badge } from '@/components/common/Badge';
import { Card } from '@/components/common/Card';
import { DescriptionList } from '@/components/common/DescriptionList';
import { EmptyState } from '@/components/common/EmptyState';
import type { Payment, PublishedEvent } from '@/types/payment';
import { PAYMENT_METHOD_LABELS, routeTo } from '@/utils/constants';
import { formatDateTime } from '@/utils/dateUtils';
import { formatCurrency } from '@/utils/formatUtils';

/** The payment: amount, refunds so far, method, provider and the moments it went through. */
export function PaymentOverviewCard({ payment }: { payment: Payment }) {
  const money = (amount: number) => formatCurrency(amount, payment.currency);
  return (
    <Card title="Payment" actions={<PaymentRecordStatusBadge status={payment.status} />}>
      <div className="flex flex-col gap-5">
        {payment.failureReason && <Alert tone="error">{payment.failureReason}</Alert>}
        <DescriptionList
          items={[
            { label: 'Amount', value: money(payment.amount) },
            {
              label: 'Refunded (done or running)',
              value: payment.refundedAmount > 0 ? money(payment.refundedAmount) : 'Nothing',
            },
            { label: 'Method', value: PAYMENT_METHOD_LABELS[payment.method] },
            { label: 'Card / account', value: payment.instrument },
            {
              label: 'Order',
              value: (
                <Link
                  to={routeTo.orderDetails(payment.orderId)}
                  className="text-brand-text hover:underline"
                >
                  #{payment.orderId}
                </Link>
              ),
            },
            {
              label: 'Customer',
              value: (
                <Link
                  to={routeTo.customerDetails(payment.customerId)}
                  className="text-brand-text hover:underline"
                >
                  #{payment.customerId}
                </Link>
              ),
            },
            { label: 'Provider reference', value: payment.providerReference },
            { label: 'Started', value: formatDateTime(payment.createdAt) },
            {
              label: 'Finished',
              value: payment.completedAt ? formatDateTime(payment.completedAt) : 'Not yet',
            },
          ]}
        />
      </div>
    </Card>
  );
}

/**
 * The messages the payment sent to the rest of the platform (through Kafka): PENDING ones have not been sent
 * yet, e.g. order-service has not been told the payment succeeded.
 */
export function PaymentEventsCard({ events }: { events: readonly PublishedEvent[] }) {
  return (
    <Card title="Messages to other services" subtitle="Events published for this payment">
      {events.length === 0 ? (
        <EmptyState title="No messages" />
      ) : (
        <ol className="flex flex-col gap-3">
          {events.map((event) => (
            <li key={event.eventId} className="rounded-xl bg-page px-4 py-3 text-sm">
              <div className="flex items-center justify-between gap-2">
                <span className="font-semibold">{event.eventType}</span>
                <Badge tone={event.status === 'PUBLISHED' ? 'success' : 'warning'}>
                  {event.status === 'PUBLISHED' ? 'Sent' : 'Waiting'}
                </Badge>
              </div>
              <p className="text-xs text-ink-soft">
                {formatDateTime(event.createdAt)} to {event.topic}
                {event.attempts > 1 ? `, ${event.attempts} attempts` : ''}
              </p>
              {event.lastError && (
                <p className="mt-1 text-xs text-danger-text">{event.lastError}</p>
              )}
            </li>
          ))}
        </ol>
      )}
    </Card>
  );
}
