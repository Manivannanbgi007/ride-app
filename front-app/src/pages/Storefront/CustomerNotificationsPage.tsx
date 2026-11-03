import { InboxPanel } from '@/components/business/Notification';
import { PageHeader } from '@/components/common/PageHeader';
import { useUnreadNotificationCount } from '@/hooks/api/notificationHooks';
import { customerRouteTo } from '@/utils/constants';

/** The customer's in-app notifications (order placed, on its way, delivered, refunds); orders open here. */
export default function CustomerNotificationsPage() {
  const { data: unread } = useUnreadNotificationCount();
  return (
    <div className="flex flex-col gap-6">
      <PageHeader title="Notifications" subtitle="News about your orders" />
      <InboxPanel unread={unread ?? 0} orderLink={customerRouteTo.orderDetails} />
    </div>
  );
}
