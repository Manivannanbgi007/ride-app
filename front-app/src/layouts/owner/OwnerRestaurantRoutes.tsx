import { Outlet } from 'react-router';

import { OwnerRestaurantGate } from '@/layouts/owner/OwnerRestaurantProvider';

/** The owner pages that work on a restaurant: shown once the owner has one (see OwnerRestaurantGate). */
export function OwnerRestaurantRoutes() {
  return (
    <OwnerRestaurantGate>
      <Outlet />
    </OwnerRestaurantGate>
  );
}
