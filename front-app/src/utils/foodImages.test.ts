import { describe, expect, it } from 'vitest';

import { categoryImageOf, cuisineImageOf } from '@/utils/foodImages';

describe('categoryImageOf', () => {
  it('finds the picture of a known category, whatever the case', () => {
    expect(categoryImageOf('Biryani')).toBe('/images/categories/biryani.jpg');
    expect(categoryImageOf(' south indian ')).toBe('/images/categories/south-indian.jpg');
  });

  it('has none for an unknown or missing category', () => {
    expect(categoryImageOf('Space food')).toBeNull();
    expect(categoryImageOf(null)).toBeNull();
    expect(categoryImageOf(undefined)).toBeNull();
  });
});

describe('cuisineImageOf', () => {
  it('uses the first cuisine that names a category', () => {
    expect(cuisineImageOf(['Mughlai', 'Biryani'])).toBe('/images/categories/biryani.jpg');
    expect(cuisineImageOf(['Unknown', 'Indo-Chinese'])).toBe('/images/categories/chinese.jpg');
    expect(cuisineImageOf(['Ice Cream'])).toBe('/images/categories/desserts.jpg');
  });

  it('tells South Indian from the rest of Indian food', () => {
    expect(cuisineImageOf(['South Indian'])).toBe('/images/categories/south-indian.jpg');
    expect(cuisineImageOf(['North Indian'])).toBe('/images/categories/north-indian.jpg');
  });

  it('has none for cuisines that name no category, or no cuisines', () => {
    expect(cuisineImageOf(['Martian'])).toBeNull();
    expect(cuisineImageOf(['Steakhouse'])).toBeNull();
    expect(cuisineImageOf([])).toBeNull();
    expect(cuisineImageOf(undefined)).toBeNull();
  });
});
