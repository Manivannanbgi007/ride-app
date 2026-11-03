/**
 * The object without its empty values (undefined, null, ''). Filters are stored like this, so "no status chosen"
 * and "status not set" are the same thing (and the same React Query key).
 */
export function withoutEmptyValues<T extends object>(value: T): T {
  return Object.fromEntries(
    Object.entries(value).filter(
      ([, entry]) => entry !== undefined && entry !== null && entry !== '',
    ),
  ) as T;
}
