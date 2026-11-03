import { Bike, ChefHat, ClipboardList, RefreshCw, Timer } from 'lucide-react';

import {
  isoDayOrUndefined,
  ORDER_STATUS_TONES,
  OWNER_ORDER_MESSAGES,
  OWNER_ORDER_TAB_ICONS,
  OWNER_ORDER_TABS,
  OWNER_ORDERS_PAGE_SIZE,
  OwnerOrderFilterBar,
  OwnerOrderStatusTabs,
  OwnerOrderTable,
  OwnerOrderTableLoader,
  ownerOrderCountFilters,
  ownerOrderFilters,
  ownerOrderSortOf,
  ownerOrderTabOf,
  type OwnerOrderSort,
  type OwnerOrderTab,
  type OwnerOrderTabId,
} from '@/components/business/OwnerOrders';
import { Alert } from '@/components/common/Alert';
import { AsyncContent } from '@/components/common/AsyncContent';
import { Button } from '@/components/common/Button';
import { Card } from '@/components/common/Card';
import { ClearFiltersButton } from '@/components/common/ClearFiltersButton';
import { EmptyState } from '@/components/common/EmptyState';
import { LiveBadge } from '@/components/common/LiveBadge';
import { PageHero, type HeroStat } from '@/components/common/PageHero';
import { Pagination } from '@/components/common/Pagination';
import { TabPanel } from '@/components/common/Tabs';
import { useOwnerOrderCounts, useOwnerOrders } from '@/hooks/api/ownerHooks';
import { pageFromParam, pageToParam, useQueryParams } from '@/hooks/useQueryParams';
import { useOwnerRestaurant } from '@/layouts/owner/ownerRestaurantContext';
import { cn } from '@/utils/classNames';
import { formatRange, type DateRange } from '@/utils/dateRangeUtils';
import { toIsoDate } from '@/utils/dateUtils';
import { ERROR_MESSAGES } from '@/utils/errorMessages';
import { formatNumber } from '@/utils/formatUtils';
import { dateRangeError } from '@/utils/validationUtils';

// The tab, the days, the order and the page live in the address: a reload or the back button keeps what the owner
// was looking at
const PARAM_KEYS = ['tab', 'from', 'to', 'sort', 'page'] as const;

type Counts = Partial<Record<OwnerOrderTabId, number>>;

/** The sum of some statuses' counts; undefined while one of them is still loading. */
function sumOf(counts: Counts, ids: readonly OwnerOrderTabId[]): number | undefined {
  let total = 0;
  for (const id of ids) {
    const count = counts[id];
    if (count === undefined) {
      return undefined;
    }
    total += count;
  }
  return total;
}

/** The kitchen at a glance, from the tabs' own counts (each chip appears once its counts have loaded). */
function kitchenStatsOf(counts: Counts): HeroStat[] {
  const stats = [
    { label: 'Waiting for you', value: counts.new, icon: Timer },
    { label: 'In the kitchen', value: sumOf(counts, ['accepted', 'preparing']), icon: ChefHat },
    { label: 'Ready or on the way', value: sumOf(counts, ['ready', 'on-the-way']), icon: Bike },
  ];
  return stats.flatMap(({ value, ...stat }) =>
    value === undefined ? [] : [{ ...stat, value: formatNumber(value) }],
  );
}

