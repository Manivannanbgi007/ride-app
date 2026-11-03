import { Crown, Store } from 'lucide-react';

import { Badge } from '@/components/common/Badge';
import { Card } from '@/components/common/Card';
import { EmptyState } from '@/components/common/EmptyState';
import type { RestaurantPoint } from '@/types/dashboard';
import { cn } from '@/utils/classNames';
import { cancellationTone } from '@/utils/dashboardUtils';
import { formatCurrency, formatNumber, formatPercent, initialsOf } from '@/utils/formatUtils';
import { PALETTE, type PaletteColour } from '@/utils/palette';

interface TopRestaurantsProps {
  restaurants: readonly RestaurantPoint[];
  currency: string;
}

/** The first three places in their own colours; the others share a quiet one. */
const PODIUM: readonly PaletteColour[] = ['sunset', 'violet', 'green'];

/** The columns of a row on a wide screen (the header uses the same). */
const COLUMNS = 'md:grid-cols-[2.25rem_minmax(0,1.4fr)_minmax(0,1.6fr)_5rem_7rem_6rem]';

/**
 * The restaurants with the most revenue in the period, as a compact ranked list: the place (the first three in
 * colour), the restaurant, its revenue with a bar next to the best one, how many orders, how many delivered and the
 * share cancelled.
 */
export function TopRestaurants({ restaurants, currency }: TopRestaurantsProps) {
  const bestRevenue = Math.max(...restaurants.map((restaurant) => restaurant.revenue), 0);

  return (
    <Card title="Top Restaurants" subtitle="By revenue from delivered orders in the period">
      {restaurants.length === 0 ? (
        <EmptyState icon={Store} title="No delivered orders in this period yet" />
      ) : (
        <div className="flex flex-col gap-1.5">
          <div
            aria-hidden="true"
            className={cn(
              'hidden gap-x-4 px-3 text-[11px] font-bold tracking-wider text-ink-soft uppercase md:grid',
              COLUMNS,
            )}
          >
            <span>#</span>
            <span>Restaurant</span>
            <span>Revenue</span>
            <span className="text-right">Orders</span>
            <span className="text-right">Delivered</span>
            <span className="text-right">Cancelled</span>
          </div>
          <ol className="flex flex-col gap-1.5">
            {restaurants.map((restaurant, index) => (
              <RestaurantRow
                key={restaurant.restaurantId}
                restaurant={restaurant}
                rank={index + 1}
                currency={currency}
                shareOfBest={bestRevenue > 0 ? restaurant.revenue / bestRevenue : 0}
              />
            ))}
          </ol>
        </div>
      )}
    </Card>
  );
}

function RestaurantRow({
  restaurant,
  rank,
  currency,
  shareOfBest,
}: {
  restaurant: RestaurantPoint;
  rank: number;
  currency: string;
  /** Revenue next to the best restaurant's (0 to 1): the best has a full bar */
  shareOfBest: number;
}) {
  const podium = PODIUM[rank - 1];
  const deliveredShare = restaurant.orders > 0 ? restaurant.completed / restaurant.orders : 0;
  return (
    <li
      className={cn(
        'grid grid-cols-[2.25rem_minmax(0,1fr)_auto] items-center gap-x-4 gap-y-2 rounded-xl px-3 py-2.5 ring-1 ring-line/50 transition-colors hover:bg-surface',
        podium ? PALETTE[podium].soft : 'bg-page/70',
        COLUMNS,
      )}
    >
      <span
        className={cn(
          'flex size-9 items-center justify-center rounded-xl text-sm font-extrabold',
          podium ? cn('text-white shadow-sm', PALETTE[podium].fill) : 'bg-surface text-ink-soft',
        )}
      >
        {rank === 1 ? <Crown aria-label="First place" className="size-4" /> : rank}
      </span>
      <div className="flex min-w-0 items-center gap-2.5">
        <span
          aria-hidden="true"
          className="hidden size-8 shrink-0 items-center justify-center rounded-lg bg-surface text-xs font-bold text-brand-text-strong ring-1 ring-line sm:flex"
        >
          {initialsOf(restaurant.restaurantName)}
        </span>
        <span className="truncate font-semibold text-ink">{restaurant.restaurantName}</span>
      </div>
      <div className="flex min-w-0 flex-col gap-1 max-md:col-span-3 max-md:row-start-2">
        <span className="text-sm font-extrabold text-ink tabular-nums">
          {formatCurrency(restaurant.revenue, currency)}
        </span>
        {/* Next to the best restaurant of the period */}
        <div
          role="meter"
          aria-label={`${restaurant.restaurantName} revenue next to the best restaurant`}
          aria-valuemin={0}
          aria-valuemax={100}
          aria-valuenow={Math.round(shareOfBest * 100)}
          className="h-1.5 overflow-hidden rounded-full bg-surface ring-1 ring-line/60"
        >
          <div
            className={cn(
              'h-full rounded-full transition-[width] duration-700',
              PALETTE[podium ?? 'sky'].line,
            )}
            style={{ width: `${shareOfBest * 100}%` }}
          />
        </div>
      </div>
      <span className="hidden text-right text-sm font-semibold tabular-nums md:block">
        {formatNumber(restaurant.orders)}
      </span>
      <span className="hidden text-right text-sm tabular-nums md:block">
        <span className="font-semibold">{formatNumber(restaurant.completed)}</span>{' '}
        <span className="text-xs font-medium text-brand-text">
          {formatPercent(deliveredShare * 100)}
        </span>
      </span>
      <span className="justify-self-end">
        <Badge tone={cancellationTone(restaurant.cancellationRate)}>
          {formatPercent(restaurant.cancellationRate)}
        </Badge>
      </span>
    </li>
  );
}
