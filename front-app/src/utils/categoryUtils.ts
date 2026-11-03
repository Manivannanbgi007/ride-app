import type { Category, CategoryStatus } from '@/types/catalog';

/**
 * The menu order after moving one category up (-1) or down (+1): every id once, as catalog-service requires.
 * Moving the first one up or the last one down changes nothing.
 */
export function moveCategory(
  ids: readonly number[],
  categoryId: number,
  direction: -1 | 1,
): number[] {
  const order = [...ids];
  const from = order.indexOf(categoryId);
  const to = from + direction;
  if (from < 0 || to < 0 || to >= order.length) {
    return order;
  }
  [order[from], order[to]] = [order[to], order[from]];
  return order;
}

/** The categories whose name contains the text (any letter case) and that have the status, if one is chosen. */
export function filterCategories(
  categories: readonly Category[],
  search: string,
  status: CategoryStatus | undefined,
): Category[] {
  const text = search.trim().toLowerCase();
  return categories.filter(
    (category) =>
      (text === '' || category.name.toLowerCase().includes(text)) &&
      (status === undefined || category.status === status),
  );
}
