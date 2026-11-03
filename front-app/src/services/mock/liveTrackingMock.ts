import { ApiError } from '@/api/apiErrorHandler';
import { deliveryService } from '@/services/deliveryService';
import type { GeoPoint, LiveRider, LiveTracking, LiveTrackingPhase } from '@/types/liveTracking';
import type { OrderTracking } from '@/types/shopping';
import { pathLengthMeters, positionAlongPath } from '@/utils/geoUtils';

/**
 * FAKE live tracking, until delivery-service sends the partner's real position.
 *
 * Only the positions are made up. Everything else is the order's real delivery, read from delivery-service
 * (GET /api/v1/delivery/orders/{orderId}, the same API the order page uses): the phase comes from the saved delivery
 * status (accepted: riding to the restaurant or waiting there; picked up: on the way; delivered), the timing from
 * the saved moments (accepted at, picked up at, delivered at), the partner, the restaurant and the address from the
 * delivery. So the map always tells the same story as the order, a reload gives the same answer (nothing is kept in
 * the browser), and it never shows a step the delivery has not reached: before the real pickup the partner waits at
 * the restaurant; before the real delivery the partner stays just short of the door.
 *
 * The partner moves along two sample routes in Bengaluru, sped up so a ride plays out in a couple of minutes; the
 * ETA is what the same distance would take on real roads. Every answer says source: 'SIMULATED', so the page labels
 * the positions and the ETA as a simulation, never as the partner's real position. The routes follow real roads:
 * they were computed once, offline, with the public OSRM demo server on OpenStreetMap data (© OpenStreetMap
 * contributors, ODbL) and simplified to a few dozen points. They are fixed sample data, not the order's addresses.
 *
 * Remove this file (and the switch in services/liveTrackingService.ts) once the backend answers
 * GET /api/v1/delivery/orders/{orderId}/live with the LiveTracking shape (types/liveTracking.ts).
 */

/** From where the partner accepted the delivery to the restaurant (1.9 km by road) */
const ROUTE_TO_RESTAURANT: GeoPoint[] = [
  { lat: 12.96973, lng: 77.6499 },
  { lat: 12.96971, lng: 77.6492 },
  { lat: 12.96919, lng: 77.6491 },
  { lat: 12.96943, lng: 77.64764 },
  { lat: 12.97269, lng: 77.64694 },
  { lat: 12.97854, lng: 77.64659 },
  { lat: 12.97834, lng: 77.64082 },
  { lat: 12.9784, lng: 77.64081 },
];

/** From the restaurant to the customer (7.3 km by road): 100 Feet Road south, then into Koramangala */
const ROUTE_TO_CUSTOMER: GeoPoint[] = [
  { lat: 12.9784, lng: 77.64081 },
  { lat: 12.97842, lng: 77.64091 },
  { lat: 12.97679, lng: 77.64101 },
  { lat: 12.96085, lng: 77.64179 },
  { lat: 12.95825, lng: 77.64143 },
  { lat: 12.95587, lng: 77.64146 },
  { lat: 12.95419, lng: 77.64133 },
  { lat: 12.95119, lng: 77.63963 },
  { lat: 12.95054, lng: 77.63932 },
  { lat: 12.95009, lng: 77.63923 },
  { lat: 12.94949, lng: 77.6395 },
  { lat: 12.94844, lng: 77.64064 },
  { lat: 12.94814, lng: 77.64082 },
  { lat: 12.94735, lng: 77.64089 },
  { lat: 12.94652, lng: 77.64046 },
  { lat: 12.94031, lng: 77.63663 },
  { lat: 12.93957, lng: 77.6361 },
  { lat: 12.93908, lng: 77.63556 },
  { lat: 12.93865, lng: 77.63477 },
  { lat: 12.93846, lng: 77.63405 },
  { lat: 12.93845, lng: 77.62949 },
  { lat: 12.93818, lng: 77.62854 },
  { lat: 12.93749, lng: 77.62729 },
  { lat: 12.93493, lng: 77.62417 },
  { lat: 12.93453, lng: 77.62389 },
  { lat: 12.93342, lng: 77.62339 },
  { lat: 12.93353, lng: 77.62287 },
  { lat: 12.9337, lng: 77.61942 },
  { lat: 12.93354, lng: 77.61816 },
  { lat: 12.93413, lng: 77.61662 },
  { lat: 12.93444, lng: 77.61627 },
  { lat: 12.93584, lng: 77.61532 },
  { lat: 12.93541, lng: 77.61449 },
];

/** Where the sample routes begin and end, so each route touches its marker */
const RESTAURANT_LOCATION = ROUTE_TO_CUSTOMER[0];
const DROPOFF_LOCATION = ROUTE_TO_CUSTOMER[ROUTE_TO_CUSTOMER.length - 1];

/** How long each part of the simulated ride takes, counted from the delivery's real moments (milliseconds) */
export const SIMULATION_TIMING = {
  /** From "accepted" to arriving at the restaurant; then the partner waits there until the real pickup */
  toRestaurantMs: 40_000,
  /** From the real pickup to the door; then the partner waits just short of it until the real delivery */
  toCustomerMs: 100_000,
} as const;

/** How far along the road to the customer the partner stops while the delivery is not confirmed yet */
const BEFORE_THE_DOOR = 0.97;
/** Average city speed for the ETA (km/h), and the time the food takes to be handed over (minutes) */
const AVERAGE_SPEED_KMH = 22;
const HANDOVER_MINUTES = 3;

