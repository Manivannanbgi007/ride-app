import {
  Ban,
  BellRing,
  Bike,
  CheckCheck,
  ChefHat,
  CircleCheck,
  CircleX,
  PackageCheck,
  type LucideIcon,
} from 'lucide-react';
import { Fragment, useEffect, useRef, type KeyboardEvent } from 'react';

import { ORDER_STATUS_TONES } from '@/components/business/OwnerOrders/orderStatusTones';
import {
  OWNER_ORDER_TABS,
  type OwnerOrderTabId,
} from '@/components/business/OwnerOrders/ownerOrderUtils';
import { cn } from '@/utils/classNames';

/** Each status tab's icon (also the heading of its list) */
export const OWNER_ORDER_TAB_ICONS: Record<OwnerOrderTabId, LucideIcon> = {
  new: BellRing,
  accepted: CircleCheck,
  preparing: ChefHat,
  ready: PackageCheck,
  'on-the-way': Bike,
  delivered: CheckCheck,
  rejected: Ban,
  cancelled: CircleX,
};

/** Keys that move between the tabs (as screen-reader users expect from tabs) */
const KEY_STEPS: Partial<Record<string, 'previous' | 'next' | 'first' | 'last'>> = {
  ArrowLeft: 'previous',
  ArrowRight: 'next',
  Home: 'first',
  End: 'last',
};

interface OwnerOrderStatusTabsProps {
  activeId: OwnerOrderTabId;
  /** How many orders each status has (with the list's dates); a count still loading is left out */
  counts: Partial<Record<OwnerOrderTabId, number>>;
  onChange: (id: OwnerOrderTabId) => void;
}

/**
 * The order statuses of the owner's Orders page as a row of cards: an icon, how many orders, and the status, each in
 * its status's own colour (the icon tile, the number, a tint on hover). The open one is filled with its colour and
 * glows; "New" gets a pulsing dot while orders wait for a decision. The finished statuses come after a divider. On a
 * narrow screen the row scrolls sideways and keeps the open status in view. Real tabs underneath (the panel is the
 * page's TabPanel).
 */
export function OwnerOrderStatusTabs({ activeId, counts, onChange }: OwnerOrderStatusTabsProps) {
  const buttons = useRef<(HTMLButtonElement | null)[]>([]);
  const activeIndex = OWNER_ORDER_TABS.findIndex((tab) => tab.id === activeId);

  // The open status stays in view in the sideways row (a link to ?tab=cancelled, a click near the edge)
  useEffect(() => {
    const button = buttons.current[activeIndex];
    if (button && typeof button.scrollIntoView === 'function') {
      button.scrollIntoView({ block: 'nearest', inline: 'nearest' });
    }
  }, [activeIndex]);

  const moveWithKeys = (event: KeyboardEvent, index: number) => {
    const step = KEY_STEPS[event.key];
    if (!step) {
      return;
    }
    event.preventDefault();
    const last = OWNER_ORDER_TABS.length - 1;
    const next = {
      previous: index === 0 ? last : index - 1,
      next: index === last ? 0 : index + 1,
      first: 0,
      last,
    }[step];
    onChange(OWNER_ORDER_TABS[next].id);
    buttons.current[next]?.focus();
  };

  return (
    <div
      role="tablist"
      aria-label="Order status"
      className="-mx-1 flex snap-x gap-3 overflow-x-auto px-1 pt-1 pb-3 xl:overflow-visible"
    >
      {OWNER_ORDER_TABS.map((tab, index) => {
        const Icon = OWNER_ORDER_TAB_ICONS[tab.id];
        const tone = ORDER_STATUS_TONES[tab.status];
        const isActive = tab.id === activeId;
        const count = counts[tab.id];
        const countId = `tab-${tab.id}-count`;
        const waiting = tab.id === 'new' && (count ?? 0) > 0 && !isActive;
        return (
          <Fragment key={tab.id}>
            {/* Between the orders in progress and the finished ones */}
            {tab.id === 'delivered' && (
              <span aria-hidden="true" className="my-3 w-px shrink-0 self-stretch bg-line" />
            )}
            <button
              ref={(element) => {
                buttons.current[index] = element;
              }}
              type="button"
              role="tab"
              id={`tab-${tab.id}`}
              aria-selected={isActive}
              // The status is the tab's name; its number of orders is read as the description
              aria-label={tab.label}
              aria-controls={`panel-${tab.id}`}
              aria-describedby={count === undefined ? undefined : countId}
              tabIndex={isActive ? 0 : -1}
              onClick={() => onChange(tab.id)}
              onKeyDown={(event) => moveWithKeys(event, index)}
              className={cn(
                'relative isolate flex min-w-[9.75rem] shrink-0 snap-start flex-col gap-3 overflow-hidden rounded-2xl p-4 text-left',
                'transition-[background-color,box-shadow,translate,color] duration-200',
                'focus-visible:ring-4 focus-visible:ring-brand-500/30 focus-visible:outline-none xl:min-w-0 xl:flex-1',
                isActive
                  ? cn(tone.fill, tone.glow, 'text-white motion-safe:-translate-y-0.5')
                  : cn(
                      'bg-surface text-ink shadow-card ring-1 ring-line motion-safe:hover:-translate-y-0.5',
                      tone.hover,
                    ),
              )}
            >
              {/* A soft light in the corner of the open tab */}
              {isActive && (
                <span
                  aria-hidden="true"
                  className="absolute -top-8 -right-8 -z-10 size-24 rounded-full bg-white/15"
                />
              )}
              <span className="flex items-start justify-between gap-3">
                <span
                  aria-hidden="true"
                  className={cn(
                    'flex size-9 items-center justify-center rounded-xl text-white',
                    isActive ? 'bg-white/20 ring-1 ring-white/30' : cn(tone.fill, 'shadow-sm'),
                  )}
                >
                  <Icon className="size-4.5" />
                </span>
                {count === undefined ? (
                  <span
                    aria-hidden="true"
                    className={cn(
                      'mt-1 h-6 w-7 rounded-md motion-safe:animate-pulse',
                      isActive ? 'bg-white/25' : 'bg-line',
                    )}
                  />
                ) : (
                  <span
                    aria-hidden="true"
                    className={cn(
                      'text-2xl leading-none font-extrabold tabular-nums',
                      !isActive && (count === 0 ? 'text-ink-soft/70' : tone.text),
                    )}
                  >
                    {count}
                  </span>
                )}
              </span>
              <span className="flex items-center gap-1.5 text-sm font-semibold whitespace-nowrap">
                {tab.label}
                {waiting && (
                  <span aria-hidden="true" className="relative flex size-2">
                    <span className="absolute inline-flex size-full rounded-full bg-st-placed-to opacity-75 motion-safe:animate-ping" />
                    <span className="relative inline-flex size-2 rounded-full bg-st-placed-to" />
                  </span>
                )}
              </span>
            </button>
            {/* What the tab's description reads ("2 orders"); the big number above is for the eyes only */}
            {count !== undefined && (
              <span id={countId} hidden>
                {count === 1 ? '1 order' : `${count} orders`}
              </span>
            )}
          </Fragment>
        );
      })}
    </div>
  );
}
