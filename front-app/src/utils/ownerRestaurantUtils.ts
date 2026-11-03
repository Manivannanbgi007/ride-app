import { z } from 'zod';

import type { OwnerOperatingHours, OwnerRestaurantRequest } from '@/types/owner';
import type { DayOfWeek, OperatingHours, RestaurantDetails } from '@/types/restaurant';
import { DAYS_OF_WEEK } from '@/utils/storefrontUtils';

/** One day of the opening-hours editor: closed, or open between two times ('HH:mm'). */
export interface HoursRow {
  day: DayOfWeek;
  open: boolean;
  opensAt: string;
  closesAt: string;
}

const DEFAULT_OPENS = '10:00';
const DEFAULT_CLOSES = '22:00';

/** A new restaurant starts open every day from 10:00 to 22:00; the owner changes what differs. */
export function defaultHoursRows(): HoursRow[] {
  return DAYS_OF_WEEK.map((day) => ({
    day,
    open: true,
    opensAt: DEFAULT_OPENS,
    closesAt: DEFAULT_CLOSES,
  }));
}

/** 'HH:mm:ss' (what the API sends) -> 'HH:mm' (what a time field shows). */
function toTimeField(time: string): string {
  return time.slice(0, 5);
}

/**
 * The editor's rows for the hours the API sent: a day without hours is closed. (The API allows several ranges a
 * day; the editor shows the first and keeps one range per day, which is how restaurants are registered.)
 */
export function hoursRowsOf(hours: readonly OperatingHours[]): HoursRow[] {
  return DAYS_OF_WEEK.map((day) => {
    const range = hours.find((entry) => entry.dayOfWeek === day);
    return range
      ? {
          day,
          open: true,
          opensAt: toTimeField(range.opensAt),
          closesAt: toTimeField(range.closesAt),
        }
      : { day, open: false, opensAt: DEFAULT_OPENS, closesAt: DEFAULT_CLOSES };
  });
}

/** The hours to send: only the open days. */
export function hoursOf(rows: readonly HoursRow[]): OwnerOperatingHours[] {
  return rows
    .filter((row) => row.open)
    .map(({ day, opensAt, closesAt }) => ({ dayOfWeek: day, opensAt, closesAt }));
}

/** What is wrong with the hours, in words (null when they can be sent). */
export function hoursProblem(rows: readonly HoursRow[]): string | null {
  const open = rows.filter((row) => row.open);
  if (open.length === 0) {
    return 'Open on at least one day.';
  }
  const bad = open.find((row) => !row.opensAt || !row.closesAt || row.closesAt <= row.opensAt);
  return bad ? 'Each open day must close after it opens (for example 10:00 to 22:00).' : null;
}

