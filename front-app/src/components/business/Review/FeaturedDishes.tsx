import { ChevronLeft, ChevronRight, UtensilsCrossed } from 'lucide-react';
import { useState, type ReactNode } from 'react';

import { FeaturedDishCard } from '@/components/business/Review/FeaturedDishCard';
import { EmptyState } from '@/components/common/EmptyState';
import { ErrorMessage } from '@/components/common/ErrorMessage';
import { Skeleton } from '@/components/common/Loader';
import { Tooltip } from '@/components/common/Tooltip';
import { useFeaturedDishes } from '@/hooks/api/reviewHooks';
import type { ReviewScope } from '@/types/review';
import { cn } from '@/utils/classNames';
import { FEATURED_DISH_PAGE_SIZE } from '@/utils/constants';
import { ERROR_MESSAGES } from '@/utils/errorMessages';

const GRID = 'grid gap-4 sm:grid-cols-2 xl:grid-cols-3';

/**
 * The best rated dishes of the period, three at a time (one on phones, two on tablets); the arrows page through
 * them with the API's pages.
 */
export function FeaturedDishes({ scope }: { scope: ReviewScope }) {
  // The page belongs to the scope it was chosen for: another period or restaurant starts at the first page
  const scopeKey = JSON.stringify(scope);
  const [position, setPosition] = useState({ scopeKey, page: 0 });
  const page = position.scopeKey === scopeKey ? position.page : 0;
  const goTo = (newPage: number) => setPosition({ scopeKey, page: newPage });

  const dishes = useFeaturedDishes(scope, page, FEATURED_DISH_PAGE_SIZE);
  const data = dishes.data;

  if (dishes.error && !data) {
    return (
      <div className="rounded-2xl bg-surface shadow-card">
        <ErrorMessage
          title={ERROR_MESSAGES.FEATURED_DISHES_LOAD_FAILED}
          error={dishes.error}
          onRetry={() => void dishes.refetch()}
          isRetrying={dishes.isFetching}
        />
      </div>
    );
  }
  if (!data) {
    return <FeaturedDishesSkeleton />;
  }
  if (data.content.length === 0 && page === 0) {
    return (
      <div className="rounded-2xl bg-surface shadow-card">
        <EmptyState
          icon={UtensilsCrossed}
          title="No rated dishes in this period"
          message="Dishes show up here once customers rate them."
        />
      </div>
    );
  }

  return (
    <section aria-label="Featured dishes" className="flex flex-col gap-3">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h2 className="text-lg font-bold text-ink">Top rated dishes</h2>
          <p className="text-sm text-ink-soft">
            The best rated dishes of the period, with their top review
          </p>
        </div>
        <div className="flex gap-2">
          <PagerButton
            label="Previous dishes"
            disabled={page === 0 || dishes.isPlaceholderData}
            onClick={() => goTo(page - 1)}
          >
            <ChevronLeft aria-hidden="true" className="size-5" />
          </PagerButton>
          <PagerButton
            label="Next dishes"
            disabled={data.last || dishes.isPlaceholderData}
            onClick={() => goTo(page + 1)}
          >
            <ChevronRight aria-hidden="true" className="size-5" />
          </PagerButton>
        </div>
      </div>
      <div
        aria-busy={dishes.isPlaceholderData}
        className={cn(GRID, 'transition-opacity', dishes.isPlaceholderData && 'opacity-60')}
      >
        {data.content.map((dish) => (
          <FeaturedDishCard key={dish.menuItemId} dish={dish} />
        ))}
      </div>
    </section>
  );
}

function PagerButton({
  label,
  disabled,
  onClick,
  children,
}: {
  label: string;
  disabled: boolean;
  onClick: () => void;
  children: ReactNode;
}) {
  return (
    <Tooltip label={label} placement="top">
      <button
        type="button"
        aria-label={label}
        disabled={disabled}
        onClick={onClick}
        className="flex size-9 cursor-pointer items-center justify-center rounded-xl bg-brand-100 text-brand-text-strong shadow-card transition duration-200 hover:bg-brand-500 hover:text-on-brand focus-visible:ring-4 focus-visible:ring-brand-500/30 focus-visible:outline-none disabled:cursor-not-allowed disabled:opacity-40 enabled:motion-safe:active:scale-95"
      >
        {children}
      </button>
    </Tooltip>
  );
}

function FeaturedDishesSkeleton() {
  return (
    <div role="status" aria-label="Loading featured dishes" className={GRID}>
      {[0, 1, 2].map((index) => (
        <div
          key={index}
          className={cn(
            'flex flex-col gap-3 rounded-2xl bg-surface p-3 shadow-card',
            index === 1 && 'max-sm:hidden',
            index === 2 && 'max-xl:hidden',
          )}
        >
          <Skeleton className="h-32 w-full rounded-xl" />
          <div className="flex flex-col gap-2">
            <Skeleton className="h-4 w-3/4" />
            <Skeleton className="h-3 w-1/3" />
          </div>
          <Skeleton className="h-10 w-full" />
        </div>
      ))}
    </div>
  );
}
