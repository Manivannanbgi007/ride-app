import { describe, expect, it } from 'vitest';

import { ApiError } from '@/api/apiErrorHandler';
import {
  canOwnerToggleVisibility,
  categoryOptionsOf,
  dishCountText,
  filterMenuItems,
  filtersFromParams,
  hasMenuFilters,
  isStaleVersion,
  menuFormDefaults,
  menuSortOf,
  menuStateCounts,
  menuStateOf,
  ownerMenuFormSchema,
  paginate,
  priceFormSchema,
  sortMenuItems,
  staleMessageOf,
  toMenuItemRequest,
} from '@/components/business/OwnerMenu/ownerMenuUtils';
import { menuItem } from '@/test/fixtures';

const biryani = menuItem({ id: 1, name: 'Chicken Biryani' });
const paneer = menuItem({
  id: 2,
  name: 'paneer tikka',
  categoryId: 5,
  categoryName: 'Starters',
  foodType: 'VEG',
  available: false,
});
const egg = menuItem({
  id: 3,
  name: 'Egg Curry',
  categoryId: 5,
  categoryName: 'Starters',
  foodType: 'EGG',
  status: 'DISABLED',
});
const blocked = menuItem({ id: 4, name: 'Blocked Dish', status: 'BLOCKED' });
const items = [biryani, paneer, egg, blocked];

const conflict = (message: string) =>
  new ApiError({ kind: 'conflict', status: 409, userMessage: message, technicalMessage: 'x' });

const validForm = {
  categoryId: '3',
  name: 'Mutton Biryani',
  description: '',
  price: '349.00',
  foodType: 'NON_VEG' as const,
  imageUrl: '',
  ingredients: '',
  nutritionInfo: '',
};

describe('menuStateOf', () => {
  it('folds the two switches of a dish into one state', () => {
    expect(menuStateOf(biryani)).toBe('listed');
    expect(menuStateOf(paneer)).toBe('soldOut');
    expect(menuStateOf(egg)).toBe('hidden');
    expect(menuStateOf(blocked)).toBe('hidden');
  });

  it('calls a hidden dish hidden whether or not it is in stock', () => {
    expect(menuStateOf({ status: 'DISABLED', available: false })).toBe('hidden');
  });

  it('lets the owner show or hide everything but a dish blocked by an admin', () => {
    expect(canOwnerToggleVisibility(biryani)).toBe(true);
    expect(canOwnerToggleVisibility(egg)).toBe(true);
    expect(canOwnerToggleVisibility(blocked)).toBe(false);
  });
});

describe('filtersFromParams', () => {
  it('reads the known values from the address', () => {
    expect(
      filtersFromParams({ search: 'bir', category: '5', type: 'VEG', status: 'soldOut' }),
    ).toEqual({ search: 'bir', categoryId: 5, foodType: 'VEG', state: 'soldOut' });
  });

  it('ignores values that mean nothing', () => {
    const filters = filtersFromParams({ category: 'abc', type: 'VEGAN', status: 'gone' });

    expect(filters.categoryId).toBeUndefined();
    expect(filters.foodType).toBeUndefined();
    expect(filters.state).toBeUndefined();
    expect(hasMenuFilters(filters)).toBe(false);
  });

  it('knows when a filter is set', () => {
    expect(hasMenuFilters({ search: 'x' })).toBe(true);
    expect(hasMenuFilters({ state: 'hidden' })).toBe(true);
    expect(hasMenuFilters({})).toBe(false);
  });
});

describe('filterMenuItems', () => {
  it('lists every dish in name order, whatever the case', () => {
    expect(filterMenuItems(items, {}).map((item) => item.name)).toEqual([
      'Blocked Dish',
      'Chicken Biryani',
      'Egg Curry',
      'paneer tikka',
    ]);
  });

  it('does not change the list it is given', () => {
    const original = [...items];
    filterMenuItems(items, {});

    expect(items).toEqual(original);
  });

  it('searches the name, in any case', () => {
    expect(filterMenuItems(items, { search: 'TIKKA' }).map((item) => item.id)).toEqual([2]);
    expect(filterMenuItems(items, { search: '  curry ' }).map((item) => item.id)).toEqual([3]);
  });

  it('filters by category, food type and state, and combines the filters', () => {
    expect(filterMenuItems(items, { categoryId: 5 }).map((item) => item.id)).toEqual([3, 2]);
    expect(filterMenuItems(items, { foodType: 'VEG' }).map((item) => item.id)).toEqual([2]);
    expect(filterMenuItems(items, { state: 'hidden' }).map((item) => item.id)).toEqual([4, 3]);
    expect(filterMenuItems(items, { state: 'soldOut' }).map((item) => item.id)).toEqual([2]);
    expect(filterMenuItems(items, { state: 'listed' }).map((item) => item.id)).toEqual([1]);
    expect(filterMenuItems(items, { categoryId: 5, state: 'hidden' }).map((i) => i.id)).toEqual([
      3,
    ]);
  });
});

