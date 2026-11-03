import { Link, useSearchParams } from 'react-router';

import { StaffSignIn } from '@/components/business/Auth/StaffSignIn';
import { usePartnerSignIn } from '@/hooks/api/authHooks';
import { useAppSelector } from '@/store/hooks';
import { selectPartner } from '@/store/slices/sessionSlice';
import { CUSTOMER_ROUTES, PARTNER_ROUTES } from '@/utils/constants';
import { safePartnerReturnTo } from '@/utils/validationUtils';

/**
 * The delivery partner's "Sign in" (user-service checks the password; only a delivery partner account is let in).
 * Afterwards the partner goes back to the page that asked for the sign-in.
 */
export default function PartnerSignInPage() {
  const [searchParams] = useSearchParams();
  const signIn = usePartnerSignIn();
  const signedIn = useAppSelector(selectPartner) !== null;

  return (
    <StaffSignIn
      audience="partner"
      title="Delivery partner sign in"
      subtitle="Go online, take deliveries and see your history. Delivery partner accounts only."
      emailPlaceholder="you@example.com"
      signIn={signIn}
      isSignedIn={signedIn}
      home={PARTNER_ROUTES.home}
      returnTo={safePartnerReturnTo(searchParams.get('returnTo'))}
      alternative={{
        prompt: 'New delivery partner?',
        label: 'Create a partner account',
        to: PARTNER_ROUTES.register,
      }}
      footer={
        <>
          Ordering food?{' '}
          <Link
            to={CUSTOMER_ROUTES.signIn}
            className="font-semibold text-brand-text hover:underline"
          >
            Customer sign in
          </Link>
        </>
      }
    />
  );
}
