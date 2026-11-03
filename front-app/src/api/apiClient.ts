import { axiosInstance } from '@/api/axiosInstance';
import type { QueryParams } from '@/types/api';

export interface RequestOptions {
  params?: QueryParams;
  headers?: Record<string, string>;
  /** Replaces the instance's timeout for this call only (a slow call such as the assistant's answer) */
  timeout?: number;
  /** Lets the caller abort the call (a cancel button) */
  signal?: AbortSignal;
}

/**
 * What services use to call the APIs: one method per HTTP verb, returning the answer's body with its type.
 * Failures are thrown as ApiError (see apiErrorHandler).
 *
 *   const order = await apiClient.get<Order>(API_ENDPOINTS.orders.detail(id));
 */
export const apiClient = {
  async get<T>(url: string, options: RequestOptions = {}): Promise<T> {
    const response = await axiosInstance.get<T>(url, options);
    return response.data;
  },

  async post<T>(url: string, body?: unknown, options: RequestOptions = {}): Promise<T> {
    const response = await axiosInstance.post<T>(url, body, options);
    return response.data;
  },

  async put<T>(url: string, body?: unknown, options: RequestOptions = {}): Promise<T> {
    const response = await axiosInstance.put<T>(url, body, options);
    return response.data;
  },

  async patch<T>(url: string, body?: unknown, options: RequestOptions = {}): Promise<T> {
    const response = await axiosInstance.patch<T>(url, body, options);
    return response.data;
  },

  /** 204 No Content answers resolve to undefined. */
  async delete<T = void>(url: string, options: RequestOptions = {}): Promise<T> {
    const response = await axiosInstance.delete<T>(url, options);
    return response.data;
  },
};
