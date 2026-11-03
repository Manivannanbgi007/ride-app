import { ClipboardList, IndianRupee, Timer, TrendingUp } from 'lucide-react';

import { OrderFilterBar, OrderStatusChips, OrderTable } from '@/components/business/Order';
import { Card } from '@/components/common/Card';
import { PagedResults } from '@/components/common/PagedResults';
import { PageHero, type HeroStat } from '@/components/common/PageHero';
import { useOrders } from '@/hooks/api/orderHooks';
import { useAppDispatch, useAppSelector } from '@/store/hooks';
import {
  orderFiltersChanged,
  orderFiltersCleared,
  orderPageChanged,
  selectOrderFilters,
  selectOrderPage,
} from '@/store/slices/orderListSlice';
import type { PageResponse } from '@/types/api';
import type { OrderStatus, OrderSummary } from '@/types/order';
import { DEFAULT_CURRENCY, DEFAULT_PAGE_SIZE, NOT_AVAILABLE } from '@/utils/constants';
import { ERROR_MESSAGES } from '@/utils/errorMessages';
import { formatCurrency, formatNumber } from '@/utils/formatUtils';
import { stagger } from '@/utils/motion';
import { dateRangeError } from '@/utils/validationUtils';

/** Each block rises into place a little after the one above it */
const RISE = 'motion-safe:animate-rise-in stagger';

/** Orders that are over: everything else is still under way */
const FINISHED: readonly OrderStatus[] = ['DELIVERED', 'CANCELLED', 'REJECTED'];

/** The hero's figures: how many orders the list found, and the value, average and orders under way of this page. */
function orderFigures(data?: PageResponse<OrderSummary>): HeroStat[] {
  const rows = data?.content ?? [];
  const value = rows.reduce((sum, order) => sum + order.totalAmount, 0);
  const money = (amount: number) =>
    rows.length > 0 ? formatCurrency(amount, DEFAULT_CURRENCY) : NOT_AVAILABLE;
  return [
    {
      label: 'Orders',
      value: data ? formatNumber(data.totalElements) : NOT_AVAILABLE,
      icon: ClipboardList,
    },
    { label: 'Value on this page', value: money(value), icon: IndianRupee },
    { label: 'Average on this page', value: money(value / rows.length), icon: TrendingUp },
    {
      label: 'Under way on this page',
      value: data
        ? formatNumber(rows.filter((order) => !FINISHED.includes(order.status)).length)
        : NOT_AVAILABLE,
      icon: Timer,
    },
  ];
}

/** "Order List": every order on the platform, newest first, with filters (kept while navigating). */
export default function OrderListPage() {
  const dispatch = useAppDispatch();
  const filters = useAppSelector(selectOrderFilters);
  const page = useAppSelector(selectOrderPage);
  // An impossible date range is explained in the filters and not searched for
  const isRangeValid = !dateRangeError(filters.fromDate, filters.toDate);
  const orders = useOrders(filters, page, DEFAULT_PAGE_SIZE, isRangeValid);
  const clearFilters = () => dispatch(orderFiltersCleared());
  // The chosen chip shows how many orders it found, once they are the ones on screen
  const found = orders.isPlaceholderData ? undefined : orders.data?.totalElements;

  return (
    <div className="flex flex-col gap-6">
      <PageHero
        title="Order List"
        eyebrow="Sales"
        icon={ClipboardList}
        colour="sunset"
        subtitle="Every order on the platform, newest first; an order's status opens what can be done with it"
        stats={orderFigures(orders.data)}
      />

      <div style={stagger(2)} className={RISE}>
        <Card>
          <OrderFilterBar
            filters={filters}
            onChange={(changes) => dispatch(orderFiltersChanged(changes))}
            onClear={clearFilters}
          />
        </Card>
      </div>

      <div style={stagger(3)} className={RISE}>
        <Card>
          <div className="flex flex-col gap-4">
            <OrderStatusChips
              status={filters.status}
              count={found}
              onChange={(status) => dispatch(orderFiltersChanged({ status }))}
            />
            <PagedResults
              query={orders}
              errorTitle={ERROR_MESSAGES.ORDERS_LOAD_FAILED}
              emptyTitle="No orders yet"
              filteredEmptyTitle="No orders match these filters"
              hasFilters={Object.keys(filters).length > 0}
              onClearFilters={clearFilters}
              onPageChange={(newPage) => dispatch(orderPageChanged(newPage))}
              isDisabled={!isRangeValid}
              columns={7}
            >
              {(rows, isUpdating) => <OrderTable orders={rows} isUpdating={isUpdating} />}
            </PagedResults>
          </div>
        </Card>
      </div>
    </div>
  );
}
