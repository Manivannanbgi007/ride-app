import { Copy, Eye, Pencil, Store, Trash2, Utensils, type LucideIcon } from 'lucide-react';
import { Link } from 'react-router';

import { MenuItemStatusBadge } from '@/components/business/StatusBadges';
import { FoodTypeMark } from '@/components/business/Storefront/FoodTypeMark';
import { Badge } from '@/components/common/Badge';
import { FoodPhoto } from '@/components/common/FoodPhoto';
import { Tooltip } from '@/components/common/Tooltip';
import type { FoodType, MenuItem } from '@/types/catalog';
import { cn } from '@/utils/classNames';
import { DEFAULT_CURRENCY, FOOD_TYPE_LABELS, routeTo } from '@/utils/constants';
import { categoryImageOf } from '@/utils/foodImages';
import { formatCurrency } from '@/utils/formatUtils';

/** Veg is green, egg amber, non-veg red (as on every food label). */
const FOOD_TYPE_TEXT: Record<FoodType, string> = {
  VEG: 'text-brand-text',
  EGG: 'text-warning-text',
  NON_VEG: 'text-danger-text',
};

export const DELETE_BLOCKED_HINT =
  'Disable the item first: only disabled or blocked items can be removed';

const ACTION_BASE = cn(
  'flex size-8 cursor-pointer items-center justify-center rounded-lg transition duration-200',
  'motion-safe:hover:-translate-y-0.5 motion-safe:active:scale-95',
  'focus-visible:ring-4 focus-visible:ring-brand-500/30 focus-visible:outline-none',
  'disabled:cursor-not-allowed disabled:opacity-40',
);
const TONES = {
  view: 'bg-brand-50 text-brand-text-strong hover:bg-brand-100',
  edit: 'bg-info-50 text-info-ink hover:bg-info-100',
  delete: 'bg-danger-50 text-danger-text hover:bg-danger-100',
  duplicate: 'bg-page text-ink-soft ring-1 ring-line hover:text-ink',
};

/**
 * The quick actions float on the photo. Where there is a mouse they show while the card is hovered or one of them
 * has keyboard focus (they stay in the tab order); on a touch screen they always show.
 */
const QUICK_ACTIONS = cn(
  'absolute top-2.5 right-2.5 flex items-center gap-1 rounded-xl bg-surface/90 p-1 shadow-lg ring-1 ring-line/60 backdrop-blur-sm transition duration-300',
  'pointer-fine:opacity-0 pointer-fine:group-focus-within:opacity-100 pointer-fine:group-hover:opacity-100',
  'motion-safe:pointer-fine:-translate-y-1 motion-safe:pointer-fine:group-focus-within:translate-y-0 motion-safe:pointer-fine:group-hover:translate-y-0',
);

interface FoodCardProps {
  item: MenuItem;
  /** The restaurant's name; null while the restaurants load */
  restaurantName: string | null;
  onEdit: (item: MenuItem) => void;
  onDelete: (item: MenuItem) => void;
  onDuplicate: (item: MenuItem) => void;
  /** This item is being copied: the button waits */
  isDuplicating?: boolean;
}

/**
 * One dish as a card: its photo as a banner (its own; without one its category's picture, marked "Category photo" so
 * the missing photo is still noticed) that zooms in on hover, the veg / non-veg mark and the price on it, then the
 * name, whether customers can order it, type / category and restaurant. Four quick actions (each with its name for
 * screen readers and a tooltip) float on the photo.
 */
