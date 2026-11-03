import { useEffect, useState } from 'react';

/**
 * The value, but only once it has stopped changing for `delayMs`: a search box asks the server once the user
 * pauses typing, not for every key press.
 */
export function useDebounce<T>(value: T, delayMs = 400): T {
  const [debounced, setDebounced] = useState(value);

  useEffect(() => {
    const timer = window.setTimeout(() => setDebounced(value), delayMs);
    return () => window.clearTimeout(timer);
  }, [value, delayMs]);

  return debounced;
}
