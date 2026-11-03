import { Store } from 'lucide-react';
import { useState, type ReactNode } from 'react';
import { Link } from 'react-router';

import { buttonStyles } from '@/components/common/Button';
import { EmptyState } from '@/components/common/EmptyState';
import { ErrorMessage } from '@/components/common/ErrorMessage';
import { PageLoader } from '@/components/common/Loader';
import { useMyRestaurants } from '@/hooks/api/ownerHooks';
import {
  OwnerRestaurantContext,
  OwnerRestaurantsContext,
  useOwnerRestaurants,
} from '@/layouts/owner/ownerRestaurantContext';
import { OWNER_ROUTES } from '@/utils/constants';

const STORAGE_KEY = 'front-app.owner.restaurant';

function savedRestaurantId(): number | null {
  try {
    const saved = Number(sessionStorage.getItem(STORAGE_KEY));
    return Number.isInteger(saved) && saved > 0 ? saved : null;
  } catch {
    // Storage blocked: the first restaurant is shown
    return null;
  }
}

function saveRestaurantId(restaurantId: number): void {
  try {
    sessionStorage.setItem(STORAGE_KEY, String(restaurantId));
  } catch {
    // Without storage the choice lasts until the page is reloaded
  }
}

/** Loads the owner's restaurants and remembers which one the pages show (kept for the browser tab). */
export function OwnerRestaurantsProvider({ children }: { children: ReactNode }) {
  const query = useMyRestaurants();
  const [chosenId, setChosenId] = useState<number | null>(savedRestaurantId);

  const restaurants = query.data ?? [];
  // A saved choice that is no longer in the list (another account, a removed restaurant) falls back to the first
  const restaurant =
    restaurants.find((candidate) => candidate.id === chosenId) ?? restaurants[0] ?? null;

  return (
    <OwnerRestaurantsContext.Provider
      value={{
        isPending: query.isPending,
        isFetching: query.isFetching,
        error: query.error,
        retry: () => void query.refetch(),
        restaurants,
        restaurant,
        select: (restaurantId) => {
          saveRestaurantId(restaurantId);
          setChosenId(restaurantId);
        },
      }}
    >
      {children}
    </OwnerRestaurantsContext.Provider>
  );
}

/**
 * Shows the owner's pages once there is a restaurant for them to work on; until then (or if the restaurants cannot
 * be loaded, or the owner has none yet) it shows that instead. The header stays, so the owner can always sign out.
 */
export function OwnerRestaurantGate({ children }: { children: ReactNode }) {
  const state = useOwnerRestaurants();

  if (state.isPending) {
    return <PageLoader />;
  }
  if (state.error) {
    return (
      <ErrorMessage
        title="Unable to load your restaurants"
        error={state.error}
        onRetry={state.retry}
        isRetrying={state.isFetching}
      />
    );
  }
  if (!state.restaurant) {
    return (
      <EmptyState
        icon={Store}
        title="You have no restaurant yet"
        message="Register your restaurant: the platform team reviews it before customers can see it."
        action={
          <Link to={OWNER_ROUTES.newRestaurant} className={buttonStyles('primary', 'sm')}>
            Register a restaurant
          </Link>
        }
      />
    );
  }
  return (
    <OwnerRestaurantContext.Provider
      value={{
        restaurants: state.restaurants,
        restaurant: state.restaurant,
        restaurantId: state.restaurant.id,
        select: state.select,
      }}
    >
      {children}
    </OwnerRestaurantContext.Provider>
  );
}
