/** catalog-service: menu items ("foods") and categories. */

/**
 * Whether an item is listed. ACTIVE: on the menu; DISABLED: hidden by the owner (who can show it again);
 * BLOCKED: hidden by a platform admin (only an admin can release it).
 */
export const MENU_ITEM_STATUSES = ['ACTIVE', 'DISABLED', 'BLOCKED'] as const;
export type MenuItemStatus = (typeof MENU_ITEM_STATUSES)[number];

export type FoodType = 'VEG' | 'NON_VEG' | 'EGG';

/** A menu item (MenuItemResponse). `available` = in stock today; separate from `status`. */
export interface MenuItem {
  id: number;
  restaurantId: number;
  categoryId: number;
  categoryName: string;
  name: string;
  description: string | null;
  price: number;
  foodType: FoodType;
  imageUrl: string | null;
  available: boolean;
  status: MenuItemStatus;
  createdAt: string;
  updatedAt: string;
  version: number;
  /** Only in the admin list; absent or null elsewhere */
  restaurantName?: string | null;
  /** What the dish is made of (free text, up to 1000 characters); null or absent when not added */
  ingredients?: string | null;
  /** Nutrition facts (free text, up to 1000 characters); null or absent when not added */
  nutritionInfo?: string | null;
}

/** ACTIVE: shown to customers and can be given to dishes. INACTIVE: hidden, and cannot be given to dishes. */
export const CATEGORY_STATUSES = ['ACTIVE', 'INACTIVE'] as const;
export type CategoryStatus = (typeof CATEGORY_STATUSES)[number];

/** A food category (CategoryResponse); the admin list includes how many items use it. */
export interface Category {
  id: number;
  name: string;
  description: string | null;
  imageUrl: string | null;
  /** 1 = first in menus */
  displayOrder: number;
  status: CategoryStatus;
  menuItemCount: number;
  createdAt: string;
  updatedAt: string;
  version: number;
}

/** A category as sent when creating or renaming it (CategoryRequest): a full replacement. */
export interface CategoryRequest {
  name: string;
  description: string | null;
  imageUrl: string | null;
}

/** The food list's filters and order. */
export interface MenuItemFilters {
  /** Item name contains (case-insensitive) */
  search?: string;
  restaurantId?: number;
  categoryId?: number;
  status?: MenuItemStatus;
  /** true = in stock, false = sold out */
  available?: boolean;
  /** 'updatedAt,desc', 'price,desc', ... (fields catalog-service allows) */
  sort: string;
}

/** A new menu item (MenuItemCreateRequest). */
export interface MenuItemCreateRequest {
  restaurantId: number;
  name: string;
  description: string | null;
  /** INR, 2 decimals */
  price: number;
  categoryId: number;
  foodType: FoodType;
  imageUrl: string | null;
  /** null or absent: none */
  ingredients?: string | null;
  /** null or absent: none */
  nutritionInfo?: string | null;
}

/** A menu item as sent when editing it: a full replacement; `version` is the one the admin saw (409 if stale). */
export type MenuItemUpdateRequest = Omit<MenuItemCreateRequest, 'restaurantId'> & {
  version: number;
};

/** How many of a category's dishes can be ordered now (CategoryStatsResponse). */
export interface CategoryStat {
  categoryId: number;
  categoryName: string;
  totalItems: number;
  orderableItems: number;
  /** 0..100 */
  orderablePercent: number;
}
