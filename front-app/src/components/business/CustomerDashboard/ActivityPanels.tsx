import {
  CalendarCheck,
  CircleCheckBig,
  CircleX,
  ClipboardList,
  Flame,
  IndianRupee,
  Minus,
  ReceiptText,
  TrendingDown,
  TrendingUp,
  Wallet,
  type LucideIcon,
} from 'lucide-react';
import { useState, type ReactNode } from 'react';

import { SegmentedControl } from '@/components/common/SegmentedControl';
import { cn } from '@/utils/classNames';
import { formatWeekdayDayMonth } from '@/utils/dateUtils';
import { formatCurrency, formatNumber, formatPercent } from '@/utils/formatUtils';

import { OrdersChart, SpendingChart, StatusDonut } from './ActivityCharts';
import { changeOf, ORDER_SERIES, type ActivityFigures } from './activityFigures';
import { PALETTE, type PaletteColour } from './palette';
import { ColourIcon } from './SectionHeading';

/**
 * How a figure compares with the period of the same length before: "↑ 50%" (or "↓", or "Same"), and when there was
 * nothing before, says so. Nothing while the earlier period loads.
 */
function ChangeNote({
  now,
  before,
  days,
}: {
  now: number;
  before: number | undefined;
  days: number;
}) {
  if (before === undefined) {
    return <span className="h-4" />;
  }
  const versus = `the previous ${days} days`;
  const change = changeOf(now, before);
  if (change === null) {
    return (
      <span className="text-[11px] text-ink-soft">
        {now > 0 ? `None in ${versus}` : `None in ${versus} either`}
      </span>
    );
  }
  const rounded = Math.round(change);
  const Icon = rounded > 0 ? TrendingUp : rounded < 0 ? TrendingDown : Minus;
  const said =
    rounded === 0
      ? `Same as ${versus}`
      : `${rounded > 0 ? 'Up' : 'Down'} ${formatPercent(Math.abs(rounded))} on ${versus}`;
  return (
    <span className="flex flex-wrap items-center gap-1 text-[11px] text-ink-soft">
      <span
        aria-hidden="true"
        className="inline-flex items-center gap-0.5 rounded-full bg-surface px-1.5 font-bold text-ink ring-1 ring-line"
      >
        <Icon className="size-3" />
        {rounded === 0 ? 'Same' : formatPercent(Math.abs(rounded))}
      </span>
      <span aria-hidden="true">vs previous {days} days</span>
      <span className="sr-only">{said}</span>
    </span>
  );
}

/** One figure of the period on a small tile of its colour's tint. */
function Figure({
  icon,
  colour,
  label,
  value,
  children,
}: {
  icon: LucideIcon;
  colour: PaletteColour;
  label: string;
  value: string;
  children: ReactNode;
}) {
  return (
    <li
      className={cn(
        'flex min-w-0 flex-col gap-1 rounded-xl p-3 ring-1 ring-line/50',
        PALETTE[colour].soft,
      )}
    >
      <span className={cn('flex items-center gap-2 text-xs font-semibold', PALETTE[colour].text)}>
        <ColourIcon icon={icon} colour={colour} size="xs" />
        {label}
      </span>
      <span className="truncate text-lg leading-tight font-extrabold text-ink tabular-nums">
        {value}
      </span>
      {children}
    </li>
  );
}

/**
 * The period in four figures: orders, what was spent, the average delivered order and how many were delivered,
 * the first two compared with the period of the same length before.
 */
export function PeriodFigures({
  figures,
  previous,
  days,
  currency,
}: {
  figures: ActivityFigures;
  /** The period before (undefined while it loads or when it could not be loaded) */
  previous: ActivityFigures | undefined;
  days: number;
  currency: string;
}) {
  const money = (amount: number) => formatCurrency(amount, currency);
  return (
    <ul aria-label="The period in figures" className="grid grid-cols-2 gap-2 lg:grid-cols-4">
      <Figure
        icon={ClipboardList}
        colour="violet"
        label="Orders"
        value={formatNumber(figures.orders)}
      >
        <ChangeNote now={figures.orders} before={previous?.orders} days={days} />
      </Figure>
      <Figure icon={IndianRupee} colour="sunset" label="Spent" value={money(figures.spent)}>
        <ChangeNote now={figures.spent} before={previous?.spent} days={days} />
      </Figure>
      <Figure
        icon={ReceiptText}
        colour="sky"
        label="Average order"
        value={figures.averageOrder === null ? '—' : money(figures.averageOrder)}
      >
        <span className="text-[11px] text-ink-soft">Per delivered order</span>
      </Figure>
      <Figure
        icon={CircleCheckBig}
        colour="green"
        label="Delivered"
        value={formatNumber(figures.delivered)}
      >
        <span className="text-[11px] text-ink-soft">
          {figures.deliveredShare === null
            ? 'No orders yet'
            : `${formatPercent(figures.deliveredShare)} of the period's orders`}
        </span>
      </Figure>
    </ul>
  );
}

const CHART_VIEWS = [
  { id: 'spending', label: 'Spending' },
  { id: 'total', label: 'Running total' },
  { id: 'orders', label: 'Orders' },
] as const;
type ChartView = (typeof CHART_VIEWS)[number]['id'];

/**
 * One chart, switched between what was spent per day, the spending adding up over the period, and the orders per
 * day by what became of them (with each part's total for the period).
 */
