import { Link } from 'react-router';

import { FoodThumbnail } from '@/components/business/Food/FoodThumbnail';
import { RatingBadge } from '@/components/business/Rating';
import { FoodTypeMark } from '@/components/business/Storefront/FoodTypeMark';
import type { FoodHit } from '@/types/storefront';
import { customerRouteTo, DEFAULT_CURRENCY } from '@/utils/constants';
import { categoryImageOf } from '@/utils/foodImages';
import { ratingOfHit } from '@/utils/ratingUtils';
import { formatCurrency } from '@/utils/formatUtils';

/** Dishes found by the search, each with the restaurant that cooks it (which opens its menu). */
export function FoodHitList({ foods }: { foods: readonly FoodHit[] }) {
  return (
    <ul className="divide-y divide-line rounded-2xl bg-surface px-6 shadow-card">
      {foods.map((food) => (
        <li key={food.id} className="flex flex-wrap items-start gap-4 py-4">
          <FoodThumbnail item={food} size="md" fallbackSrc={categoryImageOf(food.categoryName)} />
          <div className="min-w-0 flex-1">
            <div className="flex items-center gap-2">
              <FoodTypeMark foodType={food.foodType} />
              <h3 className="font-semibold">{food.name}</h3>
            </div>
            {food.description && <p className="mt-1 text-sm text-ink-soft">{food.description}</p>}
            <p className="mt-1 text-sm">
              <Link
                to={customerRouteTo.restaurantMenu(food.restaurantId)}
                className="font-semibold text-brand-text hover:underline"
              >
                {food.restaurantName}
              </Link>
              <span className="text-ink-soft">
                {' '}
                · {food.restaurantCity} · {food.categoryName}
              </span>
            </p>
          </div>
          <div className="flex flex-col items-end gap-1">
            <p className="font-semibold">{formatCurrency(food.price, DEFAULT_CURRENCY)}</p>
            <RatingBadge rating={ratingOfHit(food)} />
          </div>
        </li>
      ))}
    </ul>
  );
}
