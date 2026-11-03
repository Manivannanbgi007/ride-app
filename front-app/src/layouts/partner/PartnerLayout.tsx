import { Bike, House, ListChecks, UserRound } from 'lucide-react';
import { Suspense, useEffect, type ReactNode } from 'react';
import { Link, NavLink, useLocation } from 'react-router';

import { AssistantWidget } from '@/components/business/Assistant';
import { ErrorBoundary } from '@/components/common/ErrorBoundary';
import { PageLoader } from '@/components/common/Loader';
import { ThemeToggle } from '@/components/common/ThemeToggle';
import { PartnerAccountMenu } from '@/layouts/partner/PartnerAccountMenu';
import { LiveLocationChip, PartnerLiveLocation } from '@/layouts/partner/PartnerLiveLocation';
import { PartnerStatusChip } from '@/layouts/partner/PartnerStatusChip';
import { cn } from '@/utils/classNames';
import { PARTNER_ROUTES } from '@/utils/constants';
import { env } from '@/utils/env';

const NAV_LINKS = [
  { to: PARTNER_ROUTES.home, label: 'Today', icon: House, end: true },
  { to: PARTNER_ROUTES.deliveries, label: 'My deliveries', icon: ListChecks, end: false },
  { to: PARTNER_ROUTES.profile, label: 'Profile', icon: UserRound, end: false },
] as const;

/** The width of the top bar's content and of the pages: max-w-partner (index.css), 101.5rem (1624px). */
const FRAME_WIDTH = 'mx-auto w-full max-w-partner px-4 sm:px-6';

/**
 * The delivery partner area's frame, made for a phone on the road: a see-through top bar (the brand, the partner's
 * status, the account) and the pages as a tab bar at the bottom, in thumb reach; on a wide screen the tabs move into
 * the top bar. A page that crashes is replaced by an error message while the bars keep working. While a delivery is
 * on its way the partner's live position is shared from every page (PartnerLiveLocation).
 */
export function PartnerLayout({ children }: { children: ReactNode }) {
  const { pathname } = useLocation();

  useEffect(() => {
    window.scrollTo({ top: 0 });
  }, [pathname]);

  return (
    <PartnerLiveLocation>
      <div className="flex min-h-screen flex-col">
        {/* data-app-header: the toasts appear just under it (components/common/Toast) */}
        <header data-app-header className="sticky top-0 z-30">
          {/*
           * The frosted glass is a layer of its own: a blur on the header itself would make it the frame of the
           * fixed tab bar inside it (the bar would stick to the header instead of the bottom of the screen).
           */}
          <span
            aria-hidden="true"
            className="absolute inset-0 -z-10 border-b border-line/70 bg-surface/80 backdrop-blur-xl"
          />
          <div className={cn(FRAME_WIDTH, 'flex h-17 items-center gap-3')}>
            <Link
              to={PARTNER_ROUTES.home}
              className="flex shrink-0 items-center gap-2.5 rounded-xl focus-visible:ring-4 focus-visible:ring-brand-500/30 focus-visible:outline-none"
            >
              <span
                aria-hidden="true"
                className="flex size-9 items-center justify-center rounded-xl bg-brand-500 text-on-brand shadow-brand"
              >
                <Bike className="size-5" />
              </span>
              <span className="text-xl font-extrabold tracking-tight">
                {env.appName}
                <span className="text-brand-500">.</span>
              </span>
              <span className="hidden rounded-full bg-brand-50 px-2 py-0.5 text-xs font-bold text-brand-text-strong sm:inline">
                Partner
              </span>
            </Link>

            <nav
              aria-label="Main"
              className={cn(
                'fixed inset-x-0 bottom-0 z-40 border-t border-line/70 bg-surface/90 pb-[env(safe-area-inset-bottom)] backdrop-blur-xl',
                'lg:static lg:mx-auto lg:border-0 lg:bg-transparent lg:pb-0 lg:backdrop-blur-none',
              )}
            >
              <ul className="mx-auto grid max-w-md grid-cols-3 lg:flex lg:max-w-none lg:gap-1 lg:rounded-full lg:bg-page lg:p-1 lg:ring-1 lg:ring-line">
                {NAV_LINKS.map(({ to, label, icon: Icon, end }) => (
                  <li key={to}>
                    <NavLink
                      to={to}
                      end={end}
                      className={({ isActive }) =>
                        cn(
                          'group flex flex-col items-center gap-1 pt-2 pb-2.5 text-[11px] font-semibold transition-colors',
                          'focus-visible:ring-4 focus-visible:ring-brand-500/30 focus-visible:outline-none',
                          'lg:flex-row lg:gap-2 lg:rounded-full lg:px-4 lg:py-2 lg:text-sm',
                          isActive
                            ? 'text-brand-text-strong lg:bg-brand-500 lg:text-on-brand lg:shadow-brand'
                            : 'text-ink-soft hover:text-ink',
                        )
                      }
                    >
                      {({ isActive }) => (
                        <>
                          <span
                            aria-hidden="true"
                            className={cn(
                              'flex h-8 w-14 items-center justify-center rounded-full transition-colors duration-200',
                              'lg:h-auto lg:w-auto lg:bg-transparent',
                              isActive ? 'bg-brand-100' : 'group-hover:bg-page',
                            )}
                          >
                            <Icon className="size-5 lg:size-4" strokeWidth={isActive ? 2.5 : 2} />
                          </span>
                          {label}
                        </>
                      )}
                    </NavLink>
                  </li>
                ))}
              </ul>
            </nav>

            <div className="ml-auto flex items-center gap-2 sm:gap-3 lg:ml-0">
              <LiveLocationChip />
              <PartnerStatusChip />
              <ThemeToggle />
              <PartnerAccountMenu />
            </div>
          </div>
        </header>
        <main className={cn(FRAME_WIDTH, 'flex-1 pt-6 pb-28 lg:pt-8 lg:pb-16')}>
          <ErrorBoundary resetKey={pathname}>
            <Suspense fallback={<PageLoader />}>
              <div key={pathname} className="motion-safe:animate-page-in">
                {children}
              </div>
            </Suspense>
          </ErrorBoundary>
        </main>
        {/* The assistant (raised above the bottom tab bar on a phone) */}
        <AssistantWidget raised />
      </div>
    </PartnerLiveLocation>
  );
}
