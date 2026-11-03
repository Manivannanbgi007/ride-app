import {
  Bar,
  BarChart,
  CartesianGrid,
  Rectangle,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
  type BarShapeProps,
} from 'recharts';

import { Card } from '@/components/common/Card';
import { EmptyState } from '@/components/common/EmptyState';
import type { OrderStatus } from '@/types/order';
import { CHART_COLORS, ORDER_STATUS_COLORS } from '@/utils/chartColors';
import { ordersByStatusBars, type StatusBar } from '@/utils/dashboardUtils';

interface OrdersByStatusCardProps {
  ordersByStatus: Record<string, number>;
  className?: string;
}

/** The period's orders by the status they have now (one coloured bar per status). */
export function OrdersByStatusCard({ ordersByStatus, className }: OrdersByStatusCardProps) {
  const bars = ordersByStatusBars(ordersByStatus);

  return (
    <Card
      title="Orders by Status"
      subtitle="Where the period's orders are now"
      className={className}
    >
      {bars.length === 0 ? (
        <EmptyState title="No orders in this period" />
      ) : (
        <div className="h-60">
          <ResponsiveContainer width="100%" height="100%">
            <BarChart data={bars} margin={{ top: 10, right: 0, left: -20, bottom: 0 }}>
              <CartesianGrid vertical={false} stroke={CHART_COLORS.line} />
              <XAxis
                dataKey="label"
                tick={{ fill: CHART_COLORS.inkSoft, fontSize: 11 }}
                axisLine={false}
                tickLine={false}
                interval={0}
                angle={-35}
                textAnchor="end"
                height={70}
              />
              <YAxis
                allowDecimals={false}
                tick={{ fill: CHART_COLORS.inkSoft, fontSize: 12 }}
                axisLine={false}
                tickLine={false}
              />
              <Tooltip
                formatter={(value) => [value, 'Orders']}
                cursor={{ fill: CHART_COLORS.track }}
              />
              <Bar
                dataKey="count"
                barSize={14}
                radius={[7, 7, 0, 0]}
                shape={(props: BarShapeProps) => (
                  <Rectangle {...props} fill={colorOf(bars[props.index])} />
                )}
              />
            </BarChart>
          </ResponsiveContainer>
        </div>
      )}
    </Card>
  );
}

function colorOf(bar: StatusBar | undefined): string {
  return (bar && ORDER_STATUS_COLORS[bar.status as OrderStatus]) ?? CHART_COLORS.inkSoft;
}