export function ActivityChart({
  figures,
  currency,
}: {
  figures: ActivityFigures;
  currency: string;
}) {
  const [view, setView] = useState<ChartView>('spending');
  const total = formatCurrency(figures.spent, currency);
  const totals = ORDER_SERIES.map((series) => ({
    ...series,
    total: figures.days.reduce((sum, day) => sum + day[series.key], 0),
  }));
  return (
    <div className="flex min-w-0 flex-col gap-3">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <SegmentedControl label="Chart" options={CHART_VIEWS} value={view} onChange={setView} />
        {view === 'orders' ? (
          <ul aria-label="Orders in the period" className="flex flex-wrap gap-1.5">
            {totals.map((series) => (
              <li
                key={series.key}
                className={cn(
                  'inline-flex items-center gap-1.5 rounded-full px-2.5 py-0.5 text-[11px] font-semibold text-ink',
                  series.tint,
                )}
              >
                <span
                  aria-hidden="true"
                  className="size-2 rounded-full"
                  style={{ background: `linear-gradient(135deg, ${series.color}, ${series.to})` }}
                />
                {series.label}
                <span className="font-extrabold tabular-nums">{formatNumber(series.total)}</span>
              </li>
            ))}
          </ul>
        ) : (
          <p className="text-sm text-ink-soft">
            <span className="font-extrabold text-ink tabular-nums">{total}</span> on delivered
            orders
          </p>
        )}
      </div>
      {view === 'orders' ? (
        <OrdersChart
          days={figures.days}
          summary={`Orders per day: ${totals.map((series) => `${series.label} ${series.total}`).join(', ')}`}
        />
      ) : (
        <SpendingChart
          days={figures.days}
          currency={currency}
          cumulative={view === 'total'}
          summary={`Spending ${view === 'spending' ? 'per day' : 'adding up over the period'}: ${total} in all`}
        />
      )}
    </div>
  );
}

/** Where the period's orders stand now: a small ring, and each group with its number and share. */
export function StatusBreakdown({ figures }: { figures: ActivityFigures }) {
  return (
    <div className="flex flex-col items-center gap-3 sm:flex-row lg:flex-col">
      <StatusDonut groups={figures.groups} total={figures.orders} />
      <ul aria-label="Orders by status" className="flex w-full flex-col gap-1">
        {figures.groups.map((group) => (
          <li
            key={group.key}
            className={cn(
              'flex items-center justify-between gap-2 rounded-lg px-2.5 py-1 text-xs',
              group.count > 0 ? group.tint : 'text-ink-soft',
            )}
          >
            <span className="flex items-center gap-2 font-medium">
              <span
                aria-hidden="true"
                className="size-2.5 rounded-full"
                style={{ background: `linear-gradient(135deg, ${group.color}, ${group.to})` }}
              />
              {group.label}
            </span>
            <span className="flex items-baseline gap-1.5 tabular-nums">
              <span className="font-bold text-ink">{formatNumber(group.count)}</span>
              <span className="w-8 text-right text-ink-soft">{formatPercent(group.share)}</span>
            </span>
          </li>
        ))}
      </ul>
    </div>
  );
}

function Highlight({
  icon: Icon,
  colour,
  label,
  value,
}: {
  icon: LucideIcon;
  colour: PaletteColour;
  label: string;
  value: string;
}) {
  return (
    <li
      className={cn(
        'inline-flex items-center gap-2 rounded-full px-3 py-1 text-xs',
        PALETTE[colour].soft,
      )}
    >
      <Icon aria-hidden="true" className={cn('size-3.5', PALETTE[colour].text)} />
      <span className={cn('font-semibold', PALETTE[colour].text)}>{label}:</span>
      <span className="font-bold text-ink">{value}</span>
    </li>
  );
}

/** A few facts read from the period's days, as a row of chips: when, how often and how much. */
export function Highlights({
  figures,
  days,
  currency,
}: {
  figures: ActivityFigures;
  days: number;
  currency: string;
}) {
  const cancelled = figures.groups.find((group) => group.key === 'cancelled');
  const busiest = figures.busiestDay;
  const biggest = figures.biggestSpendDay;
  return (
    <ul aria-label="Highlights" className="flex flex-wrap gap-2">
      <Highlight
        icon={CalendarCheck}
        colour="green"
        label="Days you ordered"
        value={`${formatNumber(figures.daysWithOrders)} of ${formatNumber(days)} days`}
      />
      {busiest && (
        <Highlight
          icon={Flame}
          colour="sunset"
          label="Busiest day"
          value={`${formatWeekdayDayMonth(busiest.date)}, ${formatNumber(busiest.orders)} ${busiest.orders === 1 ? 'order' : 'orders'}`}
        />
      )}
      {biggest && (
        <Highlight
          icon={Wallet}
          colour="violet"
          label="Biggest spend"
          value={`${formatWeekdayDayMonth(biggest.date)}, ${formatCurrency(biggest.spent, currency)}`}
        />
      )}
      {cancelled && cancelled.count > 0 && (
        <Highlight
          icon={CircleX}
          colour="rose"
          label="Cancelled or declined"
          value={`${formatNumber(cancelled.count)} (${formatPercent(cancelled.share)})`}
        />
      )}
    </ul>
  );
}
