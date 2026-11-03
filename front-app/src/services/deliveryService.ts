import { apiClient } from '@/api/apiClient';
import type { AuditLogEntry, PageResponse } from '@/types/api';
import type {
  Delivery,
  DeliveryFilters,
  DeliveryReport,
  DeliverySummary,
  Partner,
  PartnerAvailability,
  PartnerFilters,
} from '@/types/delivery';
import type { OrderTracking } from '@/types/shopping';
import { API_ENDPOINTS } from '@/utils/apiEndpoints';

/** delivery-service admin APIs: deliveries and delivery partners. */
export const deliveryService = {
  /**
   * The customer's view of their order's delivery: status, partner, steps. 404 until the restaurant has the food
   * ready (the delivery starts then).
   */
  getOrderTracking: (orderId: number) =>
    apiClient.get<OrderTracking>(API_ENDPOINTS.delivery.tracking(orderId)),

  searchDeliveries: (filters: DeliveryFilters, page: number, size: number) =>
    apiClient.get<PageResponse<DeliverySummary>>(API_ENDPOINTS.admin.deliveries.list, {
      params: { ...filters, page, size, sort: 'createdAt,desc' },
    }),

  /**
   * Late deliveries right now, longest waiting first: no partner 10 minutes after the food was ready, or not
   * delivered 45 minutes after it.
   */
  getDelayedDeliveries: (page: number, size: number) =>
    apiClient.get<PageResponse<DeliverySummary>>(API_ENDPOINTS.admin.deliveries.delayed, {
      params: { page, size },
    }),

  /** Deliveries of a period (no dates: today), and how many are late now. */
  getSummary: (fromDate?: string, toDate?: string) =>
    apiClient.get<DeliveryReport>(API_ENDPOINTS.admin.deliveries.summary, {
      params: { fromDate, toDate },
    }),

  getDelivery: (deliveryId: number) =>
    apiClient.get<Delivery>(API_ENDPOINTS.admin.deliveries.details(deliveryId)),

  /**
   * A delivery not picked up yet goes to another AVAILABLE partner (who still has to accept), or, without
   * partnerUserId, back to the queue for the assignment job. Audited.
   */
  reassignDelivery: (deliveryId: number, partnerUserId: number | null, reason: string) =>
    apiClient.put<Delivery>(API_ENDPOINTS.admin.deliveries.reassign(deliveryId), {
      partnerUserId,
      reason,
    }),

  getDeliveryAuditLogs: (deliveryId: number) =>
    apiClient.get<PageResponse<AuditLogEntry>>(
      API_ENDPOINTS.admin.deliveries.auditLogs(deliveryId),
      { params: { size: 50 } },
    ),

  searchPartners: (filters: PartnerFilters, page: number, size: number) =>
    apiClient.get<PageResponse<Partner>>(API_ENDPOINTS.admin.partners.list, {
      params: { ...filters, page, size },
    }),

  /** How many partners are available, busy, offline and suspended right now. */
  getPartnerAvailability: () =>
    apiClient.get<PartnerAvailability>(API_ENDPOINTS.admin.partners.availability),

  getPartner: (userId: number) =>
    apiClient.get<Partner>(API_ENDPOINTS.admin.partners.details(userId)),

  /** The deliveries the partner has now (newest first; ones taken away from them are not listed). */
  getPartnerDeliveries: (userId: number, page: number, size: number) =>
    apiClient.get<PageResponse<DeliverySummary>>(API_ENDPOINTS.admin.partners.deliveries(userId), {
      params: { page, size },
    }),

  /** Suspend (not while on a delivery: reassign it first) or reactivate (they start OFFLINE). Audited. */
  changePartnerStatus: (userId: number, activate: boolean, reason: string) =>
    apiClient.put<Partner>(
      activate
        ? API_ENDPOINTS.admin.partners.activate(userId)
        : API_ENDPOINTS.admin.partners.deactivate(userId),
      { reason },
    ),

  getPartnerAuditLogs: (userId: number) =>
    apiClient.get<PageResponse<AuditLogEntry>>(API_ENDPOINTS.admin.partners.auditLogs(userId), {
      params: { size: 50 },
    }),
};
