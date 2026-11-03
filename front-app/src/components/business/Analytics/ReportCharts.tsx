import {
  Bar,
  BarChart,
  CartesianGrid,
  Legend,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';

import { EmptyState } from '@/components/common/EmptyState';
import type { DayPoint } from '@/types/dashboard';
import type { RevenuePeriod } from '@/types/report';
import { CHART_COLORS } from '@/utils/chartColors';
import { formatDate, formatDayMonth } from '@/utils/dateUtils';
import { formatCompactCurrency, formatCurrency } from '@/utils/formatUtils';

const AXIS_TICK = { fill: CHART_COLORS.inkSoft, fontSize: 12 };

/** Orders per day: delivered and cancelled stacked, the rest (still in progress) on top. */
export function OrdersPerDayChart({ days }: { days: readonly DayPoint[] }) {
  if (!days.some((day) => day.orders > 0)) {
    return <EmptyState title="No orders in this period" />;
  }
  const data = days.map((day) => ({
    date: day.date,
    delivered: day.completed,
    cancelled: day.cancelled,
    inProgress: Math.max(0, day.orders - day.completed - day.cancelled),
  }));
  return (
    <div className="h-72">
      <ResponsiveContainer width="100%" height="100%">
        <BarChart data={data} margin={{ top: 10, right: 10, left: -20, bottom: 0 }}>
          <CartesianGrid vertical={false} stroke={CHART_COLORS.line} />
          <XAxis
            dataKey="date"
            tickFormatter={formatDayMonth}
            tick={AXIS_TICK}
            axisLine={false}
            tickLine={false}
            minTickGap={16}
          />
          <YAxis allowDecimals={false} tick={AXIS_TICK} axisLine={false} tickLine={false} />
          <Tooltip
            labelFormatter={(date) => (typeof date === 'string' ? formatDate(date) : date)}
            cursor={{ fill: CHART_COLORS.track }}
          />
          <Legend iconType="circle" wrapperStyle={{ fontSize: 12 }} />
          <Bar dataKey="delivered" name="Delivered" stackId="orders" fill={CHART_COLORS.brand} />
          <Bar dataKey="cancelled" name="Cancelled" stackId="orders" fill={CHART_COLORS.danger} />
          <Bar
            dataKey="inProgress"
            name="In progress"
            stackId="orders"
            fill={CHART_COLORS.warning}
            radius={[4, 4, 0, 0]}
          />
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
}

/** Revenue per day, week or month of the period. */
export function RevenuePeriodsChart({
  periods,
  currency,
}: {
  periods: readonly RevenuePeriod[];
  currency: string;
}) {
  if (!periods.some((period) => period.revenue > 0)) {
    return (
      <EmptyState title="No revenue in this period" message="Revenue counts delivered orders." />
    );
  }
  return (
    <div className="h-72">
      <ResponsiveContainer width="100%" height="100%">
        <BarChart data={[...periods]} margin={{ top: 10, right: 10, left: 0, bottom: 0 }}>
          <CartesianGrid vertical={false} stroke={CHART_COLORS.line} />
          <XAxis
            dataKey="label"
            tickFormatter={(label: string) => (label.length === 10 ? formatDayMonth(label) : label)}
            tick={AXIS_TICK}
            axisLine={false}
            tickLine={false}
            minTickGap={12}
          />
          <YAxis
            tickFormatter={(value: number) => formatCompactCurrency(value, currency)}
            tick={AXIS_TICK}
            axisLine={false}
            tickLine={false}
            width={70}
          />
          <Tooltip
            formatter={(value) => [formatCurrency(Number(value), currency), 'Revenue']}
            cursor={{ fill: CHART_COLORS.track }}
          />
          <Bar dataKey="revenue" fill={CHART_COLORS.info} radius={[6, 6, 0, 0]} maxBarSize={36} />
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
}
