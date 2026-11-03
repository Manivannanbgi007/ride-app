import { Link } from 'react-router';

import { CategoryIcon } from '@/components/business/Storefront/categoryIcons';
import { FoodPhoto } from '@/components/common/FoodPhoto';
import type { StoreCategory } from '@/types/storefront';
import { customerRouteTo } from '@/utils/constants';
import { categoryImageOf } from '@/utils/foodImages';

/** A food category on the "all categories" page: its picture, name and description; opens its dishes. */
export function CategoryCard({ category }: { category: StoreCategory }) {
  return (
    <Link
      to={customerRouteTo.categoryDishes(category.id)}
      className="group flex flex-col overflow-hidden rounded-2xl bg-surface shadow-card transition-shadow hover:shadow-lg focus-visible:ring-2 focus-visible:ring-brand-500 focus-visible:outline-none"
    >
      <FoodPhoto
        sources={[category.imageUrl, categoryImageOf(category.name)]}
        className="h-40 w-full"
        fallback={
          <span className="flex h-40 w-full items-center justify-center bg-brand-50 text-brand-500">
            <CategoryIcon name={category.name} className="size-12" />
          </span>
        }
      />
      <div className="flex flex-col gap-1 p-5">
        <h3 className="font-bold group-hover:text-brand-text">{category.name}</h3>
        {category.description && <p className="text-sm text-ink-soft">{category.description}</p>}
      </div>
    </Link>
  );
}
