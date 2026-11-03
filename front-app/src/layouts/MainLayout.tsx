import { Suspense } from 'react';
import { Outlet, useLocation } from 'react-router';

import { AssistantWidget } from '@/components/business/Assistant';
import { ErrorBoundary } from '@/components/common/ErrorBoundary';
import { PageLoader } from '@/components/common/Loader';
import { LayoutProvider } from '@/context/LayoutProvider';
import { Header } from '@/layouts/Header';
import { Sidebar } from '@/layouts/Sidebar';

/**
 * The admin area's frame: the sidebar and the header stay in place while the open page (Outlet) scrolls. A page
 * that crashes is replaced by an error message, while the sidebar and header keep working; going to another page
 * shows that page again.
 */
export function MainLayout() {
  const { pathname } = useLocation();

  return (
    <LayoutProvider>
      <div className="flex min-h-screen">
        <Sidebar />
        <div className="flex min-w-0 flex-1 flex-col">
          <Header />
          <main className="flex-1 px-4 pt-4 pb-12 sm:px-8 lg:px-10">
            <ErrorBoundary resetKey={pathname}>
              <Suspense fallback={<PageLoader />}>
                {/* Keyed by the address: each page plays the small entrance (motion-reduce: none) */}
                <div key={pathname} className="motion-safe:animate-page-in">
                  <Outlet />
                </div>
              </Suspense>
            </ErrorBoundary>
          </main>
        </div>
      </div>
      <AssistantWidget />
    </LayoutProvider>
  );
}
