import { Search, Utensils } from 'lucide-react';
import { useEffect, useRef, useState, type CSSProperties } from 'react';

import { AddToCart, CartBar } from '@/components/business/Cart';
import { DishReviewsDialog, RestaurantReviews } from '@/components/business/Rating';
import { FoodTypeMark, MenuSections, RestaurantInfoCard } from '@/components/business/Storefront';
import { Alert } from '@/components/common/Alert';
import { AsyncContent } from '@/components/common/AsyncContent';
import { BackLink } from '@/components/common/BackLink';
import { Button } from '@/components/common/Button';
import { EmptyState } from '@/components/common/EmptyState';
import { FoodPhoto } from '@/components/common/FoodPhoto';
import { SkeletonLoader } from '@/components/common/Loader';
import { useDishRatings } from '@/hooks/api/ratingHooks';
import { useRestaurantMenu, useStoreRestaurant } from '@/hooks/api/storefrontHooks';
import { useAppHeaderHeight } from '@/hooks/useAppHeaderHeight';
import { useIdParam } from '@/hooks/useIdParam';
import { NotFoundPage } from '@/pages/NotFound/NotFoundPage';
import type { MenuEntry, MenuSection, RestaurantMenu } from '@/types/storefront';
import { cn } from '@/utils/classNames';
import { CUSTOMER_ROUTES } from '@/utils/constants';
import { ERROR_MESSAGES } from '@/utils/errorMessages';
import { categoryImageOf } from '@/utils/foodImages';
import { filterMenu, sectionAnchorOf } from '@/utils/storefrontUtils';

/** A restaurant's page: who they are, when they are open, and their menu by category. */
export default function RestaurantMenuPage() {
  const restaurantId = useIdParam('restaurantId');
  return restaurantId === null ? (
    <NotFoundPage />
  ) : (
    <RestaurantMenuView restaurantId={restaurantId} />
  );
}

function RestaurantMenuView({ restaurantId }: { restaurantId: number }) {
  const restaurant = useStoreRestaurant(restaurantId);
  const menu = useRestaurantMenu(restaurantId);

  return (
    <div className="mx-auto flex w-full max-w-5xl flex-col gap-6">
      <BackLink to={CUSTOMER_ROUTES.restaurants}>All restaurants</BackLink>
      <AsyncContent
        isLoading={restaurant.isPending}
        error={restaurant.error}
        errorTitle={ERROR_MESSAGES.STORE_RESTAURANT_LOAD_FAILED}
        onRetry={() => void restaurant.refetch()}
        isRetrying={restaurant.isFetching}
        loading={<SkeletonLoader lines={5} className="rounded-3xl bg-surface p-6" />}
      >
        {restaurant.data && (
          <>
            <RestaurantInfoCard restaurant={restaurant.data} />
            {!restaurant.data.openNow && (
              <Alert tone="info" title="Closed right now">
                You can look at the menu and fill your cart; orders are taken again during the
                opening hours above.
              </Alert>
            )}
            <AsyncContent
              isLoading={menu.isPending}
              error={menu.error}
              errorTitle={ERROR_MESSAGES.MENU_LOAD_FAILED}
              onRetry={() => void menu.refetch()}
              isRetrying={menu.isFetching}
              loading={<SkeletonLoader lines={8} className="rounded-3xl bg-surface p-6" />}
            >
              {menu.data && <Menu menu={menu.data} />}
              <CartBar restaurantId={restaurantId} />
            </AsyncContent>
            <RestaurantReviews restaurantId={restaurantId} restaurantName={restaurant.data.name} />
          </>
        )}
      </AsyncContent>
    </div>
  );
}

/**
 * The menu: a bar that stays under the site's header while the dishes scroll (search the menu, "Veg only", and a
 * chip per category that jumps to it and lights up while its dishes are on screen), then the dishes by category.
 */
