import { Link, useSearchParams } from 'react-router';

import { StaffSignIn } from '@/components/business/Auth/StaffSignIn';
import { useOwnerSignIn } from '@/hooks/api/authHooks';
import { useAppSelector } from '@/store/hooks';
import { selectOwner } from '@/store/slices/sessionSlice';
import { CUSTOMER_ROUTES, OWNER_ROUTES } from '@/utils/constants';
import { safeOwnerReturnTo } from '@/utils/validationUtils';

/**
 * The restaurant owner's "Sign in" (user-service checks the password; only a restaurant owner account is let in).
 * Afterwards the owner goes back to the page that asked for the sign-in.
 */
export default function OwnerSignInPage() {
  const [searchParams] = useSearchParams();
  const signIn = useOwnerSignIn();
  const signedIn = useAppSelector(selectOwner) !== null;

  return (
    <StaffSignIn
      audience="owner"
      title="Restaurant sign in"
      subtitle="Manage your orders, menu and reviews. Restaurant owner accounts only."
      emailPlaceholder="owner@yourrestaurant.com"
      signIn={signIn}
      isSignedIn={signedIn}
      home={OWNER_ROUTES.home}
      returnTo={safeOwnerReturnTo(searchParams.get('returnTo'))}
      alternative={{
        prompt: 'New restaurant?',
        label: 'Create an owner account',
        to: OWNER_ROUTES.register,
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
