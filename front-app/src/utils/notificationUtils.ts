import type { AdminNotification } from '@/types/notification';
import { NOT_AVAILABLE } from '@/utils/constants';

/** Who a notification is for: a user, or a restaurant (its owner's user id is filled in once it is sent). */
export function recipientOf(notification: AdminNotification): string {
  if (notification.recipientUserId) {
    return `User #${notification.recipientUserId}`;
  }
  return notification.restaurantId ? `Restaurant #${notification.restaurantId}` : NOT_AVAILABLE;
}