/** "Orders": the restaurant's orders by status, with the kitchen's next step on each row. New orders appear by themselves. */
export default function OwnerOrdersPage() {
  const { restaurant, restaurantId } = useOwnerRestaurant();
  const [params, setParams] = useQueryParams(PARAM_KEYS);
  const tab = ownerOrderTabOf(params.tab);
  // The days of the address: both days, or from a day until today (a link from before the date range picker, which
  // finds the same orders); otherwise none
  const fromDate = isoDayOrUndefined(params.from);
  const toDate = fromDate ? (isoDayOrUndefined(params.to) ?? toIsoDate(new Date())) : undefined;
  const range: DateRange | undefined = fromDate && toDate ? { fromDate, toDate } : undefined;
  const rangeError = dateRangeError(fromDate, toDate);
  const sort = ownerOrderSortOf(params.sort, tab);
  // Each status's number of orders, for the same days as the list (none asked for an impossible range)
  const countRequests = OWNER_ORDER_TABS.map((statusTab) => ({
    key: statusTab.id,
    filters: ownerOrderCountFilters(statusTab, fromDate, toDate),
  }));
  const counts = useOwnerOrderCounts(restaurantId, countRequests, !rangeError);

  const clearDates = () => setParams({ from: undefined, to: undefined, sort: undefined });
  const tone = ORDER_STATUS_TONES[tab.status];
  const TabIcon = OWNER_ORDER_TAB_ICONS[tab.id];
  const tabCount = rangeError ? undefined : counts[tab.id];

  return (
    <div className="flex flex-col gap-6">
      <PageHero
        eyebrow={restaurant.name}
        title="Orders"
        subtitle={
          range && !rangeError
            ? `Placed ${range.fromDate === range.toDate ? 'on' : 'in'} ${formatRange(range)}`
            : 'What your kitchen has to cook, and what is done'
        }
        icon={ClipboardList}
        colour="sunset"
        actions={<LiveBadge />}
        stats={rangeError ? [] : kitchenStatsOf(counts)}
      />

      <OwnerOrderStatusTabs
        activeId={tab.id}
        counts={counts}
        // "New" is the default tab: it keeps the address short
        // A tab starts in its own order again
        onChange={(id) => setParams({ tab: id === 'new' ? undefined : id, sort: undefined })}
      />

      <TabPanel id={tab.id}>
        {/* One card in the open status's colour: its heading, the filters as its toolbar, the orders under them */}
        <Card>
          <div className="mb-5 flex items-center gap-3">
            <span
              aria-hidden="true"
              className={cn(
                'flex size-11 shrink-0 items-center justify-center rounded-2xl text-white',
                tone.fill,
                tone.glow,
              )}
            >
              <TabIcon className="size-5" />
            </span>
            <div className="min-w-0">
              <h2 className="text-lg font-bold text-ink">{tab.label} orders</h2>
              <p className={cn('text-sm font-semibold', tone.text)}>
                {tabCount === undefined
                  ? 'Counting…'
                  : tabCount === 1
                    ? '1 order'
                    : `${tabCount} orders`}
                {range && !rangeError && (
                  <span className="font-medium text-ink-soft"> · {formatRange(range)}</span>
                )}
              </p>
            </div>
          </div>
          <div className="rounded-2xl bg-page p-4 ring-1 ring-line sm:p-5">
            <OwnerOrderFilterBar
              range={range}
              sort={sort}
              onRangeChange={(next) => setParams({ from: next?.fromDate, to: next?.toDate })}
              // The tab's own order is not written into the address
              onSortChange={(next) =>
                setParams({ sort: next === ownerOrderSortOf(undefined, tab) ? undefined : next })
              }
              onClear={clearDates}
            />
          </div>
          <div className="pt-6">
            {rangeError ? (
              // An impossible range (an edited link) is explained and not searched for
              <EmptyState
                title={OWNER_ORDER_MESSAGES.BAD_DATES}
                message={rangeError}
                action={<ClearFiltersButton size="sm" onClick={clearDates} />}
              />
            ) : (
              <OwnerOrdersResults
                restaurantId={restaurantId}
                tab={tab}
                fromDate={fromDate}
                toDate={toDate}
                sort={sort}
                page={pageFromParam(params.page)}
                onPageChange={(page) => setParams({ page: pageToParam(page) })}
                onClearDates={clearDates}
              />
            )}
          </div>
        </Card>
      </TabPanel>
    </div>
  );
}

interface OwnerOrdersResultsProps {
  restaurantId: number;
  tab: OwnerOrderTab;
  fromDate?: string;
  toDate?: string;
  sort: OwnerOrderSort;
  /** Zero-based */
  page: number;
  onPageChange: (page: number) => void;
  onClearDates: () => void;
}

/** The orders of one tab and page: loading, error, empty, or the rows with the page buttons. */
function OwnerOrdersResults({
  restaurantId,
  tab,
  fromDate,
  toDate,
  sort,
  page,
  onPageChange,
  onClearDates,
}: OwnerOrdersResultsProps) {
  // live: asks again every few seconds, so a new order shows up without reloading
  const orders = useOwnerOrders(
    restaurantId,
    ownerOrderFilters(tab, fromDate, toDate, sort),
    page,
    OWNER_ORDERS_PAGE_SIZE,
    true,
  );
  const hasDates = Boolean(fromDate || toDate);
  // A failed refresh must not hide the orders already on screen
  const refreshFailed = Boolean(orders.error && orders.data);

  return (
    <AsyncContent
      isLoading={orders.isPending}
      error={orders.data ? null : orders.error}
      isEmpty={orders.data?.content.length === 0}
      errorTitle={ERROR_MESSAGES.ORDERS_LOAD_FAILED}
      onRetry={() => void orders.refetch()}
      isRetrying={orders.isFetching}
      loading={<OwnerOrderTableLoader />}
      empty={
        page > 0 ? (
          // The orders of this page were all handled meanwhile
          <EmptyState
            title="There are no more orders here"
            action={
              <Button variant="outline" size="sm" onClick={() => onPageChange(0)}>
                Back to the first page
              </Button>
            }
          />
        ) : hasDates ? (
          <EmptyState
            title={tab.emptyFilteredTitle}
            action={<ClearFiltersButton size="sm" onClick={onClearDates} />}
          />
        ) : (
          <EmptyState title={tab.emptyTitle} message={tab.emptyMessage} />
        )
      }
    >
      {orders.data && (
        <div className="flex flex-col gap-4">
          {/* The count is in the card's heading; here only how fresh the list is */}
          <p className="flex items-center gap-1.5 self-end rounded-full bg-page px-3 py-1.5 text-xs text-ink-soft ring-1 ring-line">
            <RefreshCw
              aria-hidden="true"
              className={cn(
                'size-3.5 text-brand-text',
                orders.isFetching && 'motion-safe:animate-spin',
              )}
            />
            Updates by itself. Last checked {new Date(orders.dataUpdatedAt).toLocaleTimeString()}.
          </p>
          {refreshFailed && (
            <Alert tone="warning">
              The orders could not be refreshed just now. This list may be out of date; we will try
              again shortly.
            </Alert>
          )}
          <OwnerOrderTable
            restaurantId={restaurantId}
            orders={orders.data.content}
            label={`${tab.label} orders`}
            isUpdating={orders.isPlaceholderData}
          />
          <Pagination
            page={orders.data.page}
            size={orders.data.size}
            totalPages={orders.data.totalPages}
            totalElements={orders.data.totalElements}
            onPageChange={onPageChange}
          />
        </div>
      )}
    </AsyncContent>
  );
}
