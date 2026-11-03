import {
  USER_ROLES,
  type ActingUser,
  type AdminSession,
  type SignedInUser,
  type UserRole,
} from '@/types/user';
import { OWNER_BASE, PARTNER_BASE, ROUTES } from '@/utils/constants';
import { logger } from '@/utils/logger';

/**
 * The application has four areas: the admin area (/admin/...), the restaurant owner's area (/owner/...), the
 * delivery partner's area (/partner/...) and the customer area (every other address).
 * The area decides who the application acts as: the person who signed in to that area, with that area's access
 * token. Because the address decides, a page never sends another area's user, even right after moving from one area
 * to the other.
 */
export type AppArea = 'admin' | 'owner' | 'partner' | 'customer';

/**
 * The kind of account each area is for. user-service keeps one sign-in session (refresh cookie) per role, so the
 * four areas can be signed in at the same time in one browser.
 */
export const AREA_ROLES: Readonly<Record<AppArea, UserRole>> = {
  customer: 'CUSTOMER',
  owner: 'RESTAURANT_ADMIN',
  partner: 'DELIVERY_PARTNER',
  admin: 'PLATFORM_ADMIN',
};

/** The area a kind of account signs in to (each role has exactly one). */
export function areaOfRole(role: UserRole): AppArea {
  const area = (Object.keys(AREA_ROLES) as AppArea[]).find(
    (candidate) => AREA_ROLES[candidate] === role,
  );
  return area ?? 'customer';
}

function isInside(pathname: string, base: string): boolean {
  return pathname === base || pathname.startsWith(`${base}/`);
}

export function areaOf(pathname: string): AppArea {
  if (isInside(pathname, ROUTES.admin)) {
    return 'admin';
  }
  if (isInside(pathname, PARTNER_BASE)) {
    return 'partner';
  }
  return isInside(pathname, OWNER_BASE) ? 'owner' : 'customer';
}

export interface SessionUsers {
  adminUser: AdminSession | null;
  customer: SignedInUser | null;
  /** The signed-in restaurant owner; absent in a session that knows nothing of the owner area */
  owner?: SignedInUser | null;
  /** The signed-in delivery partner; absent in a session that knows nothing of the partner area */
  partner?: SignedInUser | null;
}

export function actingUserOf(session: SessionUsers, area: AppArea): ActingUser | null {
  switch (area) {
    case 'admin':
      return session.adminUser;
    case 'owner':
      return session.owner ?? null;
    case 'partner':
      return session.partner ?? null;
    default:
      return session.customer;
  }
}

/** Who signed in to each area is kept for the browser tab (sessionStorage): a reload keeps them signed in. */
const CUSTOMER_KEY = 'front-app.customer';
const OWNER_KEY = 'front-app.owner';
const PARTNER_KEY = 'front-app.partner';
const ADMIN_KEY = 'front-app.admin';

function loadSession(key: string): SignedInUser | null {
  try {
    const saved = sessionStorage.getItem(key);
    if (!saved) {
      return null;
    }
    const value = JSON.parse(saved) as Partial<SignedInUser>;
    const valid =
      Number.isInteger(value.id) &&
      (value.id ?? 0) > 0 &&
      USER_ROLES.includes(value.role as UserRole) &&
      typeof value.fullName === 'string' &&
      typeof value.email === 'string';
    return valid ? (value as SignedInUser) : null;
  } catch (error) {
    // Storage blocked (private window, site data blocked) or damaged: start signed out
    logger.debug('The saved sign-in could not be read', error);
    return null;
  }
}

function saveSession(key: string, user: SignedInUser | null): void {
  try {
    if (user) {
      sessionStorage.setItem(key, JSON.stringify(user));
    } else {
      sessionStorage.removeItem(key);
    }
  } catch (error) {
    // Without storage the sign-in still works until the page is reloaded
    logger.debug('The sign-in could not be saved', error);
  }
}

export const loadSignedInCustomer = (): SignedInUser | null => loadSession(CUSTOMER_KEY);
export const saveSignedInCustomer = (customer: SignedInUser | null): void =>
  saveSession(CUSTOMER_KEY, customer);
export const loadSignedInAdmin = (): SignedInUser | null => loadSession(ADMIN_KEY);
export const saveSignedInAdmin = (admin: SignedInUser | null): void =>
  saveSession(ADMIN_KEY, admin);
export const loadSignedInOwner = (): SignedInUser | null => loadSession(OWNER_KEY);
export const saveSignedInOwner = (owner: SignedInUser | null): void =>
  saveSession(OWNER_KEY, owner);
export const loadSignedInPartner = (): SignedInUser | null => loadSession(PARTNER_KEY);
export const saveSignedInPartner = (partner: SignedInUser | null): void =>
  saveSession(PARTNER_KEY, partner);
