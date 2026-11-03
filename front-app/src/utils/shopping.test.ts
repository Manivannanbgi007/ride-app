import { describe, expect, it } from 'vitest';

import { ApiError } from '@/api/apiErrorHandler';
import { cart } from '@/test/fixtures';
import type { OrderStatus } from '@/types/order';
import {
  addressLines,
  amountForFreeDelivery,
  cartItemCount,
  hasDelivery,
  isLuhnValid,
  isPastPayment,
  keepsIdempotencyKey,
  orderProgress,
  paymentDeadline,
} from '@/utils/shoppingUtils';
import {
  addressFormSchema,
  cardDigits,
  cardFormSchema,
  couponCodeSchema,
  passwordFormSchema,
  profileFormSchema,
  upiFormSchema,
  walletFormSchema,
} from '@/utils/validationUtils';

const failure = (kind: ApiError['kind']) =>
  new ApiError({ kind, userMessage: kind, technicalMessage: kind });

describe('cart figures', () => {
  it('counts the dishes, not the lines', () => {
    expect(cartItemCount(cart())).toBe(2);
    expect(cartItemCount(undefined)).toBe(0);
  });

  it('says how much more gives free delivery (from 499.00 after the discount)', () => {
    const price = {
      itemsTotal: 450,
      discountAmount: 50,
      deliveryFee: 40,
      taxAmount: 20,
      totalAmount: 460,
      currency: 'INR',
    };
    expect(amountForFreeDelivery(price)).toBe(99);
    expect(amountForFreeDelivery({ ...price, deliveryFee: 0 })).toBeNull();
    expect(amountForFreeDelivery({ ...price, itemsTotal: 0, discountAmount: 0 })).toBeNull();
  });
});

describe('idempotency keys', () => {
  it('keeps the key when the request may have been done, and changes it after a refusal', () => {
    expect(keepsIdempotencyKey(failure('network'))).toBe(true);
    expect(keepsIdempotencyKey(failure('timeout'))).toBe(true);
    expect(keepsIdempotencyKey(failure('gatewayTimeout'))).toBe(true);
    expect(keepsIdempotencyKey(failure('server'))).toBe(true);
    expect(keepsIdempotencyKey(failure('validation'))).toBe(false);
    expect(keepsIdempotencyKey(failure('conflict'))).toBe(false);
    expect(keepsIdempotencyKey(failure('unavailable'))).toBe(false);
  });
});

describe('orders and addresses', () => {
  it('knows when an unpaid order is cancelled, and whether the payment step is over', () => {
    expect(paymentDeadline({ createdAt: '2026-10-08T10:00:00Z' }).toISOString()).toBe(
      '2026-10-08T10:15:00.000Z',
    );
    expect(isPastPayment('PAYMENT_PENDING')).toBe(false);
    expect(isPastPayment('PLACED')).toBe(true);
    expect(isPastPayment('CANCELLED')).toBe(false);
  });

  it('writes an address as lines, leaving out what is missing', () => {
    expect(
      addressLines({
        line1: '12 MG Road',
        line2: null,
        city: 'Bengaluru',
        state: 'Karnataka',
        postalCode: '560001',
        country: 'India',
      }),
    ).toEqual(['12 MG Road', 'Bengaluru 560001', 'Karnataka, India']);
  });
});

