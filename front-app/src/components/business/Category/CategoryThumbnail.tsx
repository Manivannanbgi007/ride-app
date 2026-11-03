import { Utensils } from 'lucide-react';

import { FoodPhoto } from '@/components/common/FoodPhoto';
import type { Category } from '@/types/catalog';

/** The category's picture; without one (or if it cannot be loaded) a utensils icon on the app's warm tint. */
export function CategoryThumbnail({ category }: { category: Pick<Category, 'imageUrl'> }) {
  return (
    <FoodPhoto
      sources={[category.imageUrl]}
      className="size-11 rounded-xl"
      fallback={
        <span
          aria-hidden="true"
          className="flex size-11 shrink-0 items-center justify-center rounded-xl bg-warning-50 text-warning-text"
        >
          <Utensils className="size-5" />
        </span>
      }
    />
  );
}
