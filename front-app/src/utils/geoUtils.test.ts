import { describe, expect, it } from 'vitest';

import {
  bearingDeg,
  distanceMeters,
  formatDistance,
  pathLengthMeters,
  positionAlongPath,
} from '@/utils/geoUtils';

const A = { lat: 12.9784, lng: 77.6408 };
const B = { lat: 12.9784, lng: 77.6508 };
const C = { lat: 12.9884, lng: 77.6508 };

describe('geoUtils', () => {
  it('measures distances along the earth', () => {
    // 0.01 degree of latitude is about 1.11 km anywhere
    expect(distanceMeters(B, C)).toBeCloseTo(1112, -1);
    expect(pathLengthMeters([A, B, C])).toBeCloseTo(distanceMeters(A, B) + distanceMeters(B, C));
    expect(pathLengthMeters([A])).toBe(0);
  });

  it('gives the direction of travel clockwise from north', () => {
    expect(bearingDeg(A, B)).toBeCloseTo(90, 0);
    expect(bearingDeg(B, C)).toBeCloseTo(0, 0);
    expect(bearingDeg(C, B)).toBeCloseTo(180, 0);
  });

  it('finds the point, heading and road ahead after part of a route', () => {
    const start = positionAlongPath([A, B, C], 0);
    expect(start.point).toEqual(A);
    expect(start.remaining).toHaveLength(3);

    // B to C is a little longer than A to B: just past halfway is on the second leg, heading north
    const later = positionAlongPath([A, B, C], 0.75);
    expect(later.headingDeg).toBeCloseTo(0, 0);
    expect(later.point.lng).toBeCloseTo(B.lng);
    expect(later.remaining.at(-1)).toEqual(C);
    expect(pathLengthMeters(later.remaining)).toBeCloseTo(pathLengthMeters([A, B, C]) * 0.25, 0);

    expect(positionAlongPath([A, B, C], 1).point).toEqual(C);
    expect(positionAlongPath([A, B, C], 7).point).toEqual(C);
  });

  it('writes distances in metres or kilometres', () => {
    expect(formatDistance(847)).toBe('850 m');
    expect(formatDistance(3420)).toBe('3.4 km');
  });
});
