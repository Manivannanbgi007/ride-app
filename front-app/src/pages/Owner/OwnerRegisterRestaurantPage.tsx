import { useNavigate } from 'react-router';

import { RestaurantEditor } from '@/components/business/OwnerRestaurant';
import { BackLink } from '@/components/common/BackLink';
import { PageHeader } from '@/components/common/PageHeader';
import { useToast } from '@/context/ToastContext';
import { useRegisterRestaurant } from '@/hooks/api/ownerHooks';
import { OWNER_ROUTES } from '@/utils/constants';
import { defaultHoursRows, EMPTY_RESTAURANT_FORM } from '@/utils/ownerRestaurantUtils';

/**
 * Registering a restaurant. It starts as "waiting for approval": the platform team reviews it, and customers can
 * see it and order from it only once it is approved and live.
 */
export default function OwnerRegisterRestaurantPage() {
  const register = useRegisterRestaurant();
  const { showToast } = useToast();
  const navigate = useNavigate();

  return (
    <div className="flex flex-col gap-6">
      <BackLink to={OWNER_ROUTES.home}>Dashboard</BackLink>
      <PageHeader
        title="Register a restaurant"
        subtitle="The platform team reviews it before customers can see it"
      />
      <RestaurantEditor
        initialValues={EMPTY_RESTAURANT_FORM}
        initialHours={defaultHoursRows()}
        submitLabel="Send for review"
        onSave={async (request) => {
          await register.mutateAsync(request);
          showToast('Your restaurant was sent for review.');
          void navigate(OWNER_ROUTES.home);
        }}
      />
    </div>
  );
}
