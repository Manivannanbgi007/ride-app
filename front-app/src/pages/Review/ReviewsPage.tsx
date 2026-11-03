import { useMemo, useState } from 'react';
import { Link } from 'react-router';

import { FeaturedDishes, OthersReview, ReviewPeriodFilter } from '@/components/business/Review';
import { Select } from '@/components/common/Select';
import { useRestaurants } from '@/hooks/api/restaurantHooks';
import type { ReviewScope } from '@/types/review';
import { ROUTES } from '@/utils/constants';
import { periodScope, type ReviewPeriod } from '@/utils/reviewUtils';

const RESTAURANT_FILTER = { sort: 'name,asc' };
/** The restaurants offered in the filter (the admin list's page size limit is not reached on one page) */
const RESTAURANT_CHOICES = 50;

/**
 * "Reviews": what customers say about the dishes and restaurants of the platform. The period and restaurant at the
 * top apply to every part of the page: the featured dishes, the summary and the list of reviews.
 */
export default function ReviewsPage() {
  const [period, setPeriod] = useState<ReviewPeriod>({ kind: '30' });
  const [restaurantId, setRestaurantId] = useState<number | undefined>();
  const restaurants = useRestaurants(RESTAURANT_FILTER, 0, RESTAURANT_CHOICES);

  // A new object only when the period or restaurant changes (it is part of the queries' keys)
  const scope = useMemo<ReviewScope>(
    () => ({ ...(restaurantId ? { restaurantId } : {}), ...periodScope(period) }),
    [period, restaurantId],
  );

  return (
    <div className="flex flex-col gap-5">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <h1 className="text-[1.75rem] leading-tight font-extrabold tracking-tight sm:text-3xl">
            Reviews
          </h1>
          <nav aria-label="Breadcrumb" className="mt-1.5 text-sm">
            <ol className="flex items-center gap-1.5">
              <li>
                <Link to={ROUTES.dashboard} className="font-medium text-brand-text hover:underline">
                  Dashboard
                </Link>
              </li>
              <li aria-hidden="true" className="text-ink-soft">
                /
              </li>
              <li aria-current="page" className="text-ink-soft">
                Customer Reviews
              </li>
            </ol>
          </nav>
        </div>
        <div className="flex flex-wrap items-start gap-4 max-sm:w-full max-sm:flex-col">
          <div className="min-w-56">
            <Select
              aria-label="Restaurant"
              placeholder="All restaurants"
              options={(restaurants.data?.content ?? []).map((restaurant) => ({
                value: String(restaurant.id),
                label: restaurant.name,
              }))}
              value={restaurantId?.toString() ?? ''}
              onChange={(event) =>
                setRestaurantId(event.target.value ? Number(event.target.value) : undefined)
              }
            />
          </div>
          <ReviewPeriodFilter period={period} onChange={setPeriod} />
        </div>
      </div>

      <FeaturedDishes scope={scope} />
      <OthersReview scope={scope} />
    </div>
  );
}
