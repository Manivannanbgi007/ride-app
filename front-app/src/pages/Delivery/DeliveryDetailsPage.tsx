import { Shuffle } from 'lucide-react';
import { useState } from 'react';

import { AuditHistory } from '@/components/business/AuditHistory';
import {
  DeliveryOverviewCard,
  DeliveryTimelineCard,
  ReassignDialog,
} from '@/components/business/Delivery';
import { AsyncContent } from '@/components/common/AsyncContent';
import { BackLink } from '@/components/common/BackLink';
import { Button } from '@/components/common/Button';
import { Card } from '@/components/common/Card';
import { PageHeader } from '@/components/common/PageHeader';
import { useToast } from '@/context/ToastContext';
import { useDelivery, useDeliveryAuditLogs, useReassignDelivery } from '@/hooks/api/deliveryHooks';
import { useIdParam } from '@/hooks/useIdParam';
import { NotFoundPage } from '@/pages/NotFound/NotFoundPage';
import { REASSIGNABLE_STATUSES, type Delivery } from '@/types/delivery';
import { ROUTES } from '@/utils/constants';
import { ERROR_MESSAGES } from '@/utils/errorMessages';

/** One delivery: where from and to, who carries it, its whole history, and "Reassign" before pickup. */
export default function DeliveryDetailsPage() {
  const deliveryId = useIdParam('deliveryId');
  return deliveryId === null ? <NotFoundPage /> : <DeliveryDetails deliveryId={deliveryId} />;
}

function DeliveryDetails({ deliveryId }: { deliveryId: number }) {
  const delivery = useDelivery(deliveryId);
  const auditLogs = useDeliveryAuditLogs(deliveryId);

  return (
    <div className="flex flex-col gap-6">
      <BackLink to={ROUTES.deliveries}>Back to deliveries</BackLink>
      <AsyncContent
        isLoading={delivery.isPending}
        error={delivery.error}
        errorTitle={ERROR_MESSAGES.DELIVERY_LOAD_FAILED}
        onRetry={() => void delivery.refetch()}
        isRetrying={delivery.isFetching}
      >
        {delivery.data && (
          <>
            <PageHeader
              title={`Delivery #${delivery.data.id}`}
              subtitle={`Order #${delivery.data.orderId}${delivery.data.restaurantName ? ` from ${delivery.data.restaurantName}` : ''}`}
              actions={<ReassignAction delivery={delivery.data} />}
            />
            <div className="grid gap-6 xl:grid-cols-3">
              <div className="xl:col-span-2">
                <DeliveryOverviewCard delivery={delivery.data} />
              </div>
              <div className="flex flex-col gap-6">
                <DeliveryTimelineCard timeline={delivery.data.timeline} />
                <Card title="Admin actions" subtitle="Reassignments of this delivery">
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

/** Only before pickup: afterwards the food is with the partner (delivery-service refuses it too). */
function ReassignAction({ delivery }: { delivery: Delivery }) {
  const [open, setOpen] = useState(false);
  const reassign = useReassignDelivery();
  const { showToast } = useToast();
  const canReassign = REASSIGNABLE_STATUSES.includes(delivery.status);

  const submit = async (partnerUserId: number | null, reason: string) => {
    const updated = await reassign.mutateAsync({ deliveryId: delivery.id, partnerUserId, reason });
    showToast(
      updated.partnerUserId
        ? `Delivery #${updated.id} was offered to ${updated.partnerName ?? `partner #${updated.partnerUserId}`}.`
        : `Delivery #${updated.id} is back in the queue.`,
    );
  };

  return (
    <div className="flex flex-col items-end gap-2">
      <Button
        variant="outline"
        disabled={!canReassign}
        onClick={() => setOpen(true)}
        leftIcon={<Shuffle aria-hidden="true" className="size-4" />}
      >
        Reassign
      </Button>
      {!canReassign && (
        <p className="text-xs text-ink-soft">
          {delivery.status === 'PICKED_UP'
            ? 'The partner already has the food.'
            : 'The delivery is finished.'}
        </p>
      )}
      {open && (
        <ReassignDialog delivery={delivery} onSubmit={submit} onClose={() => setOpen(false)} />
      )}
    </div>
  );
}
