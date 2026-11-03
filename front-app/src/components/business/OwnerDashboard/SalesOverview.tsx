import {
  ArrowDownRight,
  ArrowUpRight,
  Ban,
  CalendarSearch,
  ClipboardList,
  Download,
  Flame,
  IndianRupee,
  Minus,
  ReceiptText,
} from 'lucide-react';

import {
  busiestDayOf,
  FigureNote,
  FigureTile,
  OrdersSalesChart,
  OrdersSalesLoader,
  salesDaysOf,
  salesHourReportRows,
  salesHoursOf,
  salesReportRows,
  SeriesLegend,
  useHiddenSeries,
  type SalesDay,
  type SalesHour,
} from '@/components/business/OrdersSalesChart';
import { OWNER_ORDER_TABS } from '@/components/business/OwnerOrders/ownerOrderUtils';
import { Alert } from '@/components/common/Alert';
import { AsyncContent } from '@/components/common/AsyncContent';
import { Button } from '@/components/common/Button';
import { Card } from '@/components/common/Card';
import { DateRangePicker } from '@/components/common/DateRangePicker';
import { EmptyState } from '@/components/common/EmptyState';
import { Skeleton, Spinner } from '@/components/common/Loader';
import { useToast } from '@/context/ToastContext';
import { useSalesSummary } from '@/hooks/api/ownerHooks';
import type { SalesSummary } from '@/types/owner';
import { cn } from '@/utils/classNames';
import { OWNER_ROUTES } from '@/utils/constants';
import { downloadCsv } from '@/utils/csvUtils';
import {
  dayCountLabel,
  daysIn,
  formatRange,
  presetLabel,
  presetRange,
  previousRangeOf,
  type DatePresetId,
  type DateRange,
} from '@/utils/dateRangeUtils';
import { formatDate, formatHour } from '@/utils/dateUtils';
import { formatCurrency, formatNumber, formatPercent } from '@/utils/formatUtils';

import { OrderStatusBreakdown } from './OrderStatusBreakdown';
import {
  DEFAULT_SALES_LABEL,
  percentChange,
  salesFiguresOf,
  type SalesFilter,
} from './salesFigures';

/** Longer ready-made ranges offered when the chosen days had no orders. */
const LONGER_PRESETS: readonly DatePresetId[] = ['30', '90'];

interface SalesOverviewProps {
  restaurantId: number;
  /** The days to show (from the address) */
  filter: SalesFilter;
  /** A ready-made range passes its id; days picked in the calendar pass none */
  onFilterChange: (range: DateRange, preset?: DatePresetId) => void;
}

/**
 * "Order & Sales Overview": the chosen days' sales, orders, average order and cancellations, compared with the same
 * number of days just before; the orders (stacked bars) and sales (area) day by day, or hour by hour for a single
 * day; and where the orders are now, each status opening its orders for the same days. The days are chosen with
 * the date range picker in the header (a ready-made range, one day or a custom range). Every figure is the
 * platform's own; they refresh by themselves like the rest of the dashboard. "Save report" downloads the days (or
 * hours) as a spreadsheet (CSV).
 */
