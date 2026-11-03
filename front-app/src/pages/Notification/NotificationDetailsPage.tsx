import { Send } from 'lucide-react';
import { useState } from 'react';

import { AuditHistory } from '@/components/business/AuditHistory';
import { NotificationDetailsCard } from '@/components/business/Notification';
import { AsyncContent } from '@/components/common/AsyncContent';
import { BackLink } from '@/components/common/BackLink';
import { Button } from '@/components/common/Button';
import { Card } from '@/components/common/Card';
import { PageHeader } from '@/components/common/PageHeader';
import { ReasonDialog } from '@/components/common/ReasonDialog';
import { useToast } from '@/context/ToastContext';
import {
  useAdminNotification,
  useNotificationAuditLogs,
  useResendNotification,
} from '@/hooks/api/notificationHooks';
import { useIdParam } from '@/hooks/useIdParam';
import { NotFoundPage } from '@/pages/NotFound/NotFoundPage';
import { RESENDABLE_STATUSES, type AdminNotification } from '@/types/notification';
import { NOTIFICATION_STATUS_LABELS, ROUTES } from '@/utils/constants';
import { ERROR_MESSAGES } from '@/utils/errorMessages';

/** One notification with how it was delivered, "Resend" for a failed or skipped one, and its history. */
export default function NotificationDetailsPage() {
  const notificationId = useIdParam('notificationId');
  return notificationId === null ? (
    <NotFoundPage />
  ) : (
    <NotificationDetails notificationId={notificationId} />
  );
}

function NotificationDetails({ notificationId }: { notificationId: number }) {
  const notification = useAdminNotification(notificationId);
  const auditLogs = useNotificationAuditLogs(notificationId);

  return (
    <div className="flex flex-col gap-6">
      <BackLink to={`${ROUTES.notifications}?tab=all`}>Back to all notifications</BackLink>
      <AsyncContent
        isLoading={notification.isPending}
        error={notification.error}
        errorTitle={ERROR_MESSAGES.NOTIFICATION_LOAD_FAILED}
        onRetry={() => void notification.refetch()}
        isRetrying={notification.isFetching}
      >
        {notification.data && (
          <>
            <PageHeader
              title={`Notification #${notification.data.id}`}
              subtitle={NOTIFICATION_STATUS_LABELS[notification.data.status]}
              actions={<ResendAction notification={notification.data} />}
            />
            <div className="grid gap-6 xl:grid-cols-3">
              <div className="xl:col-span-2">
                <NotificationDetailsCard notification={notification.data} />
              </div>
              <Card title="Change history" subtitle="Resends of this notification">
                <AsyncContent
                  isLoading={auditLogs.isPending}
                  error={auditLogs.error}
                  errorTitle={ERROR_MESSAGES.AUDIT_LOAD_FAILED}
                  onRetry={() => void auditLogs.refetch()}
                >
                  {auditLogs.data && <AuditHistory entries={auditLogs.data.content} />}
                </AsyncContent>
              </Card>
            </div>
          </>
        )}
      </AsyncContent>
    </div>
  );
}

/** Only a FAILED or SKIPPED notification can be sent again (notification-service refuses the others too). */
function ResendAction({ notification }: { notification: AdminNotification }) {
  const [open, setOpen] = useState(false);
  const resend = useResendNotification();
  const { showToast } = useToast();
  const canResend = RESENDABLE_STATUSES.includes(notification.status);

  const submit = async (reason: string) => {
    await resend.mutateAsync({ notificationId: notification.id, reason });
    showToast(`Notification #${notification.id} will be sent again in a moment.`);
  };

  return (
    <div className="flex flex-col items-end gap-2">
      <Button
        disabled={!canResend}
        onClick={() => setOpen(true)}
        leftIcon={<Send aria-hidden="true" className="size-4" />}
      >
        Resend
      </Button>
      {!canResend && (
        <p className="text-xs text-ink-soft">
          {notification.status === 'SENT'
            ? 'It was sent already.'
            : 'It is still being sent (with retries).'}
        </p>
      )}
      <ReasonDialog
        open={open}
        title={`Resend notification #${notification.id}?`}
        description="It is sent again to the same recipient, with all its attempts. A notification skipped for a missing address (an SMS without a phone number) is skipped again until the user adds one."
        confirmLabel="Resend"
        onSubmit={submit}
        onClose={() => setOpen(false)}
      />
    </div>
  );
}