/** A delivery with a partner on the way: before that, there is nothing to draw (as the real endpoint: 404). */
const ON_THE_WAY: ReadonlySet<OrderTracking['status']> = new Set([
  'ACCEPTED',
  'PICKED_UP',
  'DELIVERED',
]);

function minutesFor(meters: number): number {
  return (meters / 1000 / AVERAGE_SPEED_KMH) * 60;
}

function elapsedSince(moment: string | null, now: number): number {
  return moment === null ? 0 : Math.max(0, now - Date.parse(moment));
}

function dropoffText(address: OrderTracking['dropoffAddress']): string {
  if (!address) {
    return 'Your delivery address';
  }
  return [address.line1, address.line2, address.city].filter(Boolean).join(', ');
}

/** The simulation always fills in the route and the estimates (the page labels them as simulated). */
export type SimulatedLiveTracking = LiveTracking & {
  rider: LiveRider;
  pathToRestaurant: GeoPoint[];
  pathToCustomer: GeoPoint[];
  remainingMeters: number;
  etaMinutes: number;
  estimatedArrival: string;
};

/**
 * The simulated ride of a real delivery at a moment (pure: the same delivery and moment always give the same
 * answer). Only call it for a delivery with a partner on the way (accepted, picked up or delivered).
 */
export function simulateLiveTracking(delivery: OrderTracking, now: number): SimulatedLiveTracking {
  const { toRestaurantMs, toCustomerMs } = SIMULATION_TIMING;
  const toCustomerMeters = pathLengthMeters(ROUTE_TO_CUSTOMER);

  let phase: LiveTrackingPhase;
  let pathToRestaurant: GeoPoint[] = [];
  let pathToCustomer: GeoPoint[];
  let location: GeoPoint;
  let headingDeg: number;
  let remainingMeters: number;
  let etaMinutes: number;

  if (delivery.status === 'DELIVERED') {
    phase = 'DELIVERED';
    location = DROPOFF_LOCATION;
    headingDeg = 0;
    pathToCustomer = [];
    remainingMeters = 0;
    etaMinutes = 0;
  } else if (delivery.status === 'PICKED_UP') {
    phase = 'TO_CUSTOMER';
    const fraction = Math.min(
      elapsedSince(delivery.pickedUpAt, now) / toCustomerMs,
      BEFORE_THE_DOOR,
    );
    const ride = positionAlongPath(ROUTE_TO_CUSTOMER, fraction);
    location = ride.point;
    headingDeg = ride.headingDeg;
    pathToCustomer = ride.remaining;
    remainingMeters = pathLengthMeters(ride.remaining);
    etaMinutes = minutesFor(remainingMeters);
  } else {
    // Accepted: riding to the restaurant, then waiting there until the food is really picked up
    const elapsed = elapsedSince(delivery.acceptedAt, now);
    pathToCustomer = ROUTE_TO_CUSTOMER;
    if (elapsed < toRestaurantMs) {
      phase = 'TO_RESTAURANT';
      const ride = positionAlongPath(ROUTE_TO_RESTAURANT, elapsed / toRestaurantMs);
      location = ride.point;
      headingDeg = ride.headingDeg;
      pathToRestaurant = ride.remaining;
      remainingMeters = pathLengthMeters(ride.remaining) + toCustomerMeters;
    } else {
      phase = 'AT_RESTAURANT';
      location = RESTAURANT_LOCATION;
      headingDeg = positionAlongPath(ROUTE_TO_CUSTOMER, 0).headingDeg;
      remainingMeters = toCustomerMeters;
    }
    etaMinutes = minutesFor(remainingMeters) + HANDOVER_MINUTES;
  }

  const delivered = phase === 'DELIVERED';
  const roundedEta = delivered ? 0 : Math.max(1, Math.ceil(etaMinutes));
  // Once delivered: the real moment it was delivered
  const arrival =
    delivered && delivery.deliveredAt
      ? Date.parse(delivery.deliveredAt)
      : now + roundedEta * 60_000;
  return {
    orderId: delivery.orderId,
    source: 'SIMULATED',
    phase,
    restaurant: {
      name: delivery.restaurantName ?? 'The restaurant',
      address: '',
      location: RESTAURANT_LOCATION,
    },
    dropoff: {
      name: 'Home',
      address: dropoffText(delivery.dropoffAddress),
      location: DROPOFF_LOCATION,
    },
    partner: {
      name: delivery.partnerName ?? 'Your delivery partner',
      // delivery-service shares the phone only while the partner is on the way
      phone: delivery.partnerPhone ?? '',
      vehicleType: delivery.vehicleType ?? 'MOTORCYCLE',
    },
    rider: { location, headingDeg, updatedAt: new Date(now).toISOString() },
    pathToRestaurant,
    pathToCustomer,
    remainingMeters: Math.round(remainingMeters),
    etaMinutes: roundedEta,
    estimatedArrival: new Date(arrival).toISOString(),
  };
}

export const liveTrackingMock = {
  /**
   * Answers like the real endpoint will: 404 until a partner has accepted the delivery (and for a cancelled one),
   * then the ride as it stands now. Errors of delivery-service (404 before the food is ready, a failure) pass on.
   */
  getLiveTracking: async (orderId: number): Promise<LiveTracking> => {
    const delivery = await deliveryService.getOrderTracking(orderId);
    if (!ON_THE_WAY.has(delivery.status)) {
      throw new ApiError({
        kind: 'notFound',
        status: 404,
        userMessage: 'No delivery partner is on the way yet.',
        technicalMessage: `Simulated live tracking of order ${orderId}: delivery is ${delivery.status}`,
      });
    }
    return simulateLiveTracking(delivery, Date.now());
  },
};
