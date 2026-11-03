import { apiClient } from '@/api/apiClient';
import type { PageResponse } from '@/types/api';
import type { Delivery, DeliveryStatus, DeliverySummary, Partner } from '@/types/delivery';
import type {
  PartnerAvailabilityChoice,
  PartnerProfileUpdate,
  RiderLocationUpdate,
} from '@/types/partner';
import { API_ENDPOINTS } from '@/utils/apiEndpoints';

const API = API_ENDPOINTS.partner;

/**
 * The signed-in delivery partner's APIs. The profile belongs to delivery-partner-service; going online and the
 * deliveries to delivery-service (it decides who gets which delivery). A partner only ever sees the deliveries
 * offered to them: any other id is "not found".
 */
export const partnerService = {
  /** The partner's profile and status (created on the first call if the account is a delivery partner). */
  getMe: () => apiClient.get<Partner>(API.me),

  /** Vehicle and city: needed before going online. */
  updateProfile: (update: PartnerProfileUpdate) => apiClient.put<Partner>(API.me, update),

  /**
   * Online (AVAILABLE: deliveries in the partner's city are offered to them) or OFFLINE. 409 while on a delivery or
   * when suspended, 422 when the vehicle or city is missing.
   */
  changeAvailability: (status: PartnerAvailabilityChoice) =>
    apiClient.put<Partner>(API.availability, { status }),

  /** The delivery offered to the partner or being worked on now, or null when there is none (204). */
  getCurrentDelivery: async (): Promise<Delivery | null> => {
    // A 204 answer has an empty body
    const delivery = await apiClient.get<Delivery | ''>(API.currentDelivery);
    return delivery || null;
  },

  /** The partner's deliveries, newest first. */
  getDeliveries: (status: DeliveryStatus | undefined, page: number, size: number) =>
    apiClient.get<PageResponse<DeliverySummary>>(API.deliveries, {
      params: { status, page, size, sort: 'createdAt,desc' },
    }),

  getDelivery: (deliveryId: number) => apiClient.get<Delivery>(API.delivery(deliveryId)),

  /** ASSIGNED -> ACCEPTED: the partner goes to the restaurant. */
  acceptDelivery: (deliveryId: number) =>
    apiClient.put<Delivery>(API.deliveryAction(deliveryId, 'accept')),

  /** Says no to an offer: it goes to another partner (204). */
  declineDelivery: (deliveryId: number, reason?: string) =>
    apiClient.put<void>(API.deliveryAction(deliveryId, 'decline'), reason ? { reason } : {}),

  /** ACCEPTED -> PICKED_UP: the food is with the partner (the order is "out for delivery"). */
  pickUpDelivery: (deliveryId: number) =>
    apiClient.put<Delivery>(API.deliveryAction(deliveryId, 'pickup')),

  /** PICKED_UP -> DELIVERED. Cash on delivery: the exact amount collected must be confirmed. */
  completeDelivery: (deliveryId: number, cashCollected: number | null) =>
    apiClient.put<Delivery>(
      API.deliveryAction(deliveryId, 'deliver'),
      cashCollected === null ? {} : { cashCollected },
    ),

  /**
   * The partner's phone position while they are on the way (204). 409 once the delivery is no longer on its way (it
   * was delivered or cancelled). One sent sooner than 2 s after the last is skipped.
   */
  shareLocation: (deliveryId: number, update: RiderLocationUpdate) =>
    apiClient.put<void>(API.deliveryLocation(deliveryId), update),
};
