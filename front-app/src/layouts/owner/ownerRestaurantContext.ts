import { createContext, useContext } from 'react';

import type { RestaurantSummary } from '@/types/restaurant';

/** The owner's restaurants as the frame knows them: the header's selector reads this, even before they are loaded. */
export interface OwnerRestaurantsState {
  isPending: boolean;
  isFetching: boolean;
  error: unknown;
  retry: () => void;
  restaurants: readonly RestaurantSummary[];
  /** The restaurant the pages show; null while loading, on an error, or when the owner has none */
  restaurant: RestaurantSummary | null;
  select: (restaurantId: number) => void;
}

export const OwnerRestaurantsContext = createContext<OwnerRestaurantsState | null>(null);

export function useOwnerRestaurants(): OwnerRestaurantsState {
  const state = useContext(OwnerRestaurantsContext);
  if (!state) {
    throw new Error('The owner frame needs an <OwnerRestaurantsProvider> around it');
  }
  return state;
}

export interface OwnerRestaurantContextValue {
  /** All the owner's restaurants (at least one: the pages are not shown otherwise) */
  restaurants: readonly RestaurantSummary[];
  /** The restaurant the pages show */
  restaurant: RestaurantSummary;
  restaurantId: number;
  select: (restaurantId: number) => void;
}

export const OwnerRestaurantContext = createContext<OwnerRestaurantContextValue | null>(null);

/**
 * The restaurant an owner page works on: `const { restaurantId, restaurant } = useOwnerRestaurant();`.
 * An owner may have several restaurants; the header's selector decides which one the pages show. The pages are
 * only shown once there is one (see OwnerRestaurantGate), so it is never missing here.
 */
export function useOwnerRestaurant(): OwnerRestaurantContextValue {
  const value = useContext(OwnerRestaurantContext);
  if (!value) {
    throw new Error('Owner pages need an <OwnerRestaurantGate> around them');
  }
  return value;
}
