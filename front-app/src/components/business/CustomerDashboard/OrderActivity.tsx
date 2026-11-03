import { Activity, BarChart3 } from 'lucide-react';
import { useState } from 'react';
import { Link } from 'react-router';

import { AsyncContent } from '@/components/common/AsyncContent';
import { Button } from '@/components/common/Button';
import { Skeleton } from '@/components/common/Loader';
import { SegmentedControl } from '@/components/common/SegmentedControl';
import { useMyOrderActivity } from '@/hooks/api/customerDashboardHooks';
import { cn } from '@/utils/classNames';
import { CUSTOMER_ROUTES } from '@/utils/constants';
import { formatDate, formatDayMonth } from '@/utils/dateUtils';

import { figuresOf } from './activityFigures';
import {
  ACTIVITY_PERIODS,
  daysIn,
  previousRangeOf,
  rangeOf,
  type ActivityPeriod,
} from './activityPeriods';
import { ActivityChart, Highlights, PeriodFigures, StatusBreakdown } from './ActivityPanels';
import { SectionHeading } from './SectionHeading';

/**
 * "Your order activity" for the last 7 days, the last 30 days or this calendar month, in one compact card: the
 * period in four figures (orders and spending compared with the period of the same length before), one chart
 * (spending per day, adding up, or orders per day), where the orders stand now as a small ring, and a few
 * highlights. Every figure is added up by order-service for this customer; days without orders count as zero.
 */
export function OrderActivity() {
  const [period, setPeriod] = useState<ActivityPeriod>('month');
  const range = rangeOf(period);
  const before = previousRangeOf(range);
  const days = daysIn(range);
  const activity = useMyOrderActivity(range.fromDate, range.toDate);
  const previous = useMyOrderActivity(before.fromDate, before.toDate);

  const figures = activity.data ? figuresOf(activity.data) : undefined;
  // While another period loads, the one before is still on screen: it is not compared with anything
  const previousFigures =
    previous.data && !previous.isPlaceholderData ? figuresOf(previous.data) : undefined;
  const currency = activity.data?.currency ?? 'INR';

  return (
    <section aria-labelledby="activity-title" className="flex flex-col gap-3">
      <SectionHeading
        id="activity-title"
        icon={Activity}
        colour="violet"
        title="Your order activity"
        subtitle={`${formatDayMonth(range.fromDate)} – ${formatDate(range.toDate)}`}
      >
        <SegmentedControl
          label="Period"
          options={ACTIVITY_PERIODS}
          value={period}
          onChange={setPeriod}
        />
      </SectionHeading>
      <AsyncContent
        isLoading={activity.isPending}
        error={activity.error}
        errorTitle="Unable to load your order activity"
        onRetry={() => void activity.refetch()}
        isRetrying={activity.isFetching}
        loading={<Skeleton className="h-[30rem] w-full rounded-2xl bg-surface" />}
      >
        {figures &&
          (figures.orders === 0 ? (
            <p className="flex flex-wrap items-center gap-x-3 gap-y-1 rounded-2xl bg-surface px-4 py-3 text-sm text-ink-soft shadow-card ring-1 ring-line/60">
              <BarChart3 aria-hidden="true" className="size-4 shrink-0 text-st-accepted-text" />
              <span>
                <span className="font-semibold text-ink">No orders in this period.</span> Your
                orders and spending show up here, day by day, as soon as you order.
              </span>
              <Link
                to={CUSTOMER_ROUTES.restaurants}
                className="rounded-md font-semibold text-brand-text hover:underline focus-visible:ring-4 focus-visible:ring-brand-500/30 focus-visible:outline-none"
              >
                Find food
              </Link>
            </p>
          ) : (
            <div
              aria-busy={activity.isPlaceholderData}
              className={cn(
                'flex flex-col gap-4 rounded-2xl bg-surface p-4 shadow-card ring-1 ring-line/60 transition-opacity sm:p-5',
                activity.isPlaceholderData && 'opacity-60',
              )}
            >
              <PeriodFigures
                figures={figures}
                previous={previousFigures}
                days={days}
                currency={currency}
              />
              {previous.error && (
                <p
                  role="status"
                  className="-mt-2 flex flex-wrap items-center gap-2 text-xs text-ink-soft"
                >
                  The previous {days} days could not be loaded to compare with.
                  <Button
                    variant="ghost"
                    size="sm"
                    isLoading={previous.isFetching}
                    onClick={() => void previous.refetch()}
                  >
                    Try again
                  </Button>
                </p>
              )}
              <div className="grid gap-5 lg:grid-cols-[minmax(0,1fr)_16rem]">
                <ActivityChart figures={figures} currency={currency} />
                <StatusBreakdown figures={figures} />
              </div>
              <Highlights figures={figures} days={days} currency={currency} />
            </div>
          ))}
      </AsyncContent>
    </section>
  );
}
