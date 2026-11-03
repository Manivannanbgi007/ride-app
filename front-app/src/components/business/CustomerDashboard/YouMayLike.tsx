import { Sparkles, Utensils } from 'lucide-react';
import { Link } from 'react-router';

import { AddToCart } from '@/components/business/Cart';
import { RatingBadge } from '@/components/business/Rating';
import { FoodTypeMark } from '@/components/business/Storefront';
import { AsyncContent } from '@/components/common/AsyncContent';
import { FoodPhoto } from '@/components/common/FoodPhoto';
import { Skeleton } from '@/components/common/Loader';
import { useFrequentPicks } from '@/hooks/api/customerDashboardHooks';
import { useFoodSearch, useStoreCategories } from '@/hooks/api/storefrontHooks';
import type { FoodHit, MenuEntry, StoreCategory } from '@/types/storefront';
import { cn } from '@/utils/classNames';
import { customerRouteTo, DEFAULT_CURRENCY } from '@/utils/constants';
import { categoryImageOf } from '@/utils/foodImages';
import { formatCurrency } from '@/utils/formatUtils';
import { ratingOfHit } from '@/utils/ratingUtils';

import { CARD_ROW_ITEM, cardRow } from './cardRow';
import { PALETTE, type PaletteColour } from './palette';
import { SectionHeading } from './SectionHeading';

/** How many dishes are suggested: one row on a wide screen. */
const SHOWN = 4;
/** Asked for: enough to leave out the customer's own frequent picks and still fill the row. */
const ASKED = 12;
/** Each card's colour (its price), in turn. */
const CARD_COLOURS: readonly PaletteColour[] = ['violet', 'sky', 'amber', 'rose'];

/** The dish as the cart's "Add" takes it; search lists only dishes on a menu and in stock (the cart checks again). */
function menuEntryOf(hit: FoodHit): MenuEntry {
  return {
    id: hit.id,
    name: hit.name,
    description: hit.description,
    price: hit.price,
    foodType: hit.foodType,
    imageUrl: hit.imageUrl,
    available: true,
  };
}

/**
 * "You may like": the best rated dishes on the platform right now (search, ordered by the customers' ratings),
 * leaving out the customer's own frequent picks (they have their own row above). It says what it is: top rated, not
 * a personal prediction.
 */
export function YouMayLike() {
  const { picks } = useFrequentPicks();
  const topRated = useFoodSearch({ sort: 'RATING' }, 0, ASKED);
  const categories = useStoreCategories();
  const picked = new Set(picks.map((dish) => dish.menuItemId));
  const dishes = (topRated.data?.results ?? [])
    .filter((hit) => !picked.has(hit.id))
    .slice(0, SHOWN);

  return (
    <section aria-labelledby="you-may-like-title" className="flex flex-col gap-3">
      <SectionHeading
        id="you-may-like-title"
        icon={Sparkles}
        colour="violet"
        title="You may like"
        subtitle="Top rated by customers right now"
      />
      <AsyncContent
        isLoading={topRated.isPending}
        error={topRated.error}
        isEmpty={dishes.length === 0}
        errorTitle="Unable to load the suggestions"
        onRetry={() => void topRated.refetch()}
        isRetrying={topRated.isFetching}
        loading={
          <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
            {Array.from({ length: SHOWN }, (_, index) => (
              <Skeleton key={index} className="h-56 rounded-2xl bg-surface" />
            ))}
          </div>
        }
        empty={<p className="text-sm text-ink-soft">No dishes to suggest yet.</p>}
      >
        <ul aria-label="Suggested dishes" className={cardRow(4)}>
          {dishes.map((hit, index) => (
            <li key={hit.id} className={CARD_ROW_ITEM}>
              <SuggestionCard
                hit={hit}
                category={categories.data?.find((category) => category.id === hit.categoryId)}
                colour={CARD_COLOURS[index % CARD_COLOURS.length]}
              />
            </li>
          ))}
        </ul>
      </AsyncContent>
    </section>
  );
}

/**
 * One suggested dish, compact: its picture (its own, else its category's), what it is, where from, its stars, its
 * price and "Add".
 */
function SuggestionCard({
  hit,
  category,
  colour,
}: {
  hit: FoodHit;
  /** The dish's food category from the catalog (a search hit may come without its name) */
  category: StoreCategory | undefined;
  colour: PaletteColour;
}) {
  const dish = menuEntryOf(hit);
  return (
    <article
      aria-label={dish.name}
      className="flex w-full flex-col overflow-hidden rounded-2xl bg-surface shadow-card ring-1 ring-line/60"
    >
      <div className="relative h-28 overflow-hidden">
        <FoodPhoto
          sources={[
            dish.imageUrl,
            category?.imageUrl,
            categoryImageOf(hit.categoryName ?? category?.name),
          ]}
          className="size-full"
          fallback={
            <span
              aria-hidden="true"
              className="flex size-full items-center justify-center bg-warning-50 text-warning-text"
            >
              <Utensils className="size-8" />
            </span>
          }
        />
      </div>
      <div className="flex flex-1 flex-col gap-1 p-3">
        <div className="flex items-start gap-1.5">
          <span className="mt-0.5 shrink-0">
            <FoodTypeMark foodType={dish.foodType} />
          </span>
          <h3 className="line-clamp-1 text-sm font-bold text-ink">{dish.name}</h3>
        </div>
        <Link
          to={customerRouteTo.restaurantMenu(hit.restaurantId)}
          className="w-fit max-w-full truncate rounded-md text-xs text-ink-soft hover:text-brand-text hover:underline focus-visible:ring-4 focus-visible:ring-brand-500/30 focus-visible:outline-none"
        >
          {hit.restaurantName}
        </Link>
        <RatingBadge rating={ratingOfHit(hit)} />
        <div className="mt-auto flex items-center justify-between gap-2 pt-1.5">
          <span className={cn('font-extrabold tabular-nums', PALETTE[colour].text)}>
            {formatCurrency(dish.price, DEFAULT_CURRENCY)}
          </span>
          <AddToCart item={dish} restaurantId={hit.restaurantId} />
        </div>
      </div>
    </article>
  );
}
