import axios from 'axios';

import { toApiError } from '@/api/apiErrorHandler';
import { accessTokenFor, renewAccessToken } from '@/api/sessionRefresh';
import { env } from '@/utils/env';
import { logger } from '@/utils/logger';
import { areaOf } from '@/utils/sessionUtils';

declare module 'axios' {
  interface AxiosRequestConfig {
    /** Set on the one repeat of a request that was refused (401) and sent again with a renewed token */
    renewedAfter401?: boolean;
  }
}

const AUTHORIZATION = 'Authorization';

/**
 * The application's only Axios instance for the APIs (sign-in sessions have their own: api/sessionApi.ts). Services
 * do not use it directly but through apiClient. Every request goes to the API Gateway (VITE_API_BASE_URL); every
 * failure comes back as an ApiError.
 */
export const axiosInstance = axios.create({
  baseURL: env.apiBaseUrl,
  timeout: env.apiTimeoutMs,
  headers: { Accept: 'application/json' },
});

axiosInstance.interceptors.request.use(async (config) => {
  // The area of the open page decides who acts (the admin in /admin, the owner in /owner...): a page never sends
  // another area's person, even right after moving between areas
  const area = areaOf(window.location.pathname);
  // Renewed first when it is about to expire; null for a visitor, or when the session has just ended. The token is
  // the only thing that says who acts: the gateway and every service check it
  const token = await accessTokenFor(area);
  if (token) {
    config.headers.set(AUTHORIZATION, `Bearer ${token}`);
  }
  logger.debug(`API -> ${config.method?.toUpperCase()} ${config.url}`, config.params ?? '');
  return config;
});

axiosInstance.interceptors.response.use(
  (response) => {
    logger.debug(`API <- ${response.status} ${response.config.url}`, response.data);
    return response;
  },
  async (error: unknown) => {
    // A token refused (expired early, signed with a key user-service replaced): renewed once and sent again
    const config = axios.isAxiosError(error) ? error.config : undefined;
    if (
      config &&
      axios.isAxiosError(error) &&
      error.response?.status === 401 &&
      config.headers.has(AUTHORIZATION) &&
      !config.renewedAfter401
    ) {
      const token = await renewAccessToken(areaOf(window.location.pathname));
      if (token) {
        return axiosInstance.request({ ...config, renewedAfter401: true });
      }
    }
    const apiError = toApiError(withoutSecrets(error));
    if (apiError.kind !== 'cancelled') {
      logger.warn(apiError.message, { requestId: apiError.requestId });
    }
    return Promise.reject(apiError);
  },
);

/**
 * The failed request without its token and body (a password change carries passwords): the error is logged and
 * kept as the ApiError's cause, so nothing secret may stay in it.
 */
function withoutSecrets(error: unknown): unknown {
  if (axios.isAxiosError(error) && error.config) {
    error.config.headers.delete(AUTHORIZATION);
    error.config.data = undefined;
  }
  return error;
}