function Menu({ menu }: { menu: RestaurantMenu }) {
  const [vegOnly, setVegOnly] = useState(false);
  const [text, setText] = useState('');
  // The dish whose reviews are open ("Read reviews" next to its stars)
  const [reviewsOf, setReviewsOf] = useState<MenuEntry | null>(null);
  const dishRatings = useDishRatings(
    menu.sections.flatMap((section) => section.items.map((item) => item.id)),
  );
  const sections = filterMenu(menu.sections, { vegOnly, text });

  // The bar sits just under the site's header; a category's heading stops just under the bar
  const headerHeight = useAppHeaderHeight();
  const bar = useRef<HTMLDivElement>(null);
  const [barHeight, setBarHeight] = useState(0);
  useEffect(() => {
    const element = bar.current;
    if (!element) {
      return undefined;
    }
    const measure = () => setBarHeight(Math.round(element.getBoundingClientRect().height));
    measure();
    if (typeof ResizeObserver === 'undefined') {
      return undefined;
    }
    const observer = new ResizeObserver(measure);
    observer.observe(element);
    return () => observer.disconnect();
  }, []);
  const offset = headerHeight + barHeight + 16;

  if (menu.sections.length === 0) {
    return <EmptyState title="This restaurant has no dishes on its menu yet" />;
  }

  const clearFilters = () => {
    setVegOnly(false);
    setText('');
  };

  return (
    <div
      className="flex flex-col gap-6"
      style={{ '--menu-offset': `${offset}px` } as CSSProperties}
    >
      <div
        ref={bar}
        style={{ top: headerHeight }}
        className="sticky z-10 -mx-4 bg-page/90 px-4 py-3 backdrop-blur-md sm:mx-0 sm:rounded-2xl sm:bg-surface/90 sm:px-4 sm:shadow-card sm:ring-1 sm:ring-line/60"
      >
        <div className="flex flex-col gap-3">
          <div className="flex items-center gap-3">
            <label className="relative min-w-0 flex-1">
              <span className="sr-only">Search this menu</span>
              <Search
                aria-hidden="true"
                className="pointer-events-none absolute top-1/2 left-3.5 size-4 -translate-y-1/2 text-ink-soft"
              />
              <input
                type="search"
                placeholder="Search this menu"
                value={text}
                onChange={(event) => setText(event.target.value)}
                maxLength={100}
                className="h-10 w-full rounded-full border border-line bg-surface pr-4 pl-10 text-sm text-ink focus:border-brand-500 focus:ring-2 focus:ring-brand-100 focus:outline-none"
              />
            </label>
            <VegOnlySwitch checked={vegOnly} onChange={setVegOnly} />
          </div>
          <CategoryChips sections={sections} offset={offset} />
        </div>
      </div>

      {sections.length === 0 ? (
        <EmptyState
          title="No dishes match"
          action={
            <Button variant="outline" size="sm" onClick={clearFilters}>
              Show the whole menu
            </Button>
          }
        />
      ) : (
        <MenuSections
          sections={sections}
          ratings={dishRatings}
          renderAction={(item) => <AddToCart item={item} restaurantId={menu.restaurantId} />}
          onShowReviews={setReviewsOf}
        />
      )}
      <DishReviewsDialog dish={reviewsOf} onClose={() => setReviewsOf(null)} />
    </div>
  );
}

/** "Veg only" as a switch: a real checkbox (keyboard and screen readers) drawn as a green track. */
function VegOnlySwitch({
  checked,
  onChange,
}: {
  checked: boolean;
  onChange: (checked: boolean) => void;
}) {
  return (
    <label className="flex h-10 shrink-0 cursor-pointer items-center gap-2 rounded-full bg-surface pr-3 pl-2 text-sm font-semibold text-ink ring-1 ring-line has-[:focus-visible]:ring-4 has-[:focus-visible]:ring-brand-500/30">
      <input
        type="checkbox"
        checked={checked}
        onChange={(event) => onChange(event.target.checked)}
        className="peer sr-only"
      />
      {/* The track; its knob slides right while "Veg only" is on */}
      <span
        aria-hidden="true"
        className="inline-flex h-5 w-9 shrink-0 items-center rounded-full bg-ink-soft/40 px-0.5 transition-colors duration-200 peer-checked:bg-brand-500 peer-checked:[&>span]:translate-x-4"
      >
        <span className="size-4 rounded-full bg-white shadow-sm transition-transform duration-200" />
      </span>
      <FoodTypeMark foodType="VEG" />
      Veg only
    </label>
  );
}

/** A category's picture in its chip (the same pictures as the dishes' fallbacks), or a utensils tile without one. */
function CategoryPhoto({ name, active }: { name: string; active: boolean }) {
  const ring = active ? 'ring-2 ring-white/80' : 'ring-1 ring-line';
  return (
    <FoodPhoto
      sources={[categoryImageOf(name)]}
      className={cn('size-8 rounded-full', ring)}
      fallback={
        <span
          aria-hidden="true"
          className={cn(
            'flex size-8 shrink-0 items-center justify-center rounded-full',
            active ? 'bg-on-brand/15 text-on-brand' : 'bg-brand-50 text-brand-text-strong',
            ring,
          )}
        >
          <Utensils className="size-4" />
        </span>
      }
    />
  );
}

/**
 * A chip per category of the (filtered) menu: it jumps to the category, and the category being read is lit up (and
 * kept in view in the sideways-scrolling row): the last one whose heading has reached the sticky bars, or the last
 * category once the page is scrolled to its end. Worked out once per frame while the page scrolls, so a jump (a
 * chip, the browser's back button) lights the right one too.
 */
