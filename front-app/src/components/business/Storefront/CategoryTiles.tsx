import { Link } from 'react-router';

import { CategoryIcon } from '@/components/business/Storefront/categoryIcons';
import { FoodPhoto } from '@/components/common/FoodPhoto';
import type { StoreCategory } from '@/types/storefront';
import { customerRouteTo } from '@/utils/constants';
import { categoryImageOf } from '@/utils/foodImages';

/** The food categories; each opens the dishes of that category. */
export function CategoryTiles({ categories }: { categories: readonly StoreCategory[] }) {
  return (
    <ul className="grid grid-cols-2 gap-4 sm:grid-cols-4 lg:grid-cols-8">
      {categories.map((category) => {
        return (
          <li key={category.id}>
            <Link
              to={customerRouteTo.categoryDishes(category.id)}
              title={category.description ?? undefined}
              className="flex flex-col items-center gap-3 rounded-2xl bg-surface px-3 py-5 text-center shadow-card transition-colors hover:bg-brand-50 focus-visible:ring-2 focus-visible:ring-brand-500 focus-visible:outline-none"
            >
              <FoodPhoto
                sources={[category.imageUrl, categoryImageOf(category.name)]}
                className="size-16 rounded-full"
                fallback={
                  <span className="flex size-16 items-center justify-center rounded-full bg-brand-50 text-brand-500">
                    <CategoryIcon name={category.name} className="size-7" />
                  </span>
                }
              />
              <span className="text-sm font-semibold">{category.name}</span>
            </Link>
          </li>
        );
      })}
    </ul>
  );
}
