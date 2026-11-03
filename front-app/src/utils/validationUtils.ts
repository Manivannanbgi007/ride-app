import type { FieldValues, Path, UseFormSetError } from 'react-hook-form';
import { z } from 'zod';

import { toApiError } from '@/api/apiErrorHandler';
import { DISCOUNT_TYPES } from '@/types/coupon';
import { VEHICLE_TYPES } from '@/types/delivery';
import { PAYMENT_METHODS } from '@/types/order';
import { ADDRESS_TYPES } from '@/types/shopping';
import { USER_ROLES } from '@/types/user';
import { OWNER_BASE, OWNER_ROUTES, PARTNER_BASE, PARTNER_ROUTES, ROUTES } from '@/utils/constants';
import { isLuhnValid } from '@/utils/shoppingUtils';

/**
 * Validation rules, written once and used by every form (with React Hook Form). They match the services' own
 * rules, so a form is rejected in the browser before it is sent; the server still checks everything.
 */

/** Admin actions are audited: a reason is required, at most 500 characters (as in the services). */
export const REASON_MAX_LENGTH = 500;

/** A required reason of at most `maxLength` characters (a delivery reassignment allows 300). */
export function reasonRuleOf(
  maxLength: number,
  requiredMessage = 'Please give a reason: it is kept in the audit log.',
) {
  return z
    .string()
    .trim()
    .min(1, requiredMessage)
    .max(maxLength, `At most ${maxLength} characters.`);
}

export const reasonRule = reasonRuleOf(REASON_MAX_LENGTH);

/** "Cancel order", "Deactivate user", "Block item" ... (maxLength: what the service allows) */
export function reasonFormSchemaOf(maxLength = REASON_MAX_LENGTH, requiredMessage?: string) {
  return z.object({ reason: reasonRuleOf(maxLength, requiredMessage) });
}
export const reasonFormSchema = reasonFormSchemaOf();
export type ReasonForm = z.infer<typeof reasonFormSchema>;

/** delivery-service keeps at most 300 characters for a reassignment's reason. */
export const REASSIGN_REASON_MAX_LENGTH = 300;

/**
 * Reassigning a delivery: a partner's id, or '' for "back to the queue" (only for a delivery that has a partner).
 */
export function reassignFormSchema(hasPartner: boolean) {
  return z
    .object({
      partnerUserId: z.string(),
      reason: reasonRuleOf(REASSIGN_REASON_MAX_LENGTH),
    })
    .refine((form) => form.partnerUserId !== '' || hasPartner, {
      path: ['partnerUserId'],
      message: 'The delivery is already waiting for a partner: choose one.',
    });
}
export type ReassignForm = z.infer<ReturnType<typeof reassignFormSchema>>;

export const changeRoleFormSchema = z.object({
  role: z.enum(USER_ROLES, 'Choose a role.'),
  reason: reasonRule,
});
export type ChangeRoleForm = z.infer<typeof changeRoleFormSchema>;

export const changeCategoryFormSchema = z.object({
  categoryId: z.string().min(1, 'Choose a category.'),
  reason: reasonRule,
});
export type ChangeCategoryForm = z.infer<typeof changeCategoryFormSchema>;

/** An amount typed into a form: up to 8 digits, and at most 2 after the point (as the services store money). */
const MONEY_PATTERN = /^\d{1,8}(\.\d{1,2})?$/;
const MONEY_MESSAGE = 'A number with at most 2 decimals, e.g. 99.50.';

/** Amount in paise (whole numbers), so comparisons of money never suffer from floating-point rounding. */
export function toPaise(amount: number | string): number {
  return Math.round(Number(amount) * 100);
}

const requiredMoney = z
  .string()
  .trim()
  .regex(MONEY_PATTERN, MONEY_MESSAGE)
  .refine((value) => toPaise(value) >= 1, 'At least 0.01.');

const optionalMoney = z
  .string()
  .trim()
  .refine((value) => value === '' || MONEY_PATTERN.test(value), MONEY_MESSAGE);

