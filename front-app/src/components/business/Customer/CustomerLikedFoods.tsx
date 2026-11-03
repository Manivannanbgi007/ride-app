import { useState } from 'react';

import { LikedFoodsChart } from '@/components/business/Customer/LikedFoodsChart';
import { LikedFoodsLegend } from '@/components/business/Customer/LikedFoodsLegend';
import { AsyncContent } from '@/components/common/AsyncContent';
import { Card } from '@/components/common/Card';
import { EmptyState } from '@/components/common/EmptyState';
import { Skeleton } from '@/components/common/Loader';
import { SegmentedControl } from '@/components/common/SegmentedControl';
import { useCustomerLikedDishes } from '@/hooks/api/reviewHooks';
import {
  LIKED_DISHES_PERIODS,
  type CustomerLikedDishes,
  type LikedDishesPeriod,
} from '@/types/review';
import { formatRating } from '@/utils/customerUtils';
import { ERROR_MESSAGES } from '@/utils/errorMessages';

/** The chart shows up to 7 dishes, the legend under it the top 4. */
const LIKED_DISHES_LIMIT = 7;
/**
 * The design offers Monthly | Weekly | Daily. A customer rates few dishes, so a single day is almost always empty:
 * "All time" takes the place of "Daily".
 */
const PERIOD_LABELS: Record<LikedDishesPeriod, string> = {
  ALL: 'All time',
  MONTHLY: 'Monthly',
  WEEKLY: 'Weekly',
};
const PERIOD_OPTIONS = LIKED_DISHES_PERIODS.map((id) => ({ id, label: PERIOD_LABELS[id] }));
const PANEL_ID = 'customer-liked-foods-panel';

/** The text alternative of the chart: every dish with its average rating. */
function summarize(data: CustomerLikedDishes): string {
  const dishes = data.dishes
    .map((dish) => `${dish.dishName} ${formatRating(dish.averageRating)} out of 5`)
    .join(', ');
  return `Average rating per dish: ${dishes}.`;
}

/** "Most Liked Food": the dishes this customer rated highest (all time, last 30 days or 7 days). Loads its own data. */
export function CustomerLikedFoods({ customerId }: { customerId: number }) {
  const [period, setPeriod] = useState<LikedDishesPeriod>('ALL');
  const liked = useCustomerLikedDishes(customerId, period, LIKED_DISHES_LIMIT);

  return (
    <Card
      title="Most Liked Food"
      subtitle="Dishes this customer rated highest"
      actions={
        <SegmentedControl
          label="Most liked food period"
          options={PERIOD_OPTIONS}
          value={period}
          onChange={setPeriod}
          panelId={PANEL_ID}
        />
      }
    >
      <div id={PANEL_ID} role="tabpanel" aria-label={`${PERIOD_LABELS[period]} most liked food`}>
        <AsyncContent
          isLoading={liked.isPending}
          error={liked.error}
          isEmpty={liked.data?.dishes.length === 0}
          errorTitle={ERROR_MESSAGES.CUSTOMER_LIKED_FOODS_LOAD_FAILED}
          onRetry={() => void liked.refetch()}
          isRetrying={liked.isFetching}
          loading={
            <div role="status" aria-label="Loading" className="flex flex-col gap-4">
              <Skeleton className="h-64 w-full" />
              <Skeleton className="h-10 w-full" />
            </div>
          }
          empty={<EmptyState title="No ratings from this customer yet" />}
        >
          {liked.data && (
            <div className="flex flex-col gap-6">
              <LikedFoodsChart dishes={liked.data.dishes} summary={summarize(liked.data)} />
              <LikedFoodsLegend dishes={liked.data.dishes} totalRatings={liked.data.ratingCount} />
            </div>
          )}
        </AsyncContent>
      </div>
    </Card>
  );
}
