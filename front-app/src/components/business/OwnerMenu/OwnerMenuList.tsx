import { Ellipsis, Eye, EyeOff, Pencil, Tag, Utensils } from 'lucide-react';

import {
  canOwnerToggleVisibility,
  menuStateOf,
} from '@/components/business/OwnerMenu/ownerMenuUtils';
import { StockSwitch } from '@/components/business/OwnerMenu/StockSwitch';
import { RatingBadge } from '@/components/business/Rating/RatingBadge';
import { FoodTypeMark } from '@/components/business/Storefront/FoodTypeMark';
import { ActionMenu, type ActionMenuItem } from '@/components/common/ActionMenu';
import { Badge } from '@/components/common/Badge';
import { Button } from '@/components/common/Button';
import { FoodPhoto } from '@/components/common/FoodPhoto';
import { IconButton } from '@/components/common/IconButton';
import { Skeleton } from '@/components/common/Loader';
import type { MenuItem } from '@/types/catalog';
import type { RatingLookup } from '@/types/rating';
import { cn } from '@/utils/classNames';
import { DEFAULT_CURRENCY } from '@/utils/constants';
import { categoryImageOf } from '@/utils/foodImages';
import { formatCurrency } from '@/utils/formatUtils';
import { ratingOf } from '@/utils/ratingUtils';

/** Two cards side by side from a small tablet on, three on a small laptop, four from 1280 px. */
const GRID = 'grid gap-4 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4';

interface OwnerMenuListProps {
  items: readonly MenuItem[];
  /** Ids of the dishes with a request running: their buttons wait */
  busyIds: readonly number[];
  onToggleStock: (item: MenuItem) => void;
  onEditPrice: (item: MenuItem) => void;
  onEdit: (item: MenuItem) => void;
  /** Hide asks for a confirmation (the page does it); Show is done at once */
  onToggleVisible: (item: MenuItem) => void;
  /** The dishes' customer ratings, by dish id; undefined while not known (no stars are shown) */
  ratings?: RatingLookup;
  /** Opens what customers wrote about a rated dish */
  onShowReviews?: (item: MenuItem) => void;
}

/** The dishes as cards: the photo, what customers read, the price, and the owner's controls of each dish. */
export function OwnerMenuList({
  items,
  busyIds,
  onToggleStock,
  onEditPrice,
  onEdit,
  onToggleVisible,
  ratings,
  onShowReviews,
}: OwnerMenuListProps) {
  return (
    <ul aria-label="Dishes" className={GRID}>
      {items.map((item) => (
        <li key={item.id} className="flex">
          <OwnerMenuCard
            item={item}
            busy={busyIds.includes(item.id)}
            onToggleStock={onToggleStock}
            onEditPrice={onEditPrice}
            onEdit={onEdit}
            onToggleVisible={onToggleVisible}
            ratings={ratings}
            onShowReviews={onShowReviews}
          />
        </li>
      ))}
    </ul>
  );
}

interface OwnerMenuCardProps extends Omit<OwnerMenuListProps, 'items' | 'busyIds'> {
  item: MenuItem;
  busy: boolean;
}

