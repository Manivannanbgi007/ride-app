import type { DeliveryStatus } from '@/types/delivery';
import type { GeoPoint } from '@/types/liveTracking';
import type { RiderLocationUpdate } from '@/types/partner';
import { distanceMeters } from '@/utils/geoUtils';

/** Never two positions closer together than this (delivery-service accepts one every 2 seconds) */
export const MIN_SEND_GAP_MS = 3_000;
/** A move at least this long (metres) is sent at once (after the minimum gap) */
export const SEND_AFTER_METERS = 25;
/** Without moving, the position is still sent this often: the customer sees it is live */
export const MAX_QUIET_MS = 10_000;
/** What delivery-service accepts as accuracy (metres) */
const MAX_ACCURACY_METERS = 100_000;

/** The deliveries whose partner is on the way: to the restaurant (ACCEPTED), then to the customer (PICKED_UP) */
export function isOnTheWay(status: DeliveryStatus | undefined): boolean {
  return status === 'ACCEPTED' || status === 'PICKED_UP';
}

/** The last position that reached delivery-service, and when (milliseconds) */
export interface SentPosition {
  point: GeoPoint;
  at: number;
}

/**
 * Whether a new phone position is worth sending: the first one always; then never sooner than MIN_SEND_GAP_MS, and
 * only after a move of SEND_AFTER_METERS or MAX_QUIET_MS without sending. Keeps the phone's battery and data, and
 * the server, from a position every second.
 */
export function shouldSendPosition(
  last: SentPosition | null,
  next: GeoPoint,
  now: number,
): boolean {
  if (!last) {
    return true;
  }
  const gap = now - last.at;
  if (gap < MIN_SEND_GAP_MS) {
    return false;
  }
  return gap >= MAX_QUIET_MS || distanceMeters(last.point, next) >= SEND_AFTER_METERS;
}

/**
 * The browser's position as delivery-service takes it. The heading only when the phone knows it (it does not while
 * standing still: null or NaN), the accuracy rounded to whole metres.
 */
export function toLocationUpdate(coords: GeolocationCoordinates): RiderLocationUpdate {
  const { heading, accuracy } = coords;
  return {
    latitude: coords.latitude,
    longitude: coords.longitude,
    accuracyMeters: Number.isFinite(accuracy)
      ? Math.min(Math.round(accuracy), MAX_ACCURACY_METERS)
      : null,
    headingDeg: heading !== null && Number.isFinite(heading) ? Math.round(heading) % 360 : null,
  };
}
