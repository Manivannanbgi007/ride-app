import { Bell, Check } from 'lucide-react';
import { Link } from 'react-router';

import { Button } from '@/components/common/Button';
import type { InboxNotification } from '@/types/notification';
import { cn } from '@/utils/classNames';
import { NOTIFICATION_TYPE_LABELS, routeTo } from '@/utils/constants';
import { formatDateTime } from '@/utils/dateUtils';

interface InboxListProps {
  notifications: readonly InboxNotification[];
  onMarkRead: (notificationId: number) => void;
  /** The notification being marked read (its button waits meanwhile) */
  markingId?: number;
  isUpdating?: boolean;
  /** Where an order mentioned in a notification opens (default: the admin's order page) */
  orderLink?: (orderId: number) => string;
}

/** The acting user's in-app notifications, newest first; unread ones stand out and can be marked read. */
export function InboxList({
  notifications,
  onMarkRead,
  markingId,
  isUpdating,
  orderLink = routeTo.orderDetails,
}: InboxListProps) {
  return (
    <ul className={cn('flex flex-col divide-y divide-line', isUpdating && 'opacity-60')}>
      {notifications.map((notification) => {
        const unread = notification.readAt === null;
        return (
          <li key={notification.id} className="flex items-start gap-4 py-4">
            <span
              className={cn(
                'flex size-11 shrink-0 items-center justify-center rounded-full',
                unread ? 'bg-brand-50 text-brand-500' : 'bg-page text-ink-soft',
              )}
            >
              <Bell aria-hidden="true" className="size-5" />
            </span>
            <div className="min-w-0 flex-1">
              <div className="flex flex-wrap items-baseline justify-between gap-2">
                <p className={cn('font-semibold', !unread && 'text-ink-soft')}>
                  {unread && <span className="sr-only">Unread: </span>}
                  {notification.title}
                </p>
                <p className="text-xs text-ink-soft">{formatDateTime(notification.sentAt)}</p>
              </div>
              <p className="mt-1 text-sm">{notification.message}</p>
              <p className="mt-1 flex flex-wrap gap-3 text-xs text-ink-soft">
                {NOTIFICATION_TYPE_LABELS[notification.type] ?? notification.type}
                {notification.orderId && (
                  <Link
                    to={orderLink(notification.orderId)}
                    className="text-brand-text hover:underline"
                  >
                    Order #{notification.orderId}
                  </Link>
                )}
              </p>
            </div>
            {unread && (
              <Button
                variant="ghost"
                size="sm"
                aria-label={`Mark "${notification.title}" as read`}
                isLoading={markingId === notification.id}
                onClick={() => onMarkRead(notification.id)}
                leftIcon={<Check aria-hidden="true" className="size-4" />}
              >
                Read
              </Button>
            )}
          </li>
        );
      })}
    </ul>
  );
}