describe('payment and address forms', () => {
  const today = new Date(2026, 9, 8);

  it('checks card numbers like payment-service does', () => {
    expect(isLuhnValid('4111111111111111')).toBe(true);
    expect(isLuhnValid('4000000000000002')).toBe(true);
    expect(isLuhnValid('4111111111111112')).toBe(false);
    expect(cardDigits('4111 1111-1111 1111')).toBe('4111111111111111');
  });

  it('refuses an expired card but accepts the current month', () => {
    const schema = cardFormSchema(today);
    const card = { number: '4111 1111 1111 1111', expiryMonth: '10', expiryYear: '2026' };
    expect(schema.safeParse(card).success).toBe(true);
    expect(schema.safeParse({ ...card, expiryMonth: '9' }).success).toBe(false);
    expect(schema.safeParse({ ...card, number: '1234' }).success).toBe(false);
  });

  it('checks UPI ids, wallet ids, coupon codes and addresses', () => {
    expect(upiFormSchema.safeParse({ upiId: 'priya@okbank' }).success).toBe(true);
    expect(upiFormSchema.safeParse({ upiId: 'priya' }).success).toBe(false);
    expect(walletFormSchema.safeParse({ walletId: 'abc' }).success).toBe(false);
    expect(couponCodeSchema.safeParse({ code: ' save_50 ' }).success).toBe(true);
    expect(couponCodeSchema.safeParse({ code: 'SAVE 50' }).success).toBe(false);

    const home = {
      type: 'HOME' as const,
      line1: '12 MG Road',
      line2: '',
      city: 'Bengaluru',
      state: 'Karnataka',
      postalCode: '560001',
      country: 'India',
      latitude: null,
      longitude: null,
      makeDefault: false,
    };
    expect(addressFormSchema.safeParse(home).success).toBe(true);
    expect(addressFormSchema.safeParse({ ...home, postalCode: '56' }).success).toBe(false);
    expect(addressFormSchema.safeParse({ ...home, city: ' ' }).success).toBe(false);
    // The map pin from the device's position
    const pinned = { ...home, latitude: 12.9716, longitude: 77.5946 };
    expect(addressFormSchema.safeParse(pinned).success).toBe(true);
    expect(addressFormSchema.safeParse({ ...pinned, latitude: 91 }).success).toBe(false);
    expect(addressFormSchema.safeParse({ ...pinned, longitude: -181 }).success).toBe(false);
  });
});

describe('order progress', () => {
  const entry = (status: OrderStatus, minute: number) => ({
    status,
    actorType: 'SYSTEM' as const,
    actorUserId: null,
    note: null,
    at: `2026-10-08T10:${String(minute).padStart(2, '0')}:00Z`,
  });

  it('marks the steps done, now and next, with the first time each was reached', () => {
    const steps = orderProgress({
      status: 'PREPARING',
      timeline: [
        entry('PAYMENT_PENDING', 0),
        entry('PLACED', 1),
        entry('ACCEPTED', 5),
        entry('ACCEPTED', 6),
        entry('PREPARING', 7),
      ],
    });
    expect(steps.map((step) => step.state)).toEqual([
      'done',
      'done',
      'current',
      'upcoming',
      'upcoming',
      'upcoming',
    ]);
    expect(steps[1].at).toBe('2026-10-08T10:05:00Z');
    expect(steps[3].at).toBeNull();
  });

  it('counts a delivered order as done, and an unpaid one as not started', () => {
    expect(
      orderProgress({ status: 'DELIVERED', timeline: [] }).every((step) => step.state === 'done'),
    ).toBe(true);
    expect(
      orderProgress({ status: 'PAYMENT_PENDING', timeline: [] }).every(
        (step) => step.state === 'upcoming',
      ),
    ).toBe(true);
    expect(hasDelivery('READY_FOR_PICKUP')).toBe(true);
    expect(hasDelivery('PREPARING')).toBe(false);
  });
});

describe('profile and password forms', () => {
  it('checks the name and phone', () => {
    expect(profileFormSchema.safeParse({ fullName: 'Priya', phone: '' }).success).toBe(true);
    expect(profileFormSchema.safeParse({ fullName: ' ', phone: '' }).success).toBe(false);
    expect(profileFormSchema.safeParse({ fullName: 'Priya', phone: '98' }).success).toBe(false);
  });

  it('wants a new password that follows the rules, typed twice and different from the old one', () => {
    const form = {
      currentPassword: 'Eater1234',
      newPassword: 'Better1234',
      confirmPassword: 'Better1234',
    };
    expect(passwordFormSchema.safeParse(form).success).toBe(true);
    expect(passwordFormSchema.safeParse({ ...form, confirmPassword: 'Better12345' }).success).toBe(
      false,
    );
    expect(
      passwordFormSchema.safeParse({
        ...form,
        newPassword: 'Eater1234',
        confirmPassword: 'Eater1234',
      }).success,
    ).toBe(false);
    expect(
      passwordFormSchema.safeParse({ ...form, newPassword: 'short1', confirmPassword: 'short1' })
        .success,
    ).toBe(false);
  });
});
