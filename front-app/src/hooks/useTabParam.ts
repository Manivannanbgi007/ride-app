import { useSearchParams } from 'react-router';

/**
 * The open tab, kept in the address (?tab=partners): a link can open a page on a tab, and the back button returns
 * to the tab the admin was on. An unknown or missing value opens the first tab.
 */
export function useTabParam<T extends string>(tabIds: readonly T[]): [T, (tab: T) => void] {
  const [searchParams, setSearchParams] = useSearchParams();
  const requested = searchParams.get('tab');
  const active = tabIds.find((id) => id === requested) ?? tabIds[0];

  const setActive = (tab: T) => {
    setSearchParams(
      (current) => {
        const next = new URLSearchParams(current);
        next.set('tab', tab);
        return next;
      },
      { replace: true },
    );
  };
  return [active, setActive];
}