describe('categoryOptionsOf', () => {
  it('offers each category of the dishes once, by name', () => {
    expect(categoryOptionsOf(items)).toEqual([
      { value: '3', label: 'Biryani' },
      { value: '5', label: 'Starters' },
    ]);
  });

  it('offers nothing for an empty menu', () => {
    expect(categoryOptionsOf([])).toEqual([]);
  });
});

describe('dishCountText', () => {
  it('counts the dishes', () => {
    expect(dishCountText(12, 12)).toBe('12 dishes');
    expect(dishCountText(1, 1)).toBe('1 dish');
    expect(dishCountText(0, 0)).toBe('0 dishes');
  });

  it('says how many of all match a filter', () => {
    expect(dishCountText(3, 12)).toBe('3 of 12 dishes');
    expect(dishCountText(0, 1)).toBe('0 of 1 dish');
  });
});

describe('paginate', () => {
  const many = Array.from({ length: 25 }, (_, index) => index);

  it('cuts the list into pages', () => {
    expect(paginate(many, 0, 10)).toEqual({ rows: many.slice(0, 10), page: 0, totalPages: 3 });
    expect(paginate(many, 2, 10).rows).toEqual([20, 21, 22, 23, 24]);
  });

  it('moves a page that is too far back to the last page', () => {
    expect(paginate(many, 9, 10).page).toBe(2);
    expect(paginate(many, -1, 10).page).toBe(0);
  });

  it('has one empty page for an empty list', () => {
    expect(paginate([], 0, 10)).toEqual({ rows: [], page: 0, totalPages: 1 });
  });
});

describe('menuSortOf', () => {
  it('reads a known order and falls back to name order', () => {
    expect(menuSortOf('price-desc')).toBe('price-desc');
    expect(menuSortOf('updated')).toBe('updated');
    expect(menuSortOf('cheapest')).toBe('name');
    expect(menuSortOf(undefined)).toBe('name');
  });
});

describe('sortMenuItems', () => {
  const cheap = menuItem({ id: 5, name: 'Lassi', price: 60, updatedAt: '2026-10-01T09:00:00Z' });
  const dear = menuItem({ id: 6, name: 'Thali', price: 350, updatedAt: '2026-10-15T09:00:00Z' });
  const sameAsDear = menuItem({
    id: 7,
    name: 'Biryani Feast',
    price: 350,
    updatedAt: '2026-09-01T09:00:00Z',
  });
  const names = (list: { name: string }[]) => list.map((item) => item.name);

  it('orders by name, by price either way, or by the last change', () => {
    const list = [dear, cheap, sameAsDear];
    expect(names(sortMenuItems(list, 'name'))).toEqual(['Biryani Feast', 'Lassi', 'Thali']);
    expect(names(sortMenuItems(list, 'price'))).toEqual(['Lassi', 'Biryani Feast', 'Thali']);
    expect(names(sortMenuItems(list, 'price-desc'))).toEqual(['Biryani Feast', 'Thali', 'Lassi']);
    expect(names(sortMenuItems(list, 'updated'))).toEqual(['Thali', 'Lassi', 'Biryani Feast']);
  });

  it('leaves the list it was given as it was', () => {
    const list = [dear, cheap];
    sortMenuItems(list, 'price');
    expect(names(list)).toEqual(['Thali', 'Lassi']);
  });
});

describe('menuStateCounts', () => {
  it('counts every state, with the blocked dishes among the hidden ones', () => {
    expect(menuStateCounts(items, {})).toEqual({
      all: 4,
      listed: 1,
      soldOut: 1,
      hidden: 2,
      blocked: 1,
    });
  });

  it('counts within the other filters, whatever status is chosen', () => {
    expect(menuStateCounts(items, { categoryId: 5, state: 'listed' })).toEqual({
      all: 2,
      listed: 0,
      soldOut: 1,
      hidden: 1,
      blocked: 0,
    });
  });
});

describe('isStaleVersion', () => {
  it('is true for the conflict of an old version', () => {
    expect(isStaleVersion(conflict('This dish changed since you opened it.'))).toBe(true);
  });

  it('is false when the name is taken, and for other errors', () => {
    expect(isStaleVersion(conflict('A dish with this name already exists.'))).toBe(false);
    expect(
      isStaleVersion(
        new ApiError({ kind: 'server', status: 500, userMessage: 'Oops', technicalMessage: 'x' }),
      ),
    ).toBe(false);
    expect(isStaleVersion(new Error('boom'))).toBe(false);
  });

  it("keeps the server's words and tells the owner what happened next", () => {
    const message = staleMessageOf(conflict('Changed since you opened it.'));

    expect(message).toContain('Changed since you opened it.');
    expect(message).toContain('reloaded');
  });
});

