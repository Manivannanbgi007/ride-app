import { UtensilsCrossed } from 'lucide-react';
import { useNavigate } from 'react-router';

import { AuditHistory } from '@/components/business/AuditHistory';
import {
  RestaurantActions,
  RestaurantDocumentsCard,
  RestaurantHoursCard,
  RestaurantOwnerCard,
  RestaurantProfileCard,
} from '@/components/business/Restaurant';
import { AsyncContent } from '@/components/common/AsyncContent';
import { BackLink } from '@/components/common/BackLink';
import { Button } from '@/components/common/Button';
import { Card } from '@/components/common/Card';
import { PageHeader } from '@/components/common/PageHeader';
import { useToast } from '@/context/ToastContext';
import {
  useChangeRestaurantStatus,
  useRestaurant,
  useRestaurantAuditLogs,
} from '@/hooks/api/restaurantHooks';
import { useIdParam } from '@/hooks/useIdParam';
import { NotFoundPage } from '@/pages/NotFound/NotFoundPage';
import { useAppDispatch } from '@/store/hooks';
import { foodFiltersChanged } from '@/store/slices/foodListSlice';
import type { RestaurantAction } from '@/types/restaurant';
import { RESTAURANT_STATUS_LABELS, ROUTES } from '@/utils/constants';
import { ERROR_MESSAGES } from '@/utils/errorMessages';

/** One restaurant: what the owner submitted, its owner, hours and documents, and the admin's status actions. */
export default function RestaurantDetailsPage() {
  const restaurantId = useIdParam('restaurantId');
  return restaurantId === null ? (
    <NotFoundPage />
  ) : (
    <RestaurantDetailsView restaurantId={restaurantId} />
  );
}

function RestaurantDetailsView({ restaurantId }: { restaurantId: number }) {
  const dispatch = useAppDispatch();
  const navigate = useNavigate();
  const { showToast } = useToast();
  const details = useRestaurant(restaurantId);
  const auditLogs = useRestaurantAuditLogs(restaurantId);
  const changeStatus = useChangeRestaurantStatus();

  const changeRestaurantStatus = async (action: RestaurantAction, reason: string) => {
    const restaurant = await changeStatus.mutateAsync({ restaurantId, action, reason });
    showToast(`${restaurant.name}: ${RESTAURANT_STATUS_LABELS[restaurant.status].toLowerCase()}.`);
  };

  const showMenu = () => {
    dispatch(foodFiltersChanged({ restaurantId }));
    void navigate(ROUTES.foods);
  };

  return (
    <div className="flex flex-col gap-6">
      <BackLink to={ROUTES.restaurants}>Back to restaurants</BackLink>
      <AsyncContent
        isLoading={details.isPending}
        error={details.error}
        errorTitle={ERROR_MESSAGES.RESTAURANT_LOAD_FAILED}
        onRetry={() => void details.refetch()}
        isRetrying={details.isFetching}
      >
        {details.data && (
          <>
            <PageHeader
              title={details.data.restaurant.name}
              subtitle={`Restaurant #${details.data.restaurant.id} in ${details.data.restaurant.address.city}`}
              actions={
                <div className="flex flex-wrap gap-3">
                  <Button
                    variant="ghost"
                    onClick={showMenu}
                    leftIcon={<UtensilsCrossed aria-hidden="true" className="size-4" />}
                  >
                    Menu items
                  </Button>
                  <RestaurantActions
                    restaurant={details.data.restaurant}
                    onAction={changeRestaurantStatus}
                  />
                </div>
              }
            />
            <div className="grid gap-6 xl:grid-cols-3">
              <div className="flex flex-col gap-6 xl:col-span-2">
                <RestaurantProfileCard restaurant={details.data.restaurant} />
                <div className="grid gap-6 lg:grid-cols-2">
                  <RestaurantHoursCard hours={details.data.restaurant.operatingHours} />
                  <RestaurantDocumentsCard documents={details.data.restaurant.documents} />
                </div>
              </div>
              <div className="flex flex-col gap-6">
                <RestaurantOwnerCard owner={details.data.owner} />
                <Card title="Change history" subtitle="Admin actions on this restaurant">
                  <AsyncContent
                    isLoading={auditLogs.isPending}
                    error={auditLogs.error}
                    errorTitle={ERROR_MESSAGES.AUDIT_LOAD_FAILED}
                    onRetry={() => void auditLogs.refetch()}
                  >
                    {auditLogs.data && <AuditHistory entries={auditLogs.data.content} />}
                  </AsyncContent>
                </Card>
              </div>
            </div>
          </>
        )}
      </AsyncContent>
    </div>
  );
}
