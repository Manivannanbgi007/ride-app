import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { auditLogService } from '@/services/auditLogService';
import type { AuditLogFilters } from '@/types/audit';
import { QUERY_KEYS } from '@/utils/queryKeys';

export function useAuditLogs(filters: AuditLogFilters, page: number, size: number, enabled = true) {
  return useQuery({
    queryKey: QUERY_KEYS.auditLogs.list(filters, page, size),
    queryFn: () => auditLogService.searchAuditLogs(filters, page, size),
    placeholderData: keepPreviousData,
    enabled,
  });
}

/** The services and actions the log has (the filter lists); they change rarely. */
export function useAuditActions() {
  return useQuery({
    queryKey: QUERY_KEYS.auditLogs.actions,
    queryFn: () => auditLogService.getActions(),
    staleTime: 5 * 60_000,
  });
}

export function useAuditRecord(auditId: number) {
  return useQuery({
    queryKey: QUERY_KEYS.auditLogs.details(auditId),
    queryFn: () => auditLogService.getAuditRecord(auditId),
    // Records never change
    staleTime: Infinity,
  });
}

/**
 * The copies arrive through Kafka within seconds, so the log is reloaded right away and once more a few seconds
 * later.
 */
export function useAuditBackfill() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (reason: string) => auditLogService.backfill(reason),
    onSuccess: async () => {
      const reload = () => queryClient.invalidateQueries({ queryKey: QUERY_KEYS.auditLogs.all });
      setTimeout(() => void reload(), 5_000);
      await reload();
    },
  });
}
