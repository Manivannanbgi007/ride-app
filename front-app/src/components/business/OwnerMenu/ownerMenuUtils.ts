import type { DefaultValues } from 'react-hook-form';
import type { z } from 'zod';

import { toApiError, type ApiError } from '@/api/apiErrorHandler';
import type { FoodType, MenuItem } from '@/types/catalog';
import type { OwnerMenuItemRequest } from '@/types/owner';
import { foodFormSchema, oneOf, toIdFilter } from '@/utils/validationUtils';

/** Words the owner's menu screens use for what goes wrong (the shared ERROR_MESSAGES are not changed for this). */
export const OWNER_MENU_MESSAGES = {
  LOAD_FAILED: 'Unable to load your menu',
  CATEGORIES_FAILED: 'The categories could not be loaded. Close this window and try again.',
} as const;

/**
 * How many dishes one page shows (the list is cut into pages in the browser: the API sends it all at once).
 * Twelve fill whole rows of the card grid: two, three or four cards wide.
 */
export const OWNER_MENU_PAGE_SIZE = 12;

/**
 * Where a dish stands for customers. The two switches of a dish (in stock today, and listed) are folded into one
 * word, because a hidden dish is hidden whether or not it is in stock.
 *   listed:  on the menu and can be ordered
 *   soldOut: on the menu, marked "sold out" (cannot be ordered today)
 *   hidden:  not on the menu (hidden by the owner, or blocked by an admin)
 */
export const MENU_STATES = ['listed', 'soldOut', 'hidden'] as const;
export type MenuState = (typeof MENU_STATES)[number];

export const MENU_STATE_LABELS: Record<MenuState, string> = {
  listed: 'On the menu',
  soldOut: 'Sold out',
  hidden: 'Hidden',
};

export function menuStateOf(item: Pick<MenuItem, 'status' | 'available'>): MenuState {
  if (item.status !== 'ACTIVE') {
    return 'hidden';
  }
  return item.available ? 'listed' : 'soldOut';
}

/** Only the owner's own hiding can be undone by the owner; a dish blocked by an admin stays hidden. */
export function canOwnerToggleVisibility(item: Pick<MenuItem, 'status'>): boolean {
  return item.status !== 'BLOCKED';
}

/** The filters of the menu page; a missing one means "all". */
export interface OwnerMenuFilters {
  /** Part of the name (any case) */
  search?: string;
  categoryId?: number;
  foodType?: FoodType;
  state?: MenuState;
}

export const FOOD_TYPES: readonly FoodType[] = ['VEG', 'NON_VEG', 'EGG'];

/** The address parameters the filters live in (?search=...&category=3&type=VEG&status=soldOut&sort=price&page=2). */
export const MENU_QUERY_KEYS = ['search', 'category', 'type', 'status', 'sort', 'page'] as const;

/** The orders the dishes can be listed in; the first is the default (the order of a printed menu). */
export const MENU_SORTS = ['name', 'price', 'price-desc', 'updated'] as const;
export type MenuSort = (typeof MENU_SORTS)[number];

export const MENU_SORT_LABELS: Record<MenuSort, string> = {
  name: 'Name, A to Z',
  price: 'Price, low to high',
  'price-desc': 'Price, high to low',
  updated: 'Last changed first',
};

/** The order named in the address; anything else (missing, old link, typo) is name order. */
export function menuSortOf(value: string | undefined): MenuSort {
  return MENU_SORTS.find((sort) => sort === value) ?? 'name';
}

const byName = (a: MenuItem, b: MenuItem) =>
  a.name.localeCompare(b.name, undefined, { sensitivity: 'base' });

/** The dishes in the chosen order; dishes that tie (same price) stay in name order. A new list is returned. */
export function sortMenuItems(items: readonly MenuItem[], sort: MenuSort): MenuItem[] {
  const compare: Record<MenuSort, (a: MenuItem, b: MenuItem) => number> = {
    name: byName,
    price: (a, b) => a.price - b.price || byName(a, b),
    'price-desc': (a, b) => b.price - a.price || byName(a, b),
    updated: (a, b) => b.updatedAt.localeCompare(a.updatedAt) || byName(a, b),
  };
  return [...items].sort(compare[sort]);
}

/**
 * How many dishes stand where, among the dishes the other filters (name, category, food type) let through: the
 * counts of the status chips, so each says how many dishes choosing it would show. `blocked` is part of `hidden`.
 */
export function menuStateCounts(
  items: readonly MenuItem[],
  filters: OwnerMenuFilters,
): Record<MenuState | 'all' | 'blocked', number> {
  const counts = { all: 0, listed: 0, soldOut: 0, hidden: 0, blocked: 0 };
  for (const item of filterMenuItems(items, { ...filters, state: undefined })) {
    counts.all += 1;
    counts[menuStateOf(item)] += 1;
    if (item.status === 'BLOCKED') {
      counts.blocked += 1;
    }
  }
  return counts;
}

