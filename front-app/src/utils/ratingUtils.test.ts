import { describe, expect, it } from 'vitest';

import { formatAverage, formatReviewCount, ratingLabel, ratingOf } from '@/utils/ratingUtils';

describe('ratingOf', () => {
  const lookup = { 3: { id: 3, average: 4.3, count: 12 } };

  it('is unknown (undefined) while the ratings are not loaded', () => {
    expect(ratingOf(undefined, 3)).toBeUndefined();
  });

  it('finds the rating of an id, and says "no ratings yet" (null) for one that is not in a loaded lookup', () => {
    expect(ratingOf(lookup, 3)).toEqual({ id: 3, average: 4.3, count: 12 });
    expect(ratingOf(lookup, 4)).toBeNull();
  });
});

describe('formatting', () => {
  it('always shows one decimal', () => {
    expect(formatAverage(4)).toBe('4.0');
    expect(formatAverage(4.25)).toBe('4.3');
    expect(formatAverage(3.666)).toBe('3.7');
  });

  it('counts reviews in the singular and the plural', () => {
    expect(formatReviewCount(1)).toBe('1 review');
    expect(formatReviewCount(12)).toBe('12 reviews');
  });

  it('describes a rating for screen readers', () => {
    expect(ratingLabel({ average: 4.3, count: 12 })).toBe('Rated 4.3 out of 5, 12 reviews');
    expect(ratingLabel(null)).toBe('No ratings yet');
    expect(ratingLabel({ average: null, count: 0 })).toBe('No ratings yet');
  });
});
