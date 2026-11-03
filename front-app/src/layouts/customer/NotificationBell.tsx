import { Bell } from 'lucide-react';

import { HeaderIconLink } from '@/components/common/HeaderIcon';
import { useUnreadNotificationCount } from '@/hooks/api/notificationHooks';
import { useAppSelector } from '@/store/hooks';
import { selectCustomer } from '@/store/slices/sessionSlice';
import { CUSTOMER_ROUTES } from '@/utils/constants';

/** The bell with the signed-in customer's unread notifications (refreshed every minute); it rings on hover. */
export function NotificationBell() {
  const customer = useAppSelector(selectCustomer);
  const { data: unread = 0 } = useUnreadNotificationCount();
  if (!customer) {
    return null;
  }
  return (
    <HeaderIconLink
      to={CUSTOMER_ROUTES.notifications}
      label={unread > 0 ? `Notifications (${unread} unread)` : 'Notifications'}
      icon={Bell}
      count={unread}
      motion="ring"
      pulse
    />
  );
}
