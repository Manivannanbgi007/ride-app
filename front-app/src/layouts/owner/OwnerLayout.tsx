import { ClipboardList, LayoutDashboard, Star, Store, UtensilsCrossed } from 'lucide-react';
import { Suspense, useEffect, useRef, type ReactNode } from 'react';
import { Link, NavLink, useLocation } from 'react-router';

import { AssistantWidget } from '@/components/business/Assistant';
import { ErrorBoundary } from '@/components/common/ErrorBoundary';
import { PageLoader } from '@/components/common/Loader';
import { Select } from '@/components/common/Select';
import { ThemeToggle } from '@/components/common/ThemeToggle';
import { OwnerAccountMenu } from '@/layouts/owner/OwnerAccountMenu';
import { OwnerRestaurantsProvider } from '@/layouts/owner/OwnerRestaurantProvider';
import { useOwnerRestaurants } from '@/layouts/owner/ownerRestaurantContext';
import { cn } from '@/utils/classNames';
import { OWNER_ROUTES } from '@/utils/constants';
import { env } from '@/utils/env';

const NAV_LINKS = [
  { to: OWNER_ROUTES.home, label: 'Dashboard', icon: LayoutDashboard, end: true },
  { to: OWNER_ROUTES.orders, label: 'Orders', icon: ClipboardList, end: false },
  { to: OWNER_ROUTES.menu, label: 'Menu', icon: UtensilsCrossed, end: false },
  { to: OWNER_ROUTES.reviews, label: 'Reviews', icon: Star, end: false },
  { to: OWNER_ROUTES.restaurant, label: 'Restaurant', icon: Store, end: false },
] as const;

/**
 * The restaurant owner area's frame: a top bar (the restaurant being managed, the pages, the account), the open
 * page, and the assistant. A page that crashes is replaced by an error message while the header keeps working.
 */
export function OwnerLayout({ children }: { children: ReactNode }) {
  const { pathname } = useLocation();

  useEffect(() => {
    window.scrollTo({ top: 0 });
  }, [pathname]);

  return (
    <OwnerRestaurantsProvider>
      <div className="flex min-h-screen flex-col">
        <OwnerHeader />
        <main className="mx-auto w-full max-w-frame flex-1 px-4 pt-8 pb-16 sm:px-6 lg:px-8">
          <ErrorBoundary resetKey={pathname}>
            <Suspense fallback={<PageLoader />}>
              <div key={pathname} className="motion-safe:animate-page-in">
                {children}
              </div>
            </Suspense>
          </ErrorBoundary>
        </main>
        <AssistantWidget />
      </div>
    </OwnerRestaurantsProvider>
  );
}

function OwnerHeader() {
  const { restaurants, restaurant, select } = useOwnerRestaurants();
  const { pathname } = useLocation();
  const tabs = useRef<HTMLUListElement>(null);

  // On a narrow screen the bar scrolls: the open page's tab is brought into view
  useEffect(() => {
    tabs.current
      ?.querySelector('[aria-current="page"]')
      ?.scrollIntoView?.({ block: 'nearest', inline: 'nearest' });
  }, [pathname]);

  return (
    // data-app-header: the toasts appear just under it (components/common/Toast)
    <header data-app-header className="sticky top-0 z-20 bg-surface shadow-card">
      <div className="mx-auto flex max-w-frame flex-wrap items-center gap-x-6 gap-y-3 px-4 py-3 sm:px-6 lg:px-8">
        <Link
          to={OWNER_ROUTES.home}
          className="flex items-center gap-2 text-2xl font-extrabold tracking-tight"
        >
          <span>
            {env.appName}
            <span className="text-brand-500">.</span>
          </span>
          <span className="rounded-full bg-brand-50 px-2 py-0.5 text-xs font-bold tracking-normal text-brand-text-strong">
            Restaurant
          </span>
        </Link>
        {/*
          The pages as a tab bar: the open one is a green pill (like the main buttons), the others turn darker on hover.
          Below the xl breakpoint the bar takes its own row (it scrolls sideways on a phone).
        */}
        <nav aria-label="Main" className="order-last w-full xl:order-none xl:w-auto">
          <ul
            ref={tabs}
            className="flex w-fit max-w-full [scrollbar-width:none] gap-1 overflow-x-auto rounded-full bg-page p-1 ring-1 ring-line"
          >
            {NAV_LINKS.map(({ to, label, icon: Icon, end }) => (
              <li key={to} className="shrink-0">
                <NavLink
                  to={to}
                  end={end}
                  className={({ isActive }) =>
                    cn(
                      'flex items-center gap-2 rounded-full px-4 py-2 text-sm font-semibold whitespace-nowrap transition duration-200',
                      'focus-visible:ring-4 focus-visible:ring-brand-500/30 focus-visible:outline-none',
                      isActive
                        ? 'bg-brand-500 text-on-brand shadow-brand'
                        : 'text-ink-soft hover:bg-surface hover:text-ink hover:shadow-sm',
                    )
                  }
                >
                  {({ isActive }) => (
                    <>
                      <Icon
                        aria-hidden="true"
                        className="size-4 shrink-0"
                        strokeWidth={isActive ? 2.5 : 2}
                      />
                      {label}
                    </>
                  )}
                </NavLink>
              </li>
            ))}
          </ul>
        </nav>
        <div className="ml-auto flex items-center gap-3">
          {restaurant && restaurants.length > 1 && (
            <Select
              label="Restaurant"
              options={restaurants.map((restaurant) => ({
                value: String(restaurant.id),
                label: restaurant.name,
              }))}
              value={String(restaurant.id)}
              onChange={(event) => select(Number(event.target.value))}
            />
          )}
          <ThemeToggle />
          <OwnerAccountMenu />
        </div>
      </div>
    </header>
  );
}