function CategoryChips({ sections, offset }: { sections: readonly MenuSection[]; offset: number }) {
  const list = useRef<HTMLUListElement>(null);
  const [activeId, setActiveId] = useState<number | null>(null);
  const [moreRight, setMoreRight] = useState(false);
  const ids = sections.map((section) => section.categoryId).join(',');

  useEffect(() => {
    const headings = ids
      .split(',')
      .filter(Boolean)
      .map((id) => document.getElementById(sectionAnchorOf(Number(id))))
      .filter((heading): heading is HTMLElement => heading !== null);
    if (headings.length === 0) {
      return undefined;
    }
    let frame = 0;
    const update = () => {
      frame = 0;
      const atEnd =
        window.innerHeight + window.scrollY >= document.documentElement.scrollHeight - 2;
      let reading = headings[0];
      for (const heading of headings) {
        if (heading.getBoundingClientRect().top <= offset + 24) {
          reading = heading;
        }
      }
      if (atEnd && window.scrollY > 0) {
        reading = headings[headings.length - 1];
      }
      setActiveId(Number(reading.getAttribute('data-category-id')));
    };
    const onScroll = () => {
      if (!frame) {
        frame = requestAnimationFrame(update);
      }
    };
    update();
    window.addEventListener('scroll', onScroll, { passive: true });
    return () => {
      window.removeEventListener('scroll', onScroll);
      if (frame) {
        cancelAnimationFrame(frame);
      }
    };
  }, [ids, offset]);

  const current = sections.some((section) => section.categoryId === activeId)
    ? activeId
    : (sections[0]?.categoryId ?? null);

  // The lit chip stays in view in the row (the row scrolls; the page does not)
  useEffect(() => {
    const row = list.current;
    const chip = row?.querySelector<HTMLElement>('[aria-current="true"]');
    if (row && chip && typeof row.scrollTo === 'function') {
      row.scrollTo({ left: chip.offsetLeft - 16, behavior: 'smooth' });
    }
  }, [current]);

  // Whether chips are hidden off the row's right edge (a narrow screen, many categories); kept up to date
  useEffect(() => {
    const row = list.current;
    if (!row) {
      return undefined;
    }
    const check = () => setMoreRight(row.scrollWidth - row.scrollLeft - row.clientWidth > 1);
    check();
    row.addEventListener('scroll', check, { passive: true });
    const observer = typeof ResizeObserver === 'undefined' ? null : new ResizeObserver(check);
    observer?.observe(row);
    return () => {
      row.removeEventListener('scroll', check);
      observer?.disconnect();
    };
  }, [ids]);

  if (sections.length === 0) {
    return null;
  }
  return (
    <nav aria-label="Menu categories" className="min-w-0 flex-1">
      {/* The padding gives the chips' shadow and hover lift room inside the scrolling row */}
      <ul
        ref={list}
        className={cn(
          '-my-1 flex [scrollbar-width:none] gap-2.5 overflow-x-auto px-0.5 py-1',
          // More chips off to the right: the row fades out there, so it reads as one that scrolls
          moreRight && '[mask-image:linear-gradient(to_right,black_82%,transparent)]',
        )}
      >
        {sections.map((section) => {
          const active = section.categoryId === current;
          return (
            <li key={section.categoryId} className="shrink-0">
              <a
                href={`#${sectionAnchorOf(section.categoryId)}`}
                aria-current={active ? 'true' : undefined}
                onClick={() => setActiveId(section.categoryId)}
                className={cn(
                  'group flex h-11 items-center gap-2.5 rounded-full py-1.5 pr-2 pl-1.5 text-sm font-bold whitespace-nowrap',
                  'transition-[background-color,box-shadow,translate,color] duration-200',
                  'focus-visible:ring-4 focus-visible:ring-brand-500/30 focus-visible:outline-none',
                  active
                    ? 'bg-brand-500 text-on-brand shadow-brand'
                    : 'bg-surface text-ink shadow-sm ring-1 ring-line hover:bg-brand-50 hover:text-brand-text-strong hover:shadow-md hover:ring-brand-300 motion-safe:hover:-translate-y-0.5',
                )}
              >
                <CategoryPhoto name={section.categoryName} active={active} />
                {section.categoryName}
                <span
                  className={cn(
                    'min-w-6 rounded-full px-2 py-0.5 text-center text-xs font-extrabold tabular-nums',
                    active ? 'bg-on-brand/15 text-on-brand' : 'bg-brand-50 text-brand-text-strong',
                  )}
                >
                  {section.items.length}
                  <span className="sr-only">
                    {section.items.length === 1 ? ' dish' : ' dishes'}
                  </span>
                </span>
              </a>
            </li>
          );
        })}
      </ul>
    </nav>
  );
}
