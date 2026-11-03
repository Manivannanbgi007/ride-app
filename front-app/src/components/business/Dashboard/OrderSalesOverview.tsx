import { ClipboardList, Download, IndianRupee, ReceiptText, Undo2 } from 'lucide-react';
import { useState } from 'react';

import { OrderRatesPanel } from '@/components/business/Dashboard/OrderRatesPanel';
import { OverviewRangeFilter } from '@/components/business/Dashboard/OverviewRangeFilter';
import {
  busiestDayOf,
  FigureNote,
  FigureTile,
  OrdersSalesChart,
  OrdersSalesLoader,
  salesDaysOf,
  salesReportRows,
  SeriesLegend,
  useHiddenSeries,
} from '@/components/business/OrdersSalesChart';
import { PartialDataAlert } from '@/components/business/PartialDataAlert';
import { AsyncContent } from '@/components/common/AsyncContent';
import { Button } from '@/components/common/Button';
import { Card } from '@/components/common/Card';
import { EmptyState } from '@/components/common/EmptyState';
import { useToast } from '@/context/ToastContext';
import { useOrderActivity } from '@/hooks/api/dashboardHooks';
import type { DashboardResponse, DayPoint, PaymentOutcome } from '@/types/dashboard';
import { cn } from '@/utils/classNames';
import type { DashboardPeriodDays } from '@/utils/constants';
import { downloadCsv } from '@/utils/csvUtils';
import { orderFigures } from '@/utils/dashboardUtils';
import { formatDate } from '@/utils/dateUtils';
import { ERROR_MESSAGES } from '@/utils/errorMessages';
import { formatCurrency, formatNumber } from '@/utils/formatUtils';
import {
  activityLabel,
  activityRange,
  rollingDaysOf,
  rollingModeOf,
  rollingRange,
  type ActivityMode,
  type ActivityUnit,
} from '@/utils/orderActivityUtils';

interface OrderSalesOverviewProps {
  dashboard: DashboardResponse;
  /** The dashboard's own period ("Filter Period"): its days are already loaded with the dashboard */
  days: DashboardPeriodDays;
}

/** The days the overview draws, with their online payments, from whichever answer covers them. */
interface OverviewData {
  days: readonly DayPoint[];
  payments: PaymentOutcome | null;
  /** Services that did not answer: their figures are unknown, not zero */
  unavailable: readonly string[];
  currency: string;
}

/**
 * "Order & Sales Overview": the chosen days' sales, orders, average order and online refunds; the orders (stacked
 * bars) and sales (area) day by day; and the delivered, cancelled and payment rates. It opens on the dashboard's own
 * period, whose figures came with the dashboard (nothing more is asked); another range asks the order-activity API
 * for just those days, and its wait or failure stays inside this card. "Save Report" downloads the days (CSV).
 */
export function OrderSalesOverview({ dashboard, days }: OrderSalesOverviewProps) {
  const { showToast } = useToast();
  const [chosenMode, setChosenMode] = useState<ActivityMode | null>(null);
  const [chosenDay, setChosenDay] = useState<string | null>(null);
  // "Today" is the business day the server reports, not the browser's
  const today = dashboard.today;
  const anchor = chosenDay ?? today;
  // Until the admin picks a range, the overview follows the dashboard's period
  const mode = chosenMode ?? rollingModeOf(days);
  const rollingDays = rollingDaysOf(mode);
  const fromDashboard = rollingDays === days;

  const range =
    rollingDays === null
      ? activityRange(mode as ActivityUnit, anchor, today)
      : fromDashboard
        ? { fromDate: dashboard.chartsFrom, toDate: today }
        : rollingRange(rollingDays, today);
  const activity = useOrderActivity(fromDashboard ? null : range);
  const label =
    rollingDays === null
      ? activityLabel(mode as ActivityUnit, range)
      : `${formatDate(range.fromDate)} - ${formatDate(range.toDate)}`;
  const waiting = !fromDashboard && activity.isPlaceholderData;

  const data: OverviewData | undefined = fromDashboard
    ? {
        days: dashboard.charts.ordersPerDay,
        payments: dashboard.charts.paymentOutcome,
        unavailable: dashboard.unavailable,
        currency: dashboard.currency,
      }
    : activity.data && {
        days: activity.data.ordersPerDay,
        payments: activity.data.paymentOutcome,
        unavailable: activity.data.unavailable,
        currency: activity.data.currency ?? dashboard.currency,
      };

  const saveReport = () => {
    if (!data) {
      return;
    }
    downloadCsv(
      `orders-per-day_${range.fromDate}_${range.toDate}.csv`,
      salesReportRows(salesDaysOf(data.days)),
    );
    showToast('Report saved to your downloads.');
  };

  return (
    <Card
      compact
      title="Order & Sales Overview"
      subtitle={<span aria-live="polite">{waiting ? `Loading ${label}...` : label}</span>}
      actions={
        <Button
          variant="outline"
          size="sm"
          onClick={saveReport}
          disabled={!data || data.days.length === 0 || waiting}
          leftIcon={<Download aria-hidden="true" className="size-4" />}
        >
          <span className="max-sm:sr-only">Save Report</span>
        </Button>
      }
    >
      <div className="flex flex-col gap-4">
        <OverviewRangeFilter
          mode={mode}
          anchor={anchor}
          today={today}
          onModeChange={setChosenMode}
          onAnchorChange={setChosenDay}
        />

        {fromDashboard && data ? (
          <OverviewBody data={data} />
        ) : (
          <AsyncContent
            isLoading={activity.isPending}
            error={activity.error}
            errorTitle={ERROR_MESSAGES.ORDER_ACTIVITY_LOAD_FAILED}
            onRetry={() => void activity.refetch()}
            isRetrying={activity.isFetching}
            loading={<OrdersSalesLoader label="Loading the orders and sales" figures={4} />}
          >
            {data && (
              <div
                aria-busy={waiting}
                className={cn(
                  'flex flex-col gap-4 transition-opacity duration-200',
                  waiting && 'opacity-60',
                )}
              >
                <PartialDataAlert unavailable={data.unavailable} />
                <OverviewBody data={data} />
              </div>
            )}
          </AsyncContent>
        )}
      </div>
    </Card>
  );
}

