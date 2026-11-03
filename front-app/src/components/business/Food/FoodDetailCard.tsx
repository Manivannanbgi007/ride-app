import { Plus } from 'lucide-react';
import { Link } from 'react-router';

import { FoodTextSection } from '@/components/business/Food/FoodTextSection';
import { FoodThumbnail } from '@/components/business/Food/FoodThumbnail';
import { Button } from '@/components/common/Button';
import type { MenuItem } from '@/types/catalog';
import { cn } from '@/utils/classNames';
import { DEFAULT_CURRENCY, FOOD_TYPE_LABELS, routeTo } from '@/utils/constants';
import { formatCurrency } from '@/utils/formatUtils';

interface FoodDetailCardProps {
  item: MenuItem;
  /** The restaurant's name; null while it loads or when restaurant-service cannot be reached */
  restaurantName: string | null;
  onAdd: () => void;
  onEdit: () => void;
}

/** What customers can do with the dish right now, as one dot and a few words. */
function stockOf(item: MenuItem): { label: string; dot: string; text: string } {
  if (item.status === 'BLOCKED') {
    return { label: 'Blocked by admin', dot: 'bg-danger-500', text: 'text-danger-text' };
  }
  if (item.status === 'DISABLED') {
    return { label: 'Hidden by owner', dot: 'bg-warning-500', text: 'text-warning-text' };
  }
  return item.available
    ? { label: 'Stock available', dot: 'bg-brand-500', text: 'text-brand-text' }
    : { label: 'Sold out', dot: 'bg-warning-500', text: 'text-warning-text' };
}

/** "Detail Menus": the dish (photo, stock, name, description, price, restaurant), Add / Edit Menu, ingredients, nutrition. */
export function FoodDetailCard({ item, restaurantName, onAdd, onEdit }: FoodDetailCardProps) {
  const stock = stockOf(item);
  return (
    <section aria-labelledby="food-detail-title" className="rounded-3xl bg-surface shadow-card">
      <header className="flex flex-wrap items-baseline justify-between gap-x-4 gap-y-1 px-6 pt-6">
        <h2 id="food-detail-title" className="text-2xl font-bold tracking-tight">
          Detail Menus
        </h2>
        <p className="text-sm text-ink-soft">
          Category: {FOOD_TYPE_LABELS[item.foodType]} /{' '}
          <span className="font-medium text-brand-text">{item.categoryName}</span>
        </p>
      </header>

      <div className="flex flex-col gap-6 px-6 pt-6 pb-8 sm:flex-row">
        <div className="shrink-0 self-center sm:self-start">
          <FoodThumbnail item={item} size="xl" placeholder="utensils" />
        </div>
        <div className="flex min-w-0 flex-1 flex-col gap-2">
          <p className={cn('flex items-center gap-2 text-xs font-semibold', stock.text)}>
            <span aria-hidden="true" className={cn('size-2 rounded-full', stock.dot)} />
            {stock.label}
          </p>
          <h3 className="line-clamp-2 text-2xl leading-tight font-bold break-words">{item.name}</h3>
          {item.description ? (
            <p className="text-sm leading-6 break-words text-ink-soft">{item.description}</p>
          ) : (
            <p className="text-sm text-ink-soft italic">No description</p>
          )}
          <p className="flex flex-wrap items-baseline gap-x-3 text-sm text-ink-soft">
            <span className="text-lg font-bold text-ink">
              {formatCurrency(item.price, DEFAULT_CURRENCY)}
            </span>
            <span>
              from{' '}
              <Link
                to={routeTo.restaurantDetails(item.restaurantId)}
                className="cursor-pointer font-medium text-brand-text hover:underline focus-visible:ring-2 focus-visible:ring-brand-500/40 focus-visible:outline-none"
              >
                {restaurantName ?? `Restaurant #${item.restaurantId}`}
              </Link>
            </span>
          </p>
          <div className="mt-3 flex flex-wrap gap-3">
            <Button
              onClick={onAdd}
              leftIcon={<Plus aria-hidden="true" className="size-4" />}
              className="min-w-36"
            >
              Add Menu
            </Button>
            <Button variant="ghost" onClick={onEdit} className="min-w-32 bg-page">
              Edit Menu
            </Button>
          </div>
        </div>
      </div>

      <FoodTextSection title="Ingredients" text={item.ingredients} />
      <FoodTextSection title="Nutrition Info" text={item.nutritionInfo} />
    </section>
  );
}
