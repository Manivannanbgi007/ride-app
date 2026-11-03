import { Clock } from 'lucide-react';
import { useId } from 'react';
import {
  Area,
  Bar,
  CartesianGrid,
  ComposedChart,
  Line,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';

import { ChartTooltip } from '@/components/common/ChartTooltip';
import { CHART_COLORS } from '@/utils/chartColors';
import { DISPLAY_LOCALE } from '@/utils/constants';
import { formatDayMonth, formatHour, parseIsoDate } from '@/utils/dateUtils';
import { formatCompactCurrency, formatCurrency, formatNumber } from '@/utils/formatUtils';

import {
  ORDER_BARS,
  SALES_LINE,
  type SalesDay,
  type SalesHour,
  type SalesSeriesKey,
} from './ordersSalesSeries';

const AXIS = { fill: CHART_COLORS.inkSoft, fontSize: 12 };
/** The card's own colour: the gap between stacked bars, the ring round a dot */
const SURFACE = 'var(--color-surface)';
/** Above this many days the sales dots would crowd each other: only the line is drawn */
const MAX_DAYS_WITH_DOTS = 31;
/** Above this many days (a custom range of months) the bars are hairlines: drawn without gaps or rounding */
const MAX_DAYS_WITH_GAPS = 120;

/** The axis' day: 'Tue 13' for a week (the weekday helps most there), '13 Oct' for longer ranges. */
function tickFormatterOf(dayCount: number) {
  return (label: unknown) => {
    if (typeof label !== 'string') {
      return '';
    }
    return dayCount <= 7
      ? parseIsoDate(label).toLocaleDateString(DISPLAY_LOCALE, { weekday: 'short', day: 'numeric' })
      : formatDayMonth(label);
  };
}

/** The axis' hour: '9 am' */
const hourTick = (hour: unknown) => (typeof hour === 'number' ? formatHour(hour) : '');

interface PointTooltipProps {
  active?: boolean;
  payload?: readonly { payload?: SalesDay | SalesHour }[];
}

type OrdersSalesChartProps = {
  currency: string;
  /** The figures switched off in the legend */
  hidden: ReadonlySet<SalesSeriesKey>;
  /** Read out by screen readers instead of the drawing */
  summary: string;
} & (
  | {
      /** Each day of a range */
      days: readonly SalesDay[];
      hours?: undefined;
    }
  | {
      /** Each hour of one day (the owner's single-day view) */
      hours: readonly SalesHour[];
      days?: undefined;
    }
);

/**
 * Orders and sales per day (or per hour of one day) in one chart: the orders as rounded bars stacked bottom to top
 * (delivered, in progress, cancelled), each part a gradient, on the left scale; the sales (delivered orders' value)
 * as a sky-blue gradient area behind them with a smooth line and dots in front, on the right scale. The hover card
 * has the whole day (or hour).
 */
export function OrdersSalesChart({
  days,
  hours,
  currency,
  hidden,
  summary,
}: OrdersSalesChartProps) {
  const baseId = useId();
  const points: readonly (SalesDay | SalesHour)[] = days ?? hours;
  const byHour = days === undefined;
  const showSales = !hidden.has(SALES_LINE.key);
  const bars = ORDER_BARS.filter((series) => !hidden.has(series.key));
  const withDots = points.length <= MAX_DAYS_WITH_DOTS;
  // Many days make thin bars: a finer gap between their parts and between the days keeps them visible on a phone
  const manyDays = points.length > 14;
  const hairlines = points.length > MAX_DAYS_WITH_GAPS;
  const money = (amount: number) => formatCurrency(amount, currency);

  return (
    <div className="flex flex-col gap-1">
      {/* The scales' names, above each scale */}
      <div
        aria-hidden="true"
        className="flex justify-between text-[11px] font-semibold tracking-wide uppercase"
      >
        <span className="text-ink-soft">{bars.length > 0 ? 'Orders' : ''}</span>
        {showSales && <span className="text-st-ready-text">Sales</span>}
      </div>
      <div role="img" aria-label={summary} className="relative h-56 w-full sm:h-64">
        <div className="absolute inset-0">
          <ResponsiveContainer width="100%" height="100%">
            <ComposedChart
              data={[...points]}
              margin={{ top: 8, right: 0, left: 0, bottom: 0 }}
              barCategoryGap={hairlines ? '0%' : manyDays ? '12%' : '24%'}
            >
              <defs>
                {ORDER_BARS.map((series) => (
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
                <linearGradient id={`${baseId}-sales`} x1="0" y1="0" x2="0" y2="1">
                  <stop offset="0%" stopColor={SALES_LINE.color} stopOpacity={0.4} />
                  <stop offset="100%" stopColor={SALES_LINE.to} stopOpacity={0.02} />
                </linearGradient>
              </defs>
              <CartesianGrid vertical={false} stroke={CHART_COLORS.line} />
              <XAxis
                dataKey={byHour ? 'hour' : 'date'}
                tickFormatter={byHour ? hourTick : tickFormatterOf(points.length)}
                tick={AXIS}
                axisLine={{ stroke: CHART_COLORS.line }}
                tickLine={false}
                minTickGap={20}
                tickMargin={8}
              />
              <YAxis
                yAxisId="orders"
                hide={bars.length === 0}
                allowDecimals={false}
                tick={AXIS}
                axisLine={false}
                tickLine={false}
                width={32}
              />
              <YAxis
                yAxisId="sales"
                orientation="right"
                hide={!showSales}
                tickFormatter={(value: number) => formatCompactCurrency(value, currency)}
                tick={{ ...AXIS, fill: SALES_LINE.color }}
                axisLine={false}
                tickLine={false}
                width={56}
              />
              <Tooltip
                cursor={{ fill: CHART_COLORS.track, fillOpacity: 0.6 }}
                content={(props: PointTooltipProps) => {
                  const point = props.payload?.[0]?.payload;
                  if (!props.active || !point) {
                    return null;
                  }
                  return (
                    <ChartTooltip
                      {...('date' in point
                        ? { day: point.date }
                        : {
                            title: `${formatHour(point.hour)} – ${formatHour((point.hour + 1) % 24)}`,
                            icon: Clock,
                          })}
                      rows={[
                        {
                          label: SALES_LINE.label,
                          value: money(point.revenue),
                          color: SALES_LINE.color,
                        },
                        ...ORDER_BARS.map((series) => ({
                          label: series.label,
                          value: formatNumber(point[series.key]),
                          color: series.color,
                        })),
                        { label: 'Orders in all', value: formatNumber(point.orders) },
                      ]}
                    />
                  );
                }}
              />
              {/* Drawn in this order: the sales area behind the bars, the sales line in front of them */}
              {showSales && (
                <Area
                  yAxisId="sales"
                  type="monotone"
                  dataKey={SALES_LINE.key}
                  stroke="none"
                  fill={`url(#${baseId}-sales)`}
                  activeDot={false}
                  isAnimationActive={false}
                />
              )}
              {bars.map((series) => (
                <Bar
                  key={series.key}
                  yAxisId="orders"
                  dataKey={series.key}
                  name={series.label}
                  stackId="orders"
                  fill={`url(#${baseId}-${series.key})`}
                  stroke={hairlines ? 'none' : SURFACE}
                  strokeWidth={manyDays ? 1 : 2}
                  radius={hairlines ? 0 : manyDays ? [3, 3, 3, 3] : [6, 6, 6, 6]}
                  maxBarSize={24}
                  isAnimationActive={false}
                />
              ))}
              {showSales && (
                <Line
                  yAxisId="sales"
                  type="monotone"
                  dataKey={SALES_LINE.key}
                  name={SALES_LINE.label}
                  stroke={SALES_LINE.color}
                  strokeWidth={2.5}
                  // A dot on each day with sales only: a row of dots along zero would be noise
                  dot={(props: {
                    cx?: number;
                    cy?: number;
                    index?: number;
                    payload?: SalesDay | SalesHour;
                  }) =>
                    withDots && props.payload && props.payload.revenue > 0 ? (
                      <circle
                        key={props.index}
                        cx={props.cx}
                        cy={props.cy}
                        r={4}
                        fill={SALES_LINE.color}
                        stroke={SURFACE}
                        strokeWidth={2}
                      />
                    ) : (
                      <g key={props.index} />
                    )
                  }
                  activeDot={{ r: 6, fill: SALES_LINE.color, stroke: SURFACE, strokeWidth: 3 }}
                  isAnimationActive={false}
                />
              )}
            </ComposedChart>
          </ResponsiveContainer>
        </div>
      </div>
    </div>
  );
}
