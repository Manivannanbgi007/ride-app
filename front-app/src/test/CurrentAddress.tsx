import { useLocation } from 'react-router';

/** The router's address (path and query), for tests that check where a page led: getByTestId('address'). */
export function CurrentAddress() {
  const { pathname, search } = useLocation();
  return <output data-testid="address">{pathname + search}</output>;
}
