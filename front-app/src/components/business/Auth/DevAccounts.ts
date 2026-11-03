import { lazy } from 'react';

import { env } from '@/utils/env';

/**
 * The test accounts under the sign-in forms (DevAccountsPanel), or null. Only while developing: `vite build` turns
 * import.meta.env.DEV into false, so the panel and its passwords are left out of every build (demo, UAT,
 * pre-production, production) even when VITE_FEATURE_DEV_ACCOUNTS is on.
 */
export const DevAccounts =
  import.meta.env.DEV && env.features.devAccounts
    ? lazy(() => import('@/components/business/Auth/DevAccountsPanel'))
    : null;