/** The filters from what the address says; anything that is not a known value is ignored (no filter). */
export function filtersFromParams(
  params: Partial<Record<(typeof MENU_QUERY_KEYS)[number], string>>,
): OwnerMenuFilters {
  return {
    search: params.search,
    categoryId: toIdFilter(params.category ?? ''),
    foodType: oneOf(params.type ?? '', FOOD_TYPES),
    state: oneOf(params.status ?? '', MENU_STATES),
  };
}

export function hasMenuFilters(filters: OwnerMenuFilters): boolean {
  return Object.values(filters).some((value) => value !== undefined && value !== '');
}

/** The dishes that match every filter, in name order (the order of a printed menu). */
export function filterMenuItems(items: readonly MenuItem[], filters: OwnerMenuFilters): MenuItem[] {
  const search = filters.search?.trim().toLowerCase();
  return items
    .filter(
      (item) =>
        (!search || item.name.toLowerCase().includes(search)) &&
        (filters.categoryId === undefined || item.categoryId === filters.categoryId) &&
        (filters.foodType === undefined || item.foodType === filters.foodType) &&
        (filters.state === undefined || menuStateOf(item) === filters.state),
    )
    .sort(byName);
}

/** The categories the restaurant's dishes are in (not every category of the platform), by name. */
export function categoryOptionsOf(items: readonly MenuItem[]): { value: string; label: string }[] {
  const names = new Map<number, string>();
  for (const item of items) {
    names.set(item.categoryId, item.categoryName);
  }
  return [...names]
    .map(([id, name]) => ({ value: String(id), label: name }))
    .sort((a, b) => a.label.localeCompare(b.label, undefined, { sensitivity: 'base' }));
}

/** "1 dish", "12 dishes"; with a filter: "3 of 12 dishes". */
export function dishCountText(shown: number, total: number): string {
  const noun = total === 1 ? 'dish' : 'dishes';
  return shown === total ? `${total} ${noun}` : `${shown} of ${total} ${noun}`;
}

export interface MenuPage<T> {
  rows: T[];
  /** Zero-based; moved back to the last page when the list got shorter than the address says */
  page: number;
  totalPages: number;
}

export function paginate<T>(
  items: readonly T[],
  page: number,
  size = OWNER_MENU_PAGE_SIZE,
): MenuPage<T> {
  const totalPages = Math.max(1, Math.ceil(items.length / size));
  const current = Math.min(Math.max(page, 0), totalPages - 1);
  return { rows: items.slice(current * size, (current + 1) * size), page: current, totalPages };
}

/**
 * A 409 on a save is "the dish changed since you opened it" (its version is old), unless the server says the
 * name is taken: that one is the owner's to fix in the form, and reloading the menu would not help.
 */
export function isStaleVersion(error: unknown): boolean {
  const apiError = toApiError(error);
  return apiError.status === 409 && !/already|exists|duplicate|taken/i.test(apiError.userMessage);
}

/** The server's message for a stale save, and what to do next (the page has reloaded the menu meanwhile). */
export function staleMessageOf(error: ApiError): string {
  return `${error.userMessage} The menu has been reloaded: open the dish again.`;
}

/**
 * The dish form: the admin's food form (same fields, same limits), without the restaurant: it is the one in the
 * address. Everything is text, as typed; toMenuItemRequest turns it into what the API takes.
 */
export const ownerMenuFormSchema = foodFormSchema.omit({ restaurantId: true });
export type OwnerMenuForm = z.infer<typeof ownerMenuFormSchema>;

/** "Edit price": only the price. */
export const priceFormSchema = foodFormSchema.pick({ price: true });
export type PriceForm = z.infer<typeof priceFormSchema>;

/** The form's starting values: the dish's own when editing, empty when adding. */
export function menuFormDefaults(item?: MenuItem): DefaultValues<OwnerMenuForm> {
  return {
    categoryId: item ? String(item.categoryId) : '',
    name: item?.name ?? '',
    description: item?.description ?? '',
    price: item ? item.price.toFixed(2) : '',
    foodType: item?.foodType,
    imageUrl: item?.imageUrl ?? '',
    ingredients: item?.ingredients ?? '',
    nutritionInfo: item?.nutritionInfo ?? '',
  };
}

/** The form's text as the API wants it: numbers as numbers, an empty optional field as null. */
export function toMenuItemRequest(values: OwnerMenuForm): OwnerMenuItemRequest {
  const orNull = (text: string) => (text === '' ? null : text);
  return {
    name: values.name,
    description: orNull(values.description),
    price: Number(values.price),
    categoryId: Number(values.categoryId),
    foodType: values.foodType,
    imageUrl: orNull(values.imageUrl),
    ingredients: orNull(values.ingredients),
    nutritionInfo: orNull(values.nutritionInfo),
  };
}
