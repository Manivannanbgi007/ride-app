import { Link } from 'react-router';

import { FigureTiles } from '@/components/business/Analytics/FigureTiles';
import { NotificationStatusBadge } from '@/components/business/StatusBadges';
import { Alert } from '@/components/common/Alert';
import { Card } from '@/components/common/Card';
import { DescriptionList } from '@/components/common/DescriptionList';
import type { AdminNotification, NotificationSummary } from '@/types/notification';
import { NOTIFICATION_CHANNEL_LABELS, NOTIFICATION_TYPE_LABELS, routeTo } from '@/utils/constants';
import { formatDate, formatDateTime } from '@/utils/dateUtils';
import { formatNumber } from '@/utils/formatUtils';
import { recipientOf } from '@/utils/notificationUtils';

/** The period's notifications per status and channel (a status that did not happen counts 0). */
export function NotificationSummaryTiles({ summary }: { summary: NotificationSummary }) {
  const period =
    summary.fromDate === summary.toDate
      ? formatDate(summary.fromDate)
      : `${formatDate(summary.fromDate)} - ${formatDate(summary.toDate)}`;
  const { byStatus, byChannel } = summary;
  return (
    <FigureTiles
      figures={[
        { label: 'Notifications', value: formatNumber(summary.total), hint: period },
        { label: 'Sent', value: formatNumber(byStatus.SENT ?? 0), tone: 'brand' },
        {
          label: 'Failed',
          value: formatNumber(byStatus.FAILED ?? 0),
          hint: 'Can be resent',
          tone: (byStatus.FAILED ?? 0) > 0 ? 'danger' : 'ink',
        },
        { label: 'Pending', value: formatNumber(byStatus.PENDING ?? 0), tone: 'warning' },
        {
          label: 'Skipped',
          value: formatNumber(byStatus.SKIPPED ?? 0),
          hint: 'Nothing to send it to',
        },
        {
          label: 'Push / in-app',
          value: formatNumber(byChannel.PUSH ?? 0),
          hint: `${formatNumber(byChannel.EMAIL ?? 0)} email, ${formatNumber(byChannel.SMS ?? 0)} SMS`,
          tone: 'info',
        },
      ]}
    />
  );
}

/** One notification: what was sent to whom, how, how many attempts, and the last error. */
export function NotificationDetailsCard({ notification }: { notification: AdminNotification }) {
  return (
    <Card
      title={notification.subject}
      subtitle={NOTIFICATION_TYPE_LABELS[notification.type]}
      actions={<NotificationStatusBadge status={notification.status} />}
    >
      <div className="flex flex-col gap-5">
        {notification.lastError && (
          <Alert
            tone={notification.status === 'SKIPPED' ? 'info' : 'error'}
            title={notification.status === 'SKIPPED' ? 'Why it was skipped' : 'Last error'}
          >
            {notification.lastError}
          </Alert>
        )}
        <p className="rounded-xl bg-page px-4 py-3 text-sm whitespace-pre-wrap">
          {notification.body}
        </p>
        <DescriptionList
          items={[
            {
              label: 'To',
              value: notification.recipientUserId ? (
                <Link
                  to={routeTo.customerDetails(notification.recipientUserId)}
                  className="text-brand-text hover:underline"
                >
                  {recipientOf(notification)}
                </Link>
              ) : (
                recipientOf(notification)
              ),
            },
            { label: 'Channel', value: NOTIFICATION_CHANNEL_LABELS[notification.channel] },
            { label: 'Address used', value: notification.recipientAddress },
            {
              label: 'Restaurant',
              value: notification.restaurantId && (
                <Link
                  to={routeTo.restaurantDetails(notification.restaurantId)}
                  className="text-brand-text hover:underline"
                >
                  #{notification.restaurantId}
                </Link>
              ),
            },
            {
              label: 'Order',
              value: notification.orderId && (
                <Link
                  to={routeTo.orderDetails(notification.orderId)}
                  className="text-brand-text hover:underline"
                >
                  #{notification.orderId}
                </Link>
              ),
            },
            { label: 'Attempts', value: formatNumber(notification.attempts) },
            { label: 'Created', value: formatDateTime(notification.createdAt) },
            {
              label: 'Next attempt',
              value: notification.nextAttemptAt && formatDateTime(notification.nextAttemptAt),
            },
            { label: 'Sent', value: notification.sentAt && formatDateTime(notification.sentAt) },
            { label: 'Read', value: notification.readAt && formatDateTime(notification.readAt) },
            { label: 'Event', value: notification.sourceEventId },
            { label: 'Request id', value: notification.requestId },
          ]}
        />
      </div>
    </Card>
  );
}