export function SalesOverview({ restaurantId, filter, onFilterChange }: SalesOverviewProps) {
  const { showToast } = useToast();
  const { range, preset, problem } = filter;
  const previous = previousRangeOf(range);
  const sales = useSalesSummary(restaurantId, range.fromDate, range.toDate, true);
  const before = useSalesSummary(restaurantId, previous.fromDate, previous.toDate);
  const dayCount = daysIn(range);

  const saveReport = () => {
    const summary = sales.data;
    if (!summary) {
      return;
    }
    const rows =
      dayCount === 1 && summary.perHour
        ? salesHourReportRows(salesHoursOf(summary.perHour))
        : salesReportRows(salesDaysOf(summary.perDay ?? []));
    downloadCsv(`orders-and-sales_${range.fromDate}_${range.toDate}.csv`, rows);
    showToast('Report saved to your downloads.');
  };

  return (
    <Card
      compact
      title="Order & Sales Overview"
      subtitle={`${dayCount === 1 ? 'One day' : dayCountLabel(dayCount)}, compared with ${formatRange(previous)}`}
      actions={
        <div className="flex w-full flex-wrap items-center gap-2 sm:w-auto">
          <DateRangePicker
            label="Overview dates"
            value={range}
            preset={preset}
            onChange={(next, id) => next && onFilterChange(next, id)}
            className="min-w-0 flex-1 sm:w-80 sm:flex-none"
          />
          <Button
            variant="outline"
            onClick={saveReport}
            disabled={!sales.data || sales.isPlaceholderData}
            leftIcon={<Download aria-hidden="true" className="size-4" />}
            className="h-11"
          >
            <span className="max-sm:sr-only">Save report</span>
          </Button>
        </div>
      }
    >
      {problem && (
        <Alert tone="warning" title="The dates in this link could not be used" className="mb-4">
          {problem} Showing the {DEFAULT_SALES_LABEL.toLowerCase()} instead.
        </Alert>
      )}
      <AsyncContent
        isLoading={sales.isPending}
        // A failed refresh keeps the figures on screen; only a range that never loaded shows the error
        error={sales.data ? null : sales.error}
        errorTitle="Unable to load your orders and sales"
        onRetry={() => void sales.refetch()}
        isRetrying={sales.isFetching}
        loading={<OrdersSalesLoader label="Loading your orders and sales" figures={4} />}
      >
        {sales.data && (
          <div className="relative">
            {/* Other days were chosen: the figures on screen are the days before until the new ones arrive */}
            {sales.isPlaceholderData && (
              <p
                role="status"
                className="absolute -top-2 right-0 z-10 inline-flex items-center gap-2 rounded-full bg-surface px-3 py-1.5 text-xs font-semibold text-ink shadow-card ring-1 ring-line motion-safe:animate-pop-in"
              >
                <Spinner size="sm" label="Loading" />
                Loading {formatRange(range)}…
              </p>
            )}
            <div
              aria-busy={sales.isPlaceholderData}
              className={cn(
                'transition-opacity duration-300',
                sales.isPlaceholderData && 'pointer-events-none opacity-50',
              )}
            >
              <OverviewBody
                summary={sales.data}
                range={range}
                previousRange={previous}
                // The days before load on their own: no comparison until they are the right ones
                previous={before.isPlaceholderData ? undefined : before.data}
                previousFailed={!before.data && before.isError}
                longerRanges={LONGER_PRESETS.filter(
                  (id) => id !== preset && daysIn(presetRange(id)) > dayCount,
                )}
                onFilterChange={onFilterChange}
              />
            </div>
          </div>
        )}
      </AsyncContent>
    </Card>
  );
}

interface OverviewBodyProps {
  summary: SalesSummary;
  range: DateRange;
  previousRange: DateRange;
  /** The days before, for the comparison (undefined while they load or when they failed) */
  previous?: SalesSummary;
  previousFailed: boolean;
  /** Ready-made ranges to offer when these days had no orders */
  longerRanges: readonly DatePresetId[];
  onFilterChange: (range: DateRange, preset?: DatePresetId) => void;
}

/** "1 pm – 2 pm" */
const hourSpan = (hour: number) => `${formatHour(hour)} – ${formatHour((hour + 1) % 24)}`;

