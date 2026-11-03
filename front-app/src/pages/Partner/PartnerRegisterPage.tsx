import { StaffRegister } from '@/components/business/Auth/StaffRegister';
import { usePartnerRegister } from '@/hooks/api/authHooks';
import { useAppSelector } from '@/store/hooks';
import { selectPartner } from '@/store/slices/sessionSlice';
import { PARTNER_ROUTES } from '@/utils/constants';
import { partnerRegisterFormSchema } from '@/utils/validationUtils';

/**
 * "Become a delivery partner": a delivery partner account, signed in straight away. The partner then adds their
 * vehicle and city (the profile page) before going online.
 */
export default function PartnerRegisterPage() {
  const registration = usePartnerRegister();
  const signedIn = useAppSelector(selectPartner) !== null;

  return (
    <StaffRegister
      audience="partner"
      title="Become a delivery partner"
      subtitle="Deliver for the restaurants of your city, whenever you choose to go online."
      schema={partnerRegisterFormSchema}
      phoneHint="Customers call you about their delivery."
      submitLabel="Create partner account"
      registration={registration}
      isSignedIn={signedIn}
      home={PARTNER_ROUTES.home}
      next={PARTNER_ROUTES.profile}
      welcome={(firstName) =>
        `Welcome, ${firstName}! Add your vehicle and city to start delivering.`
      }
      signInYourself="Your partner account is ready: please sign in."
      signInRoute={PARTNER_ROUTES.signIn}
      signInPrompt="Already a partner?"
    />
  );
}
