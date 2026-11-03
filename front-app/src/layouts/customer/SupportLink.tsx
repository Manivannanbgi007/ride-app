import { MessageSquare } from 'lucide-react';

import { HeaderIconLink } from '@/components/common/HeaderIcon';
import { useMyConversation } from '@/hooks/api/chatHooks';
import { useAppSelector } from '@/store/hooks';
import { selectCustomer } from '@/store/slices/sessionSlice';
import { CUSTOMER_ROUTES } from '@/utils/constants';

/** The chat with Support, with the number of its unread messages (refreshed every minute and on live updates). */
export function SupportLink() {
  const customer = useAppSelector(selectCustomer);
  const { data: conversation } = useMyConversation();
  if (!customer) {
    return null;
  }
  const unread = conversation?.unreadCount ?? 0;
  return (
    <HeaderIconLink
      to={CUSTOMER_ROUTES.support}
      label={unread > 0 ? `Support chat (${unread} unread)` : 'Support chat'}
      icon={MessageSquare}
      count={unread}
      motion="pop"
    />
  );
}
