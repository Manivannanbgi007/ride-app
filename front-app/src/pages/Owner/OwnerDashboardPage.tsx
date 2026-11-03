import { ChefHat, EyeOff, Star, UtensilsCrossed } from 'lucide-react';

import {
  AttentionCards,
  QuickActions,
  RecentOrdersCard,
  RestaurantStatusNotice,
  SalesOverview,
  salesFilterOf,
  salesFilterParams,
  TopDishesCard,
  WaitingOrdersCard,
} from '@/components/business/OwnerDashboard';
import { RestaurantStatusBadge } from '@/components/business/StatusBadges';
import { LiveBadge } from '@/components/common/LiveBadge';
import { PageHero, type HeroStat } from '@/components/common/PageHero';
import { useOwnerMenu } from '@/hooks/api/ownerHooks';
import { useRestaurantRating } from '@/hooks/api/ratingHooks';
import { useQueryParams } from '@/hooks/useQueryParams';
import { useOwnerRestaurant } from '@/layouts/owner/ownerRestaurantContext';
import { useAppSelector } from '@/store/hooks';
import { selectOwner } from '@/store/slices/sessionSlice';
import type { MenuItem } from '@/types/catalog';
import type { RatingDetails } from '@/types/rating';
import { DEFAULT_CURRENCY } from '@/utils/constants';
import type { DatePresetId, DateRange } from '@/utils/dateRangeUtils';
import { formatNumber } from '@/utils/formatUtils';
import { formatAverage } from '@/utils/ratingUtils';

// The overview's days live in the address (?range=30 or ?from=...&to=...), so a refresh or a shared link keeps them
const PARAM_KEYS = ['range', 'from', 'to'] as const;

/** "Good morning" before noon, "Good afternoon" until 5 pm, then "Good evening". */
function greetingOf(hour: number): string {
  if (hour < 12) {
    return 'Good morning';
  }
  return hour < 17 ? 'Good afternoon' : 'Good evening';
}

/** The hero's chips: the customers' rating (all time) and the menu as it is now; each appears once it has loaded. */
function heroStatsOf(rating?: RatingDetails, dishes?: readonly MenuItem[]): HeroStat[] {
  const stats: HeroStat[] = [];
  if (rating) {
    stats.push({
      label:
        rating.count > 0
          ? `Customer rating · ${formatNumber(rating.count)} ${rating.count === 1 ? 'review' : 'reviews'}`
          : 'Customer rating · none yet',
      value: rating.average === null ? '—' : `${formatAverage(rating.average)} / 5`,
      icon: Star,
    });
  }
  if (dishes) {
    const active = dishes.filter((dish) => dish.status === 'ACTIVE');
    stats.push(
      {
        label: 'Dishes customers can order',
        value: formatNumber(active.filter((dish) => dish.available).length),
        icon: UtensilsCrossed,
      },
      {
        label: 'Sold out · hidden',
        value: `${formatNumber(active.filter((dish) => !dish.available).length)} · ${formatNumber(dishes.length - active.length)}`,
        icon: EyeOff,
      },
    );
  }
  return stats;
}

/**
 * The owner's home. First what needs the owner now (live): the orders waiting at each step of the kitchen, with
 * Accept and Reject for the new ones. Then how the restaurant did on the chosen days (`?range=` or `?from=&to=`,
 * last 7 days by default): the order and sales overview and the top dishes. Then the latest orders and shortcuts.
 * Every figure is the platform's own; orders and figures refresh by themselves.
 */
export default function OwnerDashboardPage() {
  const { restaurant, restaurantId } = useOwnerRestaurant();
  const owner = useAppSelector(selectOwner);
  const [params, setParams] = useQueryParams(PARAM_KEYS);
  const filter = salesFilterOf(params);
  const rating = useRestaurantRating(restaurantId);
  const menu = useOwnerMenu(restaurantId);

  const changeFilter = (range: DateRange, preset?: DatePresetId) =>
    setParams({
      range: undefined,
      from: undefined,
      to: undefined,
      ...salesFilterParams(range, preset),
    });

  return (
    <div className="flex flex-col gap-8">
      <PageHero
        eyebrow={restaurant.name}
        title={`${greetingOf(new Date().getHours())}, ${owner?.fullName.split(' ')[0] ?? 'there'}!`}
        subtitle="Your kitchen right now, and how your sales are going"
        icon={ChefHat}
        colour="green"
        actions={
          <>
            <RestaurantStatusBadge status={restaurant.status} />
            <LiveBadge />
          </>
        }
        stats={heroStatsOf(rating.data, menu.data)}
      />

      <RestaurantStatusNotice restaurant={restaurant} />

      <AttentionCards restaurantId={restaurantId} />

      <WaitingOrdersCard restaurantId={restaurantId} />

      <SalesOverview restaurantId={restaurantId} filter={filter} onFilterChange={changeFilter} />

      <div className="grid items-start gap-6 lg:grid-cols-2">
        <TopDishesCard
          restaurantId={restaurantId}
          range={filter.range}
          currency={DEFAULT_CURRENCY}
        />
        <RecentOrdersCard restaurantId={restaurantId} />
      </div>

      <QuickActions />
    </div>
  );
}
