import { createContext, useContext } from 'react';

import type { LiveStatus } from '@/realtime/liveConnection';

/** The state of the live connection, for parts of the screen that show "Live" (see LiveUpdatesProvider). */
export const LiveStatusContext = createContext<LiveStatus>('closed');

export function useLiveStatus(): LiveStatus {
  return useContext(LiveStatusContext);
}
