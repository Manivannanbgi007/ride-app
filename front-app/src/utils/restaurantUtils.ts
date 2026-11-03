import {
  RESTAURANT_ACTIONS,
  type RestaurantAction,
  type RestaurantStatus,
} from '@/types/restaurant';

/** The admin actions allowed from a status (the same rules restaurant-service applies). */
export function allowedRestaurantActions(status: RestaurantStatus): RestaurantAction[] {
  return (Object.keys(RESTAURANT_ACTIONS) as RestaurantAction[]).filter((action) =>
    (RESTAURANT_ACTIONS[action].from as readonly RestaurantStatus[]).includes(status),
  );
}
