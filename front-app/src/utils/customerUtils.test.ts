import { describe, expect, it } from 'vitest';

import {
  formatRating,
  LIKED_DISH_COLORS,
  likedDishColor,
  ratingShare,
} from '@/utils/customerUtils';

describe('customerUtils', () => {
  it('computes a share in whole percent, 0 without ratings', () => {
    expect(ratingShare(6, 10)).toBe(60);
    expect(ratingShare(1, 3)).toBe(33);
    expect(ratingShare(0, 0)).toBe(0);
  });

  it('cycles through the four colours', () => {
    expect(likedDishColor(0)).toBe(LIKED_DISH_COLORS[0]);
    expect(likedDishColor(4)).toBe(LIKED_DISH_COLORS[0]);
    expect(likedDishColor(6)).toBe(LIKED_DISH_COLORS[2]);
  });

  it('shows one decimal', () => {
    expect(formatRating(4)).toBe('4.0');
    expect(formatRating(4.25)).toBe('4.3');
  });
});
