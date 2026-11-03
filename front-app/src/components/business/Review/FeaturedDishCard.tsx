import { Quote, Star, Store, UtensilsCrossed } from 'lucide-react';

import { ReviewerAvatar } from '@/components/business/Review/ReviewerAvatar';
import { FoodPhoto } from '@/components/common/FoodPhoto';
import { Tooltip } from '@/components/common/Tooltip';
import type { FeaturedDish } from '@/types/review';
import { categoryImageOf } from '@/utils/foodImages';

/**
 * One of the best rated dishes, compact: its photo as a banner (its own; without one, its category's picture, marked
 * "Category photo", so a missing photo is still noticed), the average rating, the dish, its restaurant, and the top
 * review with who wrote it.
 */
export function FeaturedDishCard({ dish }: { dish: FeaturedDish }) {
  const review = dish.topReview;
  const average = dish.averageRating.toFixed(1);
  const ratings = `${dish.ratingCount} ${dish.ratingCount === 1 ? 'rating' : 'ratings'}`;

  return (
    <article className="flex flex-col overflow-hidden rounded-2xl bg-surface shadow-card ring-1 ring-line/60 transition-shadow hover:shadow-lg">
      <div className="relative h-32 overflow-hidden bg-warning-50">
        {dish.imageUrl ? (
          <img
            src={dish.imageUrl}
            alt={dish.dishName}
            loading="lazy"
            className="size-full object-cover"
          />
        ) : (
          <span role="img" aria-label={`No photo of ${dish.dishName}`} className="block size-full">
            <FoodPhoto
              sources={[categoryImageOf(dish.categoryName)]}
              className="size-full"
              fallback={
                <span className="flex size-full items-center justify-center text-warning-text">
                  <UtensilsCrossed aria-hidden="true" className="size-9" />
                </span>
              }
            />
          </span>
        )}
        <span
          aria-hidden="true"
          className="pointer-events-none absolute inset-x-0 bottom-0 h-1/2 bg-linear-to-t from-black/45 to-transparent"
        />
        <Tooltip label={ratings} placement="top" className="absolute top-2.5 right-2.5">
          <span
            className="flex items-center gap-1 rounded-full bg-white/95 px-2.5 py-1 text-sm font-extrabold text-slate-900 shadow-md"
            aria-label={`Average rating ${average} out of 5, ${ratings}`}
            role="img"
          >
            <Star aria-hidden="true" className="size-4 fill-warning-500 text-warning-500" />
            {average}
            <span className="text-xs font-semibold text-slate-500">({dish.ratingCount})</span>
          </span>
        </Tooltip>
        {!dish.imageUrl && (
          <span className="absolute bottom-2.5 left-2.5 rounded-full bg-black/45 px-2 py-0.5 text-[10px] font-semibold text-white backdrop-blur-sm">
            Category photo
          </span>
        )}
      </div>

      <div className="flex flex-1 flex-col gap-2 p-4">
        <div className="min-w-0">
          <h3 className="line-clamp-1 font-bold text-ink">{dish.dishName}</h3>
          <p className="flex min-w-0 items-center gap-1.5 text-xs text-ink-soft">
            {dish.categoryName && (
              <span className="shrink-0 font-semibold tracking-wide text-brand-text uppercase">
                {dish.categoryName}
              </span>
            )}
            {dish.categoryName && <span aria-hidden="true">·</span>}
            <Store aria-hidden="true" className="size-3 shrink-0" />
            <span className="truncate">{dish.restaurantName}</span>
          </p>
        </div>
        <p className="flex gap-1.5 text-sm leading-5 text-ink-soft">
          <Quote aria-hidden="true" className="mt-0.5 size-3.5 shrink-0 text-brand-300" />
          <span className="line-clamp-2">{review?.comment ?? 'Rated without a comment.'}</span>
        </p>
        <div className="mt-auto flex items-center gap-2 border-t border-line pt-2.5">
          <ReviewerAvatar name={review?.customerName ?? dish.dishName} size="sm" />
          <p className="min-w-0 truncate text-xs">
            <span className="font-semibold text-ink">{review?.customerName ?? 'Customers'}</span>
            <span className="text-ink-soft"> wrote the top review</span>
          </p>
        </div>
      </div>
    </article>
  );
}
