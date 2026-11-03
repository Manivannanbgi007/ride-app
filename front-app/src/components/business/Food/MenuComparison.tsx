import { useId, useState } from 'react';

import { Card } from '@/components/common/Card';
import { DonutChart } from '@/components/common/DonutChart';
import { EmptyState } from '@/components/common/EmptyState';
import { ErrorMessage } from '@/components/common/ErrorMessage';
import { Skeleton } from '@/components/common/Loader';
import { Tooltip } from '@/components/common/Tooltip';
import { useCategoryStats } from '@/hooks/api/catalogHooks';
import type { CategoryStat } from '@/types/catalog';
import { CHART_COLORS } from '@/utils/chartColors';
import { ERROR_MESSAGES } from '@/utils/errorMessages';
import { formatPercent } from '@/utils/formatUtils';

/** How many categories are compared. */
const CATEGORY_LIMIT = 3;
/** Blue, red, amber: one per category, in the order the service returns them. */
const COLORS = [CHART_COLORS.info, CHART_COLORS.danger, CHART_COLORS.warning];

const tooltipOf = (stat: CategoryStat) =>
  `${stat.orderableItems} of ${stat.totalItems} dishes can be ordered now`;

/** "Menu Comparison": for the biggest categories, the share of dishes customers can order right now. */
export function MenuComparison() {
  const stats = useCategoryStats(CATEGORY_LIMIT);
  const [showChart, setShowChart] = useState(true);
  const [showValue, setShowValue] = useState(true);

  return (
    <Card
      title="Menu Comparison"
      subtitle="Share of each category's dishes that can be ordered now"
      actions={
        <div className="flex flex-wrap items-center gap-5">
          <Toggle label="Chart" checked={showChart} onChange={setShowChart} />
          <Toggle label="Show Value" checked={showValue} onChange={setShowValue} />
        </div>
      }
    >
      {stats.isPending ? (
        <div
          aria-label="Loading the menu comparison"
          className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-6"
          role="status"
        >
          {Array.from({ length: CATEGORY_LIMIT }, (_, index) => (
            <div key={index} className="flex flex-col items-center gap-3">
              <Skeleton className="size-28 rounded-full" />
              <Skeleton className="h-4 w-24" />
            </div>
          ))}
        </div>
      ) : stats.error ? (
        <ErrorMessage
          title={ERROR_MESSAGES.CATEGORY_STATS_LOAD_FAILED}
          error={stats.error}
          onRetry={() => void stats.refetch()}
          isRetrying={stats.isFetching}
        />
      ) : stats.data.length === 0 ? (
        <EmptyState title="No categories yet" />
      ) : showChart ? (
        <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-6">
          {stats.data.map((stat, index) => (
            <Tooltip
              key={stat.categoryId}
              label={tooltipOf(stat)}
              className="justify-center"
              placement="top"
            >
              <figure className="flex flex-col items-center gap-3">
                <DonutChart
                  size={112}
                  percent={stat.orderablePercent}
                  color={COLORS[index % COLORS.length]}
                  label={`${stat.categoryName}: ${formatPercent(stat.orderablePercent)} can be ordered now`}
                >
                  {showValue && (
                    // Smaller than the ring's default: it fits the small ring's hole
                    <span className="text-lg">{formatPercent(stat.orderablePercent)}</span>
                  )}
                </DonutChart>
                <figcaption className="text-sm font-semibold">{stat.categoryName}</figcaption>
              </figure>
            </Tooltip>
          ))}
        </div>
      ) : (
        <ul className="flex flex-col gap-5">
          {stats.data.map((stat, index) => (
            <li key={stat.categoryId}>
              <Tooltip label={tooltipOf(stat)} className="flex w-full" placement="top">
                <div className="w-full">
                  <div className="mb-1.5 flex items-center justify-between gap-3 text-sm">
                    <span className="font-semibold">{stat.categoryName}</span>
                    {showValue && (
                      <span className="font-bold">{formatPercent(stat.orderablePercent)}</span>
                    )}
                  </div>
                  <div
                    role="progressbar"
                    aria-label={`${stat.categoryName}: can be ordered now`}
                    aria-valuemin={0}
                    aria-valuemax={100}
                    aria-valuenow={Math.round(stat.orderablePercent)}
                    className="h-2.5 w-full overflow-hidden rounded-full bg-(--color-chart-track)"
                  >
                    <div
                      className="h-full rounded-full"
                      style={{
                        width: `${Math.min(100, Math.max(0, stat.orderablePercent))}%`,
                        backgroundColor: COLORS[index % COLORS.length],
                      }}
                    />
                  </div>
                </div>
              </Tooltip>
            </li>
          ))}
        </ul>
      )}
    </Card>
  );
}

function Toggle({
  label,
  checked,
  onChange,
}: {
  label: string;
  checked: boolean;
  onChange: (checked: boolean) => void;
}) {
  const id = useId();
  return (
    <label htmlFor={id} className="flex cursor-pointer items-center gap-2 text-sm font-medium">
      <input
        id={id}
        type="checkbox"
        checked={checked}
        onChange={(event) => onChange(event.target.checked)}
        className="size-4 cursor-pointer accent-brand-500"
      />
      {label}
    </label>
  );
}
