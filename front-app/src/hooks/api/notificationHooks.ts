import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { notificationService } from '@/services/notificationService';
import { useActingUser } from '@/hooks/useActingUser';
import type { NotificationFilters } from '@/types/notification';
import { QUERY_KEYS } from '@/utils/queryKeys';

const REFRESH_EVERY_MS = 60_000;

/** The number on the bell, refreshed every minute while the application is open. */
export function useUnreadNotificationCount() {
  const actingUser = useActingUser();
  return useQuery({
    queryKey: QUERY_KEYS.unreadNotificationCount(actingUser?.id ?? 0),
    queryFn: () => notificationService.getUnreadCount(),
    enabled: actingUser !== null,
    refetchInterval: REFRESH_EVERY_MS,
  });
}

/** The acting user's inbox (kept per user: switching the acting user never shows another's notifications). */
export function useInbox(unreadOnly: boolean, page: number, size: number) {
  const actingUser = useActingUser();
  return useQuery({
    queryKey: QUERY_KEYS.inbox(actingUser?.id ?? 0, unreadOnly, page),
    queryFn: () => notificationService.getInbox(unreadOnly, page, size),
    enabled: actingUser !== null,
    placeholderData: keepPreviousData,
    refetchInterval: REFRESH_EVERY_MS,
  });
}

/** Marking read changes the inbox and the bell. */
export function useMarkNotificationRead() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (notificationId: number) => notificationService.markRead(notificationId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: QUERY_KEYS.inboxAll }),
  });
}

export function useMarkAllNotificationsRead() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => notificationService.markAllRead(),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: QUERY_KEYS.inboxAll }),
  });
}

export function useAdminNotifications(
  filters: NotificationFilters,
  page: number,
  size: number,
  enabled = true,
) {
  return useQuery({
    queryKey: QUERY_KEYS.adminNotifications.list(filters, page, size),
    queryFn: () => notificationService.searchNotifications(filters, page, size),
    placeholderData: keepPreviousData,
    enabled,
  });
}

export function useNotificationSummary(fromDate?: string, toDate?: string, enabled = true) {
  return useQuery({
    queryKey: QUERY_KEYS.adminNotifications.summary(fromDate, toDate),
    queryFn: () => notificationService.getSummary(fromDate, toDate),
    placeholderData: keepPreviousData,
    enabled,
  });
}

export function useAdminNotification(notificationId: number) {
  return useQuery({
    queryKey: QUERY_KEYS.adminNotifications.details(notificationId),
    queryFn: () => notificationService.getNotification(notificationId),
  });
}

export function useNotificationAuditLogs(notificationId: number) {
  return useQuery({
    queryKey: QUERY_KEYS.adminNotifications.auditLogs(notificationId),
    queryFn: () => notificationService.getAuditLogs(notificationId),
  });
}

/** After a resend the notification is PENDING again; the lists, totals and its history change. */
export function useResendNotification() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ notificationId, reason }: { notificationId: number; reason: string }) =>
      notificationService.resend(notificationId, reason),
    onSuccess: async (notification) => {
      queryClient.setQueryData(
        QUERY_KEYS.adminNotifications.details(notification.id),
        notification,
      );
      await queryClient.invalidateQueries({ queryKey: QUERY_KEYS.adminNotifications.all });
    },
  });
}
