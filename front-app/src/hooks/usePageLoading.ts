import { useSyncExternalStore } from 'react';

import { getPageLoading, subscribeToPageLoading, type PageLoading } from '@/utils/pageLoadTracker';

/** Pages whose code is downloading, and page loaders on screen (see utils/pageLoadTracker.ts). */
export function usePageLoading(): PageLoading {
  return useSyncExternalStore(subscribeToPageLoading, getPageLoading, getPageLoading);
}
