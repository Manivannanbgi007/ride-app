import { Suspense, useEffect } from 'react';
import { Link, Outlet, useLocation } from 'react-router';

import { AssistantWidget } from '@/components/business/Assistant';
import { ErrorBoundary } from '@/components/common/ErrorBoundary';
import { PageLoader } from '@/components/common/Loader';
import { CustomerHeader } from '@/layouts/customer/CustomerHeader';
import { CUSTOMER_ROUTES, ROUTES } from '@/utils/constants';
import { env } from '@/utils/env';

/**
 * The customer area's frame: top bar, the open page, footer. Each new page starts at the top; a page that crashes
 * is replaced by an error message while the header keeps working.
 */
export function CustomerLayout() {
  const { pathname } = useLocation();

  useEffect(() => {
    window.scrollTo({ top: 0 });
  }, [pathname]);

  return (
    <div className="flex min-h-screen flex-col">
      <CustomerHeader />
      <main className="mx-auto w-full max-w-frame flex-1 px-4 pt-8 pb-16 sm:px-6 lg:px-8">
        <ErrorBoundary resetKey={pathname}>
          <Suspense fallback={<PageLoader />}>
            <div key={pathname} className="motion-safe:animate-page-in">
              <Outlet />
            </div>
          </Suspense>
        </ErrorBoundary>
      </main>
      <footer className="bg-surface">
        <div className="mx-auto flex max-w-frame flex-wrap items-center justify-between gap-4 px-4 py-6 text-sm text-ink-soft sm:px-6 lg:px-8">
          <p>
            <span className="font-semibold text-ink">{env.appName}</span> · Food from the
            restaurants of your city · © {new Date().getFullYear()}
          </p>
          <nav aria-label="More" className="flex gap-4">
            <Link to={CUSTOMER_ROUTES.restaurants} className="hover:text-brand-text">
              Restaurants
            </Link>
            <Link to={ROUTES.dashboard} className="hover:text-brand-text">
              Admin area
            </Link>
          </nav>
        </div>
      </footer>
      <AssistantWidget />
    </div>
  );
}
