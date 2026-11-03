import type { AppArea } from '@/utils/sessionUtils';

/** One area's access token. */
export interface AreaToken {
  /** The signed JWT, sent as `Authorization: Bearer <value>` */
  value: string;
  /** When user-service stops accepting it (milliseconds since 1970) */
  expiresAt: number;
  /** The user it was issued to: a token of someone else is never sent */
  userId: number;
}

/**
 * The access tokens of the four areas, in memory only: never in localStorage or sessionStorage, where any script on
 * the page could read them. A reload forgets them; the first request after it gets a new one with the area's
 * refresh cookie (api/sessionRefresh.ts), so the person stays signed in.
 */
const tokens = new Map<AppArea, AreaToken>();

export const tokenStore = {
  get: (area: AppArea): AreaToken | null => tokens.get(area) ?? null,
  set: (area: AppArea, token: AreaToken): void => {
    tokens.set(area, token);
  },
  clear: (area: AppArea): void => {
    tokens.delete(area);
  },
  clearAll: (): void => {
    tokens.clear();
  },
};

/** The token of a sign-in or refresh answer. */
export function tokenOf(
  session: { accessToken: string; expiresIn: number; userId: number },
  now = Date.now(),
): AreaToken {
  return {
    value: session.accessToken,
    expiresAt: now + session.expiresIn * 1000,
    userId: session.userId,
  };
}
