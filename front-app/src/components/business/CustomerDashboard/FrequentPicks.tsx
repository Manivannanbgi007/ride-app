import { Repeat, ShoppingBag, Utensils } from 'lucide-react';
import { Link } from 'react-router';

import { AddToCart } from '@/components/business/Cart';
import { FoodTypeMark } from '@/components/business/Storefront';
import { AsyncContent } from '@/components/common/AsyncContent';
import { FoodPhoto } from '@/components/common/FoodPhoto';
import { Skeleton } from '@/components/common/Loader';
import { useCart } from '@/hooks/api/cartHooks';
import { useFrequentPicks } from '@/hooks/api/customerDashboardHooks';
import type { FavouriteDish } from '@/types/order';
import type { MenuEntry } from '@/types/storefront';
import { cn } from '@/utils/classNames';
import { CUSTOMER_ROUTES, customerRouteTo, DEFAULT_CURRENCY } from '@/utils/constants';
import { categoryImageOf } from '@/utils/foodImages';
import { formatCurrency } from '@/utils/formatUtils';

import { CARD_ROW_ITEM, cardRow } from './cardRow';
import { PALETTE, type PaletteColour } from './palette';
import { SectionHeading, SectionLink } from './SectionHeading';

/** How many picks are shown: one row on a wide screen. */
const SHOWN = 4;
/** Each card's colour (its "Ordered 3 times" label), in turn. */
const CARD_COLOURS: readonly PaletteColour[] = ['rose', 'violet', 'sky', 'amber'];

/** The dish as the cart's "Add" takes it: today's price and stock from the catalog (order-service asked it). */
function menuEntryOf(dish: FavouriteDish): MenuEntry {
  return {
    id: dish.menuItemId,
    name: dish.name,
    description: null,
    price: dish.price ?? 0,
    foodType: dish.foodType ?? 'VEG',
    imageUrl: dish.imageUrl,
    // No price: the dish is on no menu any more, so it cannot be ordered
    available: dish.available && dish.price !== null,
  };
}

/**
 * "Your frequent picks": the dishes the customer ordered again and again, most orders first, each with today's
 * price and an "Add" that goes through the cart (the server checks the price, the stock and that the cart holds one
 * restaurant's dishes; a cart of another restaurant is only replaced once the customer agrees). A dish that cannot
 * be ordered now says so, has no "Add", and points to its restaurant's menu instead. Until a dish was ordered twice,
 * one line says what will appear here.
 */
export function FrequentPicks() {
  const frequent = useFrequentPicks();
  const cart = useCart();
  const picks = frequent.picks.slice(0, SHOWN);
  const cartHasItems = Boolean(cart.data?.items.length);

  return (
    <section aria-labelledby="frequent-picks-title" className="flex flex-col gap-3">
      <SectionHeading
        id="frequent-picks-title"
        icon={Repeat}
        colour="rose"
        title="Your frequent picks"
        subtitle="Dishes you order again and again, at today’s prices"
      >
        {cartHasItems && (
          <SectionLink to={CUSTOMER_ROUTES.cart} colour="rose">
            View cart
          </SectionLink>
        )}
      </SectionHeading>
      <AsyncContent
        isLoading={frequent.isPending}
        error={frequent.error}
        isEmpty={picks.length === 0}
        errorTitle="Unable to load your frequent picks"
        onRetry={() => void frequent.refetch()}
        isRetrying={frequent.isFetching}
        loading={
          <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
            {Array.from({ length: SHOWN }, (_, index) => (
              <Skeleton key={index} className="h-28 rounded-2xl bg-surface" />
            ))}
          </div>
        }
        empty={
          <p className="flex flex-wrap items-center gap-x-3 gap-y-1 rounded-2xl bg-surface px-4 py-3 text-sm text-ink-soft shadow-card ring-1 ring-line/60">
            <Repeat aria-hidden="true" className="size-4 shrink-0 text-st-rejected-text" />
            Your frequent picks will appear here once you have ordered a dish a few times.
            <Link
              to={CUSTOMER_ROUTES.restaurants}
              className="rounded-md font-semibold text-brand-text hover:underline focus-visible:ring-4 focus-visible:ring-brand-500/30 focus-visible:outline-none"
            >
              Find food
            </Link>
          </p>
        }
      >
        <ul aria-label="Frequent picks" className={cardRow(4)}>
          {picks.map((dish, index) => (
            <li key={dish.menuItemId} className={CARD_ROW_ITEM}>
              <PickCard dish={dish} colour={CARD_COLOURS[index % CARD_COLOURS.length]} />
            </li>
          ))}
        </ul>
      </AsyncContent>
    </section>
  );
}

/** One frequent pick, side by side with its photo: what it is, where from, how often, today's price, "Add". */
function PickCard({ dish, colour }: { dish: FavouriteDish; colour: PaletteColour }) {
  const entry = menuEntryOf(dish);
  const times = `Ordered ${dish.timesOrdered} ${dish.timesOrdered === 1 ? 'time' : 'times'}`;
  return (
    <article
      aria-label={dish.name}
      className="flex w-full gap-3 rounded-2xl bg-surface p-3 shadow-card ring-1 ring-line/60"
    >
      <span className="relative size-20 shrink-0 overflow-hidden rounded-xl sm:size-24">
        <FoodPhoto
          sources={[dish.imageUrl, categoryImageOf(dish.categoryName)]}
          className={cn('size-full', !entry.available && 'grayscale')}
          fallback={
            <span
              aria-hidden="true"
              className="flex size-full items-center justify-center bg-warning-50 text-warning-text"
            >
              <Utensils className="size-7" />
            </span>
          }
        />
      </span>
      <div className="flex min-w-0 flex-1 flex-col gap-1">
        <div className="flex items-start gap-1.5">
          <span className="mt-0.5 shrink-0">
            <FoodTypeMark foodType={entry.foodType} />
          </span>
          <h3 className="line-clamp-2 text-sm leading-snug font-bold text-ink">{dish.name}</h3>
        </div>
        <Link
          to={customerRouteTo.restaurantMenu(dish.restaurantId)}
          className="w-fit max-w-full truncate rounded-md text-xs text-ink-soft hover:text-brand-text hover:underline focus-visible:ring-4 focus-visible:ring-brand-500/30 focus-visible:outline-none"
        >
          {dish.restaurantName}
        </Link>
        <span
          className={cn(
            'w-fit rounded-full px-2 py-0.5 text-[11px] font-bold',
            PALETTE[colour].soft,
            PALETTE[colour].text,
          )}
        >
          {times}
        </span>
        <div className="mt-auto flex flex-wrap items-center justify-between gap-2 pt-1">
          {entry.available ? (
            <>
              <span className="font-extrabold text-ink tabular-nums">
                {formatCurrency(entry.price, DEFAULT_CURRENCY)}
              </span>
              <AddToCart item={entry} restaurantId={dish.restaurantId} />
            </>
          ) : (
            <>
              <span className="text-xs font-semibold text-ink-soft">
                {dish.price === null ? 'No longer on the menu' : 'Not available right now'}
              </span>
              <Link
                to={customerRouteTo.restaurantMenu(dish.restaurantId)}
                aria-label={`See ${dish.restaurantName}'s menu instead of ${dish.name}`}
                className="inline-flex items-center gap-1 rounded-md text-xs font-semibold text-brand-text hover:underline focus-visible:ring-4 focus-visible:ring-brand-500/30 focus-visible:outline-none"
              >
                <ShoppingBag aria-hidden="true" className="size-3.5" />
                See menu
              </Link>
            </>
          )}
        </div>
      </div>
    </article>
  );
}
