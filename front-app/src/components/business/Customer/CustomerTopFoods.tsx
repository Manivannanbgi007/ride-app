import { useState } from 'react';

import { CustomerTopFoodRow } from '@/components/business/Customer/CustomerTopFoodRow';
import { AsyncContent } from '@/components/common/AsyncContent';
import { Card } from '@/components/common/Card';
import { EmptyState } from '@/components/common/EmptyState';
import { Skeleton } from '@/components/common/Loader';
import { SegmentedControl } from '@/components/common/SegmentedControl';
import { useCustomerTopFoods } from '@/hooks/api/orderHooks';
import { DISH_REVENUE_PERIODS, type DishRevenuePeriod } from '@/types/order';
import { ERROR_MESSAGES } from '@/utils/errorMessages';

const TOP_FOODS_LIMIT = 5;
const PERIOD_LABELS: Record<DishRevenuePeriod, string> = {
  MONTHLY: 'Monthly',
  WEEKLY: 'Weekly',
  DAILY: 'Daily',
};
const PERIOD_OPTIONS = DISH_REVENUE_PERIODS.map((id) => ({ id, label: PERIOD_LABELS[id] }));
const PANEL_ID = 'customer-top-foods-panel';

/** "Most Ordered Food": the dishes this customer orders most (last 30 days, 7 days or today). Loads its own data. */
export function CustomerTopFoods({ customerId }: { customerId: number }) {
  const [period, setPeriod] = useState<DishRevenuePeriod>('MONTHLY');
  const foods = useCustomerTopFoods(customerId, period, TOP_FOODS_LIMIT);

  return (
    <Card
      title="Most Ordered Food"
      subtitle="What this customer orders most"
      actions={
        <SegmentedControl
          label="Most ordered food period"
          options={PERIOD_OPTIONS}
          value={period}
          onChange={setPeriod}
          panelId={PANEL_ID}
        />
      }
    >
      <div id={PANEL_ID} role="tabpanel" aria-label={`${PERIOD_LABELS[period]} most ordered food`}>
        <AsyncContent
          isLoading={foods.isPending}
          error={foods.error}
          isEmpty={foods.data?.items.length === 0}
          errorTitle={ERROR_MESSAGES.CUSTOMER_TOP_FOODS_LOAD_FAILED}
          onRetry={() => void foods.refetch()}
          isRetrying={foods.isFetching}
          loading={
            <div role="status" aria-label="Loading" className="flex flex-col gap-4">
              {Array.from({ length: 4 }, (_, index) => (
                <Skeleton key={index} className="h-16 w-full" />
              ))}
            </div>
          }
          empty={<EmptyState title="No orders in this period" />}
        >
          {foods.data && (
            <ul className="-my-4 divide-y divide-line">
              {foods.data.items.map((food) => (
                <CustomerTopFoodRow key={food.menuItemId} food={food} />
              ))}
            </ul>
          )}
        </AsyncContent>
      </div>
    </Card>
  );
}
