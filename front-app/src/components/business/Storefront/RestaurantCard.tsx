import { MapPin } from 'lucide-react';
import { Link } from 'react-router';

import { RatingBadge } from '@/components/business/Rating';
import { Badge } from '@/components/common/Badge';
import { FoodPhoto } from '@/components/common/FoodPhoto';
import { cn } from '@/utils/classNames';
import { customerRouteTo } from '@/utils/constants';
import { cuisineImageOf } from '@/utils/foodImages';
import type { ShownRating } from '@/utils/ratingUtils';
import { initialsOf } from '@/utils/formatUtils';
import { tileStyleOf } from '@/utils/storefrontUtils';

/**
 * A restaurant's picture. Restaurants have no photos of their own yet, so it shows the picture of their cuisine
 * (when it names a known one); otherwise, or if that cannot be loaded, a coloured tile with the initials.
 */
export function RestaurantTile({
  id,
  name,
  cuisines,
  className,
}: {
  id: number;
  name: string;
  cuisines?: readonly string[];
  className?: string;
}) {
  return (
    <FoodPhoto
      sources={[cuisineImageOf(cuisines)]}
      className={className}
      fallback={
        <span
          aria-hidden="true"
          className={cn(
            'flex items-center justify-center font-extrabold',
            tileStyleOf(id),
            className,
          )}
        >
          {initialsOf(name)}
        </span>
      }
    />
  );
}

interface RestaurantCardProps {
  id: number;
  name: string;
  cuisines: readonly string[];
  /** The city, or a whole address line (search results) */
  place: string;
  /** Unknown in search results (search-service does not say) */
  openNow?: boolean;
  distanceKm?: number | null;
  /** Null: no ratings yet. Undefined (not known yet): no stars are shown. */
  rating?: ShownRating | null;
  /** A deal of the restaurant's own ("₹100 off"), shown on its picture */
  deal?: string;
}

/** A restaurant in a list: opens its menu. */
export function RestaurantCard({
  id,
  name,
  cuisines,
  place,
  openNow,
  distanceKm,
  rating,
  deal,
}: RestaurantCardProps) {
  return (
    <Link
      to={customerRouteTo.restaurantMenu(id)}
      className="group flex flex-col overflow-hidden rounded-2xl bg-surface shadow-card transition-shadow hover:shadow-lg focus-visible:ring-2 focus-visible:ring-brand-500 focus-visible:outline-none"
    >
      <span className="relative block">
        <RestaurantTile id={id} name={name} cuisines={cuisines} className="h-32 w-full text-4xl" />
        {deal && (
          <span className="absolute top-3 left-3 max-w-[85%] truncate rounded-full bg-brand-500 px-2.5 py-1 text-xs font-bold text-on-brand shadow-md">
            {deal}
          </span>
        )}
      </span>
      <div className="flex flex-1 flex-col gap-2 p-5">
        <div className="flex items-start justify-between gap-2">
          <h3 className="font-bold group-hover:text-brand-text">{name}</h3>
          {openNow !== undefined && (
            <span className="shrink-0 whitespace-nowrap">
              <Badge tone={openNow ? 'success' : 'neutral'}>
                {openNow ? 'Open now' : 'Closed'}
              </Badge>
            </span>
          )}
        </div>
        <p className="text-sm text-ink-soft">
          {cuisines.length > 0 ? cuisines.join(' · ') : 'Many cuisines'}
        </p>
        <RatingBadge rating={rating} />
        <p className="mt-auto flex items-center gap-1 text-xs text-ink-soft">
          <MapPin aria-hidden="true" className="size-3.5" />
          {place}
          {distanceKm !== undefined && distanceKm !== null && ` · ${distanceKm.toFixed(1)} km`}
        </p>
      </div>
    </Link>
  );
}
