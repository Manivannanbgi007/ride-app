import { act, render, screen, waitFor } from '@testing-library/react';
import { Suspense } from 'react';
import { describe, expect, it, vi } from 'vitest';

import { ErrorFallback } from '@/components/common/ErrorBoundary/ErrorFallback';
import { NavigationProgress, PageLoader } from '@/components/common/Loader';
import { lazyPage } from '@/utils/pageLoadTracker';

/** A page whose code "downloads" until finish() is called. */
function slowPage(title: string) {
  let finish = () => {};
  const Page = lazyPage(
    () =>
      new Promise<{ default: () => React.JSX.Element }>((resolve) => {
        finish = () => resolve({ default: () => <h1>{title}</h1> });
      }),
  );
  return { Page, finish: () => finish() };
}

describe('NavigationProgress', () => {
  it('says the next page is loading while its code downloads, and stops when it is there', async () => {
    const { Page, finish } = slowPage('Orders');
    // The page on screen stays (no fallback): the bar is the loading sign
    render(
      <>
        <NavigationProgress />
        <Suspense fallback={null}>
          <Page />
        </Suspense>
      </>,
    );

    expect(await screen.findByText('Loading the page')).toBeInTheDocument();

    act(finish);
    expect(await screen.findByRole('heading', { name: 'Orders' })).toBeInTheDocument();
    await waitFor(() => expect(screen.queryByText('Loading the page')).not.toBeInTheDocument());
  });

  it('stays away while the page loader stands in for the page (one loading sign at a time)', async () => {
    const { Page, finish } = slowPage('Dashboard');
    render(
      <>
        <NavigationProgress />
        <Suspense fallback={<PageLoader />}>
          <Page />
        </Suspense>
      </>,
    );

    expect(screen.getByRole('status', { name: 'Loading the page…' })).toBeInTheDocument();
    await act(async () => {});
    expect(screen.queryByText('Loading the page')).not.toBeInTheDocument();

    act(finish);
    expect(await screen.findByRole('heading', { name: 'Dashboard' })).toBeInTheDocument();
    expect(screen.queryByRole('status', { name: 'Loading the page…' })).not.toBeInTheDocument();
  });
});

describe('ErrorFallback', () => {
  it('asks to reload when a page could not be downloaded (trying again in place cannot work)', () => {
    render(
      <ErrorFallback
        error={
          new TypeError(
            'Failed to fetch dynamically imported module: http://localhost/assets/OrderListPage.js',
          )
        }
        onReset={vi.fn()}
      />,
    );

    expect(
      screen.getByRole('heading', { name: 'This page could not be downloaded' }),
    ).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Reload page' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Try again' })).not.toBeInTheDocument();
  });

  it('offers "Try again" for a page that crashed', () => {
    render(<ErrorFallback error={new Error('Cannot read x of undefined')} onReset={vi.fn()} />);

    expect(
      screen.getByRole('heading', { name: 'This page ran into a problem' }),
    ).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Try again' })).toBeInTheDocument();
  });
});
