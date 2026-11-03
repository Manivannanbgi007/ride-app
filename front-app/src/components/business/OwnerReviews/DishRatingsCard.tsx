import { ChevronRight, UtensilsCrossed } from 'lucide-react';
import { useState } from 'react';

import { FoodThumbnail } from '@/components/business/Food/FoodThumbnail';
import { DishReviewsDialog } from '@/components/business/Rating/DishReviewsDialog';
import { RatingBadge } from '@/components/business/Rating/RatingBadge';
import { AsyncContent } from '@/components/common/AsyncContent';
import { Card } from '@/components/common/Card';
import { EmptyState } from '@/components/common/EmptyState';
import { SkeletonLoader } from '@/components/common/Loader';
import { useOwnerMenu } from '@/hooks/api/ownerHooks';
import { useDishRatings } from '@/hooks/api/ratingHooks';
import { categoryImageOf } from '@/utils/foodImages';
import { stagger } from '@/utils/motion';
import { ratingOf } from '@/utils/ratingUtils';

interface DishRatingsCardProps {
  restaurantId: number;
  className?: string;
}

/**
 * How each dish is rated: the best rated first, dishes nobody has rated yet last (in menu order). A rated dish opens
 * the reviews that rated it.
 */
export function DishRatingsCard({ restaurantId, className }: DishRatingsCardProps) {
  const menu = useOwnerMenu(restaurantId);
  const ratings = useDishRatings(menu.data?.map((dish) => dish.id) ?? []);
  const [shown, setShown] = useState<{ id: number; name: string } | null>(null);

  const dishes = [...(menu.data ?? [])].sort(
    (a, b) => (ratings?.[b.id]?.average ?? -1) - (ratings?.[a.id]?.average ?? -1),
  );

  return (
    <Card
      title="Dish ratings"
      subtitle="Stars customers gave to single dishes"
      className={className}
    >
      <AsyncContent
        isLoading={menu.isPending}
        error={menu.error}
        isEmpty={menu.data?.length === 0}
        errorTitle="Unable to load your dishes"
        onRetry={() => void menu.refetch()}
        isRetrying={menu.isFetching}
        loading={<SkeletonLoader lines={4} />}
        empty={
          <EmptyState
            icon={UtensilsCrossed}
            title="Your menu is empty"
            message="Add dishes under Menu."
          />
        }
      >
        <ul className="-my-1 flex flex-col">
          {dishes.map((dish, index) => {
            const rated = Boolean(ratings?.[dish.id]);
            const content = (
              <>
                <FoodThumbnail
                  item={dish}
                  size="sm"
                  fallbackSrc={categoryImageOf(dish.categoryName)}
                />
                <span className="flex min-w-0 flex-1 flex-col gap-0.5">
                  <span className="truncate text-sm font-semibold text-ink">{dish.name}</span>
                  <RatingBadge rating={ratingOf(ratings, dish.id)} />
                </span>
                {rated && (
                  <ChevronRight
                    aria-hidden="true"
                    className="size-4 shrink-0 text-ink-soft transition-transform group-hover:translate-x-0.5"
                  />
                )}
              </>
            );
            return (
              <li
                key={dish.id}
                style={stagger(index)}
                className="stagger motion-safe:animate-rise-in"
              >
                {rated ? (
                  <button
                    type="button"
                    aria-label={`Read the reviews of ${dish.name}`}
                    onClick={() => setShown({ id: dish.id, name: dish.name })}
                    className="group -mx-2 flex w-[calc(100%+1rem)] items-center gap-3 rounded-2xl px-2 py-2 text-left transition hover:bg-page focus-visible:ring-4 focus-visible:ring-brand-500/30 focus-visible:outline-none"
                  >
                    {content}
                  </button>
                ) : (
                  <div className="flex items-center gap-3 py-2">{content}</div>
                )}
              </li>
            );
          })}
        </ul>
      </AsyncContent>
      <DishReviewsDialog
        dish={shown}
        onClose={() => setShown(null)}
        emptyMessage="Customers rate a dish when they review a delivered order with it."
      />
    </Card>
  );
}
