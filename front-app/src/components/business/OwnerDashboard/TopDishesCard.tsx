import { Medal, Utensils } from 'lucide-react';
import { Link } from 'react-router';

import { DishPhoto } from '@/components/business/OwnerOrders/DishPhoto';
import { useDishLooks } from '@/components/business/OwnerOrders/useDishLooks';
import { AsyncContent } from '@/components/common/AsyncContent';
import { Card } from '@/components/common/Card';
import { EmptyState } from '@/components/common/EmptyState';
import { SkeletonLoader } from '@/components/common/Loader';
import { useTopItems } from '@/hooks/api/ownerHooks';
import { cn } from '@/utils/classNames';
import { OWNER_ROUTES } from '@/utils/constants';
import { formatRange, type DateRange } from '@/utils/dateRangeUtils';
import { formatCurrency, formatNumber } from '@/utils/formatUtils';
import { stagger } from '@/utils/motion';

/** How many dishes are ranked (the API sends the period's best sellers, most sold first). */
const SHOWN = 5;

/** The medal colours of the first three places; the others get a plain number. */
const PLACES = [
  'bg-linear-to-br from-amber-300 to-amber-500 text-amber-950',
  'bg-linear-to-br from-slate-200 to-slate-400 text-slate-900',
  'bg-linear-to-br from-orange-300 to-orange-600 text-orange-950',
];

interface TopDishesCardProps {
  restaurantId: number;
  /** The overview's days */
  range: DateRange;
  currency: string;
}

/**
 * The chosen days' most ordered dishes, with their photo: how many portions were sold and what they brought in. The
 * bar shows each dish against the best seller (it grows in), so the owner sees at a glance how far ahead it is.
 */
export function TopDishesCard({ restaurantId, range, currency }: TopDishesCardProps) {
  const topItems = useTopItems(restaurantId, range.fromDate, range.toDate);
  const looks = useDishLooks(restaurantId);
  const items = topItems.data?.items.slice(0, SHOWN) ?? [];
  const best = Math.max(1, ...items.map((item) => item.quantity));

  return (
    <Card
      title="Top dishes"
      subtitle={`Most ordered · ${formatRange(range)}`}
      actions={
        <Link
          to={OWNER_ROUTES.menu}
          className="rounded-md text-sm font-semibold text-brand-text hover:underline focus-visible:ring-4 focus-visible:ring-brand-500/30 focus-visible:outline-none"
        >
          Menu
        </Link>
      }
    >
      <AsyncContent
        isLoading={topItems.isPending}
        error={topItems.data ? null : topItems.error}
        isEmpty={items.length === 0}
        errorTitle="Unable to load your top dishes"
        onRetry={() => void topItems.refetch()}
        isRetrying={topItems.isFetching}
        loading={<SkeletonLoader lines={4} />}
        empty={
          <EmptyState
            icon={Utensils}
            title="No dishes ordered on these days"
            message="Your best sellers show up here once customers order."
          />
        }
      >
        <ol
          aria-label="Top dishes"
          aria-busy={topItems.isPlaceholderData}
          className={cn(
            'flex flex-col gap-4 transition-opacity',
            topItems.isPlaceholderData && 'opacity-50',
          )}
        >
          {items.map((item, index) => (
            <li
              key={item.menuItemId}
              style={stagger(index)}
              className="flex items-center gap-3 text-sm stagger motion-safe:animate-rise-in"
            >
              <span className="relative shrink-0">
                <DishPhoto
                  look={looks.get(item.menuItemId)}
                  className="size-12 rounded-2xl object-cover"
                />
                <span
                  aria-hidden="true"
                  className={cn(
                    'absolute -top-1.5 -left-1.5 flex size-6 items-center justify-center rounded-full text-xs font-extrabold tabular-nums shadow-md ring-2 ring-surface',
                    PLACES[index] ?? 'bg-page text-ink',
                  )}
                >
                  {index === 0 ? <Medal className="size-3.5" /> : index + 1}
                </span>
              </span>
              <div className="flex min-w-0 flex-1 flex-col gap-1.5">
                <div className="flex items-baseline justify-between gap-3">
                  <span className="min-w-0 truncate font-semibold text-ink" title={item.name}>
                    {item.name}
                  </span>
                  <span className="shrink-0 font-semibold text-ink tabular-nums">
                    {formatCurrency(item.revenue, currency)}
                  </span>
                </div>
                <div className="flex items-center gap-3">
                  <span
                    aria-hidden="true"
                    className="h-2 flex-1 overflow-hidden rounded-full bg-chart-track"
                  >
                    <span
                      className="block h-full origin-left rounded-full bg-linear-to-r from-brand-400 to-brand-600 stagger motion-safe:animate-bar-grow"
                      style={{
                        width: `${Math.max(4, (item.quantity / best) * 100)}%`,
                        ...stagger(index + 2),
                      }}
                    />
                  </span>
                  <span className="w-28 shrink-0 text-right text-xs text-ink-soft tabular-nums">
                    {formatNumber(item.quantity)} sold · {formatNumber(item.orders)}{' '}
                    {item.orders === 1 ? 'order' : 'orders'}
                  </span>
                </div>
              </div>
            </li>
          ))}
        </ol>
      </AsyncContent>
    </Card>
  );
}
