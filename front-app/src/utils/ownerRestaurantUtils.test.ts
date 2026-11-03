import { describe, expect, it } from 'vitest';

import type { RestaurantDetails } from '@/types/restaurant';
import {
  cuisinesOf,
  defaultHoursRows,
  EMPTY_RESTAURANT_FORM,
  formValuesOf,
  hoursOf,
  hoursProblem,
  hoursRowsOf,
  requestOf,
  restaurantFormSchema,
  type RestaurantFormValues,
} from '@/utils/ownerRestaurantUtils';

const valid: RestaurantFormValues = {
  ...EMPTY_RESTAURANT_FORM,
  name: 'Spice Garden',
  cuisines: 'Biryani, North Indian',
  phone: '+919876543210',
  email: 'spice@example.com',
  line1: '12 MG Road',
  city: 'Bengaluru',
  state: 'Karnataka',
  postalCode: '560001',
  legalBusinessName: 'Spice Garden Pvt Ltd',
  foodLicenseNumber: '10020042001234',
  foodLicenseUrl: 'https://docs.example.com/fssai.pdf',
};

const errorsOf = (values: RestaurantFormValues): string[] => {
  const result = restaurantFormSchema.safeParse(values);
  return result.success ? [] : result.error.issues.map((issue) => String(issue.path[0]));
};

describe('opening hours', () => {
  it('starts a new restaurant open every day from 10:00 to 22:00', () => {
    const rows = defaultHoursRows();
    expect(rows).toHaveLength(7);
    expect(
      rows.every((row) => row.open && row.opensAt === '10:00' && row.closesAt === '22:00'),
    ).toBe(true);
    expect(rows[0].day).toBe('MONDAY');
    expect(rows[6].day).toBe('SUNDAY');
  });

  it('turns the hours of the API into rows (a day without hours is closed) and back', () => {
    const rows = hoursRowsOf([
      { dayOfWeek: 'MONDAY', opensAt: '09:30:00', closesAt: '21:00:00' },
      { dayOfWeek: 'FRIDAY', opensAt: '09:30:00', closesAt: '23:00:00' },
    ]);
    expect(rows.find((row) => row.day === 'MONDAY')).toMatchObject({
      open: true,
      opensAt: '09:30',
      closesAt: '21:00',
    });
    expect(rows.find((row) => row.day === 'TUESDAY')?.open).toBe(false);
    // Only the open days are sent
    expect(hoursOf(rows)).toEqual([
      { dayOfWeek: 'MONDAY', opensAt: '09:30', closesAt: '21:00' },
      { dayOfWeek: 'FRIDAY', opensAt: '09:30', closesAt: '23:00' },
    ]);
  });

  it('says what is wrong with hours that cannot be sent', () => {
    const rows = defaultHoursRows();
    expect(hoursProblem(rows)).toBeNull();
    expect(hoursProblem(rows.map((row) => ({ ...row, open: false })))).toBe(
      'Open on at least one day.',
    );
    expect(hoursProblem([{ ...rows[0], closesAt: '09:00' }, ...rows.slice(1)])).toMatch(
      /close after it opens/,
    );
    expect(hoursProblem([{ ...rows[0], opensAt: '' }, ...rows.slice(1)])).toMatch(
      /close after it opens/,
    );
    // A closed day with odd times does not matter
    expect(
      hoursProblem([{ ...rows[0], open: false, closesAt: '01:00' }, ...rows.slice(1)]),
    ).toBeNull();
  });
});

