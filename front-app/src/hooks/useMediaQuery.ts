import { useCallback, useSyncExternalStore } from 'react';

/** Wide screens, from Tailwind's `lg` breakpoint (64rem): the sidebar is part of the page there. */
export const WIDE_SCREEN = '(min-width: 64rem)';

/**
 * Whether a CSS media query matches right now, following changes (a window resized, a phone turned). Without
 * matchMedia (tests, very old browsers) it answers false.
 */
export function useMediaQuery(query: string): boolean {
  const subscribe = useCallback(
    (onChange: () => void) => {
      if (typeof window.matchMedia !== 'function') {
        return () => undefined;
      }
      const list = window.matchMedia(query);
      list.addEventListener('change', onChange);
      return () => list.removeEventListener('change', onChange);
    },
    [query],
  );
  return useSyncExternalStore(
    subscribe,
    () => typeof window.matchMedia === 'function' && window.matchMedia(query).matches,
    () => false,
  );
}