function OwnerMenuCard({
  item,
  busy,
  onToggleStock,
  onEditPrice,
  onEdit,
  onToggleVisible,
  ratings,
  onShowReviews,
}: OwnerMenuCardProps) {
  const state = menuStateOf(item);
  const hidden = state === 'hidden';
  const moreLabel = `More actions for ${item.name}`;
  const rating = ratingOf(ratings, item.id);
  const rated = rating !== null && rating !== undefined && rating.count > 0;

  const more: ActionMenuItem[] = [
    { key: 'price', label: 'Change the price', icon: Tag, onSelect: () => onEditPrice(item) },
  ];
  if (canOwnerToggleVisibility(item)) {
    more.push(
      hidden
        ? {
            key: 'show',
            label: 'Show on the menu',
            icon: Eye,
            onSelect: () => onToggleVisible(item),
          }
        : {
            key: 'hide',
            label: 'Hide from the menu',
            icon: EyeOff,
            onSelect: () => onToggleVisible(item),
          },
    );
  }

  return (
    <article
      aria-label={item.name}
      aria-busy={busy}
      className="flex w-full flex-col overflow-hidden rounded-3xl bg-surface shadow-card ring-1 ring-line/60 transition-shadow duration-200 hover:shadow-lg"
    >
      <div className="relative aspect-video overflow-hidden bg-page">
        <FoodPhoto
          sources={[item.imageUrl, categoryImageOf(item.categoryName)]}
          fallback={
            <span
              aria-hidden="true"
              className="flex size-full items-center justify-center bg-brand-50 text-brand-text"
            >
              <Utensils className="size-10" />
            </span>
          }
          className={cn('size-full', hidden && 'opacity-50 grayscale')}
        />
        {/* Where the dish stands, over the photo; nothing is shown while customers can order it */}
        <div className="absolute top-3 left-3 flex flex-wrap gap-2">
          {item.status === 'BLOCKED' && (
            <Badge dot tone="danger">
              Blocked by admin
            </Badge>
          )}
          {item.status === 'DISABLED' && (
            <Badge dot tone="neutral">
              Hidden
            </Badge>
          )}
          {state === 'soldOut' && (
            <Badge dot tone="warning">
              Sold out
            </Badge>
          )}
        </div>
      </div>

      <div className="flex flex-1 flex-col gap-3 p-4">
        <div className="flex items-start justify-between gap-3">
          <div className="min-w-0">
            <div className="flex items-start gap-2">
              <span className="mt-1 shrink-0">
                <FoodTypeMark foodType={item.foodType} />
              </span>
              <h3
                className="line-clamp-2 text-base font-bold break-words text-ink"
                title={item.name}
              >
                {item.name}
              </h3>
            </div>
            <p className="mt-0.5 truncate text-sm text-ink-soft">{item.categoryName}</p>
            {/* The customers' stars, and what they wrote once someone has rated the dish */}
            {rating !== undefined && (
              <p className="mt-1.5 flex flex-wrap items-center gap-x-2 gap-y-1">
                <RatingBadge rating={rating} />
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
            )}
          </div>
          <p className="shrink-0 text-lg font-bold text-ink tabular-nums">
            {formatCurrency(item.price, DEFAULT_CURRENCY)}
          </p>
        </div>

        {item.description ? (
          <p className="line-clamp-2 text-sm break-words text-ink-soft" title={item.description}>
            {item.description}
          </p>
        ) : (
          <p className="text-sm text-ink-soft italic">No description yet</p>
        )}

        {item.status === 'BLOCKED' && (
          <p className="text-xs text-danger-ink">
            Only the platform team can put this dish back on the menu.
          </p>
        )}

        <div
          role="group"
          aria-label={`Actions for ${item.name}`}
          className="mt-auto flex flex-wrap items-center justify-between gap-2 border-t border-line pt-3"
        >
          <StockSwitch
            dishName={item.name}
            inStock={item.available}
            disabled={busy}
            onChange={() => onToggleStock(item)}
          />
          <div className="flex items-center gap-1.5">
            <Button
              variant="secondary"
              size="sm"
              disabled={busy}
              aria-label={`Edit ${item.name}`}
              leftIcon={<Pencil aria-hidden="true" className="size-4" />}
              onClick={() => onEdit(item)}
            >
              Edit
            </Button>
            {/* While a request runs, nothing else can be started on the dish */}
            {busy ? (
              <IconButton label={moreLabel} icon={Ellipsis} disabled onClick={() => undefined} />
            ) : (
              <ActionMenu label={moreLabel} title={item.name} items={more} />
            )}
          </div>
        </div>
      </div>
    </article>
  );
}

/** Grey cards while the menu loads: the page keeps its shape. */
export function OwnerMenuLoader({ count = 6 }: { count?: number }) {
  return (
    <div role="status" aria-label="Loading your menu" className={GRID}>
      {Array.from({ length: count }, (_, index) => (
        <div
          key={index}
          className="flex flex-col overflow-hidden rounded-3xl bg-surface shadow-card"
        >
          <Skeleton className="aspect-video w-full rounded-none" />
          <div className="flex flex-col gap-3 p-4">
            <div className="flex justify-between gap-3">
              <Skeleton className="h-5 w-1/2" />
              <Skeleton className="h-5 w-16" />
            </div>
            <Skeleton className="h-3 w-1/3" />
            <Skeleton className="h-3 w-full" />
            <Skeleton className="h-9 w-full" />
          </div>
        </div>
      ))}
    </div>
  );
}