describe('ownerMenuFormSchema', () => {
  const messagesOf = (input: Record<string, unknown>) => {
    const result = ownerMenuFormSchema.safeParse({ ...validForm, ...input });
    return result.success ? [] : result.error.issues.map((issue) => issue.message);
  };

  it('accepts a complete dish', () => {
    expect(ownerMenuFormSchema.safeParse(validForm).success).toBe(true);
  });

  it('asks for the name, the category and the food type', () => {
    expect(messagesOf({ name: '   ' })).toContain('Please enter the name.');
    expect(messagesOf({ categoryId: '' })).toContain('Choose a category.');
    expect(messagesOf({ foodType: undefined })).toContain('Choose the food type.');
  });

  it('limits the lengths as the API does', () => {
    expect(messagesOf({ name: 'x'.repeat(121) })).toContain('At most 120 characters.');
    expect(messagesOf({ description: 'x'.repeat(501) })).toContain('At most 500 characters.');
    expect(messagesOf({ ingredients: 'x'.repeat(1001) })).toContain('At most 1000 characters.');
    expect(messagesOf({ nutritionInfo: 'x'.repeat(1001) })).toContain('At most 1000 characters.');
    expect(messagesOf({ imageUrl: `https://a.example/${'x'.repeat(490)}` })).toContain(
      'At most 500 characters.',
    );
  });

  it('keeps the price between 1.00 and 100000.00 with at most 2 decimals', () => {
    expect(messagesOf({ price: '' })).toContain('Please enter the price.');
    expect(messagesOf({ price: '12.345' })).toContain(
      'Use a number with at most 2 decimals, e.g. 249.00.',
    );
    expect(messagesOf({ price: 'abc' })).not.toEqual([]);
    expect(messagesOf({ price: '0.99' })).toContain(
      'The price must be between 1.00 and 100000.00.',
    );
    expect(messagesOf({ price: '100000.01' })).toContain(
      'The price must be between 1.00 and 100000.00.',
    );
    expect(messagesOf({ price: '1' })).toEqual([]);
    expect(messagesOf({ price: '100000.00' })).toEqual([]);
  });

  it('takes only web addresses as the image', () => {
    expect(messagesOf({ imageUrl: 'not a url' })).toContain(
      'Enter a full web address (https://...).',
    );
    expect(messagesOf({ imageUrl: 'ftp://files.example/dish.jpg' })).not.toEqual([]);
    expect(messagesOf({ imageUrl: 'http://img.example/dish.jpg' })).toEqual([]);
  });
});

describe('priceFormSchema', () => {
  it('checks only the price', () => {
    expect(priceFormSchema.safeParse({ price: '249.50' }).success).toBe(true);
    expect(priceFormSchema.safeParse({ price: '0' }).success).toBe(false);
  });
});

describe('menuFormDefaults and toMenuItemRequest', () => {
  it('starts an empty form for a new dish', () => {
    expect(menuFormDefaults()).toMatchObject({ name: '', price: '', categoryId: '' });
    expect(menuFormDefaults().foodType).toBeUndefined();
  });

  it('starts with the values of the dish being edited', () => {
    const defaults = menuFormDefaults(
      menuItem({ price: 299, imageUrl: 'https://img.example/a.jpg', ingredients: 'Rice' }),
    );

    expect(defaults).toMatchObject({
      categoryId: '3',
      name: 'Chicken Biryani',
      description: 'Slow-cooked with saffron',
      price: '299.00',
      foodType: 'NON_VEG',
      imageUrl: 'https://img.example/a.jpg',
      ingredients: 'Rice',
      nutritionInfo: '',
    });
  });

  it('turns the form into what the API takes: numbers, and null for empty optional fields', () => {
    expect(toMenuItemRequest(validForm)).toEqual({
      name: 'Mutton Biryani',
      description: null,
      price: 349,
      categoryId: 3,
      foodType: 'NON_VEG',
      imageUrl: null,
      ingredients: null,
      nutritionInfo: null,
    });
  });

  it('keeps the optional texts that were filled in', () => {
    expect(
      toMenuItemRequest({
        ...validForm,
        description: 'Spicy',
        imageUrl: 'https://img.example/a.jpg',
        ingredients: 'Rice, mutton',
        nutritionInfo: '600 kcal',
      }),
    ).toMatchObject({
      description: 'Spicy',
      imageUrl: 'https://img.example/a.jpg',
      ingredients: 'Rice, mutton',
      nutritionInfo: '600 kcal',
    });
  });
});
