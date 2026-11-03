import {
  DashboardSkeleton,
  LiveDeliveryTracking,
  OrdersByStatusCard,
  OrderSalesOverview,
  OrderTotalsCards,
  PeriodFilter,
  RevenueChartCard,
  TopRestaurants,
} from '@/components/business/Dashboard';
import { LayoutDashboard } from 'lucide-react';

import { PartialDataAlert } from '@/components/business/PartialDataAlert';
import { AsyncContent } from '@/components/common/AsyncContent';
import { PageHero } from '@/components/common/PageHero';
import { useCurrentUser } from '@/hooks/api/userHooks';
import { useDashboard } from '@/hooks/api/dashboardHooks';
import { useAppDispatch, useAppSelector } from '@/store/hooks';
import { periodDaysChanged, selectPeriodDays } from '@/store/slices/reportPeriodSlice';
import { dashboardFigures } from '@/utils/dashboardUtils';
import { env } from '@/utils/env';
import { ERROR_MESSAGES } from '@/utils/errorMessages';
import { stagger } from '@/utils/motion';

/** Each block of the dashboard rises into place a little after the one above it */
const RISE = 'motion-safe:animate-rise-in stagger';

/**
 * The admin dashboard (the design's main screen). The page only puts the pieces together: the data comes from
 * hooks, the figures from dashboardUtils, and each card is its own component.
 */
export default function DashboardPage() {
  const dispatch = useAppDispatch();
  const days = useAppSelector(selectPeriodDays);
  const dashboard = useDashboard(days);
  const { data: user } = useCurrentUser();
  const data = dashboard.data;
  const figures = data ? dashboardFigures(data) : null;

  return (
    <div className="flex flex-col gap-6">
      <PageHero
        title="Dashboard"
        eyebrow="Overview"
        icon={LayoutDashboard}
        subtitle={`Hi, ${user?.fullName ?? 'there'}. Welcome back to ${env.appName} Admin!`}
        actions={
          <PeriodFilter
            days={days}
            onChange={(newDays) => dispatch(periodDaysChanged(newDays))}
            fromDate={data?.chartsFrom}
            toDate={data?.today}
          />
        }
      />

      <AsyncContent
        isLoading={dashboard.isPending}
        error={dashboard.error}
        errorTitle={ERROR_MESSAGES.DASHBOARD_LOAD_FAILED}
        onRetry={() => void dashboard.refetch()}
        isRetrying={dashboard.isFetching}
        loading={<DashboardSkeleton />}
      >
        {data && figures && (
          <div className="flex flex-col gap-4">
            <PartialDataAlert unavailable={data.unavailable} />
            <OrderTotalsCards totals={figures.totals} currency={data.currency} days={days} />
            <div style={stagger(4)} className={RISE}>
              <OrderSalesOverview dashboard={data} days={days} />
            </div>
            <div style={stagger(6)} className={RISE}>
              <LiveDeliveryTracking />
            </div>
            <div style={stagger(8)} className={`grid gap-4 xl:grid-cols-3 ${RISE}`}>
              <RevenueChartCard className="xl:col-span-2" />
              <OrdersByStatusCard ordersByStatus={data.charts.ordersByStatus} />
            </div>
            <div style={stagger(10)} className={RISE}>
              <TopRestaurants
                restaurants={data.charts.restaurantPerformance}
                currency={data.currency}
              />
            </div>
          </div>
        )}
      </AsyncContent>
    </div>
  );
}
