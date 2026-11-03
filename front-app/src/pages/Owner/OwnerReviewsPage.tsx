import { Frown, MessageSquareText, Star, ThumbsUp } from 'lucide-react';

import { DishRatingsCard, OwnerReviewList } from '@/components/business/OwnerReviews';
import { RatingOverview } from '@/components/business/Rating';
import { AsyncContent } from '@/components/common/AsyncContent';
import { Card } from '@/components/common/Card';
import { EmptyState } from '@/components/common/EmptyState';
import { SkeletonLoader } from '@/components/common/Loader';
import { PageHero, type HeroStat } from '@/components/common/PageHero';
import { useRestaurantRating } from '@/hooks/api/ratingHooks';
import { useOwnerRestaurant } from '@/layouts/owner/ownerRestaurantContext';
import type { RatingDetails } from '@/types/rating';
import { ERROR_MESSAGES } from '@/utils/errorMessages';
import { formatNumber, formatPercent } from '@/utils/formatUtils';
import { formatAverage } from '@/utils/ratingUtils';

/** The rating at a glance in the hero: the average, how many reviews, the five-star share and the unhappy ones. */
function ratingStatsOf(rating?: RatingDetails): HeroStat[] {
  if (!rating || rating.count === 0 || rating.average === null) {
    return [];
  }
  const low = rating.distribution['1'] + rating.distribution['2'];
  return [
    { label: 'Average rating', value: `${formatAverage(rating.average)} / 5`, icon: Star },
    { label: 'Reviews', value: formatNumber(rating.count), icon: MessageSquareText },
    {
      label: 'Gave five stars',
      value: formatPercent((rating.distribution['5'] / rating.count) * 100),
      icon: ThumbsUp,
    },
    { label: 'One or two stars', value: formatNumber(low), icon: Frown },
  ];
}

/**
 * What customers say about the restaurant: the rating at a glance, every review (the unhappy ones flagged), how the
 * stars are spread, and how each dish is rated (a dish opens its reviews). Customers rate their delivered orders, so
 * there is nothing to write here: this page is for reading. On a phone: the overall rating, the reviews, the dishes.
 */
export default function OwnerReviewsPage() {
  const { restaurant, restaurantId } = useOwnerRestaurant();
  const rating = useRestaurantRating(restaurantId);

  return (
    <div className="flex flex-col gap-6">
      <PageHero
        eyebrow={restaurant.name}
        title="Reviews"
        subtitle={`What customers say about ${restaurant.name}`}
        icon={Star}
        colour="amber"
        stats={ratingStatsOf(rating.data)}
      />

      <div className="grid items-start gap-6 lg:grid-cols-[minmax(0,1fr)_23rem]">
        <Card
          title="Overall rating"
          subtitle="Every review, all time"
          className="lg:col-start-2 lg:row-start-1"
        >
          <AsyncContent
            isLoading={rating.isPending}
            error={rating.error}
            errorTitle={ERROR_MESSAGES.RATINGS_LOAD_FAILED}
            onRetry={() => void rating.refetch()}
            isRetrying={rating.isFetching}
            loading={<SkeletonLoader lines={4} />}
          >
            {rating.data &&
              (rating.data.count === 0 ? (
                <EmptyState
                  icon={Star}
                  title="No ratings yet"
                  message="Your first rating appears here once a customer reviews a delivered order."
                />
              ) : (
                <RatingOverview details={rating.data} />
              ))}
          </AsyncContent>
        </Card>

        {/* The list waits for the rating: without any review there is no list to ask for */}
        {rating.data ? (
          <OwnerReviewList
            restaurantId={restaurantId}
            hasReviews={rating.data.count > 0}
            className="lg:col-start-1 lg:row-span-2 lg:row-start-1"
          />
        ) : (
          <Card title="What customers say" className="lg:col-start-1 lg:row-span-2 lg:row-start-1">
            {rating.error ? (
              <EmptyState title="The reviews cannot be shown until the rating loads" />
            ) : (
              <SkeletonLoader lines={6} />
            )}
          </Card>
        )}

        <DishRatingsCard restaurantId={restaurantId} className="lg:col-start-2 lg:row-start-2" />
      </div>
    </div>
  );
}
