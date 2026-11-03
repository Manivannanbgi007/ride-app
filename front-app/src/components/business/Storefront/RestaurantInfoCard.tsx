import { ChevronDown, Clock, MapPin, Phone, type LucideIcon } from 'lucide-react';
import type { ReactNode } from 'react';

import { RestaurantRatingLine } from '@/components/business/Rating';
import { FoodPhoto } from '@/components/common/FoodPhoto';
import type { StoreRestaurant } from '@/types/storefront';
import { cn } from '@/utils/classNames';
import { DAY_LABELS } from '@/utils/constants';
import { cuisineImageOf } from '@/utils/foodImages';
import { dayOfWeekIn, DAYS_OF_WEEK, hoursByDay } from '@/utils/storefrontUtils';

/**
 * The top of a restaurant's page: a cover picture of its cuisine with the name over it, whether it is open, its
 * cuisines and rating (a link to the reviews) and what it says about itself; under it the address, the phone and
 * today's hours, with the whole week's hours one click away.
 */
export function RestaurantInfoCard({ restaurant }: { restaurant: StoreRestaurant }) {
  const hours = hoursByDay(restaurant.operatingHours);
  const today = dayOfWeekIn(restaurant.timeZone);
  const { address } = restaurant;
  const todayHours = hours[today];

  return (
    <section
      aria-labelledby="restaurant-name"
      className="overflow-hidden rounded-3xl bg-surface shadow-card"
    >
      <div className="relative isolate">
        {/* The cover: the restaurant's cuisine (restaurants have no photos of their own), darkened under the text */}
        <FoodPhoto
          sources={[cuisineImageOf(restaurant.cuisines)]}
          className="absolute inset-0 -z-20 size-full"
          fallback={
            <span
              aria-hidden="true"
              className="absolute inset-0 -z-20 bg-linear-to-br from-brand-600 via-brand-800 to-brand-900"
            />
          }
        />
        <span
          aria-hidden="true"
          className="absolute inset-0 -z-10 bg-linear-to-t from-black/85 via-black/55 to-black/15"
        />
        <div className="flex min-h-64 flex-col justify-end gap-3 p-6 text-white sm:min-h-80 sm:p-8">
          <ul aria-label="About" className="flex flex-wrap items-center gap-2">
            <li>
              {restaurant.openNow ? (
                <span className="inline-flex items-center gap-2 rounded-full bg-brand-500 px-3 py-1 text-xs font-bold text-on-brand shadow-brand">
                  <span aria-hidden="true" className="relative flex size-2">
                    <span className="absolute inline-flex size-full rounded-full bg-on-brand opacity-60 motion-safe:animate-ping" />
                    <span className="relative inline-flex size-2 rounded-full bg-on-brand" />
                  </span>
                  Open now
                </span>
              ) : (
                <span className="inline-flex items-center gap-2 rounded-full bg-white/20 px-3 py-1 text-xs font-bold ring-1 ring-white/30 backdrop-blur">
                  <span aria-hidden="true" className="size-2 rounded-full bg-white/70" />
                  Closed now
                </span>
              )}
            </li>
            {restaurant.cuisines.map((cuisine) => (
              <li
                key={cuisine}
                className="rounded-full bg-white/15 px-3 py-1 text-xs font-semibold ring-1 ring-white/25 backdrop-blur"
              >
                {cuisine}
              </li>
            ))}
          </ul>
          <h1
            id="restaurant-name"
            className="text-3xl leading-tight font-extrabold tracking-tight sm:text-5xl"
          >
            {restaurant.name}
          </h1>
          {restaurant.description && (
            <p className="max-w-2xl text-sm text-white/90 sm:text-base">{restaurant.description}</p>
          )}
          <RestaurantRatingLine restaurantId={restaurant.id} onPhoto />
        </div>
      </div>

      <div className="grid gap-3 p-4 sm:grid-cols-3 sm:p-5">
        <InfoTile icon={MapPin} label="Address">
          {[address.line1, address.line2, address.city, address.postalCode]
            .filter(Boolean)
            .join(', ')}
        </InfoTile>
        <InfoTile icon={Phone} label="Phone">
          <a
            href={`tel:${restaurant.phone}`}
            className="font-semibold text-brand-text hover:underline"
          >
            {restaurant.phone}
          </a>
        </InfoTile>
        <InfoTile icon={Clock} label="Opening hours">
          <span className="font-semibold">
            {todayHours === 'Closed' ? 'Closed today' : `Today, ${todayHours}`}
          </span>
          <details className="group mt-1">
            <summary className="flex w-fit cursor-pointer list-none items-center gap-1 rounded-md text-xs font-semibold text-brand-text hover:underline focus-visible:ring-4 focus-visible:ring-brand-500/30 focus-visible:outline-none [&::-webkit-details-marker]:hidden">
              All opening hours
              <ChevronDown
                aria-hidden="true"
                className="size-3.5 transition-transform group-open:rotate-180"
              />
            </summary>
            <dl className="mt-2 grid grid-cols-[auto_1fr] gap-x-4 gap-y-1 text-sm">
              {DAYS_OF_WEEK.map((day) => (
                <div
                  key={day}
                  className={cn('contents', day === today && 'font-semibold text-brand-text')}
                >
                  <dt>
                    {DAY_LABELS[day]}
                    {day === today && <span className="sr-only"> (today)</span>}
                  </dt>
                  <dd className={cn(hours[day] === 'Closed' && day !== today && 'text-ink-soft')}>
                    {hours[day]}
                  </dd>
                </div>
              ))}
            </dl>
          </details>
        </InfoTile>
      </div>
    </section>
  );
}

function InfoTile({
  icon: Icon,
  label,
  children,
}: {
  icon: LucideIcon;
  label: string;
  children: ReactNode;
}) {
  return (
    <div className="flex gap-3 rounded-2xl bg-page p-4 ring-1 ring-line">
      <span
        aria-hidden="true"
        className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-brand-50 text-brand-text-strong"
      >
        <Icon className="size-5" />
      </span>
      <div className="flex min-w-0 flex-col text-sm text-ink">
        <span className="text-xs font-medium text-ink-soft">{label}</span>
        {children}
      </div>
    </div>
  );
}
