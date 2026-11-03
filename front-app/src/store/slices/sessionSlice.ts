import { createSlice, type PayloadAction } from '@reduxjs/toolkit';

import type { AdminSession, SignedInUser } from '@/types/user';
import {
  loadSignedInAdmin,
  loadSignedInCustomer,
  loadSignedInOwner,
  loadSignedInPartner,
} from '@/utils/sessionUtils';

/**
 * Who signed in to each area, shared by the whole application (the pages and headers show it). Each area acts as
 * the person who signed in to it, with that area's access token (api/tokenStore.ts, in memory only); which area a
 * page belongs to is decided by its address (see utils/sessionUtils.ts and hooks/useActingUser.ts). Only who they are
 * is kept here and in sessionStorage, never a token.
 */
interface SessionState {
  /** The signed-in admin; kept for the browser tab */
  adminUser: AdminSession | null;
  /** The signed-in customer; kept for the browser tab */
  customer: SignedInUser | null;
  /** The signed-in restaurant owner; kept for the browser tab. Absent in a session built without the owner area. */
  owner?: SignedInUser | null;
  /** The signed-in delivery partner; kept for the browser tab. Absent in a session built without the partner area. */
  partner?: SignedInUser | null;
}

const initialState: SessionState = {
  adminUser: loadSignedInAdmin(),
  customer: loadSignedInCustomer(),
  owner: loadSignedInOwner(),
  partner: loadSignedInPartner(),
};

const sessionSlice = createSlice({
  name: 'session',
  initialState,
  reducers: {
    adminSignedIn(state, action: PayloadAction<SignedInUser>) {
      state.adminUser = action.payload;
    },
    adminSignedOut(state) {
      state.adminUser = null;
    },
    customerSignedIn(state, action: PayloadAction<SignedInUser>) {
      state.customer = action.payload;
    },
    customerSignedOut(state) {
      state.customer = null;
    },
    ownerSignedIn(state, action: PayloadAction<SignedInUser>) {
      state.owner = action.payload;
    },
    ownerSignedOut(state) {
      state.owner = null;
    },
    partnerSignedIn(state, action: PayloadAction<SignedInUser>) {
      state.partner = action.payload;
    },
    partnerSignedOut(state) {
      state.partner = null;
    },
  },
  selectors: {
    // Only a platform administrator account is the admin (the admin sign-in refuses the others)
    selectAdminUser: (state) =>
      state.adminUser?.role === 'PLATFORM_ADMIN' ? state.adminUser : null,
    // Each area is for its own kind of account only (the sign-ins refuse the others): a saved or injected session of
    // another role counts as signed out, so every page (guard, sign-in, header) agrees and none redirects forever
    selectCustomer: (state) => (state.customer?.role === 'CUSTOMER' ? state.customer : null),
    selectOwner: (state) => (state.owner?.role === 'RESTAURANT_ADMIN' ? state.owner : null),
    selectPartner: (state) => (state.partner?.role === 'DELIVERY_PARTNER' ? state.partner : null),
  },
});

export const {
  adminSignedIn,
  adminSignedOut,
  customerSignedIn,
  customerSignedOut,
  ownerSignedIn,
  ownerSignedOut,
  partnerSignedIn,
  partnerSignedOut,
} = sessionSlice.actions;
export const { selectAdminUser, selectCustomer, selectOwner, selectPartner } =
  sessionSlice.selectors;
export default sessionSlice.reducer;
