/** delivery-service (admin): deliveries and delivery partners. */

/**
 * PENDING (food ready, waiting for a partner) -> ASSIGNED (offered, not accepted yet) -> ACCEPTED (partner on the
 * way to the restaurant) -> PICKED_UP -> DELIVERED; any unfinished delivery becomes CANCELLED with its order.
 */
export const DELIVERY_STATUSES = [
  'PENDING',
  'ASSIGNED',
  'ACCEPTED',
  'PICKED_UP',
  'DELIVERED',
  'CANCELLED',
] as const;
export type DeliveryStatus = (typeof DELIVERY_STATUSES)[number];

/** Before pickup another partner can still take over (delivery-service checks it again). */
export const REASSIGNABLE_STATUSES: readonly DeliveryStatus[] = ['PENDING', 'ASSIGNED', 'ACCEPTED'];

/**
 * OFFLINE / AVAILABLE: switched by the partner. BUSY: set by the system from the moment a delivery is offered.
 * SUSPENDED: only an admin suspends or reactivates a partner.
 */
export const PARTNER_STATUSES = ['OFFLINE', 'AVAILABLE', 'BUSY', 'SUSPENDED'] as const;
export type PartnerStatus = (typeof PARTNER_STATUSES)[number];

export const VEHICLE_TYPES = ['BICYCLE', 'SCOOTER', 'MOTORCYCLE', 'CAR'] as const;
export type VehicleType = (typeof VEHICLE_TYPES)[number];

/** What happened to a delivery (one line of its timeline). */
export type DeliveryEventType =
  | 'CREATED'
  | 'ASSIGNED'
  | 'ACCEPTED'
  | 'DECLINED'
  | 'EXPIRED'
  | 'REASSIGNED'
  | 'PICKED_UP'
  | 'DELIVERED'
  | 'CANCELLED';

export type DeliveryActorType = 'SYSTEM' | 'DELIVERY_PARTNER' | 'ADMIN';

/** A delivery partner (PartnerResponse). */
export interface Partner {
  userId: number;
  fullName: string;
  phone: string | null;
  vehicleType: VehicleType | null;
  vehicleNumber: string | null;
  city: string | null;
  status: PartnerStatus;
  /** Why the partner was suspended, or why an unanswered offer sent them offline */
  statusReason: string | null;
  /** Set while BUSY */
  currentDeliveryId: number | null;
  availableSince: string | null;
  completedDeliveries: number;
  /** Vehicle and city filled in, so the partner can go online */
  readyToWork: boolean;
  createdAt: string;
  updatedAt: string;
  version: number;
}

/** How many partners have each status right now. */
export interface PartnerAvailability {
  byStatus: Record<PartnerStatus, number>;
  total: number;
}

/** One line of a delivery list (DeliverySummaryResponse). */
export interface DeliverySummary {
  id: number;
  orderId: number;
  status: DeliveryStatus;
  restaurantName: string | null;
  pickupCity: string | null;
  dropoffCity: string | null;
  partnerUserId: number | null;
  partnerName: string | null;
  /** Cash on delivery: what the partner collects (0 for paid orders) */
  cashToCollect: number | null;
  readyAt: string;
  assignedAt: string | null;
  deliveredAt: string | null;
  /** How long the food has been waiting or travelling; null once finished */
  minutesSinceReady: number | null;
}

export interface DeliveryAddress {
  line1: string | null;
  line2: string | null;
  city: string | null;
  state: string | null;
  postalCode: string | null;
  country: string | null;
}

export interface DeliveryTimelineEntry {
  event: DeliveryEventType;
  /** The delivery's status after the event */
  status: DeliveryStatus;
  /** The partner the event is about */
  partnerUserId: number | null;
  actorType: DeliveryActorType;
  actorUserId: number | null;
  note: string | null;
  at: string;
}

/** A delivery with its whole history (DeliveryResponse). Details can arrive late (null for a short time). */
export interface Delivery {
  id: number;
  orderId: number;
  status: DeliveryStatus;
  restaurantId: number;
  restaurantName: string | null;
  pickupAddress: DeliveryAddress | null;
  customerId: number | null;
  customerName: string | null;
  customerPhone: string | null;
  dropoffAddress: DeliveryAddress | null;
  /** 'CASH_ON_DELIVERY', 'UPI', ... */
  paymentMethod: string | null;
  cashToCollect: number | null;
  cashCollected: number | null;
  currency: string | null;
  partnerUserId: number | null;
  partnerName: string | null;
  vehicleType: VehicleType | null;
  cancellationReason: string | null;
  readyAt: string;
  assignedAt: string | null;
  acceptedAt: string | null;
  pickedUpAt: string | null;
  deliveredAt: string | null;
  cancelledAt: string | null;
  createdAt: string;
  updatedAt: string;
  version: number;
  /** Oldest first */
  timeline: DeliveryTimelineEntry[];
}

/**
 * Deliveries of a period (DeliveryReportResponse): per status by the day they were created; the average time
 * from "food ready" to "delivered"; and how many are late right now (whatever the period).
 */
export interface DeliveryReport {
  fromDate: string;
  toDate: string;
  byStatus: Record<DeliveryStatus, number>;
  total: number;
  delivered: number;
  cancelled: number;
  averageDeliveryMinutes: number | null;
  delayedNow: number;
}

/** The delivery list's filters (dates: created, 'YYYY-MM-DD', inclusive). */
export interface DeliveryFilters {
  status?: DeliveryStatus;
  orderId?: number;
  partnerUserId?: number;
  customerId?: number;
  restaurantId?: number;
  /** Pickup city (exact) */
  city?: string;
  fromDate?: string;
  toDate?: string;
}

/** The partner list's filters. */
export interface PartnerFilters {
  status?: PartnerStatus;
  /** Exact city */
  city?: string;
  /** Part of the name or phone number */
  q?: string;
}