/** An optional whole number of at least `min` ('' = not set). */
function optionalWholeNumber(min: number) {
  return z
    .string()
    .trim()
    .refine(
      (value) => value === '' || (/^\d+$/.test(value) && Number(value) >= min),
      `A whole number of at least ${min}, or empty.`,
    );
}

function isWebAddress(value: string): boolean {
  try {
    const url = new URL(value);
    return url.protocol === 'http:' || url.protocol === 'https:';
  } catch {
    return false;
  }
}

/** An email address, as user-service accepts it (it is stored in lower case). */
const emailRule = z
  .string()
  .trim()
  .min(1, 'Please enter your email address.')
  .max(255, 'At most 255 characters.')
  .regex(
    /^[^\s@]+@[^\s@]+\.[^\s@]+$/,
    'Please enter a valid email address, e.g. name@example.com.',
  );

/** user-service's password rules: 8 to 72 characters with at least one letter and one digit. */
const passwordRule = z
  .string()
  .min(8, 'At least 8 characters.')
  .max(72, 'At most 72 characters.')
  .regex(/^(?=.*[A-Za-z])(?=.*\d)/, 'Use at least one letter and one digit.');

export const signInFormSchema = z.object({
  email: emailRule,
  password: z.string().min(1, 'Please enter your password.'),
});
export type SignInForm = z.infer<typeof signInFormSchema>;

/** A new customer account (user-service rules); the phone number is optional. */
export const registerFormSchema = z
  .object({
    fullName: z
      .string()
      .trim()
      .min(1, 'Please enter your name.')
      .max(100, 'At most 100 characters.'),
    email: emailRule,
    phone: z
      .string()
      .trim()
      .refine(
        (value) => value === '' || /^\+?\d{10,15}$/.test(value),
        '10 to 15 digits, optionally starting with +.',
      ),
    password: passwordRule,
    confirmPassword: z.string(),
  })
  .refine((form) => form.password === form.confirmPassword, {
    path: ['confirmPassword'],
    message: 'The two passwords are not the same.',
  });
export type RegisterForm = z.infer<typeof registerFormSchema>;

/** The customer's own details (the email cannot be changed). */
export const profileFormSchema = z.object({
  fullName: z.string().trim().min(1, 'Please enter your name.').max(100, 'At most 100 characters.'),
  phone: z
    .string()
    .trim()
    .refine(
      (value) => value === '' || /^\+?\d{10,15}$/.test(value),
      '10 to 15 digits, optionally starting with +.',
    ),
});
export type ProfileForm = z.infer<typeof profileFormSchema>;

export const passwordFormSchema = z
  .object({
    currentPassword: z.string().min(1, 'Please enter your current password.'),
    newPassword: passwordRule,
    confirmPassword: z.string(),
  })
  .refine((form) => form.newPassword === form.confirmPassword, {
    path: ['confirmPassword'],
    message: 'The two passwords are not the same.',
  })
  .refine((form) => form.newPassword !== form.currentPassword, {
    path: ['newPassword'],
    message: 'Choose a password different from the current one.',
  });
export type PasswordForm = z.infer<typeof passwordFormSchema>;

/** A delivery address (user-service rules). Every field is text in the form; '' = not given. */
export const addressFormSchema = z.object({
  type: z.enum(ADDRESS_TYPES, 'Choose a kind of address.'),
  line1: z
    .string()
    .trim()
    .min(1, 'Please enter the street and number.')
    .max(255, 'At most 255 characters.'),
  line2: z.string().trim().max(255, 'At most 255 characters.'),
  city: z.string().trim().min(1, 'Please enter the city.').max(100, 'At most 100 characters.'),
  state: z.string().trim().min(1, 'Please enter the state.').max(100, 'At most 100 characters.'),
  postalCode: z
    .string()
    .trim()
    .regex(/^[A-Za-z0-9 -]{3,10}$/, '3 to 10 letters, digits, spaces or hyphens.'),
  country: z
    .string()
    .trim()
    .min(1, 'Please enter the country.')
    .max(100, 'At most 100 characters.'),
  // The map pin from the device's position ("Use my current location"): both or neither
  latitude: z.number().min(-90).max(90).nullable(),
  longitude: z.number().min(-180).max(180).nullable(),
  makeDefault: z.boolean(),
});
export type AddressForm = z.infer<typeof addressFormSchema>;

