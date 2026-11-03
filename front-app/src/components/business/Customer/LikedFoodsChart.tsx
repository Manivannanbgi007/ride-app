import {
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';

import type { LikedDish } from '@/types/review';
import { CHART_COLORS } from '@/utils/chartColors';
import { formatRating, likedDishColor } from '@/utils/customerUtils';
import { formatNumber } from '@/utils/formatUtils';
import { formatReviewDate } from '@/utils/reviewUtils';

const LABEL_MAX = 10;

function truncate(text: string): string {
  return text.length > LABEL_MAX ? `${text.slice(0, LABEL_MAX - 1)}…` : text;
}

interface TooltipProps {
  active?: boolean;
  payload?: readonly { payload?: LikedDish }[];
}

function LikedTooltip({ active, payload }: TooltipProps) {
  const dish = payload?.[0]?.payload;
  if (!active || !dish) {
    return null;
  }
  return (
    <div className="rounded-xl bg-surface px-4 py-2.5 text-center shadow-card ring-1 ring-line">
      <p className="text-sm font-bold">{dish.dishName}</p>
      <p className="text-xs text-ink">
        {formatRating(dish.averageRating)} rating · {formatNumber(dish.ratingCount)}{' '}
        {dish.ratingCount === 1 ? 'rating' : 'ratings'}
      </p>
      <p className="text-xs text-ink-soft">last rated {formatReviewDate(dish.lastRatedAt)}</p>
    </div>
  );
}

/** One rounded bar per dish: the customer's average rating (0 to 5), the colours taking turns. */
export function LikedFoodsChart({ dishes, summary }: { dishes: LikedDish[]; summary: string }) {
  return (
    <div role="img" aria-label={summary} className="h-64 w-full">
      <ResponsiveContainer width="100%" height="100%">
        <BarChart data={dishes} margin={{ top: 16, right: 8, left: 0, bottom: 0 }}>
          <CartesianGrid vertical={false} stroke={CHART_COLORS.line} />
          <XAxis
            dataKey="dishName"
            tickFormatter={truncate}
            tick={{ fill: CHART_COLORS.inkSoft, fontSize: 12 }}
            axisLine={false}
            tickLine={false}
            interval={0}
          />
          <YAxis
            domain={[0, 5]}
            ticks={[0, 1, 2, 3, 4, 5]}
            tick={{ fill: CHART_COLORS.inkSoft, fontSize: 12 }}
            axisLine={false}
            tickLine={false}
            width={24}
          />
          <Tooltip
            cursor={{ fill: CHART_COLORS.track, opacity: 0.5 }}
            content={(props) => <LikedTooltip active={props.active} payload={props.payload} />}
          />
          <Bar
            dataKey="averageRating"
            maxBarSize={18}
            radius={[9, 9, 9, 9]}
            isAnimationActive={false}
          >
            {dishes.map((dish, index) => (
              <Cell key={dish.menuItemId} fill={likedDishColor(index)} />
            ))}
          </Bar>
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
}
