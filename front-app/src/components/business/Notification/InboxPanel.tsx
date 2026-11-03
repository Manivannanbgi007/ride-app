import { CheckCheck } from 'lucide-react';
import { useState } from 'react';

import { InboxList } from '@/components/business/Notification/InboxList';
import { Button } from '@/components/common/Button';
import { Card } from '@/components/common/Card';
import { PagedResults } from '@/components/common/PagedResults';
import { useToast } from '@/context/ToastContext';
import {
  useInbox,
  useMarkAllNotificationsRead,
  useMarkNotificationRead,
} from '@/hooks/api/notificationHooks';
import { ERROR_MESSAGES } from '@/utils/errorMessages';

const INBOX_PAGE_SIZE = 20;

interface InboxPanelProps {
  /** How many are unread (the bell's number): "Mark all as read" is off at 0 */
  unread: number;
  /** Where an order mentioned in a notification opens (each area has its own order page) */
  orderLink?: (orderId: number) => string;
}

/**
 * The acting user's inbox: "Unread only", "Mark all as read", the notifications page by page. The admin area and
 * the customer area both show it (each acts as its own user).
 */
export function InboxPanel({ unread, orderLink }: InboxPanelProps) {
  const [unreadOnly, setUnreadOnly] = useState(false);
  const [page, setPage] = useState(0);
  const inbox = useInbox(unreadOnly, page, INBOX_PAGE_SIZE);
  const markRead = useMarkNotificationRead();
  const markAllRead = useMarkAllNotificationsRead();
  const { showToast } = useToast();

  const readOne = (notificationId: number) => {
    markRead.mutate(notificationId, {
      onError: () => showToast(ERROR_MESSAGES.NOTIFICATION_READ_FAILED, 'error'),
    });
  };
  const readAll = () => {
    markAllRead.mutate(undefined, {
      onSuccess: () => showToast('All your notifications are marked as read.'),
      onError: () => showToast(ERROR_MESSAGES.NOTIFICATION_READ_FAILED, 'error'),
    });
  };

  return (
    <Card
      title="My notifications"
      subtitle="In-app notifications sent to you, newest first"
      actions={
        <div className="flex flex-wrap items-center gap-4">
          <label className="flex items-center gap-2 text-sm">
            <input
              type="checkbox"
              checked={unreadOnly}
              onChange={(event) => {
                setUnreadOnly(event.target.checked);
                setPage(0);
              }}
              className="size-4 accent-brand-500"
            />
            Unread only
          </label>
          <Button
            variant="outline"
            size="sm"
            disabled={unread === 0}
            isLoading={markAllRead.isPending}
            onClick={readAll}
            leftIcon={<CheckCheck aria-hidden="true" className="size-4" />}
          >
            Mark all as read
          </Button>
        </div>
      }
    >
      <PagedResults
        query={inbox}
        errorTitle={ERROR_MESSAGES.INBOX_LOAD_FAILED}
        emptyTitle="No notifications yet"
        filteredEmptyTitle="Nothing unread"
        hasFilters={unreadOnly}
        onClearFilters={() => setUnreadOnly(false)}
        onPageChange={setPage}
        columns={3}
      >
        {(rows, isUpdating) => (
          <InboxList
            notifications={rows}
            onMarkRead={readOne}
            markingId={markRead.isPending ? markRead.variables : undefined}
            isUpdating={isUpdating}
            orderLink={orderLink}
          />
        )}
      </PagedResults>
    </Card>
  );
}
