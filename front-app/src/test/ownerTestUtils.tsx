import type { ReactElement } from 'react';
import { Route, Routes } from 'react-router';

import { OwnerRestaurantContext } from '@/layouts/owner/ownerRestaurantContext';
import { makeStore } from '@/store/store';
import { restaurantSummary } from '@/test/fixtures';
import { renderWithProviders } from '@/test/renderWithProviders';
import type { RestaurantSummary } from '@/types/restaurant';
import type { SignedInUser } from '@/types/user';

/** A signed-in restaurant owner (the session of the owner area). */
export const ownerUser: SignedInUser = {
  id: 423,
  role: 'RESTAURANT_ADMIN',
  fullName: 'Test Owner',
  email: 'test.owner@fooddelivery.local',
};

interface OwnerRenderOptions {
  /** The address the test starts at (default: the pattern itself) */
  route?: string;
  /** The restaurant the page works on (default: id 3, "Biryani House") */
  restaurant?: RestaurantSummary;
}

/**
 * Renders an owner page the way the owner area does: signed in as an owner, with a restaurant chosen (the
 * `useOwnerRestaurant()` of the page works), at the address `route` that matches `path` (for pages that read the
 * address, e.g. '/owner/orders/:orderId'). Returns what renderWithProviders returns.
 */
export function renderOwnerPage(
  page: ReactElement,
  path: string,
  {
    route = path,
    restaurant = restaurantSummary({ ownerUserId: ownerUser.id }),
  }: OwnerRenderOptions = {},
) {
  return renderWithProviders(
    <OwnerRestaurantContext.Provider
      value={{
        restaurants: [restaurant],
        restaurant,
        restaurantId: restaurant.id,
        select: () => undefined,
      }}
    >
      <Routes>
        <Route path={path} element={page} />
      </Routes>
    </OwnerRestaurantContext.Provider>,
    {
      route,
      store: makeStore({ session: { adminUser: null, customer: null, owner: ownerUser } }),
    },
  );
}
