import { apiClient } from '@/api/apiClient';
import type {
  CustomerReport,
  DeliveryPerformanceReport,
  OrderReport,
  ReportParams,
  RestaurantReport,
  RevenueGroupBy,
  RevenueReport,
} from '@/types/report';
import { API_ENDPOINTS } from '@/utils/apiEndpoints';

/**
 * admin-service reports. A report never fails because one service is down: its figures are null and the service
 * is named in `unavailable`.
 */
export const reportService = {
  getOrderReport: (params: ReportParams) =>
    apiClient.get<OrderReport>(API_ENDPOINTS.admin.reports.orders, { params: { ...params } }),

  getRevenueReport: (params: ReportParams, groupBy: RevenueGroupBy) =>
    apiClient.get<RevenueReport>(API_ENDPOINTS.admin.reports.revenue, {
      params: { ...params, groupBy },
    }),

  getRestaurantReport: (params: ReportParams) =>
    apiClient.get<RestaurantReport>(API_ENDPOINTS.admin.reports.restaurants, {
      params: { ...params },
    }),

  getCustomerReport: (params: ReportParams) =>
    apiClient.get<CustomerReport>(API_ENDPOINTS.admin.reports.customers, { params: { ...params } }),

  getDeliveryReport: (params: ReportParams) =>
    apiClient.get<DeliveryPerformanceReport>(API_ENDPOINTS.admin.reports.deliveries, {
      params: { ...params },
    }),
};
