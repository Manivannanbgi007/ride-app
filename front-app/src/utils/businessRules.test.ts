import { describe, expect, it } from 'vitest';

import type { Category } from '@/types/catalog';
import type { Coupon } from '@/types/coupon';
import { filterCategories, moveCategory } from '@/utils/categoryUtils';
import { couponFormToTerms, couponToForm, usesLeft } from '@/utils/couponUtils';
import { allowedRestaurantActions } from '@/utils/restaurantUtils';
import {
  categoryFormSchema,
  couponFormSchema,
  refundFormSchema,
  toPaise,
  type CouponForm,
} from '@/utils/validationUtils';

const couponForm = (changes: Partial<CouponForm> = {}): CouponForm => ({
  code: 'welcome100',
  description: '',
  discountType: 'PERCENTAGE',
  discountValue: '20',
  maxDiscount: '150',
  minOrderAmount: '',
  restaurantId: '',
  validFrom: '2026-10-01',
  validUntil: '2026-10-31',
  usageLimit: '1000',
  perCustomerLimit: '',
  ...changes,
});

const errorsOf = (result: { success: boolean; error?: { issues: { path: PropertyKey[] }[] } }) =>
  result.success ? [] : (result.error?.issues.map((issue) => issue.path.join('.')) ?? []);

describe('restaurant status actions', () => {
  it('offers only the changes restaurant-service allows from each status', () => {
    expect(allowedRestaurantActions('PENDING')).toEqual(['approve', 'reject']);
    expect(allowedRestaurantActions('APPROVED')).toEqual(['activate']);
    expect(allowedRestaurantActions('ACTIVE')).toEqual(['deactivate', 'suspend']);
    expect(allowedRestaurantActions('INACTIVE')).toEqual(['activate', 'suspend']);
    expect(allowedRestaurantActions('SUSPENDED')).toEqual(['activate']);
    expect(allowedRestaurantActions('REJECTED')).toEqual([]);
  });
});

describe('category order and filters', () => {
  it('moves one category and keeps every id exactly once', () => {
    expect(moveCategory([1, 2, 3], 2, -1)).toEqual([2, 1, 3]);
    expect(moveCategory([1, 2, 3], 2, 1)).toEqual([1, 3, 2]);
    expect(moveCategory([1, 2, 3], 1, -1)).toEqual([1, 2, 3]);
    expect(moveCategory([1, 2, 3], 3, 1)).toEqual([1, 2, 3]);
  });

  it('filters by name (any letter case) and status', () => {
    const categories = [
      { id: 1, name: 'Pizza', status: 'ACTIVE' },
      { id: 2, name: 'Pasta', status: 'INACTIVE' },
    ] as Category[];

    expect(filterCategories(categories, 'PIZ', undefined).map((c) => c.id)).toEqual([1]);
    expect(filterCategories(categories, '', 'INACTIVE').map((c) => c.id)).toEqual([2]);
    expect(filterCategories(categories, ' ', undefined)).toHaveLength(2);
  });

  it('requires a name and a web address for the picture', () => {
    expect(
      errorsOf(categoryFormSchema.safeParse({ name: ' ', description: '', imageUrl: '' })),
    ).toEqual(['name']);
    expect(
      errorsOf(
        categoryFormSchema.safeParse({ name: 'Rolls', description: '', imageUrl: 'ftp://x' }),
      ),
    ).toEqual(['imageUrl']);
  });
});

describe('coupon form', () => {
  it('accepts valid terms', () => {
    expect(couponFormSchema.safeParse(couponForm()).success).toBe(true);
  });

  it.each([
    [{ code: 'X!' }, 'code'],
    [{ discountValue: '120' }, 'discountValue'],
    [{ discountValue: '0' }, 'discountValue'],
    [{ discountValue: '9.999' }, 'discountValue'],
    [{ discountType: 'FIXED' as const, maxDiscount: '50' }, 'maxDiscount'],
    [{ validUntil: '2026-09-30' }, 'validUntil'],
    [{ usageLimit: '0' }, 'usageLimit'],
    [{ restaurantId: 'abc' }, 'restaurantId'],
  ])('refuses %o', (changes, field) => {
    expect(errorsOf(couponFormSchema.safeParse(couponForm(changes)))).toContain(field);
  });

  it('turns the text boxes into the API terms (empty = not set)', () => {
    expect(couponFormToTerms(couponForm({ description: '  First order  ' }))).toEqual({
      description: 'First order',
      discountType: 'PERCENTAGE',
      discountValue: 20,
      maxDiscount: 150,
      minOrderAmount: null,
      restaurantId: null,
      validFrom: '2026-10-01',
      validUntil: '2026-10-31',
      usageLimit: 1000,
      perCustomerLimit: null,
    });
  });

  it('fills the form from a coupon, and counts the uses left', () => {
    const coupon = {
      code: 'SAVE50',
      description: null,
      discountType: 'FIXED',
      discountValue: 50,
      maxDiscount: null,
      minOrderAmount: 0,
      restaurantId: 7,
      validFrom: '2026-10-01',
      validUntil: '2026-10-31',
      usageLimit: 10,
      perCustomerLimit: 1,
      timesUsed: 4,
    } as Coupon;

    expect(couponToForm(coupon)).toMatchObject({
      code: 'SAVE50',
      discountValue: '50',
      maxDiscount: '',
      minOrderAmount: '',
      restaurantId: '7',
      usageLimit: '10',
      perCustomerLimit: '1',
    });
    expect(usesLeft(coupon)).toBe(6);
  });
});

describe('refund form', () => {
  const schema = refundFormSchema(462.6);

  it('accepts an empty amount (everything) or one up to what can still be refunded', () => {
    expect(schema.safeParse({ amount: '', reason: 'Cancelled' }).success).toBe(true);
    expect(schema.safeParse({ amount: '462.60', reason: 'Cancelled' }).success).toBe(true);
  });

  it('refuses more than can be refunded, nothing, or no reason', () => {
    expect(errorsOf(schema.safeParse({ amount: '462.61', reason: 'x' }))).toEqual(['amount']);
    expect(errorsOf(schema.safeParse({ amount: '0.00', reason: 'x' }))).toEqual(['amount']);
    expect(errorsOf(schema.safeParse({ amount: '10', reason: '' }))).toEqual(['reason']);
  });

  it('compares money in paise, never with floating-point rounding', () => {
    expect(toPaise(562.9) - toPaise(100.3)).toBe(46260);
  });
});