/** A coupon code as order-service accepts it (it is upper-cased there). */
export const couponCodeSchema = z.object({
  code: z
    .string()
    .trim()
    .regex(/^[A-Za-z0-9_-]{3,30}$/, '3 to 30 letters, digits, - or _.'),
});

/** Checkout: where to, how to pay, and a note for the restaurant. */
export const checkoutFormSchema = z.object({
  addressId: z.string().min(1, 'Choose where the food goes.'),
  paymentMethod: z.enum(PAYMENT_METHODS, 'Choose how you pay.'),
  notes: z.string().trim().max(500, 'At most 500 characters.'),
});
export type CheckoutForm = z.infer<typeof checkoutFormSchema>;

/** The card number's digits (spaces and dashes typed for readability are left out). */
export function cardDigits(number: string): string {
  return number.replace(/[\s-]/g, '');
}

/** payment-service's checks, done first in the browser: a real card number, an expiry not in the past. */
export function cardFormSchema(today: Date = new Date()) {
  return z
    .object({
      number: z
        .string()
        .refine((value) => /^\d{12,19}$/.test(cardDigits(value)), '12 to 19 digits.')
        .refine((value) => isLuhnValid(cardDigits(value)), 'This card number is not valid.'),
      expiryMonth: z.string().min(1, 'Month?'),
      expiryYear: z.string().min(1, 'Year?'),
    })
    .refine(
      (form) =>
        Number(form.expiryYear) * 12 + Number(form.expiryMonth) >=
        today.getFullYear() * 12 + today.getMonth() + 1,
      { path: ['expiryMonth'], message: 'The card has expired.' },
    );
}
export type CardForm = z.infer<ReturnType<typeof cardFormSchema>>;

export const upiFormSchema = z.object({
  upiId: z
    .string()
    .trim()
    .regex(/^[a-zA-Z0-9.\-_]{2,64}@[a-zA-Z]{2,64}$/, 'A UPI id such as name@bank.'),
});
export type UpiForm = z.infer<typeof upiFormSchema>;

export const walletFormSchema = z.object({
  walletId: z.string().trim().min(4, 'At least 4 characters.').max(64, 'At most 64 characters.'),
});
export type WalletForm = z.infer<typeof walletFormSchema>;
/**
 * Where to go after signing in: only an address of the customer area (never another site, and never the admin
 * or the owner area, which act as other users).
 */
export function safeReturnTo(value: string | null): string | null {
  if (!value?.startsWith('/') || value.startsWith('//') || value.startsWith('/\\')) {
    return null;
  }
  const inOtherArea = [ROUTES.admin, OWNER_BASE].some(
    (base) => value === base || value.startsWith(`${base}/`),
  );
  return inOtherArea ? null : value;
}

/** Where to go after the admin signs in: only an address inside the admin area (never another site). */
export function safeAdminReturnTo(value: string | null): string | null {
  if (!value?.startsWith(`${ROUTES.admin}/`) && value !== ROUTES.admin) {
    return null;
  }
  return value === ROUTES.signIn ? null : value;
}

/** Where to go after the partner signs in: only an address of the partner area (never another site). */
export function safePartnerReturnTo(value: string | null): string | null {
  if (!value?.startsWith(`${PARTNER_BASE}/`) && value !== PARTNER_BASE) {
    return null;
  }
  return value === PARTNER_ROUTES.signIn || value === PARTNER_ROUTES.register ? null : value;
}

/**
 * A new delivery partner account: as a customer's, but the phone number is required (customers call the partner
 * who brings their food).
 */
export const partnerRegisterFormSchema = registerFormSchema.refine((form) => form.phone !== '', {
  path: ['phone'],
  message: 'Please enter your phone number: customers call you about their delivery.',
});

