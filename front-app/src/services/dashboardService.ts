import { apiClient } from '@/api/apiClient';
import type {
  DashboardResponse,
  MonthlyRevenueComparison,
  OrderActivityResponse,
} from '@/types/dashboard';
import type { RevenueGroupBy, RevenueReport } from '@/types/report';
import { API_ENDPOINTS } from '@/utils/apiEndpoints';
import { DEFAULT_CURRENCY } from '@/utils/constants';
import { monthlyRevenueSeries } from '@/utils/dashboardUtils';
import { toIsoDate } from '@/utils/dateUtils';

/** admin-service APIs behind the dashboard. */
export const dashboardService = {
  /** Cards and charts of the last `days` days (1 to 90). */
  getDashboard: (days: number): Promise<DashboardResponse> =>
    apiClient.get<DashboardResponse>(API_ENDPOINTS.admin.dashboard, { params: { days } }),

  /** The order charts of one chosen period (a day, a week, a month): both days included, at most 366 days. */
  getOrderActivity: (fromDate: string, toDate: string): Promise<OrderActivityResponse> =>
    apiClient.get<OrderActivityResponse>(API_ENDPOINTS.admin.orderActivity, {
      params: { fromDate, toDate },
    }),

  /** Revenue per day, week or month between two calendar days (at most 366 days). */
  getRevenueReport: (
    fromDate: string,
    toDate: string,
    groupBy: RevenueGroupBy,
  ): Promise<RevenueReport> =>
    apiClient.get<RevenueReport>(API_ENDPOINTS.admin.reports.revenue, {
      params: { fromDate, toDate, groupBy },
    }),

  /**
   * This year's revenue per month next to last year's. Two reports, because one covers at most 366 days; both
   * are asked at the same time.
   */
  getMonthlyRevenueComparison: async (today: Date): Promise<MonthlyRevenueComparison> => {
    const year = today.getFullYear();
    const [thisYear, lastYear] = await Promise.all([
      dashboardService.getRevenueReport(`${year}-01-01`, toIsoDate(today), 'MONTH'),
      dashboardService.getRevenueReport(`${year - 1}-01-01`, `${year - 1}-12-31`, 'MONTH'),
    ]);
    return {
      currency: thisYear.currency ?? lastYear.currency ?? DEFAULT_CURRENCY,
      thisYear: year,
      lastYear: year - 1,
      months: monthlyRevenueSeries(thisYear.periods, lastYear.periods),
      unavailable: [...new Set([...thisYear.unavailable, ...lastYear.unavailable])],
    };
  },
};
