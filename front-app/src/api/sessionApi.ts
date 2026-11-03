import axios from 'axios';

import { ApiError, toApiError } from '@/api/apiErrorHandler';
import type { LoginResponse } from '@/types/storefront';
import type { UserRole } from '@/types/user';
import { API_ENDPOINTS } from '@/utils/apiEndpoints';
import { env } from '@/utils/env';

/**
 * The calls that start, renew and end a sign-in session (user-service /api/v1/auth): the only ones that let the
 * browser send and store cookies, because the refresh token is an HttpOnly cookie of each area that JavaScript cannot
 * read. They are kept apart from axiosInstance: no Authorization header, no renewal on 401 (that would loop), and
 * nothing is logged (the answers carry the access token, the sign-in carries the password).
 * Exported for its tests only: everything else uses sessionApi.
 */
export const sessionAxios = axios.create({
  baseURL: env.apiBaseUrl,
  timeout: env.apiTimeoutMs,
  withCredentials: true,
  headers: { Accept: 'application/json' },
});

sessionAxios.interceptors.response.use(undefined, (error: unknown) => {
  const apiError = toApiError(error);
  // The same error without the original request (its body holds the password): safe to log anywhere
  return Promise.reject(
    new ApiError({
      kind: apiError.kind,
      status: apiError.status,
      userMessage: apiError.userMessage,
      fieldErrors: apiError.fieldErrors,
      requestId: apiError.requestId,
      code: apiError.code,
      technicalMessage: apiError.message,
    }),
  );
});

export const sessionApi = {
  /**
   * Checks the email and password and starts a session: an access token in the answer, the area's refresh cookie in
   * the browser. With `role`, an account of another kind gets 403 with code WRONG_ACCOUNT_TYPE (and no session);
   * 401 for a wrong email or password, 403 for a deactivated account.
   */
  login: async (email: string, password: string, role?: UserRole): Promise<LoginResponse> =>
    (await sessionAxios.post<LoginResponse>(API_ENDPOINTS.auth.login, { email, password, role }))
      .data,

  /**
   * A new access token with the area's refresh cookie (the cookie is renewed too). 401: the session ended (sign in
   * again); 409: another tab renewed it a moment ago (try once more).
   */
  refresh: async (role: UserRole): Promise<LoginResponse> =>
    (await sessionAxios.post<LoginResponse>(API_ENDPOINTS.auth.refresh, { role })).data,

  /** Ends the area's session on the server and removes its cookie (204, also when it had already ended). */
  logout: async (role: UserRole): Promise<void> => {
    await sessionAxios.post(API_ENDPOINTS.auth.logout, { role });
  },
};