function OverviewBody({
  summary,
  range,
  previousRange,
  previous,
  previousFailed,
  longerRanges,
  onFilterChange,
}: OverviewBodyProps) {
  const { hidden, toggle } = useHiddenSeries();
  const figures = salesFiguresOf(summary);
  const earlier = previous && salesFiguresOf(previous);
  const money = (amount: number) => formatCurrency(amount, summary.currency);
  const dayCount = daysIn(range);
  // One day is drawn hour by hour (when the API sends the hours), a range day by day
  const hours = dayCount === 1 && summary.perHour ? salesHoursOf(summary.perHour) : null;
  const days = salesDaysOf(summary.perDay ?? []);
  const comparison = {
    failed: previousFailed,
    earlier: `${dayCount === 1 ? 'on' : 'in'} ${formatRange(previousRange)}`,
  };

  // Each status opens its tab of the Orders page, for the same days
  const ordersLink = (status: string) => {
    const tab = OWNER_ORDER_TABS.find((entry) => entry.status === status);
    if (!tab) {
      return undefined;
    }
    const params = new URLSearchParams({ from: range.fromDate, to: range.toDate });
    if (tab.id !== 'new') {
      params.set('tab', tab.id);
    }
    return `${OWNER_ROUTES.orders}?${params.toString()}`;
  };

  return (
    <div className="grid gap-5 xl:grid-cols-[minmax(0,1fr)_17rem]">
      <div className="flex min-w-0 flex-col gap-4">
        <ul aria-label="Key figures" className="grid grid-cols-2 gap-3 lg:grid-cols-4">
          <FigureTile
            icon={IndianRupee}
            colour="sky"
            label="Sales"
            hint="Value of the delivered orders"
            value={money(figures.sales)}
            note={
              <ChangeNote
                now={figures.sales}
                before={earlier?.sales}
                format={money}
                {...comparison}
              />
            }
          />
          <FigureTile
            icon={ClipboardList}
            colour="violet"
            label="Orders"
            hint="Orders placed, whatever their status now"
            value={formatNumber(figures.orders)}
            note={
              <ChangeNote
                now={figures.orders}
                before={earlier?.orders}
                format={formatNumber}
                {...comparison}
              />
            }
          />
          <FigureTile
            icon={ReceiptText}
            colour="green"
            label="Average order"
            hint="Sales divided by the delivered orders"
            value={figures.averageOrder === null ? '—' : money(figures.averageOrder)}
            note={
              <ChangeNote
                now={figures.averageOrder}
                before={earlier?.averageOrder}
                format={money}
                {...comparison}
              />
            }
          />
          <FigureTile
            icon={Ban}
            colour="rose"
            label="Cancellations"
            hint="Orders cancelled by the customer or rejected by the restaurant"
            value={formatNumber(figures.cancelled)}
            note={
              <FigureNote
                text={
                  figures.cancelledShare === null
                    ? 'No orders yet'
                    : `${formatPercent(figures.cancelledShare)} of the orders`
                }
              />
            }
          />
        </ul>

        {figures.orders === 0 ? (
          <EmptyState
            icon={CalendarSearch}
            title={`No orders ${dayCount === 1 ? 'on' : 'in'} ${formatRange(range)}`}
            message={
              longerRanges.length > 0
                ? 'Try a longer range to see earlier orders.'
                : 'Orders will show here day by day as they come in.'
            }
            action={
              longerRanges.length > 0 && (
                <div className="flex flex-wrap justify-center gap-2">
                  {longerRanges.map((id) => (
                    <Button
                      key={id}
                      variant="outline"
                      size="sm"
                      onClick={() => onFilterChange(presetRange(id), id)}
                    >
                      Show the {presetLabel(id).toLowerCase()}
                    </Button>
                  ))}
                </div>
              )
            }
          />
        ) : (
          <div className="flex flex-col gap-3">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <SeriesLegend
                counts={{
                  completed: figures.delivered,
                  inProgress: figures.inProgress,
                  cancelled: figures.cancelled,
                }}
                hidden={hidden}
                onToggle={toggle}
              />
              <span className="text-xs font-semibold text-ink-soft">
                {hours ? 'Hour by hour' : 'Day by day'}
              </span>
            </div>
            {hours ? (
              <OrdersSalesChart
                hours={hours}
                currency={summary.currency}
                hidden={hidden}
                summary={chartSummary(range, figures.orders, money(figures.sales), hours)}
              />
            ) : (
              <OrdersSalesChart
                days={days}
                currency={summary.currency}
                hidden={hidden}
                summary={chartSummary(range, figures.orders, money(figures.sales), days)}
              />
            )}
            <BusiestNote points={hours ?? days} />
          </div>
        )}
      </div>

      <OrderStatusBreakdown
        ordersByStatus={summary.ordersByStatus}
        linkOf={ordersLink}
        className="border-t border-line pt-4 xl:border-t-0 xl:border-l xl:pt-0 xl:pl-5"
      />
    </div>
  );
}

