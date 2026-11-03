import { Navigate, Outlet, useLocation } from 'react-router';

import { useSignedOutHere } from '@/hooks/useSignedOutHere';
import { PartnerLayout } from '@/layouts/partner/PartnerLayout';
import { useAppSelector } from '@/store/hooks';
import { selectPartner } from '@/store/slices/sessionSlice';
import { PARTNER_ROUTES, partnerRouteTo } from '@/utils/constants';

/**
 * The partner pages inside their frame: a visitor (or a signed-in customer, owner or admin: selectPartner is null for
 * any account but a delivery partner) is sent to the partner "Sign in" first and comes back to the same page
 * afterwards. When the partner signs out while on a page, the sign-in is a plain one: the next person to sign in does
 * not land on the page the last partner left. Not security (the services check every request); only guidance.
 */
export function RequirePartner() {
  const partner = useAppSelector(selectPartner);
  const { pathname, search } = useLocation();
  const signedOutHere = useSignedOutHere(partner);
  return partner ? (
    <PartnerLayout>
      <Outlet />
    </PartnerLayout>
  ) : (
    <Navigate
      to={signedOutHere ? PARTNER_ROUTES.signIn : partnerRouteTo.signIn(pathname + search)}
      replace
    />
  );
}