export function FoodCard({
  item,
  restaurantName,
  onEdit,
  onDelete,
  onDuplicate,
  isDuplicating = false,
}: FoodCardProps) {
  const removable = item.status !== 'ACTIVE';
  return (
    <article
      aria-label={item.name}
      className="group flex h-full flex-col overflow-hidden rounded-2xl bg-surface shadow-card ring-1 ring-line/60 transition duration-300 hover:shadow-xl hover:ring-brand-300/70 motion-safe:hover:-translate-y-1"
    >
      <div className="relative h-40 overflow-hidden bg-warning-50">
        <Link
          to={routeTo.foodDetails(item.id)}
          tabIndex={-1}
          aria-hidden="true"
          className="block size-full"
        >
          <FoodPhoto
            sources={[item.imageUrl, categoryImageOf(item.categoryName)]}
            className={cn(
              'size-full transition-transform duration-700 ease-out motion-safe:group-hover:scale-105',
              item.status !== 'ACTIVE' && 'grayscale',
            )}
            fallback={
              <span className="flex size-full items-center justify-center text-warning-text">
                <Utensils className="size-9" />
              </span>
            }
          />
        </Link>
        {/* Dark at the bottom, so the price and the tag read on any picture */}
        <span
          aria-hidden="true"
          className="pointer-events-none absolute inset-x-0 bottom-0 h-2/3 bg-linear-to-t from-black/60 via-black/15 to-transparent"
        />
        <span className="absolute top-2.5 left-2.5 flex rounded-lg bg-surface/95 p-1 shadow-md">
          <FoodTypeMark foodType={item.foodType} />
        </span>
        {!item.imageUrl && (
          <span className="pointer-events-none absolute bottom-2.5 left-2.5 rounded-full bg-black/50 px-2 py-0.5 text-[10px] font-semibold text-white backdrop-blur-sm">
            Category photo
          </span>
        )}
        <span className="pointer-events-none absolute right-2.5 bottom-2.5 rounded-full bg-surface/95 px-3 py-1 text-sm font-extrabold text-ink tabular-nums shadow-md">
          {formatCurrency(item.price, DEFAULT_CURRENCY)}
        </span>

        <div className={QUICK_ACTIONS}>
          <Tooltip label={`View ${item.name}`} icon={Eye} placement="top">
            <Link
              to={routeTo.foodDetails(item.id)}
              aria-label={`View ${item.name}`}
              className={cn(ACTION_BASE, TONES.view)}
            >
              <Eye aria-hidden="true" className="size-4" />
            </Link>
          </Tooltip>
          <ActionButton
            label={`Edit ${item.name}`}
            icon={Pencil}
            tone={TONES.edit}
            onClick={() => onEdit(item)}
          />
          <ActionButton
            label={`Duplicate ${item.name}`}
            icon={Copy}
            tone={TONES.duplicate}
            disabled={isDuplicating}
            onClick={() => onDuplicate(item)}
          />
          <Tooltip
            label={removable ? `Delete ${item.name}` : DELETE_BLOCKED_HINT}
            icon={Trash2}
            placement="top"
          >
            <button
              type="button"
              aria-label={`Delete ${item.name}`}
              disabled={!removable}
              onClick={() => onDelete(item)}
              className={cn(ACTION_BASE, TONES.delete)}
            >
              <Trash2 aria-hidden="true" className="size-4" />
            </button>
          </Tooltip>
        </div>
      </div>

      <div className="flex flex-1 flex-col gap-1.5 p-4">
        <div className="flex items-start justify-between gap-2">
          <h3
            className="line-clamp-1 font-bold text-ink transition-colors group-hover:text-brand-text"
            title={item.name}
          >
            {item.name}
          </h3>
          {item.status !== 'ACTIVE' ? (
            <MenuItemStatusBadge status={item.status} />
          ) : (
            !item.available && (
              <Badge dot tone="warning">
                Sold out
              </Badge>
            )
          )}
        </div>
        <p className="text-xs font-semibold">
          <span className={FOOD_TYPE_TEXT[item.foodType]}>
            {FOOD_TYPE_LABELS[item.foodType]} / {item.categoryName}
          </span>
        </p>
        <p className="mt-auto flex min-w-0 items-center gap-1.5 pt-1 text-xs text-ink-soft">
          <Store aria-hidden="true" className="size-3.5 shrink-0" />
          <span className="truncate">{restaurantName ?? `Restaurant #${item.restaurantId}`}</span>
        </p>
      </div>
    </article>
  );
}

function ActionButton({
  label,
  icon: Icon,
  tone,
  disabled,
  onClick,
}: {
  label: string;
  icon: LucideIcon;
  tone: string;
  disabled?: boolean;
  onClick: () => void;
}) {
  return (
    <Tooltip label={label} icon={Icon} placement="top">
      <button
        type="button"
        aria-label={label}
        disabled={disabled}
        onClick={onClick}
        className={cn(ACTION_BASE, tone)}
      >
        <Icon aria-hidden="true" className="size-4" />
      </button>
    </Tooltip>
  );
}
