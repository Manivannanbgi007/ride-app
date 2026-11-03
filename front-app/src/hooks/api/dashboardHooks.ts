import { keepPreviousData, useQuery } from '@tanstack/react-query';

import { dashboardService } from '@/services/dashboardService';
import type { ActivityRange } from '@/utils/orderActivityUtils';
import { QUERY_KEYS } from '@/utils/queryKeys';

/**
 * The dashboard of the last `days` days. While another period loads, the previous one stays on the screen
 * (placeholderData) instead of the page flashing back to its loading state.
 */
export function useDashboard(days: number) {
  return useQuery({
    queryKey: QUERY_KEYS.dashboard(days),
    queryFn: () => dashboardService.getDashboard(days),
    placeholderData: keepPreviousData,
  });
}

/** This year's revenue per month next to last year's (the "Total Revenue" chart). */
export function useMonthlyRevenue() {
  const today = new Date();
  return useQuery({
    queryKey: QUERY_KEYS.monthlyRevenue(today.getFullYear()),
    queryFn: () => dashboardService.getMonthlyRevenueComparison(today),
    // Revenue of whole months changes slowly
    staleTime: 5 * 60_000,
  });
}

/**
 * The order charts of one chosen day, week or month; nothing is asked while no range is chosen (the dashboard's
 * own period is showing). While another range loads, the previous one stays on the screen.
 */
export function useOrderActivity(range: ActivityRange | null) {
  return useQuery({
    queryKey: QUERY_KEYS.orderActivity(range?.fromDate ?? '', range?.toDate ?? ''),
    queryFn: () => dashboardService.getOrderActivity(range!.fromDate, range!.toDate),
    enabled: range !== null,
    placeholderData: keepPreviousData,
  });
}