describe('the restaurant form rules (the same as restaurant-service)', () => {
  it('accepts a complete form', () => {
    expect(errorsOf(valid)).toEqual([]);
  });

  it('asks for what is missing', () => {
    expect(errorsOf(EMPTY_RESTAURANT_FORM)).toEqual(
      expect.arrayContaining([
        'name',
        'cuisines',
        'phone',
        'email',
        'line1',
        'city',
        'state',
        'postalCode',
        'legalBusinessName',
        'foodLicenseNumber',
        'foodLicenseUrl',
      ]),
    );
  });

  it('checks the phone, e-mail, postal code and licence address', () => {
    expect(errorsOf({ ...valid, phone: '12345' })).toEqual(['phone']);
    expect(errorsOf({ ...valid, phone: '98765 43210' })).toEqual(['phone']);
    expect(errorsOf({ ...valid, email: 'not-an-email' })).toEqual(['email']);
    expect(errorsOf({ ...valid, postalCode: '56' })).toEqual(['postalCode']);
    expect(errorsOf({ ...valid, foodLicenseUrl: 'ftp://files/licence.pdf' })).toEqual([
      'foodLicenseUrl',
    ]);
    expect(errorsOf({ ...valid, foodLicenseUrl: 'licence.pdf' })).toEqual(['foodLicenseUrl']);
  });

  it("allows one to five cuisines made of letters, spaces and & ' -", () => {
    expect(cuisinesOf(' Biryani , ,North Indian ')).toEqual(['Biryani', 'North Indian']);
    expect(
      errorsOf({ ...valid, cuisines: 'Biryani, Fast Food, Tex-Mex, Pan Asian & more, Thai' }),
    ).toEqual([]);
    expect(errorsOf({ ...valid, cuisines: 'A, B, C, D, E, F' })).toEqual(['cuisines']);
    expect(errorsOf({ ...valid, cuisines: 'Biryani, 4Pizza' })).toEqual(['cuisines']);
    expect(errorsOf({ ...valid, cuisines: ' , ' })).toEqual(['cuisines']);
  });

  it('keeps the coordinates optional and within range', () => {
    expect(errorsOf({ ...valid, latitude: '12.97', longitude: '77.59' })).toEqual([]);
    expect(errorsOf({ ...valid, latitude: '91' })).toEqual(['latitude']);
    expect(errorsOf({ ...valid, longitude: '-181' })).toEqual(['longitude']);
    expect(errorsOf({ ...valid, latitude: 'north' })).toEqual(['latitude']);
  });
});

describe('the request', () => {
  const hours = [{ dayOfWeek: 'MONDAY' as const, opensAt: '10:00', closesAt: '22:00' }];

  it('is built from the form: cuisines split, empty optional fields null, numbers parsed', () => {
    const request = requestOf(
      {
        ...valid,
        description: '',
        line2: '',
        taxRegistrationNumber: '',
        latitude: '12.97',
        longitude: '',
      },
      hours,
    );
    expect(request).toMatchObject({
      name: 'Spice Garden',
      description: null,
      cuisines: ['Biryani', 'North Indian'],
      taxRegistrationNumber: null,
      address: { line2: null, latitude: 12.97, longitude: null, city: 'Bengaluru' },
      documents: [{ type: 'FOOD_LICENSE', url: 'https://docs.example.com/fssai.pdf' }],
      operatingHours: hours,
    });
  });

  it('keeps the documents the form does not edit, and replaces the old licence', () => {
    const request = requestOf(valid, hours, [
      { type: 'FOOD_LICENSE', url: 'https://old.example/licence.pdf' },
      { type: 'TAX_REGISTRATION', url: 'https://docs.example.com/gst.pdf' },
    ]);
    expect(request.documents).toEqual([
      { type: 'FOOD_LICENSE', url: 'https://docs.example.com/fssai.pdf' },
      { type: 'TAX_REGISTRATION', url: 'https://docs.example.com/gst.pdf' },
    ]);
  });

  it('starts the form from a restaurant the owner already has', () => {
    const details = {
      name: 'QA Test Kitchen',
      description: null,
      cuisines: ['North Indian', 'Biryani'],
      phone: '9876500003',
      email: 'qa@example.com',
      address: {
        line1: '12 Test Street',
        line2: null,
        city: 'Bengaluru',
        state: 'Karnataka',
        postalCode: '560001',
        country: 'India',
        latitude: 12.9716,
        longitude: null,
      },
      timeZone: 'Asia/Kolkata',
      legalBusinessName: 'QA Pvt Ltd',
      taxRegistrationNumber: null,
      foodLicenseNumber: '123',
      documents: [{ type: 'FOOD_LICENSE', url: 'https://docs.example.com/l.pdf', submittedAt: '' }],
    } as unknown as RestaurantDetails;

    expect(formValuesOf(details)).toMatchObject({
      name: 'QA Test Kitchen',
      description: '',
      cuisines: 'North Indian, Biryani',
      latitude: '12.9716',
      longitude: '',
      taxRegistrationNumber: '',
      foodLicenseUrl: 'https://docs.example.com/l.pdf',
    });
  });
});
