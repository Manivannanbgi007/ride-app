import type { QueryClient } from '@tanstack/react-query';

import { sessionApi } from '@/api/sessionApi';
import { tokenOf, tokenStore } from '@/api/tokenStore';
import type { AppDispatch } from '@/store/store';
import {
  adminSignedIn,
  adminSignedOut,
  customerSignedIn,
  customerSignedOut,
  ownerSignedIn,
  ownerSignedOut,
  partnerSignedIn,
  partnerSignedOut,
} from '@/store/slices/sessionSlice';
import type { LoginResponse } from '@/types/storefront';
import type { SignedInUser } from '@/types/user';
import { logger } from '@/utils/logger';
import { QUERY_KEYS } from '@/utils/queryKeys';
import {
  AREA_ROLES,
  saveSignedInAdmin,
  saveSignedInCustomer,
  saveSignedInOwner,
  saveSignedInPartner,
  type AppArea,
} from '@/utils/sessionUtils';

/** Where a session's effects go: the application's store and React Query cache (or a test's). */
export interface SessionTargets {
  dispatch: AppDispatch;
  queryClient: QueryClient;
}

const SIGNED_IN = {
  customer: customerSignedIn,
  owner: ownerSignedIn,
  partner: partnerSignedIn,
  admin: adminSignedIn,
} as const;

const SIGNED_OUT = {
  customer: customerSignedOut,
  owner: ownerSignedOut,
  partner: partnerSignedOut,
  admin: adminSignedOut,
} as const;

const SAVE = {
  customer: saveSignedInCustomer,
  owner: saveSignedInOwner,
  partner: saveSignedInPartner,
  admin: saveSignedInAdmin,
} as const;

/** Data of the area's previous person is dropped, so one person never sees another's cart, orders or restaurant. */
function forgetData(area: AppArea, queryClient: QueryClient): void {
  switch (area) {
    case 'customer':
      queryClient.removeQueries({ queryKey: QUERY_KEYS.customerAll });
      break;
    case 'owner':
      queryClient.removeQueries({ queryKey: QUERY_KEYS.ownerAll });
      break;
    case 'partner':
      queryClient.removeQueries({ queryKey: QUERY_KEYS.partnerAreaAll });
      break;
    default:
      // What an admin looked at is not kept for anyone
      queryClient.clear();
  }
}

/**
 * A successful sign-in to `area`: the person is remembered for the browser tab (who they are, not the token), the
 * access token is kept in memory, and the area's previous data is dropped.
 */
export function startAreaSession(
  area: AppArea,
  login: LoginResponse,
  { dispatch, queryClient }: SessionTargets,
): SignedInUser {
  const user: SignedInUser = {
    id: login.userId,
    role: login.role,
    fullName: login.fullName,
    email: login.email,
  };
  forgetData(area, queryClient);
  tokenStore.set(area, tokenOf(login));
  SAVE[area](user);
  dispatch(SIGNED_IN[area](user));
  return user;
}

/**
 * Signs `area` out in this tab: the token, the remembered person and the area's data are dropped. With
 * `endOnServer` (signing out on purpose) user-service also ends the session and removes the refresh cookie; a
 * session the server already ended (renewal refused) needs no call.
 */
export function endAreaSession(
  area: AppArea,
  { dispatch, queryClient }: SessionTargets,
  { endOnServer }: { endOnServer: boolean },
): void {
  tokenStore.clear(area);
  SAVE[area](null);
  dispatch(SIGNED_OUT[area]());
  forgetData(area, queryClient);
  if (endOnServer) {
    // Signed out here whatever happens; if the call fails, the server session still expires after 7 days
    Promise.resolve()
      .then(() => sessionApi.logout(AREA_ROLES[area]))
      .catch((error: unknown) => {
        logger.warn('The session could not be ended on the server', error);
      });
  }
}
