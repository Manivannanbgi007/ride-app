import { useId } from 'react';
import {
  Area,
  AreaChart,
  Bar,
  BarChart,
  CartesianGrid,
  Pie,
  PieChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';

import { ChartTooltip } from '@/components/common/ChartTooltip';
import { CHART_COLORS } from '@/utils/chartColors';
import { formatDayMonth } from '@/utils/dateUtils';
import { formatCompactCurrency, formatCurrency, formatNumber } from '@/utils/formatUtils';

import { ORDER_SERIES, type ActivityDay, type StatusGroupFigure } from './activityFigures';

const AXIS = { fill: CHART_COLORS.inkSoft, fontSize: 12 };
/** The card's own colour: the gap between stacked bars, the ring round a hovered point */
const SURFACE = 'var(--color-surface)';

/** The axis' day ('17 Oct'). */
const dayLabel = (label: unknown) => (typeof label === 'string' ? formatDayMonth(label) : '');

interface DayTooltipProps {
  active?: boolean;
  payload?: readonly { payload?: ActivityDay }[];
}

const orderCount = (count: number) => `${formatNumber(count)} ${count === 1 ? 'order' : 'orders'}`;

/**
 * Spending as a glowing area under a violet-to-rose-to-orange line: per day, or as a running total over the period.
 * The points of the days with spending are marked; the hover card has the day's spending and orders.
 */
export function SpendingChart({
  days,
  currency,
  cumulative,
  summary,
}: {
  days: readonly ActivityDay[];
  currency: string;
  cumulative: boolean;
  /** Read out by screen readers */
  summary: string;
}) {
  const gradientId = useId();
  const key = cumulative ? 'spentSoFar' : 'spent';
  const money = (amount: number) => formatCurrency(amount, currency);
  return (
    <div role="img" aria-label={summary} className="relative h-56 w-full">
      <div className="absolute inset-0">
        <ResponsiveContainer width="100%" height="100%">
          <AreaChart data={[...days]} margin={{ top: 12, right: 8, left: 0, bottom: 0 }}>
            <defs>
              {/* Across the chart's width (not the line's own box, which has no height when the line is flat) */}
              <linearGradient
                id={`${gradientId}-line`}
                gradientUnits="userSpaceOnUse"
                x1="0"
                y1="0"
                x2="100%"
                y2="0"
              >
                <stop offset="0%" stopColor={CHART_COLORS.violet} />
                <stop offset="55%" stopColor={CHART_COLORS.rose} />
                <stop offset="100%" stopColor={CHART_COLORS.sunset} />
              </linearGradient>
              <linearGradient id={`${gradientId}-fill`} x1="0" y1="0" x2="0" y2="1">
                <stop offset="0%" stopColor={CHART_COLORS.rose} stopOpacity={0.35} />
                <stop offset="100%" stopColor={CHART_COLORS.violet} stopOpacity={0} />
              </linearGradient>
            </defs>
            <CartesianGrid vertical={false} stroke={CHART_COLORS.line} strokeDasharray="3 6" />
            <XAxis
              dataKey="date"
              tickFormatter={dayLabel}
              tick={AXIS}
              axisLine={false}
              tickLine={false}
              minTickGap={28}
              tickMargin={10}
            />
            <YAxis
              tickFormatter={(value: number) => formatCompactCurrency(value, currency)}
              tick={AXIS}
              axisLine={false}
              tickLine={false}
              width={60}
            />
            <Tooltip
              cursor={{ stroke: CHART_COLORS.rose, strokeOpacity: 0.4, strokeDasharray: '4 4' }}
              content={(props: DayTooltipProps) => {
                const day = props.payload?.[0]?.payload;
                return props.active && day ? (
                  <ChartTooltip
                    day={day.date}
                    rows={[
                      { label: 'Spent', value: money(day.spent), color: CHART_COLORS.rose },
                      ...(cumulative ? [{ label: 'So far', value: money(day.spentSoFar) }] : []),
                      { label: 'Orders', value: formatNumber(day.orders) },
                    ]}
                  />
                ) : null;
              }}
            />
            <Area
              type="monotone"
              dataKey={key}
              stroke={`url(#${gradientId}-line)`}
              strokeWidth={3.5}
              fill={`url(#${gradientId}-fill)`}
              dot={(props: { cx?: number; cy?: number; index?: number; payload?: ActivityDay }) =>
                props.payload && props.payload.spent > 0 ? (
                  <circle
                    key={props.index}
                    cx={props.cx}
                    cy={props.cy}
                    r={4.5}
                    fill={CHART_COLORS.rose}
                    stroke={SURFACE}
                    strokeWidth={2}
                  />
                ) : (
                  <g key={props.index} />
                )
              }
              activeDot={{ r: 7, fill: CHART_COLORS.rose, stroke: SURFACE, strokeWidth: 3 }}
              isAnimationActive={false}
            />
          </AreaChart>
        </ResponsiveContainer>
      </div>
    </div>
  );
}

/**
 * Orders per day as rounded, stacked bars (delivered, still going, cancelled or declined), each part a gradient
 * from its colour at the top to a deeper one, with a thin gap between the parts.
 */
export function OrdersChart({
  days,
  summary,
}: {
  days: readonly ActivityDay[];
  /** Read out by screen readers */
  summary: string;
}) {
  const baseId = useId();
  return (
    <div role="img" aria-label={summary} className="relative h-56 w-full">
      <div className="absolute inset-0">
        <ResponsiveContainer width="100%" height="100%">
          <BarChart data={[...days]} margin={{ top: 12, right: 8, left: -20, bottom: 0 }}>
            <defs>
              {ORDER_SERIES.map((series) => (
                <linearGradient
                  key={series.key}
                  id={`${baseId}-${series.key}`}
                  x1="0"
                  y1="0"
                  x2="0"
                  y2="1"
                >
                  <stop offset="0%" stopColor={series.color} />
                  <stop offset="100%" stopColor={series.to} />
                </linearGradient>
              ))}
            </defs>
            <CartesianGrid vertical={false} stroke={CHART_COLORS.line} strokeDasharray="3 6" />
            <XAxis
              dataKey="date"
              tickFormatter={dayLabel}
              tick={AXIS}
              axisLine={false}
              tickLine={false}
              minTickGap={28}
              tickMargin={10}
            />
            <YAxis allowDecimals={false} tick={AXIS} axisLine={false} tickLine={false} />
            <Tooltip
              cursor={{ fill: CHART_COLORS.track, radius: 8 }}
              content={(props: DayTooltipProps) => {
                const day = props.payload?.[0]?.payload;
                return props.active && day ? (
                  <ChartTooltip
                    day={day.date}
                    rows={[
                      ...ORDER_SERIES.map((series) => ({
                        label: series.label,
                        value: formatNumber(day[series.key]),
                        color: series.color,
                      })),
                      { label: 'In all', value: orderCount(day.orders) },
                    ]}
                  />
                ) : null;
              }}
            />
            {ORDER_SERIES.map((series) => (
              <Bar
                key={series.key}
                dataKey={series.key}
                name={series.label}
                stackId="orders"
                fill={`url(#${baseId}-${series.key})`}
                stroke={SURFACE}
                strokeWidth={2}
                radius={[6, 6, 6, 6]}
                maxBarSize={26}
                isAnimationActive={false}
              />
            ))}
          </BarChart>
        </ResponsiveContainer>
      </div>
    </div>
  );
}

const DONUT_SIZE = 148;

/**
 * Where the period's orders stand now, as a ring: a rounded slice per group in its gradient (the empty groups have
 * none), the number of orders in the middle.
 */
export function StatusDonut({
  groups,
  total,
}: {
  groups: readonly StatusGroupFigure[];
  total: number;
}) {
  const baseId = useId();
  const slices = groups
    .filter((group) => group.count > 0)
    .map((group) => ({
      name: group.label,
      value: group.count,
      fill: `url(#${baseId}-${group.key})`,
    }));
  const summary = groups.map((group) => `${group.label}: ${formatNumber(group.count)}`).join(', ');
  return (
    <div
      role="img"
      aria-label={`${orderCount(total)}. ${summary}`}
      className="relative shrink-0"
      style={{ width: DONUT_SIZE, height: DONUT_SIZE }}
    >
      <PieChart width={DONUT_SIZE} height={DONUT_SIZE}>
        <defs>
          {groups.map((group) => (
            <linearGradient
              key={group.key}
              id={`${baseId}-${group.key}`}
              x1="0"
              y1="0"
              x2="1"
              y2="1"
            >
              <stop offset="0%" stopColor={group.color} />
              <stop offset="100%" stopColor={group.to} />
            </linearGradient>
          ))}
        </defs>
        <Pie
          data={[{ name: 'Track', value: 1, fill: CHART_COLORS.track }]}
          dataKey="value"
          innerRadius={50}
          outerRadius={70}
          stroke="none"
          isAnimationActive={false}
        />
        <Pie
          data={slices}
          dataKey="value"
          innerRadius={50}
          outerRadius={70}
          startAngle={90}
          endAngle={-270}
          paddingAngle={slices.length > 1 ? 4 : 0}
          cornerRadius={10}
          stroke="none"
          isAnimationActive={false}
        />
      </PieChart>
      <span className="absolute inset-0 flex flex-col items-center justify-center">
        <span className="text-2xl font-extrabold text-ink tabular-nums">{formatNumber(total)}</span>
        <span className="text-xs font-medium text-ink-soft">
          {total === 1 ? 'order' : 'orders'}
        </span>
      </span>
    </div>
  );
}