/** What a partner fills in before going online (delivery-partner-service rules). */
export const partnerProfileFormSchema = z.object({
  vehicleType: z.enum(VEHICLE_TYPES, 'Please choose your vehicle.'),
  vehicleNumber: z
    .string()
    .trim()
    .min(1, 'Please enter the vehicle number.')
    .max(20, 'At most 20 characters.'),
  city: z
    .string()
    .trim()
    .min(1, 'Please enter the city you deliver in.')
    .max(100, 'At most 100 characters.'),
});
export type PartnerProfileForm = z.infer<typeof partnerProfileFormSchema>;

/** Where to go after the owner signs in: only an address of the owner area (never another site). */
export function safeOwnerReturnTo(value: string | null): string | null {
  if (!value?.startsWith(`${OWNER_BASE}/`) && value !== OWNER_BASE) {
    return null;
  }
  return value === OWNER_ROUTES.signIn || value === OWNER_ROUTES.register ? null : value;
}
/** A food category (catalog-service rules: name up to 60, unique; description up to 255; image a web address). */
export const categoryFormSchema = z.object({
  name: z
    .string()
    .trim()
    .min(1, 'Please give the category a name.')
    .max(60, 'At most 60 characters.'),
  description: z.string().trim().max(255, 'At most 255 characters.'),
  imageUrl: z
    .string()
    .trim()
    .max(500, 'At most 500 characters.')
    .refine((value) => value === '' || isWebAddress(value), 'A web address starting with https://'),
});
export type CategoryForm = z.infer<typeof categoryFormSchema>;

/**
 * A coupon's terms (promotion-service rules). Every field is text in the form ('' = not set) and is turned into
 * numbers when sent (utils/couponUtils.ts).
 */
export const couponFormSchema = z
  .object({
    code: z
      .string()
      .trim()
      .regex(/^[A-Za-z0-9_-]{3,30}$/, '3 to 30 letters, digits, - or _.'),
    description: z.string().trim().max(255, 'At most 255 characters.'),
    discountType: z.enum(DISCOUNT_TYPES, 'Choose how the discount works.'),
    discountValue: requiredMoney,
    maxDiscount: optionalMoney,
    minOrderAmount: optionalMoney,
    restaurantId: optionalWholeNumber(1),
    validFrom: z.string().min(1, 'Choose the first day.'),
    validUntil: z.string().min(1, 'Choose the last day.'),
    usageLimit: optionalWholeNumber(1),
    perCustomerLimit: optionalWholeNumber(1),
  })
  .superRefine((form, context) => {
    if (form.discountType === 'PERCENTAGE' && toPaise(form.discountValue) > toPaise(100)) {
      context.addIssue({
        code: 'custom',
        path: ['discountValue'],
        message: 'A percentage cannot be more than 100.',
      });
    }
    if (form.discountType === 'FIXED' && form.maxDiscount !== '') {
      context.addIssue({
        code: 'custom',
        path: ['maxDiscount'],
        message: 'Only for percentage coupons: leave it empty.',
      });
    }
    if (form.validFrom && form.validUntil && form.validUntil < form.validFrom) {
      context.addIssue({
        code: 'custom',
        path: ['validUntil'],
        message: 'The last day is before the first day.',
      });
    }
  });
export type CouponForm = z.infer<typeof couponFormSchema>;

/**
 * A refund of a payment: an amount between 0.01 and what can still be refunded (empty = all of it), and a reason.
 */
export function refundFormSchema(refundable: number) {
  return z.object({
    amount: optionalMoney.refine(
      (value) => value === '' || (toPaise(value) >= 1 && toPaise(value) <= toPaise(refundable)),
      `Between 0.01 and ${refundable.toFixed(2)}, or empty to refund everything.`,
    ),
    reason: reasonRule,
  });
}
export type RefundForm = z.infer<ReturnType<typeof refundFormSchema>>;

/** An id typed into a filter: digits only (an empty box means "no filter"). */
export function isWholeNumber(text: string): boolean {
  return /^\d+$/.test(text.trim());
}

/** '42' -> 42; '' or anything else -> undefined (no filter). */
export function toIdFilter(text: string): number | undefined {
  return isWholeNumber(text) ? Number(text.trim()) : undefined;
}

