import type { LikedDish } from '@/types/review';
import { likedDishColor, ratingShare } from '@/utils/customerUtils';
import { formatNumber } from '@/utils/formatUtils';

const LEGEND_SIZE = 4;

/** The top four dishes: colour dot, name with its share of the customer's ratings, and the ratings count. */
export function LikedFoodsLegend({
  dishes,
  totalRatings,
}: {
  dishes: LikedDish[];
  totalRatings: number;
}) {
  return (
    <ul className="grid gap-x-8 gap-y-4 sm:grid-cols-2">
      {dishes.slice(0, LEGEND_SIZE).map((dish, index) => (
        <li key={dish.menuItemId} className="flex items-center gap-3">
          <span
            aria-hidden="true"
            className="size-4 shrink-0 rounded-md"
            style={{ backgroundColor: likedDishColor(index) }}
          />
          <span className="min-w-0 flex-1 truncate text-sm font-medium">
            {dish.dishName} ({ratingShare(dish.ratingCount, totalRatings)}%)
          </span>
          <span className="text-xl font-bold">{formatNumber(dish.ratingCount)}</span>
        </li>
      ))}
    </ul>
  );
}
