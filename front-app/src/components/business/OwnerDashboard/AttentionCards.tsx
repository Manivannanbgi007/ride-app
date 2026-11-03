import { BellRing, ChefHat, CircleCheck, PackageCheck, type LucideIcon } from 'lucide-react';
import { Link } from 'react-router';

import {
  ownerOrderCountFilters,
  ownerOrderTabOf,
  type OwnerOrderTabId,
} from '@/components/business/OwnerOrders/ownerOrderUtils';
import { Skeleton } from '@/components/common/Loader';
import { useOwnerOrderCounts } from '@/hooks/api/ownerHooks';
import { cn } from '@/utils/classNames';
import { OWNER_ROUTES } from '@/utils/constants';
import { formatNumber } from '@/utils/formatUtils';
import { stagger } from '@/utils/motion';

/** The steps of the kitchen that wait for the owner (or for a rider), in the order of the work. */
const STEPS: readonly { tab: OwnerOrderTabId; icon: LucideIcon; todo: string; idle: string }[] = [
  { tab: 'new', icon: BellRing, todo: 'Accept or reject them', idle: 'No order is waiting' },
  { tab: 'accepted', icon: CircleCheck, todo: 'Start preparing them', idle: 'Nothing to start' },
  { tab: 'preparing', icon: ChefHat, todo: 'Mark them ready', idle: 'Nothing on the stove' },
  {
    tab: 'ready',
    icon: PackageCheck,
    todo: 'Waiting for a rider',
    idle: 'Nothing waiting for pickup',
  },
];

// Every order of these steps, whatever day it was placed: the same counts as the order tabs (one cache)
const REQUESTS = STEPS.map(({ tab }) => ({
  key: tab,
  filters: ownerOrderCountFilters(ownerOrderTabOf(tab)),
}));

/**
 * "Needs your attention": how many orders wait at each step of the kitchen right now, each a link to its tab.
 * New orders stand out while there are any: the customer is waiting for an answer.
 */
export function AttentionCards({ restaurantId }: { restaurantId: number }) {
  const counts = useOwnerOrderCounts(restaurantId, REQUESTS);

  return (
    <section aria-labelledby="attention-title" className="flex flex-col gap-3">
      <h2 id="attention-title" className="text-lg font-bold text-ink">
        Needs your attention
      </h2>
      <ul className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
        {STEPS.map(({ tab, icon: Icon, todo, idle }, index) => {
          const step = ownerOrderTabOf(tab);
          const count = counts[tab];
          const urgent = tab === 'new' && (count ?? 0) > 0;
          const hint = count === 0 ? idle : todo;
          return (
            <li key={tab} style={stagger(index)} className="stagger motion-safe:animate-rise-in">
              <Link
                to={tab === 'new' ? OWNER_ROUTES.orders : `${OWNER_ROUTES.orders}?tab=${tab}`}
                aria-label={`${step.label}: ${count === undefined ? 'loading' : formatNumber(count)} ${count === 1 ? 'order' : 'orders'}. ${hint}`}
                className={cn(
                  'flex h-full items-center gap-4 rounded-2xl p-4 shadow-card ring-1 transition duration-200',
                  'focus-visible:ring-4 focus-visible:ring-brand-500/30 focus-visible:outline-none motion-safe:hover:-translate-y-0.5',
                  urgent
                    ? 'bg-brand-500 text-on-brand ring-brand-500 hover:bg-brand-400'
                    : 'bg-surface text-ink ring-line/70 hover:ring-brand-200',
                )}
              >
                <span
                  aria-hidden="true"
                  className={cn(
                    'flex size-11 shrink-0 items-center justify-center rounded-xl',
                    urgent ? 'bg-on-brand/10' : 'bg-brand-50 text-brand-text-strong',
                  )}
                >
                  <Icon className="size-5" />
                </span>
                <span className="flex min-w-0 flex-1 flex-col">
                  <span className="text-sm font-semibold">{step.label}</span>
                  <span className={cn('text-xs', urgent ? 'text-on-brand/80' : 'text-ink-soft')}>
                    {hint}
                  </span>
                </span>
                {count === undefined ? (
                  <Skeleton className="h-8 w-8" />
                ) : (
                  <span aria-hidden="true" className="text-3xl font-extrabold tabular-nums">
                    {formatNumber(count)}
                  </span>
                )}
              </Link>
            </li>
          );
        })}
      </ul>
    </section>
  );
}
