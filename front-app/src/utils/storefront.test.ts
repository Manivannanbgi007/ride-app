import { afterEach, describe, expect, it } from 'vitest';

import { pageFromParam, pageToParam } from '@/hooks/useQueryParams';
import type { MenuSection } from '@/types/storefront';
import {
  actingUserOf,
  areaOf,
  loadSignedInCustomer,
  saveSignedInCustomer,
} from '@/utils/sessionUtils';
import { dayOfWeekIn, filterMenu, formatTimeOfDay, hoursByDay } from '@/utils/storefrontUtils';
import { registerFormSchema, safeReturnTo, signInFormSchema } from '@/utils/validationUtils';

const priya = {
  id: 7,
  role: 'CUSTOMER' as const,
  fullName: 'Priya Sharma',
  email: 'priya@example.com',
};

describe('who the application acts as', () => {
  afterEach(() => sessionStorage.clear());

  it('acts as the admin under /admin and as the signed-in customer everywhere else', () => {
    const session = { adminUser: { id: 1, role: 'PLATFORM_ADMIN' as const }, customer: priya };
    expect(areaOf('/admin')).toBe('admin');
    expect(areaOf('/admin/orders/42')).toBe('admin');
    expect(areaOf('/administrator')).toBe('customer');
    expect(areaOf('/restaurants/3')).toBe('customer');
    expect(actingUserOf(session, 'admin')?.id).toBe(1);
    expect(actingUserOf(session, 'customer')?.id).toBe(7);
    expect(actingUserOf({ ...session, customer: null }, 'customer')).toBeNull();
  });

  it('keeps the signed-in customer for the browser tab, and ignores damaged data', () => {
    expect(loadSignedInCustomer()).toBeNull();
    saveSignedInCustomer(priya);
    expect(loadSignedInCustomer()).toEqual(priya);
    saveSignedInCustomer(null);
    expect(loadSignedInCustomer()).toBeNull();

    sessionStorage.setItem('front-app.customer', '{not json');
    expect(loadSignedInCustomer()).toBeNull();
    sessionStorage.setItem('front-app.customer', JSON.stringify({ ...priya, role: 'KING' }));
    expect(loadSignedInCustomer()).toBeNull();
  });
});

describe('opening hours', () => {
  it('writes times the way people read them', () => {
    expect(formatTimeOfDay('05:30:00')).toBe('5:30 am');
    expect(formatTimeOfDay('00:00')).toBe('12:00 am');
    expect(formatTimeOfDay('12:15:00')).toBe('12:15 pm');
    expect(formatTimeOfDay('23:05:00')).toBe('11:05 pm');
  });

  it('lists every day, with its slots or "Closed"', () => {
    const hours = hoursByDay([
      { dayOfWeek: 'MONDAY', opensAt: '11:00:00', closesAt: '15:00:00' },
      { dayOfWeek: 'MONDAY', opensAt: '19:00:00', closesAt: '23:00:00' },
    ]);
    expect(hours.MONDAY).toBe('11:00 am - 3:00 pm, 7:00 pm - 11:00 pm');
    expect(hours.SUNDAY).toBe('Closed');
  });

  it("knows the day in the restaurant's own time zone", () => {
    // Sunday 20:00 in London is already Monday in India
    const moment = new Date('2026-10-11T20:00:00Z');
    expect(dayOfWeekIn('Asia/Kolkata', moment)).toBe('MONDAY');
    expect(dayOfWeekIn('Europe/London', moment)).toBe('SUNDAY');
  });
});

describe('menu filter', () => {
  const sections: MenuSection[] = [
    {
      categoryId: 3,
      categoryName: 'Biryani',
      items: [
        {
          id: 1,
          name: 'Veg Biryani',
          description: null,
          price: 220,
          foodType: 'VEG',
          imageUrl: null,
          available: true,
        },
        {
          id: 2,
          name: 'Chicken Biryani',
          description: 'Hyderabadi',
          price: 299,
          foodType: 'NON_VEG',
          imageUrl: null,
          available: true,
        },
      ],
    },
    {
      categoryId: 7,
      categoryName: 'Desserts',
      items: [
        {
          id: 3,
          name: 'Egg Pudding',
          description: null,
          price: 90,
          foodType: 'EGG',
          imageUrl: null,
          available: false,
        },
      ],
    },
  ];

  it('keeps only matching dishes and drops empty sections', () => {
    expect(filterMenu(sections, { vegOnly: true, text: '' })).toEqual([
      { ...sections[0], items: [sections[0].items[0]] },
    ]);
    expect(filterMenu(sections, { vegOnly: false, text: 'hyderabadi' })[0].items[0].id).toBe(2);
    expect(filterMenu(sections, { vegOnly: false, text: '' })).toEqual(sections);
  });
});

describe('sign-in and register forms', () => {
  it('checks the email and asks for a password', () => {
    expect(
      signInFormSchema.safeParse({ email: ' priya@example.com ', password: 'x' }).success,
    ).toBe(true);
    expect(signInFormSchema.safeParse({ email: 'priya', password: 'x' }).success).toBe(false);
    expect(signInFormSchema.safeParse({ email: 'priya@example.com', password: '' }).success).toBe(
      false,
    );
  });

  it("follows user-service's password and phone rules, and wants the password twice", () => {
    const valid = {
      fullName: 'Priya Sharma',
      email: 'priya@example.com',
      phone: '',
      password: 'Eater1234',
      confirmPassword: 'Eater1234',
    };
    expect(registerFormSchema.safeParse(valid).success).toBe(true);
    expect(registerFormSchema.safeParse({ ...valid, phone: '+919800000007' }).success).toBe(true);
    expect(registerFormSchema.safeParse({ ...valid, phone: '12345' }).success).toBe(false);
    expect(
      registerFormSchema.safeParse({ ...valid, password: 'eatertoo', confirmPassword: 'eatertoo' })
        .success,
    ).toBe(false);
    expect(registerFormSchema.safeParse({ ...valid, confirmPassword: 'Eater12345' }).success).toBe(
      false,
    );
  });

  it('only goes back to customer-area addresses after signing in', () => {
    expect(safeReturnTo('/restaurants/3?tab=menu')).toBe('/restaurants/3?tab=menu');
    expect(safeReturnTo('https://evil.example')).toBeNull();
    expect(safeReturnTo('//evil.example')).toBeNull();
    expect(safeReturnTo('/admin/orders')).toBeNull();
    expect(safeReturnTo(null)).toBeNull();
  });
});

describe('pages in the address', () => {
  it('counts from 1 in the address and from 0 in the APIs', () => {
    expect(pageFromParam(undefined)).toBe(0);
    expect(pageFromParam('3')).toBe(2);
    expect(pageFromParam('abc')).toBe(0);
    expect(pageFromParam('0')).toBe(0);
    expect(pageToParam(0)).toBeUndefined();
    expect(pageToParam(2)).toBe('3');
  });
});
