import { useState } from 'react';

import { DishRevenueChart } from '@/components/business/Food/DishRevenueChart';
import { AsyncContent } from '@/components/common/AsyncContent';
import { Card } from '@/components/common/Card';
import { EmptyState } from '@/components/common/EmptyState';
import { Skeleton } from '@/components/common/Loader';
import { SegmentedControl } from '@/components/common/SegmentedControl';
import { useDishRevenue } from '@/hooks/api/orderHooks';
import { DISH_REVENUE_PERIODS, type DishRevenue, type DishRevenuePeriod } from '@/types/order';
import { DEFAULT_CURRENCY } from '@/utils/constants';
import { ERROR_MESSAGES } from '@/utils/errorMessages';
import { formatCurrency, formatNumber } from '@/utils/formatUtils';

const PERIOD_LABELS: Record<DishRevenuePeriod, string> = {
  MONTHLY: 'Monthly',
  WEEKLY: 'Weekly',
  DAILY: 'Daily',
};
const PERIOD_OPTIONS = DISH_REVENUE_PERIODS.map((id) => ({ id, label: PERIOD_LABELS[id] }));
const PERIOD_SPAN: Record<DishRevenuePeriod, string> = {
  MONTHLY: 'the last 12 months',
  WEEKLY: 'the last 12 weeks',
  DAILY: 'the last 30 days',
};
const PANEL_ID = 'dish-revenue-panel';

/** The text alternative of the chart: the totals and the best window. */
function summarize(data: DishRevenue): string {
  const best = data.points.reduce((top, point) => (point.revenue > top.revenue ? point : top));
  return (
    `Revenue chart for ${PERIOD_SPAN[data.period]}: ${formatCurrency(data.totalRevenue, DEFAULT_CURRENCY)} ` +
    `from ${formatNumber(data.totalQuantity)} sold. Best: ${best.label}, ` +
    `${formatCurrency(best.revenue, DEFAULT_CURRENCY)}.`
  );
}

/** "Revenue": what the dish earned from delivered orders, by month, week or day. Loads its own data. */
export function DishRevenueCard({ menuItemId }: { menuItemId: number }) {
  const [period, setPeriod] = useState<DishRevenuePeriod>('MONTHLY');
  const revenue = useDishRevenue(menuItemId, period);
  const data = revenue.data;
  const noSales = data !== undefined && data.totalRevenue === 0 && data.totalQuantity === 0;

  return (
    <Card
      title="Revenue"
      subtitle="Revenue of this dish from delivered orders"
      actions={
        <SegmentedControl
          label="Revenue period"
          options={PERIOD_OPTIONS}
          value={period}
          onChange={setPeriod}
          panelId={PANEL_ID}
        />
      }
    >
      <div id={PANEL_ID} role="tabpanel" aria-label={`${PERIOD_LABELS[period]} revenue`}>
        <AsyncContent
          isLoading={revenue.isPending}
          error={revenue.error}
          isEmpty={noSales}
          errorTitle={ERROR_MESSAGES.DISH_REVENUE_LOAD_FAILED}
          onRetry={() => void revenue.refetch()}
          isRetrying={revenue.isFetching}
          loading={
            <div role="status" aria-label="Loading" className="flex flex-col gap-4">
              <Skeleton className="h-6 w-1/2" />
              <Skeleton className="h-72 w-full" />
            </div>
          }
          empty={
            <EmptyState title="No sales yet" message="Delivered orders of this dish appear here." />
          }
        >
          {data && (
            <div className="flex flex-col gap-4">
              <p className="text-sm text-ink-soft">
                <span className="text-xl font-bold text-ink">
                  {formatCurrency(data.totalRevenue, DEFAULT_CURRENCY)}
                </span>{' '}
                from {formatNumber(data.totalQuantity)} sold in {PERIOD_SPAN[data.period]}
              </p>
              <DishRevenueChart data={data} summary={summarize(data)} />
            </div>
          )}
        </AsyncContent>
      </div>
    </Card>
  );
}
