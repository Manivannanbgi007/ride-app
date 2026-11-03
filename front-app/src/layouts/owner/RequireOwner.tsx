import { Navigate, Outlet, useLocation } from 'react-router';

import { useSignedOutHere } from '@/hooks/useSignedOutHere';
import { OwnerLayout } from '@/layouts/owner/OwnerLayout';
import { useAppSelector } from '@/store/hooks';
import { selectOwner } from '@/store/slices/sessionSlice';
import { OWNER_ROUTES, ownerRouteTo } from '@/utils/constants';

/**
 * The owner pages inside their frame: a visitor (or a signed-in customer or admin: selectOwner is null for any
 * account but a restaurant owner) is sent to the owner "Sign in" first and comes back to the same page afterwards.
 * When the owner signs out while on a page, the sign-in is a plain one: the next person to sign in does not land on
 * the page the last owner left. Not security (the services check every request); only guidance.
 */
export function RequireOwner() {
  const owner = useAppSelector(selectOwner);
  const { pathname, search } = useLocation();
  const signedOutHere = useSignedOutHere(owner);
  return owner ? (
    <OwnerLayout>
      <Outlet />
    </OwnerLayout>
  ) : (
    <Navigate
      to={signedOutHere ? OWNER_ROUTES.signIn : ownerRouteTo.signIn(pathname + search)}
      replace
    />
  );
}
