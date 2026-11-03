import { Utensils } from 'lucide-react';

import type { DishLook } from '@/components/business/OwnerOrders/useDishLooks';
import { FoodPhoto } from '@/components/common/FoodPhoto';
import { cn } from '@/utils/classNames';
import { categoryImageOf } from '@/utils/foodImages';

/**
 * A dish's picture: its own, else its category's, else a utensils tile (never a broken image). Decorative: the dish's
 * name is always written next to it. The size and shape come from `className`.
 */
export function DishPhoto({ look, className }: { look?: DishLook; className: string }) {
  return (
    <FoodPhoto
      sources={[look?.imageUrl, look ? categoryImageOf(look.categoryName) : null]}
      fallback={
        <span
          aria-hidden="true"
          className={cn(
            'flex shrink-0 items-center justify-center bg-warning-50 text-warning-text',
            className,
          )}
        >
          <Utensils className="size-1/2" />
        </span>
      }
      className={className}
    />
  );
}
