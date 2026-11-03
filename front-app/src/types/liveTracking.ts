import type { VehicleType } from '@/types/delivery';

/** A point on the map (WGS84 degrees, as Leaflet, OpenStreetMap and phone GPS use them). */
export interface GeoPoint {
  lat: number;
  lng: number;
}

/**
 * Where the delivery is, as the customer sees it on the map:
 * TO_RESTAURANT (the partner rides to the restaurant) -> AT_RESTAURANT (waits for the food) -> TO_CUSTOMER
 * (the food is on its way) -> DELIVERED.
 */
export const LIVE_TRACKING_PHASES = [
  'TO_RESTAURANT',
  'AT_RESTAURANT',
  'TO_CUSTOMER',
  'DELIVERED',
] as const;
export type LiveTrackingPhase = (typeof LIVE_TRACKING_PHASES)[number];

/**
 * Where the positions come from: GPS, the partner's phone (through delivery-service); SIMULATED, a sample ride for
 * demos (services/mock/liveTrackingMock.ts), whose positions and arrival time are never shown as real.
 */
export type LiveTrackingSource = 'GPS' | 'SIMULATED';

export interface LiveTrackingPlace {
  name: string;
  address: string;
  /** Null when the address has no map coordinates: then there is no pin (never a guessed one) */
  location: GeoPoint | null;
}

/** The partner's latest GPS position, as their phone sent it. */
export interface LiveRider {
  location: GeoPoint;
  /** Direction of travel, degrees clockwise from north; null when the phone does not know it (standing still) */
  headingDeg: number | null;
  /** How exact the phone says the position is (metres), when it says so */
  accuracyMeters?: number | null;
  /** When the position was received (ISO 8601) */
  updatedAt: string;
}

/**
 * GET /api/v1/delivery/orders/{orderId}/live: the partner's real GPS position (shared from their phone while the
 * delivery is on its way) and the two addresses. delivery-service sends only what it really knows: no route, no
 * distance and no arrival time. The demo simulation (services/mock/liveTrackingMock.ts,
 * VITE_FEATURE_FAKE_LIVE_TRACKING=true) also fills the optional route and estimates, labelled as simulated.
 */
export interface LiveTracking {
  orderId: number;
  /** GPS: the partner's phone; SIMULATED: the demo ride */
  source?: LiveTrackingSource;
  phase: LiveTrackingPhase;
  restaurant: LiveTrackingPlace;
  dropoff: LiveTrackingPlace;
  partner: {
    name: string;
    /** Shared only while the partner is on the way */
    phone: string | null;
    vehicleType: VehicleType | null;
  };
  /** Null until the partner's phone has sent a position, and once the food is delivered */
  rider: LiveRider | null;
  /** When the food was delivered (ISO 8601) */
  deliveredAt?: string | null;
  /** Simulation only: the road from the partner to the restaurant */
  pathToRestaurant?: GeoPoint[];
  /** Simulation only: the road to the customer */
  pathToCustomer?: GeoPoint[];
  /** Simulation only: road distance still to cover (metres) */
  remainingMeters?: number | null;
  /** Simulation only: minutes until the food arrives */
  etaMinutes?: number | null;
  /** Simulation only: when the food is expected (ISO 8601); the delivery time once delivered */
  estimatedArrival?: string | null;
}
