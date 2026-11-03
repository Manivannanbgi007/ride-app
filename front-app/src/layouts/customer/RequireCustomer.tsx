import { Navigate, Outlet, useLocation } from 'react-router';

import { useSignedOutHere } from '@/hooks/useSignedOutHere';
import { useAppSelector } from '@/store/hooks';
import { selectCustomer } from '@/store/slices/sessionSlice';
import { CUSTOMER_ROUTES, customerRouteTo } from '@/utils/constants';

/**
 * Pages of a signed-in customer (cart, checkout, orders, addresses): a visitor is sent to "Sign in" first and
 * comes back to the same page afterwards. When the customer signs out while on one of them, they go to the
 * storefront's home, where "Sign out" leads from every other page too (no ?returnTo= in the address). Not security
 * (the services check every request); only guidance.
 */
export function RequireCustomer() {
  const customer = useAppSelector(selectCustomer);
  const { pathname, search } = useLocation();
  const signedOutHere = useSignedOutHere(customer);
  // selectCustomer is null for anything but a customer account, so a saved sign-in of another role is a visitor
  return customer ? (
    <Outlet />
  ) : (
    <Navigate
      to={signedOutHere ? CUSTOMER_ROUTES.home : customerRouteTo.signIn(pathname + search)}
      replace
    />
  );
}