/** What screen readers hear instead of the chart. */
function chartSummary(
  range: DateRange,
  orders: number,
  sales: string,
  points: readonly SalesDay[] | readonly SalesHour[],
): string {
  const busiest = busiestDayOf<SalesDay | SalesHour>(points);
  const busiestText = !busiest
    ? '.'
    : 'date' in busiest
      ? `; busiest day ${formatDate(busiest.date)} with ${formatNumber(busiest.orders)}.`
      : `; busiest hour ${hourSpan(busiest.hour)} with ${formatNumber(busiest.orders)}.`;
  const per = daysIn(range) === 1 ? 'hour' : 'day';
  return `Orders and sales per ${per}, ${formatRange(range)}: ${formatNumber(orders)} orders and ${sales} in sales${busiestText}`;
}

/** "Busiest day: Thu, 16 Oct 2026 · 6 orders" (or the busiest hour of a single day). */
function BusiestNote({ points }: { points: readonly SalesDay[] | readonly SalesHour[] }) {
  const busiest = busiestDayOf<SalesDay | SalesHour>(points);
  if (!busiest) {
    return null;
  }
  const isDay = 'date' in busiest;
  return (
    <p className="inline-flex flex-wrap items-center gap-x-2 gap-y-1 self-start rounded-full bg-st-placed-soft px-3 py-1.5 text-xs text-st-placed-text">
      <Flame aria-hidden="true" className="size-3.5" />
      <span className="font-semibold">Busiest {isDay ? 'day' : 'hour'}:</span>
      <span className="font-bold">
        {isDay
          ? formatRange({ fromDate: busiest.date, toDate: busiest.date })
          : hourSpan(busiest.hour)}
      </span>
      <span>
        · {formatNumber(busiest.orders)} {busiest.orders === 1 ? 'order' : 'orders'}
      </span>
    </p>
  );
}

interface ChangeNoteProps {
  /** This range's value; null when there is none (an average without delivered orders) */
  now: number | null;
  /** The days before: undefined while they load, null when there is no value */
  before: number | null | undefined;
  format: (value: number) => string;
  failed: boolean;
  /** When "before" was: 'in 19 – 25 Oct 2026', 'on Fri, 31 Oct 2026' */
  earlier: string;
}

/**
 * How a figure changed from the same number of days before: an arrow and a percentage, then the earlier value. Kept
 * short (the card's subtitle says which days "before" are); the whole sentence is the tooltip.
 */
function ChangeNote({ now, before, format, failed, earlier }: ChangeNoteProps) {
  if (failed) {
    return (
      <FigureNote text="Comparison unavailable" title={`The days ${earlier} could not be loaded`} />
    );
  }
  if (before === undefined) {
    return <Skeleton className="h-4 w-28" />;
  }
  if (before === null) {
    return <FigureNote text="None delivered before" title={`No delivered orders ${earlier}`} />;
  }
  if (now === null) {
    return <FigureNote text={`${format(before)} before`} title={`${format(before)} ${earlier}`} />;
  }
  const change = percentChange(now, before);
  if (change === null) {
    return now > 0 ? (
      <FigureNote text="New: none before" title={`None ${earlier}`} />
    ) : (
      <FigureNote text="None before either" title={`None ${earlier} either`} />
    );
  }
  const rounded = Math.round(change);
  const direction = rounded > 0 ? 'up' : rounded < 0 ? 'down' : 'flat';
  const Arrow = direction === 'up' ? ArrowUpRight : direction === 'down' ? ArrowDownRight : Minus;
  return (
    <span
      className="flex min-w-0 items-center gap-1.5 text-xs"
      title={`${format(before)} ${earlier}`}
    >
      <span
        className={cn(
          'inline-flex shrink-0 items-center gap-0.5 rounded-full bg-surface px-1.5 py-0.5 font-bold',
          direction === 'up' && 'text-st-delivered-text',
          direction === 'down' && 'text-st-rejected-text',
          direction === 'flat' && 'text-ink-soft',
        )}
      >
        <Arrow aria-hidden="true" className="size-3.5" />
        <span className="sr-only">
          {direction === 'up' ? 'Up ' : direction === 'down' ? 'Down ' : 'No change, '}
        </span>
        {formatPercent(Math.abs(change))}
      </span>
      <span className="truncate text-ink-soft">from {format(before)}</span>
    </span>
  );
}
