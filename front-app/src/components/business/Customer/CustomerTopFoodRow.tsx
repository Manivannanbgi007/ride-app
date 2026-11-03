import { Eye } from 'lucide-react';
import { Link } from 'react-router';

import { FoodThumbnail } from '@/components/business/Food/FoodThumbnail';
import { ActionMenu } from '@/components/common/ActionMenu';
import type { CustomerTopFood } from '@/types/order';
import { DEFAULT_CURRENCY, routeTo } from '@/utils/constants';
import { formatCurrency, formatNumber } from '@/utils/formatUtils';

/** One dish the customer orders often: photo, name, category, counts, what they spent on it, and a small menu. */
export function CustomerTopFoodRow({ food }: { food: CustomerTopFood }) {
  const link = routeTo.foodDetails(food.menuItemId);
  return (
    <li className="flex items-center gap-4 py-4">
      <Link
        to={link}
        className="shrink-0 cursor-pointer"
        aria-label={`View ${food.name}`}
        tabIndex={-1}
      >
        <FoodThumbnail item={food} size="md" placeholder="utensils" />
      </Link>
      <div className="min-w-0 flex-1">
        <Link
          to={link}
          className="block cursor-pointer truncate text-sm font-bold hover:text-brand-text-strong"
        >
          {food.name}
        </Link>
        {food.categoryName && (
          <p className="mt-0.5 truncate text-xs font-semibold tracking-wide text-brand-text uppercase">
            {food.categoryName}
          </p>
        )}
        <p className="mt-1 text-xs text-ink-soft">
          Ordered {formatNumber(food.orders)} {food.orders === 1 ? 'time' : 'times'} ·{' '}
          {formatNumber(food.quantity)} pcs
        </p>
      </div>
      <div className="shrink-0 text-right">
        <p className="text-lg font-bold">{formatCurrency(food.totalSpent, DEFAULT_CURRENCY)}</p>
        <p className="text-xs text-ink-soft">
          {formatCurrency(food.unitPrice, DEFAULT_CURRENCY)} each
        </p>
      </div>
      <ActionMenu
        label={`Actions for ${food.name}`}
        items={[{ key: 'view', label: 'View dish', icon: Eye, to: link }]}
      />
    </li>
  );
}
