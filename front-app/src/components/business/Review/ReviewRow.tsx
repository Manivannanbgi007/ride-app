import { Star, UtensilsCrossed } from 'lucide-react';

import { ReviewerAvatar } from '@/components/business/Review/ReviewerAvatar';
import { ReviewTagPill } from '@/components/business/Review/ReviewTagPill';
import { FoodPhoto } from '@/components/common/FoodPhoto';
import { StarRating } from '@/components/common/StarRating';
import type { MenuDishLook } from '@/hooks/api/storefrontHooks';
import type { Review } from '@/types/review';
import { categoryImageOf } from '@/utils/foodImages';
import { formatReviewDate } from '@/utils/reviewUtils';

interface ReviewRowProps {
  review: Review;
  /** The dishes' pictures from their menus (empty while they load: plain tiles then) */
  looks?: ReadonlyMap<number, MenuDishLook>;
}

/**
 * One review, compact: who and when, the tags, the comment, the overall rating, and each rated dish with its
 * picture (its own, else its category's) and stars.
 */
export function ReviewRow({ review, looks }: ReviewRowProps) {
  return (
    <li className="grid grid-cols-[auto_1fr] gap-x-3 gap-y-2 py-4 first:pt-0 sm:grid-cols-[auto_1fr_auto]">
      <ReviewerAvatar name={review.customerName} size="md" />
      <div className="min-w-0">
        <div className="flex flex-wrap items-start justify-between gap-x-4 gap-y-1.5">
          <div className="min-w-0">
            <h3 className="truncate font-semibold">{review.customerName}</h3>
            <p className="text-xs text-ink-soft">
              {review.restaurantName} · {formatReviewDate(review.createdAt)}
            </p>
          </div>
          {review.tags.length > 0 && (
            <ul aria-label="Tags" className="flex flex-wrap gap-1.5">
              {review.tags.map((tag) => (
                <li key={tag}>
                  <ReviewTagPill tag={tag} />
                </li>
              ))}
            </ul>
          )}
        </div>
        {review.comment && (
          <p className="mt-1.5 text-sm leading-6 break-words text-ink">{review.comment}</p>
        )}
        {review.dishRatings.length > 0 && (
          <ul aria-label="Dish ratings" className="mt-2 flex flex-wrap gap-1.5">
            {review.dishRatings.map((dish) => {
              const look = looks?.get(dish.menuItemId);
              return (
                <li
                  key={dish.menuItemId}
                  className="inline-flex items-center gap-1.5 rounded-full bg-page py-0.5 pr-2.5 pl-0.5 text-xs ring-1 ring-line/60"
                >
                  <span className="size-6 shrink-0 overflow-hidden rounded-full bg-warning-50">
                    <FoodPhoto
                      sources={[look?.imageUrl, categoryImageOf(look?.categoryName)]}
                      className="size-full"
                      fallback={
                        <span
                          aria-hidden="true"
                          className="flex size-full items-center justify-center text-warning-text"
                        >
                          <UtensilsCrossed className="size-3" />
                        </span>
                      }
                    />
                  </span>
                  {dish.dishName}
                  <span className="font-semibold">{dish.rating}</span>
                  <Star aria-hidden="true" className="size-3 fill-warning-500 text-warning-500" />
                </li>
              );
            })}
          </ul>
        )}
      </div>
      <div className="col-start-2 flex items-center gap-2 sm:col-start-3 sm:row-start-1 sm:flex-col sm:items-end sm:gap-1 sm:pl-4">
        <p className="text-xl font-bold tracking-tight tabular-nums">{review.rating.toFixed(1)}</p>
        <StarRating value={review.rating} />
      </div>
    </li>
  );
}
