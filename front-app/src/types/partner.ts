import type { DeliveryStatus, VehicleType } from '@/types/delivery';

/**
 * The delivery partner's own APIs. The partner (PartnerResponse), their deliveries (DeliveryResponse) and the
 * history rows (DeliverySummaryResponse) have the shapes in types/delivery.ts.
 */

/** PUT /api/v1/delivery/partners/me: what the partner fills in before going online. */
export interface PartnerProfileUpdate {
  vehicleType: VehicleType;
  vehicleNumber: string;
  /** Deliveries are offered from restaurants in this city */
  city: string;
}

/** What the partner can switch to themselves (BUSY and SUSPENDED are decided by the platform). */
export type PartnerAvailabilityChoice = 'AVAILABLE' | 'OFFLINE';

/** The steps of a delivery the partner takes, in order. */
export type PartnerDeliveryAction = 'accept' | 'decline' | 'pickup' | 'deliver';

/** The history list's filter: every delivery, or only those in one status. */
export type PartnerHistoryStatus = DeliveryStatus | 'ALL';

/**
 * The partner's GPS position from their phone, sent while they are on the way to the restaurant or the customer
 * (delivery-service shows it only to that order's customer). Degrees; accuracy in metres; heading 0-360 (north = 0).
 */
export interface RiderLocationUpdate {
  latitude: number;
  longitude: number;
  accuracyMeters: number | null;
  headingDeg: number | null;
}
