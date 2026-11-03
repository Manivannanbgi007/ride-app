import { toApiError } from '@/api/apiErrorHandler';
import {
  CurrentDeliveryCard,
  DeliveryOutcomeHero,
  PartnerDeliveryInfo,
  PartnerDeliveryTimeline,
} from '@/components/business/PartnerArea';
import { AsyncContent } from '@/components/common/AsyncContent';
import { BackLink } from '@/components/common/BackLink';
import { EmptyState } from '@/components/common/EmptyState';
import { usePartnerDelivery } from '@/hooks/api/partnerHooks';
import { useIdParam } from '@/hooks/useIdParam';
import { NotFoundPage } from '@/pages/NotFound/NotFoundPage';
import { PARTNER_ROUTES } from '@/utils/constants';
import { nextPartnerStep } from '@/utils/partnerAreaUtils';

/**
 * One of the partner's deliveries: its outcome and figures on top (the hero), then the route, or the ticket with the
 * next step while it is still on its way, beside every step it went through (the history).
 */
export default function PartnerDeliveryDetailsPage() {
  const deliveryId = useIdParam('deliveryId');
  return deliveryId === null ? (
    <NotFoundPage />
  ) : (
    <PartnerDeliveryDetails deliveryId={deliveryId} />
  );
}

function PartnerDeliveryDetails({ deliveryId }: { deliveryId: number }) {
  const delivery = usePartnerDelivery(deliveryId);
  // Only a first load that failed replaces the page: a failed refresh keeps the delivery on screen
  const loadError = delivery.data ? null : delivery.error;
  // A delivery taken away from the partner (or another partner's) is "not found" for them
  const isMissing = loadError !== null && toApiError(loadError).kind === 'notFound';

  return (
    <div className="flex flex-col gap-6">
      <BackLink to={PARTNER_ROUTES.deliveries}>Back to my deliveries</BackLink>
      {isMissing ? (
        <div className="rounded-3xl bg-surface shadow-card">
          <EmptyState
            title="Delivery not found"
            message="It is not one of your deliveries (an offer you did not take goes to another partner)."
          />
        </div>
      ) : (
        <AsyncContent
          isLoading={delivery.isPending}
          error={loadError}
          errorTitle="This delivery could not be loaded"
          onRetry={() => void delivery.refetch()}
          isRetrying={delivery.isFetching}
        >
          {delivery.data && (
            <div className="flex flex-col gap-6">
              <DeliveryOutcomeHero delivery={delivery.data} />
              <div className="grid items-start gap-6 lg:grid-cols-2">
                {nextPartnerStep(delivery.data.status) ? (
                  <CurrentDeliveryCard delivery={delivery.data} hideDetailsLink />
                ) : (
                  <PartnerDeliveryInfo delivery={delivery.data} />
                )}
                <PartnerDeliveryTimeline timeline={delivery.data.timeline} />
              </div>
            </div>
          )}
        </AsyncContent>
      )}
    </div>
  );
}