const CUISINE_PATTERN = /^[A-Za-z][A-Za-z &'-]*$/;
const PHONE_PATTERN = /^\+?[0-9]{10,15}$/;
const POSTAL_PATTERN = /^[A-Za-z0-9 -]{3,10}$/;
export const FOOD_LICENSE = 'FOOD_LICENSE';

/** 'Biryani, North Indian' -> ['Biryani', 'North Indian'] */
export function cuisinesOf(text: string): string[] {
  return text
    .split(',')
    .map((cuisine) => cuisine.trim())
    .filter(Boolean);
}

function isWebAddress(text: string): boolean {
  try {
    const url = new URL(text);
    return url.protocol === 'https:' || url.protocol === 'http:';
  } catch {
    return false;
  }
}

const coordinate = (label: string, limit: number) =>
  z
    .string()
    .trim()
    .refine(
      (value) =>
        value === '' || (Number.isFinite(Number(value)) && Math.abs(Number(value)) <= limit),
      `${label} is a number between -${limit} and ${limit}.`,
    );

/**
 * The restaurant form (restaurant-service's rules). Every field is text, as typed; `requestOf` turns it into the
 * request. The cuisines are one field, separated by commas.
 */
export const restaurantFormSchema = z.object({
  name: z.string().trim().min(1, 'Please enter the name.').max(150, 'At most 150 characters.'),
  description: z.string().trim().max(1000, 'At most 1000 characters.'),
  cuisines: z
    .string()
    .refine((text) => cuisinesOf(text).length >= 1, 'Enter at least one cuisine, e.g. Biryani.')
    .refine((text) => cuisinesOf(text).length <= 5, 'At most 5 cuisines.')
    .refine(
      (text) => cuisinesOf(text).every((cuisine) => cuisine.length <= 40),
      'Each cuisine has at most 40 characters.',
    )
    .refine(
      (text) => cuisinesOf(text).every((cuisine) => CUISINE_PATTERN.test(cuisine)),
      "Cuisines use letters, spaces, & ' and - only.",
    ),
  phone: z.string().trim().regex(PHONE_PATTERN, '10 to 15 digits, optionally starting with +.'),
  email: z
    .string()
    .trim()
    .max(255, 'At most 255 characters.')
    .pipe(z.email('Enter a valid email address.')),
  line1: z
    .string()
    .trim()
    .min(1, 'Please enter the street address.')
    .max(255, 'At most 255 characters.'),
  line2: z.string().trim().max(255, 'At most 255 characters.'),
  city: z.string().trim().min(1, 'Please enter the city.').max(100, 'At most 100 characters.'),
  state: z.string().trim().min(1, 'Please enter the state.').max(100, 'At most 100 characters.'),
  postalCode: z
    .string()
    .trim()
    .regex(POSTAL_PATTERN, '3 to 10 letters, digits, spaces or hyphens.'),
  country: z
    .string()
    .trim()
    .min(1, 'Please enter the country.')
    .max(100, 'At most 100 characters.'),
  latitude: coordinate('Latitude', 90),
  longitude: coordinate('Longitude', 180),
  timeZone: z
    .string()
    .trim()
    .min(1, 'Please enter the time zone.')
    .max(50, 'At most 50 characters.'),
  legalBusinessName: z
    .string()
    .trim()
    .min(1, 'Please enter the legal business name.')
    .max(200, 'At most 200 characters.'),
  taxRegistrationNumber: z.string().trim().max(50, 'At most 50 characters.'),
  foodLicenseNumber: z
    .string()
    .trim()
    .min(1, 'Please enter the food licence number.')
    .max(50, 'At most 50 characters.'),
  foodLicenseUrl: z
    .string()
    .trim()
    .max(500, 'At most 500 characters.')
    .refine(isWebAddress, 'Enter the web address of the licence document (https://...).'),
});
export type RestaurantFormValues = z.infer<typeof restaurantFormSchema>;

export const EMPTY_RESTAURANT_FORM: RestaurantFormValues = {
  name: '',
  description: '',
  cuisines: '',
  phone: '',
  email: '',
  line1: '',
  line2: '',
  city: '',
  state: '',
  postalCode: '',
  country: 'India',
  latitude: '',
  longitude: '',
  timeZone: 'Asia/Kolkata',
  legalBusinessName: '',
  taxRegistrationNumber: '',
  foodLicenseNumber: '',
  foodLicenseUrl: '',
};

/** The form's starting values for a restaurant the owner already has. */
export function formValuesOf(restaurant: RestaurantDetails): RestaurantFormValues {
  const license = restaurant.documents.find((document) => document.type === FOOD_LICENSE);
  const address = restaurant.address as RestaurantDetails['address'] & {
    latitude?: number | null;
    longitude?: number | null;
  };
  return {
    name: restaurant.name,
    description: restaurant.description ?? '',
    cuisines: restaurant.cuisines.join(', '),
    phone: restaurant.phone,
    email: restaurant.email,
    line1: address.line1,
    line2: address.line2 ?? '',
    city: address.city,
    state: address.state,
    postalCode: address.postalCode,
    country: address.country,
    latitude:
      address.latitude === null || address.latitude === undefined ? '' : String(address.latitude),
    longitude:
      address.longitude === null || address.longitude === undefined
        ? ''
        : String(address.longitude),
    timeZone: restaurant.timeZone,
    legalBusinessName: restaurant.legalBusinessName,
    taxRegistrationNumber: restaurant.taxRegistrationNumber ?? '',
    foodLicenseNumber: restaurant.foodLicenseNumber,
    foodLicenseUrl: license?.url ?? '',
  };
}

/**
 * The request for the form's values. The licence document is the one the form edits; other documents the
 * restaurant already has (tax registration, ID proof...) are kept as they are.
 */
export function requestOf(
  values: RestaurantFormValues,
  hours: readonly OwnerOperatingHours[],
  otherDocuments: readonly { type: string; url: string }[] = [],
): OwnerRestaurantRequest {
  const number = (text: string) => (text === '' ? null : Number(text));
  return {
    name: values.name,
    description: values.description || null,
    cuisines: cuisinesOf(values.cuisines),
    phone: values.phone,
    email: values.email,
    address: {
      line1: values.line1,
      line2: values.line2 || null,
      city: values.city,
      state: values.state,
      postalCode: values.postalCode,
      country: values.country,
      latitude: number(values.latitude),
      longitude: number(values.longitude),
    },
    timeZone: values.timeZone,
    legalBusinessName: values.legalBusinessName,
    taxRegistrationNumber: values.taxRegistrationNumber || null,
    foodLicenseNumber: values.foodLicenseNumber,
    documents: [
      { type: FOOD_LICENSE, url: values.foodLicenseUrl },
      ...otherDocuments.filter((document) => document.type !== FOOD_LICENSE),
    ],
    operatingHours: [...hours],
  };
}
