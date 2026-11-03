import { useSearchParams } from 'react-router';

/**
 * Filters kept in the address (?city=Pune&page=2) instead of Redux: a customer can share or bookmark a list, and
 * the back button returns to the previous filters. Changing any filter goes back to the first page.
 *
 *   const [params, setParams] = useQueryParams(['search', 'city', 'page']);
 *   setParams({ city: 'Pune' });   // ?city=Pune (page removed)
 */
export function useQueryParams<K extends string>(
  keys: readonly K[],
): [Partial<Record<K, string>>, (changes: Partial<Record<K, string | undefined>>) => void] {
  const [searchParams, setSearchParams] = useSearchParams();
  const values: Partial<Record<K, string>> = {};
  for (const key of keys) {
    const value = searchParams.get(key)?.trim();
    if (value) {
      values[key] = value;
    }
  }

  const update = (changes: Partial<Record<K, string | undefined>>) => {
    setSearchParams(
      (current) => {
        const next = new URLSearchParams(current);
        if (!('page' in changes)) {
          next.delete('page');
        }
        for (const [key, value] of Object.entries(changes) as [K, string | undefined][]) {
          if (value?.trim()) {
            next.set(key, value.trim());
          } else {
            next.delete(key);
          }
        }
        return next;
      },
      { replace: true },
    );
  };
  return [values, update];
}

/** '2' -> 1 (pages count from 0 in the APIs, from 1 in the address); anything else -> 0. */
export function pageFromParam(value: string | undefined): number {
  const page = Number(value);
  return Number.isInteger(page) && page >= 1 ? page - 1 : 0;
}

/** 0 -> undefined (no ?page= on the first page), 1 -> '2'. */
export function pageToParam(page: number): string | undefined {
  return page > 0 ? String(page + 1) : undefined;
}
