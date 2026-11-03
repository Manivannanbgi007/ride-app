import type { GeoPoint } from '@/types/liveTracking';

const EARTH_RADIUS_METERS = 6_371_000;
const toRadians = (degrees: number) => (degrees * Math.PI) / 180;
const toDegrees = (radians: number) => (radians * 180) / Math.PI;

/** Straight-line distance between two points on the earth (haversine), in metres. */
export function distanceMeters(from: GeoPoint, to: GeoPoint): number {
  const dLat = toRadians(to.lat - from.lat);
  const dLng = toRadians(to.lng - from.lng);
  const a =
    Math.sin(dLat / 2) ** 2 +
    Math.cos(toRadians(from.lat)) * Math.cos(toRadians(to.lat)) * Math.sin(dLng / 2) ** 2;
  return 2 * EARTH_RADIUS_METERS * Math.asin(Math.min(1, Math.sqrt(a)));
}

/** The direction from one point to another, degrees clockwise from north (0-360). */
export function bearingDeg(from: GeoPoint, to: GeoPoint): number {
  const lat1 = toRadians(from.lat);
  const lat2 = toRadians(to.lat);
  const dLng = toRadians(to.lng - from.lng);
  const y = Math.sin(dLng) * Math.cos(lat2);
  const x = Math.cos(lat1) * Math.sin(lat2) - Math.sin(lat1) * Math.cos(lat2) * Math.cos(dLng);
  return (toDegrees(Math.atan2(y, x)) + 360) % 360;
}

/** The length of a route along its points, in metres. */
export function pathLengthMeters(path: readonly GeoPoint[]): number {
  let total = 0;
  for (let index = 1; index < path.length; index += 1) {
    total += distanceMeters(path[index - 1], path[index]);
  }
  return total;
}

/** A point between two others (`fraction` 0 = `from`, 1 = `to`). Exact enough over a city's distances. */
export function lerpPoint(from: GeoPoint, to: GeoPoint, fraction: number): GeoPoint {
  return {
    lat: from.lat + (to.lat - from.lat) * fraction,
    lng: from.lng + (to.lng - from.lng) * fraction,
  };
}

export interface PositionOnPath {
  point: GeoPoint;
  headingDeg: number;
  /** The rest of the route, starting at `point` */
  remaining: GeoPoint[];
}

/**
 * Where someone is after covering `fraction` (0-1) of a route's length: the point, the direction they ride in,
 * and the road still ahead of them.
 */
export function positionAlongPath(path: readonly GeoPoint[], fraction: number): PositionOnPath {
  if (path.length === 0) {
    throw new Error('positionAlongPath needs a route with at least one point');
  }
  if (path.length === 1) {
    return { point: path[0], headingDeg: 0, remaining: [path[0]] };
  }
  const wanted = pathLengthMeters(path) * Math.min(1, Math.max(0, fraction));
  let covered = 0;
  for (let index = 1; index < path.length; index += 1) {
    const from = path[index - 1];
    const to = path[index];
    const segment = distanceMeters(from, to);
    if (covered + segment >= wanted || index === path.length - 1) {
      const within = segment === 0 ? 1 : Math.min(1, (wanted - covered) / segment);
      const point = lerpPoint(from, to, within);
      return {
        point,
        headingDeg: bearingDeg(from, to),
        remaining: [point, ...path.slice(index)],
      };
    }
    covered += segment;
  }
  // Not reached: the loop returns on its last segment
  const last = path[path.length - 1];
  return { point: last, headingDeg: 0, remaining: [last] };
}

/** "850 m" under a kilometre, "3.4 km" above. */
export function formatDistance(meters: number): string {
  if (meters < 1000) {
    return `${Math.max(0, Math.round(meters / 10) * 10)} m`;
  }
  return `${(meters / 1000).toFixed(1)} km`;
}
