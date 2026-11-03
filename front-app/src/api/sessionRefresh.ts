import { toApiError } from '@/api/apiErrorHandler';
import { endAreaSession } from '@/api/areaSession';
import { queryClient } from '@/api/queryClient';
import { sessionApi } from '@/api/sessionApi';
import { tokenOf, tokenStore } from '@/api/tokenStore';
import { store } from '@/store/store';
import type { LoginResponse } from '@/types/storefront';
import type { UserRole } from '@/types/user';
import { logger } from '@/utils/logger';
import { actingUserOf, AREA_ROLES, type AppArea } from '@/utils/sessionUtils';

/**
 * A token with less time left than this is renewed before it is sent: a slow request must not outlive it, and the
 * assistant refuses one with less than 3 minutes left (an answer can take 150 s, and the platform is called with it)
 */
export const RENEW_BEFORE_MS = 240_000;
/** After a 409 (another tab renewed the same session at that moment) the renewal is tried once more, this later */
export const CONFLICT_RETRY_MS = 300;

/** The renewal running for each area: everyone who needs a token meanwhile waits for the same one */
const renewals = new Map<AppArea, Promise<string | null>>();

/**
 * The access token to send for `area`: the one in memory while it has more than 4 minutes left, otherwise a new one
 * (renewAccessToken). null when nobody is signed in to the area, or when the session has ended (the area is then
 * signed out and its pages send the person to its sign-in).
 */
export async function accessTokenFor(area: AppArea): Promise<string | null> {
  const user = actingUserOf(store.getState().session, area);
  if (!user) {
    return null;
  }
  const token = tokenStore.get(area);
  if (token && token.userId === user.id && token.expiresAt - Date.now() > RENEW_BEFORE_MS) {
    return token.value;
  }
  return renewAccessToken(area);
}

/**
 * A new access token for `area` from user-service, with the area's refresh cookie: after a reload (the token was
 * only in memory), shortly before it expires, or when an API refused it. One renewal at a time per area, in this tab
 * and (where the browser can) across its tabs, which share the cookie.
 */
export function renewAccessToken(area: AppArea): Promise<string | null> {
  const running = renewals.get(area);
  if (running) {
    return running;
  }
  const renewal = oneAtATime(area, () => renew(area)).finally(() => renewals.delete(area));
  renewals.set(area, renewal);
  return renewal;
}

async function renew(area: AppArea): Promise<string | null> {
  const user = actingUserOf(store.getState().session, area);
  if (!user) {
    return null;
  }
  try {
    const session = await refreshTolerantOfOtherTabs(AREA_ROLES[area]);
    if (session.userId !== user.id) {
      // Someone else signed in to this area in another tab: the cookie is theirs now, this tab's person is out
      logger.info(`Another account signed in to the ${area} area in another tab: signed out here`);
      signOut(area);
      return null;
    }
    tokenStore.set(area, tokenOf(session));
    return session.accessToken;
  } catch (error) {
    const apiError = toApiError(error);
    if (apiError.kind === 'unauthorized') {
      // The session ended: signed out elsewhere, expired, password or role changed, account deactivated
      logger.info(`The ${area} session has ended: sign in again`);
      signOut(area);
    } else {
      // The server could not be asked (network, restart): the person stays signed in, the next request tries again
      logger.warn(`The ${area} session could not be renewed`, apiError.message);
    }
    return null;
  }
}

async function refreshTolerantOfOtherTabs(role: UserRole): Promise<LoginResponse> {
  try {
    return await sessionApi.refresh(role);
  } catch (error) {
    if (toApiError(error).kind !== 'conflict') {
      throw error;
    }
    // Another tab renewed the same session a moment ago: the browser now holds its new cookie
    await new Promise((resolve) => window.setTimeout(resolve, CONFLICT_RETRY_MS));
    return sessionApi.refresh(role);
  }
}

/** Runs `task` while no other tab of this browser renews the same area (Web Locks API; without it, just runs). */
function oneAtATime<T>(area: AppArea, task: () => Promise<T>): Promise<T> {
  const locks = typeof navigator === 'undefined' ? undefined : navigator.locks;
  return locks ? locks.request(`food-delivery-session-${area}`, task) : task();
}

function signOut(area: AppArea): void {
  endAreaSession(area, { dispatch: store.dispatch, queryClient }, { endOnServer: false });
}
