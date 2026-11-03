import { Link } from 'react-router';

import { NotificationStatusBadge } from '@/components/business/StatusBadges';
import { Table, type TableColumn } from '@/components/common/Table';
import type { AdminNotification } from '@/types/notification';
import {
  NOTIFICATION_CHANNEL_LABELS,
  NOTIFICATION_TYPE_LABELS,
  NOT_AVAILABLE,
  routeTo,
} from '@/utils/constants';
import { formatDateTime } from '@/utils/dateUtils';
import { recipientOf } from '@/utils/notificationUtils';

const COLUMNS: readonly TableColumn<AdminNotification>[] = [
  {
    header: 'Created',
    render: (notification) => (
      <Link
        to={routeTo.notificationDetails(notification.id)}
        className="font-semibold whitespace-nowrap text-brand-text hover:underline"
      >
        {formatDateTime(notification.createdAt)}
      </Link>
    ),
  },
  {
    header: 'Notification',
    render: (notification) => (
      <div className="max-w-xs">
        <p className="truncate">{notification.subject}</p>
        <p className="text-xs text-ink-soft">{NOTIFICATION_TYPE_LABELS[notification.type]}</p>
      </div>
    ),
  },
  {
    header: 'To',
    render: (notification) => (
      <div>
        <p>{recipientOf(notification)}</p>
        <p className="text-xs text-ink-soft">
          {NOTIFICATION_CHANNEL_LABELS[notification.channel]}
          {notification.recipientAddress ? `: ${notification.recipientAddress}` : ''}
        </p>
      </div>
    ),
  },
  {
    header: 'Order',
    render: (notification) =>
      notification.orderId ? (
        <Link to={routeTo.orderDetails(notification.orderId)} className="hover:underline">
          #{notification.orderId}
        </Link>
      ) : (
        NOT_AVAILABLE
      ),
  },
  { header: 'Attempts', align: 'right', render: (notification) => notification.attempts },
  {
    header: 'Status',
    render: (notification) => (
      <div className="flex max-w-xs flex-col items-start gap-1">
        <NotificationStatusBadge status={notification.status} />
        {notification.lastError && (
          <span className="line-clamp-2 text-xs text-ink-soft">{notification.lastError}</span>
        )}
      </div>
    ),
  },
];

interface AdminNotificationTableProps {
  notifications: readonly AdminNotification[];
  isUpdating?: boolean;
}

/** Notifications as the admin sees them; the time opens the notification (resend, history). */
export function AdminNotificationTable({ notifications, isUpdating }: AdminNotificationTableProps) {
  return (
    <Table
      caption="Notifications"
      columns={COLUMNS}
      rows={notifications}
      rowKey={(notification) => notification.id}
      isUpdating={isUpdating}
    />
  );
}
