import { Link, useSearchParams } from 'react-router';

import { StaffSignIn } from '@/components/business/Auth/StaffSignIn';
import { useAdminSignIn } from '@/hooks/api/authHooks';
import { useAppSelector } from '@/store/hooks';
import { selectAdminUser } from '@/store/slices/sessionSlice';
import { CUSTOMER_ROUTES, ROUTES } from '@/utils/constants';
import { safeAdminReturnTo } from '@/utils/validationUtils';

/**
 * The administrator's "Sign in" (user-service checks the password; only a platform administrator account is let
 * in). Afterwards the admin goes back to the page that asked for the sign-in.
 */
export default function AdminSignInPage() {
  const [searchParams] = useSearchParams();
  const signIn = useAdminSignIn();
  const signedIn = useAppSelector(selectAdminUser) !== null;

  return (
    <StaffSignIn
      audience="admin"
      title="Admin sign in"
      subtitle="Orders, restaurants, payments and everything that keeps the platform running. Administrator accounts only."
      emailPlaceholder="admin@yourcompany.com"
      signIn={signIn}
      isSignedIn={signedIn}
      home={ROUTES.dashboard}
      returnTo={safeAdminReturnTo(searchParams.get('returnTo'))}
      footer={
        <>
          Looking for food?{' '}
          <Link to={CUSTOMER_ROUTES.home} className="font-semibold text-brand-text hover:underline">
            Go to the storefront
          </Link>
        </>
      }
    />
  );
}
