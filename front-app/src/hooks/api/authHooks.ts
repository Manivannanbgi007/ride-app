import { useMutation, useQueryClient } from '@tanstack/react-query';

import { ApiError, toApiError } from '@/api/apiErrorHandler';
import { endAreaSession, startAreaSession } from '@/api/areaSession';
import { userService } from '@/services/userService';
import { useAppDispatch } from '@/store/hooks';
import type { RegisterRequest } from '@/types/storefront';
import type { SignedInUser } from '@/types/user';
import { AREA_ROLES, type AppArea } from '@/utils/sessionUtils';

/** Shown when an admin, restaurant or partner account signs in to the customer area. */
export const NOT_A_CUSTOMER_MESSAGE =
  'This account is not a customer account. Restaurant owners, delivery partners and admins use their own apps.';

/** Shown when a customer, partner or admin account signs in to the restaurant owner area. */
export const NOT_AN_OWNER_MESSAGE =
  'This account is not a restaurant owner account. Customers shop in the storefront; admins use the admin area.';

/** Shown when a customer, owner or admin account signs in to the delivery partner area. */
export const NOT_A_PARTNER_MESSAGE =
  'This account is not a delivery partner account. Customers shop in the storefront; restaurant owners use the restaurant area.';

/** Shown when a customer, owner or partner account signs in to the admin area. */
export const NOT_AN_ADMIN_MESSAGE =
  'This account is not an administrator account. Customers shop in the storefront; restaurant owners use the restaurant area.';

const WRONG_AREA_MESSAGES: Record<AppArea, string> = {
  customer: NOT_A_CUSTOMER_MESSAGE,
  owner: NOT_AN_OWNER_MESSAGE,
  partner: NOT_A_PARTNER_MESSAGE,
  admin: NOT_AN_ADMIN_MESSAGE,
};

/** The kind of account registration creates per area (a customer account is the default: not named) */
const REGISTER_ROLES: Partial<Record<AppArea, RegisterRequest['role']>> = {
  owner: 'RESTAURANT_ADMIN',
  partner: 'DELIVERY_PARTNER',
};

/** user-service's code for an account of another kind than the area it signs in to */
const WRONG_ACCOUNT_TYPE = 'WRONG_ACCOUNT_TYPE';

/**
 * Signing in to one area. user-service checks the password and that the account is of the area's kind (it then
 * returns an access token and sets the area's refresh cookie); the page checks the kind again. The area's previous
 * person's data is dropped first (api/areaSession.ts).
 */
function useAreaSignIn(area: AppArea) {
  const dispatch = useAppDispatch();
  const queryClient = useQueryClient();
  return async (email: string, password: string): Promise<SignedInUser> => {
    const role = AREA_ROLES[area];
    let login;
    try {
      login = await userService.login(email, password, role);
    } catch (error) {
      if (toApiError(error).code === WRONG_ACCOUNT_TYPE) {
        throw refusal(area, 'An account of another kind');
      }
      throw error;
    }
    if (login.role !== role) {
      throw refusal(area, `A ${login.role} account`);
    }
    return startAreaSession(area, login, { dispatch, queryClient });
  };
}

function refusal(area: AppArea, who: string): ApiError {
  return new ApiError({
    kind: 'forbidden',
    userMessage: WRONG_AREA_MESSAGES[area],
    technicalMessage: `${who} signed in to the ${area} area`,
  });
}

/** Signing out of one area: here at once, and on the server (its refresh cookie is removed). */
function useAreaSignOut(area: AppArea) {
  const dispatch = useAppDispatch();
  const queryClient = useQueryClient();
  return () => endAreaSession(area, { dispatch, queryClient }, { endOnServer: true });
}

/**
 * Creates an account of the area's kind and signs in with it straight away. Resolves to null when the account was
 * created but the sign-in after it failed (for example too many attempts): the person then signs in themselves.
 */
function useAreaRegister(area: AppArea) {
  const signIn = useAreaSignIn(area);
  return useMutation({
    mutationFn: async (request: RegisterRequest): Promise<SignedInUser | null> => {
      const role = REGISTER_ROLES[area];
      await userService.register(role ? { ...request, role } : request);
      try {
        return await signIn(request.email, request.password);
      } catch {
        return null;
      }
    },
  });
}

function useAreaSignInMutation(area: AppArea) {
  const signIn = useAreaSignIn(area);
  return useMutation({
    mutationFn: ({ email, password }: { email: string; password: string }) =>
      signIn(email, password),
  });
}

/** Signing in to the customer area (only CUSTOMER accounts). */
export function useSignIn() {
  return useAreaSignInMutation('customer');
}

/** A new customer account, signed in straight away (null: created, but sign in yourself). */
export function useRegister() {
  return useAreaRegister('customer');
}

export function useSignOut() {
  return useAreaSignOut('customer');
}

/** Signing in to the restaurant owner area (only RESTAURANT_ADMIN accounts). */
export function useOwnerSignIn() {
  return useAreaSignInMutation('owner');
}

/**
 * A new restaurant owner account, signed in straight away (the owner then registers their restaurant, which the
 * platform team reviews). null: created, but sign in yourself.
 */
export function useOwnerRegister() {
  return useAreaRegister('owner');
}

export function useOwnerSignOut() {
  return useAreaSignOut('owner');
}

/** Signing in to the delivery partner area (only DELIVERY_PARTNER accounts). */
export function usePartnerSignIn() {
  return useAreaSignInMutation('partner');
}

/** A new delivery partner account, signed in straight away (null: created, but sign in yourself). */
export function usePartnerRegister() {
  return useAreaRegister('partner');
}

export function usePartnerSignOut() {
  return useAreaSignOut('partner');
}

/** Signing in to the admin area (only PLATFORM_ADMIN accounts). */
export function useAdminSignIn() {
  return useAreaSignInMutation('admin');
}

/** Signing the admin out: the session ends and nothing the admin looked at stays in the browser's cache. */
export function useAdminSignOut() {
  return useAreaSignOut('admin');
}
