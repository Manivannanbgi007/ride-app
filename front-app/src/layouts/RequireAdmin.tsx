import { Navigate, Outlet, useLocation } from 'react-router';

import { useSignedOutHere } from '@/hooks/useSignedOutHere';
import { useAppSelector } from '@/store/hooks';
import { selectAdminUser } from '@/store/slices/sessionSlice';
import { adminRouteTo, ROUTES } from '@/utils/constants';

/**
 * The admin area's pages: a visitor (or a signed-in customer or owner: selectAdminUser is null for any account but
 * a platform administrator) is sent to the admin "Sign in" first and comes back to the same page afterwards. When
 * the admin signs out while on a page, the sign-in is a plain one: the next person to sign in does not land on the
 * page the last admin left. Not security (the services check every request); only guidance.
 */
export function RequireAdmin() {
  const admin = useAppSelector(selectAdminUser);
  const { pathname, search } = useLocation();
  const signedOutHere = useSignedOutHere(admin);
  if (admin) {
    return <Outlet />;
  }
  return (
    <Navigate to={signedOutHere ? ROUTES.signIn : adminRouteTo.signIn(pathname + search)} replace />
  );
}
