import { apiClient } from '@/api/apiClient';
import type { AuditLogEntry, PageResponse } from '@/types/api';
import type {
  AdminNotification,
  InboxNotification,
  NotificationFilters,
  NotificationSummary,
  UnreadCountResponse,
} from '@/types/notification';
import { API_ENDPOINTS } from '@/utils/apiEndpoints';

/** notification-service APIs: the acting user's inbox, and (admin) every notification. */
export const notificationService = {
  /** How many of the acting user's in-app notifications are unread (the number on the bell). */
  getUnreadCount: async (): Promise<number> => {
    const response = await apiClient.get<UnreadCountResponse>(
      API_ENDPOINTS.notifications.unreadCount,
    );
    return response.unread;
  },

  /** The acting user's in-app notifications, newest first. */
  getInbox: (unreadOnly: boolean, page: number, size: number) =>
    apiClient.get<PageResponse<InboxNotification>>(API_ENDPOINTS.notifications.list, {
      params: { unreadOnly, page, size },
    }),

  /** Marking twice keeps the first time it was read. */
  markRead: (notificationId: number) =>
    apiClient.put<InboxNotification>(API_ENDPOINTS.notifications.markRead(notificationId)),

  markAllRead: () => apiClient.put<UnreadCountResponse>(API_ENDPOINTS.notifications.markAllRead),

  searchNotifications: (filters: NotificationFilters, page: number, size: number) =>
    apiClient.get<PageResponse<AdminNotification>>(API_ENDPOINTS.admin.notifications.list, {
      params: { ...filters, page, size },
    }),

  /** Totals per status and channel; without dates: today. */
  getSummary: (fromDate?: string, toDate?: string) =>
    apiClient.get<NotificationSummary>(API_ENDPOINTS.admin.notifications.summary, {
      params: { fromDate, toDate },
    }),

  getNotification: (notificationId: number) =>
    apiClient.get<AdminNotification>(API_ENDPOINTS.admin.notifications.details(notificationId)),

  /** Sends a FAILED or SKIPPED notification again, with all its attempts. Audited. */
  resend: (notificationId: number, reason: string) =>
    apiClient.post<AdminNotification>(API_ENDPOINTS.admin.notifications.resend(notificationId), {
      reason,
    }),

  getAuditLogs: (notificationId: number) =>
    apiClient.get<PageResponse<AuditLogEntry>>(
      API_ENDPOINTS.admin.notifications.auditLogs(notificationId),
      { params: { size: 50 } },
    ),
};
