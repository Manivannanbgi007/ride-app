import { StaffRegister } from '@/components/business/Auth/StaffRegister';
import { useOwnerRegister } from '@/hooks/api/authHooks';
import { useAppSelector } from '@/store/hooks';
import { selectOwner } from '@/store/slices/sessionSlice';
import { OWNER_ROUTES } from '@/utils/constants';
import { registerFormSchema } from '@/utils/validationUtils';

/**
 * "Become a restaurant partner": a restaurant owner account, signed in straight away, then on to "Register a
 * restaurant". The restaurant starts as waiting for approval: the platform team reviews it in the admin area
 * (Restaurants: approve, then activate) before customers can see it.
 */
export default function OwnerRegisterPage() {
  const registration = useOwnerRegister();
  const signedIn = useAppSelector(selectOwner) !== null;

  return (
    <StaffRegister
      audience="owner"
      title="Become a restaurant partner"
      subtitle="Create your owner account, then register your restaurant for review."
      schema={registerFormSchema}
      phoneHint="Optional: the platform team may call you about your restaurant's review."
      submitLabel="Create owner account"
      registration={registration}
      isSignedIn={signedIn}
      home={OWNER_ROUTES.home}
      next={OWNER_ROUTES.newRestaurant}
      welcome={(firstName) =>
        `Welcome, ${firstName}! Now register your restaurant: the platform team reviews it.`
      }
      signInYourself="Your owner account is ready: please sign in."
      signInRoute={OWNER_ROUTES.signIn}
      signInPrompt="Already have an owner account?"
    />
  );
}
