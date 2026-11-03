import { FoodCard } from '@/components/business/Food/FoodCard';
import { Skeleton } from '@/components/common/Loader';
import type { MenuItem } from '@/types/catalog';
import { cn } from '@/utils/classNames';
import { stagger } from '@/utils/motion';

/** 1 / 2 / 3 / 4 / 5 columns by width. */
const GRID = 'grid grid-cols-1 gap-5 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 2xl:grid-cols-5';

interface FoodGridProps {
  items: readonly MenuItem[];
  /** Restaurant id -> name (what is known so far) */
  restaurantNames: ReadonlyMap<number, string>;
  isUpdating?: boolean;
  duplicatingId?: number | null;
  onEdit: (item: MenuItem) => void;
  onDelete: (item: MenuItem) => void;
  onDuplicate: (item: MenuItem) => void;
}

/** The menu items as cards; they rise into place one after the other. */
export function FoodGrid({
  items,
  restaurantNames,
  isUpdating = false,
  duplicatingId = null,
  onEdit,
  onDelete,
  onDuplicate,
}: FoodGridProps) {
  return (
    <ul
      aria-label="Menu items"
      aria-busy={isUpdating}
      className={cn(GRID, 'transition-opacity', isUpdating && 'opacity-60')}
    >
      {items.map((item, index) => (
        <li
          key={item.id}
          style={stagger(index)}
          className="flex flex-col stagger motion-safe:animate-rise-in"
        >
          <FoodCard
            item={item}
            restaurantName={item.restaurantName ?? restaurantNames.get(item.restaurantId) ?? null}
            isDuplicating={duplicatingId === item.id}
            onEdit={onEdit}
            onDelete={onDelete}
            onDuplicate={onDuplicate}
          />
        </li>
      ))}
    </ul>
  );
}

/** Grey cards while the first page loads. */
export function FoodGridLoader({ count = 10 }: { count?: number }) {
  return (
    <div role="status" aria-label="Loading the menu items" className={GRID}>
      {Array.from({ length: count }, (_, index) => (
        <div
          key={index}
          className="flex flex-col overflow-hidden rounded-2xl bg-surface shadow-card ring-1 ring-line/60"
        >
          <Skeleton className="h-40 w-full" />
          <div className="flex flex-col gap-2.5 p-4">
            <Skeleton className="h-4 w-3/4" />
            <Skeleton className="h-3 w-1/2" />
            <Skeleton className="h-3 w-2/3" />
          </div>
        </div>
      ))}
    </div>
  );
}
