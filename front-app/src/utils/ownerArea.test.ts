import { afterEach, describe, expect, it } from 'vitest';

import { makeStore } from '@/store/store';
import {
  customerSignedIn,
  ownerSignedIn,
  selectCustomer,
  selectOwner,
} from '@/store/slices/sessionSlice';
import {
  actingUserOf,
  areaOf,
  loadSignedInCustomer,
  loadSignedInOwner,
  saveSignedInCustomer,
  saveSignedInOwner,
} from '@/utils/sessionUtils';
import { safeOwnerReturnTo, safeReturnTo } from '@/utils/validationUtils';

const owner = {
  id: 423,
  role: 'RESTAURANT_ADMIN' as const,
  fullName: 'Test Owner',
  email: 'test.owner@fooddelivery.local',
};
const customer = {
  id: 7,
  role: 'CUSTOMER' as const,
  fullName: 'Priya Sharma',
  email: 'priya@example.com',
};
const admin = { id: 1, role: 'PLATFORM_ADMIN' as const };

describe('the three areas', () => {
  afterEach(() => sessionStorage.clear());

  it('tells the owner area from the admin and customer areas by the address', () => {
    expect(areaOf('/owner')).toBe('owner');
    expect(areaOf('/owner/orders/5')).toBe('owner');
    expect(areaOf('/owner/sign-in')).toBe('owner');
    // Only a whole path segment counts
    expect(areaOf('/ownership')).toBe('customer');
    expect(areaOf('/admin/orders')).toBe('admin');
    expect(areaOf('/customer/orders')).toBe('customer');
  });

  it('acts as the signed-in owner in the owner area, and as nobody when nobody is signed in', () => {
    const session = { adminUser: admin, customer, owner };
    expect(actingUserOf(session, 'owner')?.id).toBe(423);
    expect(actingUserOf(session, 'admin')?.id).toBe(1);
    expect(actingUserOf(session, 'customer')?.id).toBe(7);
    expect(actingUserOf({ adminUser: admin, customer }, 'owner')).toBeNull();
    expect(actingUserOf({ adminUser: admin, customer, owner: null }, 'owner')).toBeNull();
  });

  it('keeps the owner and the customer separately for the browser tab', () => {
    saveSignedInOwner(owner);
    saveSignedInCustomer(customer);
    expect(loadSignedInOwner()).toEqual(owner);
    expect(loadSignedInCustomer()).toEqual(customer);

    saveSignedInOwner(null);
    expect(loadSignedInOwner()).toBeNull();
    expect(loadSignedInCustomer()).toEqual(customer);
  });

  it('ignores a damaged saved owner', () => {
    sessionStorage.setItem('front-app.owner', '{"id":"x"}');
    expect(loadSignedInOwner()).toBeNull();
    sessionStorage.setItem('front-app.owner', 'not json');
    expect(loadSignedInOwner()).toBeNull();
  });
});

describe('who counts as signed in to an area', () => {
  it('lets only an owner account be the signed-in owner, and only a customer account the customer', () => {
    const store = makeStore({ session: { adminUser: null, customer: null, owner: null } });
    // Another role in the owner slot (a damaged or tampered session) counts as signed out
    store.dispatch(ownerSignedIn({ ...customer }));
    expect(selectOwner(store.getState())).toBeNull();
    store.dispatch(ownerSignedIn(owner));
    expect(selectOwner(store.getState())?.id).toBe(423);

    store.dispatch(customerSignedIn({ ...owner }));
    expect(selectCustomer(store.getState())).toBeNull();
    store.dispatch(customerSignedIn(customer));
    expect(selectCustomer(store.getState())?.id).toBe(7);
  });
});

describe('where to go after signing in', () => {
  it('sends an owner back only into the owner area', () => {
    expect(safeOwnerReturnTo('/owner/orders')).toBe('/owner/orders');
    expect(safeOwnerReturnTo('/owner')).toBe('/owner');
    expect(safeOwnerReturnTo('/owner/sign-in')).toBeNull();
    expect(safeOwnerReturnTo('/owner/register')).toBeNull();
    expect(safeOwnerReturnTo('/customer/cart')).toBeNull();
    expect(safeOwnerReturnTo('/admin')).toBeNull();
    expect(safeOwnerReturnTo('//evil.example/owner')).toBeNull();
    expect(safeOwnerReturnTo('https://evil.example')).toBeNull();
    expect(safeOwnerReturnTo(null)).toBeNull();
  });

  it('never sends a customer into the owner or admin area', () => {
    expect(safeReturnTo('/customer/cart')).toBe('/customer/cart');
    expect(safeReturnTo('/owner/orders')).toBeNull();
    expect(safeReturnTo('/owner')).toBeNull();
    expect(safeReturnTo('/admin/orders')).toBeNull();
    // Only a whole path segment is another area
    expect(safeReturnTo('/ownership')).toBe('/ownership');
  });
});
