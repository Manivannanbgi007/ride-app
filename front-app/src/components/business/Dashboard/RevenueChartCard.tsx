import {
  CartesianGrid,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';

import { PartialDataAlert } from '@/components/business/PartialDataAlert';
import { AsyncContent } from '@/components/common/AsyncContent';
import { Card } from '@/components/common/Card';
import { EmptyState } from '@/components/common/EmptyState';
import { useMonthlyRevenue } from '@/hooks/api/dashboardHooks';
import type { MonthlyRevenueComparison } from '@/types/dashboard';
import { CHART_COLORS } from '@/utils/chartColors';
import { ERROR_MESSAGES } from '@/utils/errorMessages';
import { formatCompactCurrency, formatCurrency } from '@/utils/formatUtils';

/**
 * "Total Revenue": this year's revenue per month against last year's. It loads its own data (a different API
 * than the rest of the dashboard), so a failure here leaves the other cards working.
 */
export function RevenueChartCard({ className }: { className?: string }) {
  const revenue = useMonthlyRevenue();
  const data = revenue.data;
  const hasRevenue = data?.months.some(
    (month) => (month.thisYear ?? 0) > 0 || (month.lastYear ?? 0) > 0,
  );

  return (
    <Card
      title="Total Revenue"
      subtitle="Delivered orders per month, this year against last year"
      actions={data && <YearLegend data={data} />}
      className={className}
    >
      <AsyncContent
        isLoading={revenue.isPending}
        error={revenue.error}
        isEmpty={!hasRevenue}
        errorTitle={ERROR_MESSAGES.REVENUE_LOAD_FAILED}
        onRetry={() => void revenue.refetch()}
        isRetrying={revenue.isFetching}
        empty={
          <EmptyState
            title="No revenue yet"
            message="Delivered orders of this year and last year appear here."
          />
        }
      >
        {data && (
          <div className="flex flex-col gap-4">
            <PartialDataAlert unavailable={data.unavailable} />
            <div className="h-60">
              <ResponsiveContainer width="100%" height="100%">
                <LineChart data={data.months} margin={{ top: 10, right: 10, left: 0, bottom: 0 }}>
                  <CartesianGrid vertical={false} stroke={CHART_COLORS.line} />
                  <XAxis
                    dataKey="month"
                    tick={{ fill: CHART_COLORS.inkSoft, fontSize: 12 }}
                    axisLine={false}
                    tickLine={false}
                  />
                  <YAxis
                    tickFormatter={(value: number) => formatCompactCurrency(value, data.currency)}
                    tick={{ fill: CHART_COLORS.inkSoft, fontSize: 12 }}
                    axisLine={false}
                    tickLine={false}
                    width={70}
                  />
                  <Tooltip formatter={(value) => formatCurrency(Number(value), data.currency)} />
                  <Line
                    type="monotone"
                    dataKey="thisYear"
                    name={String(data.thisYear)}
                    stroke={CHART_COLORS.info}
                    strokeWidth={3}
                    dot={false}
                    connectNulls={false}
                  />
                  <Line
                    type="monotone"
                    dataKey="lastYear"
                    name={String(data.lastYear)}
                    stroke={CHART_COLORS.danger}
                    strokeWidth={3}
                    dot={false}
                  />
                </LineChart>
              </ResponsiveContainer>
            </div>
          </div>
        )}
      </AsyncContent>
    </Card>
  );
}

function YearLegend({ data }: { data: MonthlyRevenueComparison }) {
  return (
    <div className="flex items-center gap-5 text-xs text-ink-soft">
      <span className="flex items-center gap-2">
        <span className="size-3 rounded-full bg-info-500" />
        {data.thisYear}
      </span>
      <span className="flex items-center gap-2">
        <span className="size-3 rounded-full bg-danger-500" />
        {data.lastYear}
      </span>
    </div>
  );
}
