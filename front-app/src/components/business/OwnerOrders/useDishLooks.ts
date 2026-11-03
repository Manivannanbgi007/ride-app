import { useMemo } from 'react';

import { useOwnerMenu } from '@/hooks/api/ownerHooks';

/** What a dish looks like on the restaurant's menu: its own picture and its category (whose picture stands in). */
export interface DishLook {
  imageUrl: string | null;
  categoryName: string;
}

/**
 * The pictures of the restaurant's dishes, by menu item, from its menu (orders keep no pictures: a dish looks the way
 * the menu shows it). Empty while the menu loads or when it cannot be loaded: the dishes then show a plain tile.
 */
export function useDishLooks(restaurantId: number): ReadonlyMap<number, DishLook> {
  const menu = useOwnerMenu(restaurantId);
  return useMemo(
    () =>
      new Map(
        (menu.data ?? []).map((dish) => [
          dish.id,
          { imageUrl: dish.imageUrl, categoryName: dish.categoryName },
        ]),
      ),
    [menu.data],
  );
}
