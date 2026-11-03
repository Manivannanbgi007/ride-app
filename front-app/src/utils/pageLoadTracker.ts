import { lazy, type ComponentType } from 'react';

import { env } from '@/utils/env';
import { logger } from '@/utils/logger';

/**
 * Page loading, as the loading bar and the page loader see it.
 *
 * Each page's code is downloaded the first time the page is opened (lazyPage below, used in App.tsx). While a
 * download runs, the page on screen stays (the router waits for the next page), so the thin bar at the top shows
 * that something is coming; on the very first page there is nothing to keep, and the big page loader stands in
 * for the page instead. The bar stays away while that loader is shown: one loading sign at a time.
 *
 * Every download is also timed for the browser's Performance panel ("Front-App · page code /admin/orders").
 */
export interface PageLoading {
  /** Pages whose code is downloading */
  downloads: number;
  /** Page loaders on screen */
  loadersShown: number;
}

let state: PageLoading = { downloads: 0, loadersShown: 0 };
const listeners = new Set<() => void>();

function change(update: (current: PageLoading) => PageLoading): void {
  state = update(state);
  listeners.forEach((listener) => listener());
}

export function subscribeToPageLoading(listener: () => void): () => void {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

export function getPageLoading(): PageLoading {
  return state;
}

/** A page loader appeared; call the function it returns when it goes away. */
export function pageLoaderShown(): () => void {
  change((current) => ({ ...current, loadersShown: current.loadersShown + 1 }));
  return () => change((current) => ({ ...current, loadersShown: current.loadersShown - 1 }));
}

/**
 * React.lazy for a page, with its download tracked and timed:
 *
 *   const OrderListPage = lazyPage(() => import('@/pages/Order/OrderListPage'));
 */
export function lazyPage<T extends ComponentType>(load: () => Promise<{ default: T }>) {
  return lazy(() => {
    // The address being opened (the router changes it before it draws the page)
    const page = window.location.pathname;
    const startedAt = performance.now();
    // React asks for the code while drawing: the bar is told just after, never in the middle of a drawing
    queueMicrotask(() => change((current) => ({ ...current, downloads: current.downloads + 1 })));
    return load().finally(() => {
      change((current) => ({ ...current, downloads: current.downloads - 1 }));
      measure(`page code ${page}`, startedAt);
    });
  });
}

let appStartMeasured = false;

/** Time from opening the address to the application's first screen (once per visit). */
export function measureAppStart(): void {
  if (!appStartMeasured) {
    appStartMeasured = true;
    measure('app started', 0);
  }
}

/**
 * Records "Front-App · <what>" from `start` until now: it shows in DevTools > Performance (the Timings track) and,
 * while developing, in the console. performance.getEntriesByType('measure') lists them all.
 */
function measure(what: string, start: number): void {
  const name = `${env.appName} · ${what}`;
  try {
    const entry = performance.measure(name, { start, end: performance.now() });
    logger.debug(`${name}: ${Math.round(entry.duration)} ms`);
  } catch {
    // A browser (or test environment) without the User Timing API: nothing to record
  }
}

/**
 * The page's code could not be downloaded (connection lost, or the application was updated and the old file is
 * gone). Trying again in place does not help (React remembers the failed download); reloading the page does.
 */
export function isPageDownloadError(error: Error): boolean {
  return /dynamically imported module|Importing a module script failed|Unable to preload CSS/i.test(
    error.message,
  );
}
