import { useId } from 'react';
import {
  Area,
  AreaChart,
  CartesianGrid,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';

import type { DishRevenue, DishRevenuePeriod, DishRevenuePoint } from '@/types/order';
import { CHART_COLORS } from '@/utils/chartColors';
import { DEFAULT_CURRENCY, DISPLAY_LOCALE } from '@/utils/constants';
import { formatDate, parseIsoDate } from '@/utils/dateUtils';
import { formatCompactCurrency, formatCurrency } from '@/utils/formatUtils';

/** The date line of a hover: 'Oct 2026' for a month, 'Week of 5 Oct 2026', '5 Oct 2026'. */
function describeWindow(period: DishRevenuePeriod, start: string): string {
  switch (period) {
    case 'MONTHLY':
      return parseIsoDate(start).toLocaleDateString(DISPLAY_LOCALE, {
        month: 'long',
        year: 'numeric',
      });
    case 'WEEKLY':
      return `Week of ${formatDate(start)}`;
    case 'DAILY':
      return formatDate(start);
  }
}

interface TooltipProps {
  active?: boolean;
  payload?: readonly { payload?: DishRevenuePoint }[];
  period: DishRevenuePeriod;
}

function RevenueTooltip({ active, payload, period }: TooltipProps) {
  const point = payload?.[0]?.payload;
  if (!active || !point) {
    return null;
  }
  return (
    <div className="rounded-xl bg-surface px-4 py-2.5 text-center shadow-card ring-1 ring-line">
      <p className="text-sm font-bold">{formatCurrency(point.revenue, DEFAULT_CURRENCY)}</p>
      <p className="text-xs text-ink-soft">{describeWindow(period, point.start)}</p>
      <p className="text-xs text-ink-soft">
        {point.quantity} sold in {point.orders} {point.orders === 1 ? 'order' : 'orders'}
      </p>
    </div>
  );
}

/** The revenue as a soft blue area with a hover bar and a tooltip; the axis shows rupees in short form (10K). */
export function DishRevenueChart({ data, summary }: { data: DishRevenue; summary: string }) {
  const gradientId = useId();
  return (
    <div role="img" aria-label={summary} className="h-72 w-full">
      <ResponsiveContainer width="100%" height="100%">
        <AreaChart data={data.points} margin={{ top: 10, right: 8, left: 0, bottom: 0 }}>
          <defs>
            <linearGradient id={gradientId} x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stopColor={CHART_COLORS.info} stopOpacity={0.28} />
              <stop offset="100%" stopColor={CHART_COLORS.info} stopOpacity={0.02} />
            </linearGradient>
          </defs>
          <CartesianGrid vertical={false} stroke={CHART_COLORS.line} />
          <XAxis
            dataKey="label"
            tick={{ fill: CHART_COLORS.inkSoft, fontSize: 12 }}
            axisLine={false}
            tickLine={false}
            interval="preserveStartEnd"
            minTickGap={12}
          />
          <YAxis
            domain={[0, 'auto']}
            tickFormatter={(value: number) => formatCompactCurrency(value, DEFAULT_CURRENCY)}
            tick={{ fill: CHART_COLORS.inkSoft, fontSize: 12 }}
            axisLine={false}
            tickLine={false}
            width={64}
          />
          <Tooltip
            cursor={{ stroke: CHART_COLORS.info, strokeOpacity: 0.25, strokeWidth: 12 }}
            content={(props) => (
              <RevenueTooltip active={props.active} payload={props.payload} period={data.period} />
            )}
          />
          <Area
            type="monotone"
            dataKey="revenue"
            stroke={CHART_COLORS.info}
            strokeWidth={2.5}
            fill={`url(#${gradientId})`}
            dot={data.points.length === 1 ? { r: 4, fill: CHART_COLORS.info } : false}
            activeDot={{ r: 6, stroke: '#fff', strokeWidth: 2 }}
            isAnimationActive={false}
          />
        </AreaChart>
      </ResponsiveContainer>
    </div>
  );
}