/** One of the allowed values, or undefined ("All ..."): turns a <select>'s text back into a typed value. */
export function oneOf<T extends string>(value: string, allowed: readonly T[]): T | undefined {
  return allowed.find((option) => option === value);
}

/** A date range for the filters ('YYYY-MM-DD'): the message to show when "to" is before "from". */
export function dateRangeError(fromDate?: string, toDate?: string): string | undefined {
  return fromDate && toDate && toDate < fromDate
    ? 'The "to" date is before the "from" date.'
    : undefined;
}

/** A filter box for an id: the message to show for anything but digits (an empty box is fine). */
export function idFilterError(text: string): string | undefined {
  return text.trim() && !isWholeNumber(text) ? 'Numbers only.' : undefined;
}

/**
 * Shows a failed submission inside the form: the server's messages next to the fields they are about (400 with
 * `errors`), and the general message (a 409 conflict, a 422 business rule, a network error) above the buttons.
 */
export function showServerErrors<T extends FieldValues>(
  error: unknown,
  setError: UseFormSetError<T>,
  fieldNames: readonly Path<T>[],
): void {
  const apiError = toApiError(error);
  for (const [field, message] of Object.entries(apiError.fieldErrors)) {
    if (fieldNames.includes(field as Path<T>)) {
      setError(field as Path<T>, { message });
    }
  }
  setError('root.server', { message: apiError.userMessage });
}

export const FOOD_NAME_MAX_LENGTH = 120;
export const FOOD_DESCRIPTION_MAX_LENGTH = 500;
export const FOOD_IMAGE_URL_MAX_LENGTH = 500;
/** Ingredients and nutrition info: free text, several lines */
export const FOOD_TEXT_MAX_LENGTH = 1000;
export const FOOD_PRICE_MIN = 1;
export const FOOD_PRICE_MAX = 100000;

function isWebUrl(text: string): boolean {
  try {
    const { protocol } = new URL(text);
    return protocol === 'http:' || protocol === 'https:';
  } catch {
    return false;
  }
}

/** New / edit menu item: the fields as typed (strings); the restaurant is only chosen when creating. */
export const foodFormSchema = z.object({
  restaurantId: z.string().min(1, 'Choose a restaurant.'),
  categoryId: z.string().min(1, 'Choose a category.'),
  name: z
    .string()
    .trim()
    .min(1, 'Please enter the name.')
    .max(FOOD_NAME_MAX_LENGTH, `At most ${FOOD_NAME_MAX_LENGTH} characters.`),
  description: z
    .string()
    .trim()
    .max(FOOD_DESCRIPTION_MAX_LENGTH, `At most ${FOOD_DESCRIPTION_MAX_LENGTH} characters.`),
  ingredients: z
    .string()
    .trim()
    .max(FOOD_TEXT_MAX_LENGTH, `At most ${FOOD_TEXT_MAX_LENGTH} characters.`),
  nutritionInfo: z
    .string()
    .trim()
    .max(FOOD_TEXT_MAX_LENGTH, `At most ${FOOD_TEXT_MAX_LENGTH} characters.`),
  price: z
    .string()
    .trim()
    .min(1, 'Please enter the price.')
    .regex(/^\d+(\.\d{1,2})?$/, 'Use a number with at most 2 decimals, e.g. 249.00.')
    .refine(
      (value) => Number(value) >= FOOD_PRICE_MIN && Number(value) <= FOOD_PRICE_MAX,
      'The price must be between 1.00 and 100000.00.',
    ),
  foodType: z.enum(['VEG', 'NON_VEG', 'EGG'], 'Choose the food type.'),
  imageUrl: z
    .string()
    .trim()
    .max(FOOD_IMAGE_URL_MAX_LENGTH, `At most ${FOOD_IMAGE_URL_MAX_LENGTH} characters.`)
    .refine((value) => value === '' || isWebUrl(value), 'Enter a full web address (https://...).'),
});
export type FoodForm = z.infer<typeof foodFormSchema>;
