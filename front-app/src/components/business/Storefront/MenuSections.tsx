import { Utensils } from 'lucide-react';
import type { ReactNode } from 'react';

import { RatingBadge } from '@/components/business/Rating';
import { FoodTypeMark } from '@/components/business/Storefront/FoodTypeMark';
import { Badge } from '@/components/common/Badge';
import { FoodPhoto } from '@/components/common/FoodPhoto';
import type { RatingLookup } from '@/types/rating';
import type { MenuEntry, MenuSection } from '@/types/storefront';
import { cn } from '@/utils/classNames';
import { DEFAULT_CURRENCY } from '@/utils/constants';
import { categoryImageOf } from '@/utils/foodImages';
import { formatCurrency } from '@/utils/formatUtils';
import { ratingOf, type ShownRating } from '@/utils/ratingUtils';
import { sectionAnchorOf } from '@/utils/storefrontUtils';

/**
 * One dish as a card: what it is, its price and stars, what the restaurant says about it on the left; its photo on
 * the right with "Add" sitting on the photo's lower edge (or "Sold out" when it cannot be ordered today).
 */
function MenuItemCard({
  item,
  categoryName,
  rating,
  action,
  onShowReviews,
}: {
  item: MenuEntry;
  categoryName: string;
  rating?: ShownRating | null;
  action?: ReactNode;
  onShowReviews?: (item: MenuEntry) => void;
}) {
  const rated = rating !== null && rating !== undefined && rating.count > 0;
  return (
    <li className="flex">
      <div
        className={cn(
          'flex w-full gap-4 rounded-2xl bg-surface p-4 shadow-card ring-1 ring-line/60 transition-shadow duration-200 hover:shadow-lg',
          !item.available && 'opacity-75',
        )}
      >
        <div className="flex min-w-0 flex-1 flex-col gap-1.5">
          <FoodTypeMark foodType={item.foodType} />
          <h4 className="text-base leading-snug font-bold break-words text-ink">{item.name}</h4>
          <p className="text-base font-extrabold text-ink tabular-nums">
            {formatCurrency(item.price, DEFAULT_CURRENCY)}
          </p>
          <p className="flex flex-wrap items-center gap-x-3 gap-y-1">
            <RatingBadge rating={rating} />
            {/* What customers wrote about the dish, once someone has rated it */}
            {rated && onShowReviews && (
              <button
                type="button"
                aria-label={`Read the reviews of ${item.name}`}
                onClick={() => onShowReviews(item)}
                className="cursor-pointer rounded-md text-xs font-semibold text-brand-text hover:underline focus-visible:ring-4 focus-visible:ring-brand-500/30 focus-visible:outline-none"
              >
                Read reviews
              </button>
            )}
          </p>
          {item.description && (
            <p className="line-clamp-2 text-sm break-words text-ink-soft" title={item.description}>
              {item.description}
            </p>
          )}
        </div>

        <div className="relative w-28 shrink-0 self-start pb-4 sm:w-36">
          <FoodPhoto
            sources={[item.imageUrl, categoryImageOf(categoryName)]}
            className={cn(
              'aspect-square w-full rounded-2xl shadow-sm',
              !item.available && 'grayscale',
            )}
            fallback={
              <span
                aria-hidden="true"
                className="flex aspect-square w-full items-center justify-center rounded-2xl bg-warning-50 text-warning-text"
              >
                <Utensils className="size-8" />
              </span>
            }
          />
          <div className="absolute inset-x-0 bottom-0 flex justify-center">
            {item.available ? (
              action && <div className="rounded-xl bg-surface shadow-lg">{action}</div>
            ) : (
              <Badge>Sold out</Badge>
            )}
          </div>
        </div>
      </div>
    </li>
  );
}

interface MenuSectionsProps {
  sections: readonly MenuSection[];
  /** Shown on each dish's photo (the customer area's "Add") */
  renderAction?: (item: MenuEntry) => ReactNode;
  /** The dishes' ratings, by dish id; undefined while not known (no stars are shown) */
  ratings?: RatingLookup;
  /** Opens a rated dish's reviews ("Read reviews" next to its stars); without it there is no link */
  onShowReviews?: (item: MenuEntry) => void;
}

/**
 * A restaurant's dishes by category, each category under its own heading with its dishes as cards, one under the
 * other (the page is one focused column). A category's heading is where its link in the menu bar jumps to: it
 * stops clear of the sticky bars above (--menu-offset, set by the page).
 */
export function MenuSections({
  sections,
  renderAction,
  ratings,
  onShowReviews,
}: MenuSectionsProps) {
  return (
    <div className="flex flex-col gap-10">
      {sections.map((section) => (
        <section key={section.categoryId} aria-labelledby={sectionAnchorOf(section.categoryId)}>
          <h3
            id={sectionAnchorOf(section.categoryId)}
            data-category-id={section.categoryId}
            className="mb-4 flex scroll-mt-(--menu-offset) items-baseline gap-3 text-2xl font-extrabold tracking-tight text-ink"
          >
            {section.categoryName}
            <span className="text-sm font-semibold text-ink-soft">
              {section.items.length} {section.items.length === 1 ? 'dish' : 'dishes'}
            </span>
          </h3>
          <ul className="flex flex-col gap-4">
            {section.items.map((item) => (
              <MenuItemCard
                key={item.id}
                item={item}
                categoryName={section.categoryName}
                rating={ratingOf(ratings, item.id)}
                action={renderAction?.(item)}
                onShowReviews={onShowReviews}
              />
            ))}
          </ul>
        </section>
      ))}
    </div>
  );
}