function OverviewBody({ data }: { data: OverviewData }) {
  const { hidden, toggle } = useHiddenSeries();
  const figures = orderFigures(data.days, data.payments, data.unavailable);
  const { totals } = figures;
  const chartDays = salesDaysOf(data.days);
  const money = (amount: number) => formatCurrency(amount, data.currency);
  const refundedAmount = data.payments?.refundedAmount;
  // One decimal: "2.4 a day"
  const perDay =
    totals && data.days.length > 0
      ? Math.round((totals.orders / data.days.length) * 10) / 10
      : null;
  const busiest = busiestDayOf(chartDays);

  const first = data.days[0]?.date;
  const last = data.days[data.days.length - 1]?.date;
  const chartSummary =
    totals && first && last
      ? `Orders and sales per day from ${formatDate(first)} to ${formatDate(last)}: ` +
        `${formatNumber(totals.orders)} orders and ${money(totals.revenue)} in sales` +
        (busiest
          ? `; busiest day ${formatDate(busiest.date)} with ${formatNumber(busiest.orders)}.`
          : '.')
      : '';

  return (
    <div className="grid gap-5 xl:grid-cols-[minmax(0,1fr)_16rem]">
      <div className="flex min-w-0 flex-col gap-4">
        <ul aria-label="Key figures" className="grid grid-cols-2 gap-3 lg:grid-cols-4">
          <FigureTile
            icon={IndianRupee}
            colour="sky"
            label="Sales"
            hint="The value of the delivered orders, before refunds (cancelled orders are not counted)"
            value={totals ? money(totals.revenue) : '—'}
            note={<FigureNote text="Before refunds" />}
          />
          <FigureTile
            icon={ClipboardList}
            colour="violet"
            label="Orders"
            hint="Orders placed on these days, whatever their status now"
            value={totals ? formatNumber(totals.orders) : '—'}
            note={
              <FigureNote
                text={perDay === null ? 'Not available' : `${formatNumber(perDay)} a day`}
              />
            }
          />
          <FigureTile
            icon={ReceiptText}
            colour="green"
            label="Average order"
            hint="Sales divided by the delivered orders"
            value={totals && totals.delivered > 0 ? money(totals.revenue / totals.delivered) : '—'}
            note={
              <FigureNote
                text={totals ? `${formatNumber(totals.delivered)} delivered` : 'Not available'}
              />
            }
          />
          <FigureTile
            icon={Undo2}
            colour="sunset"
            label="Online refunds"
            hint="Money returned on these days' online payments, full and partial refunds, whatever the order's status. Sales are not reduced by it."
            value={
              refundedAmount === undefined || refundedAmount === null ? '—' : money(refundedAmount)
            }
            note={
              <FigureNote
                text={
                  data.payments
                    ? `${formatNumber(data.payments.refunded)} refunded in full`
                    : 'Payments not available'
                }
              />
            }
          />
        </ul>

        {totals &&
          (totals.orders === 0 ? (
            <EmptyState title="No orders in this period" />
          ) : (
            <div className="flex flex-col gap-3">
              <SeriesLegend
                counts={{
                  completed: totals.delivered,
                  inProgress: Math.max(0, totals.orders - totals.delivered - totals.cancelled),
                  cancelled: totals.cancelled,
                }}
                hidden={hidden}
                onToggle={toggle}
              />
              <OrdersSalesChart
                days={chartDays}
                currency={data.currency}
                hidden={hidden}
                summary={chartSummary}
              />
            </div>
          ))}
      </div>

      <OrderRatesPanel
        figures={figures}
        payments={data.payments}
        className="border-t border-line pt-4 xl:border-t-0 xl:border-l xl:pt-0 xl:pl-5"
      />
    </div>
  );
}
