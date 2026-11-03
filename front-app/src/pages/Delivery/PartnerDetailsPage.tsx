import { UserCheck, UserX } from 'lucide-react';
import { useState } from 'react';
import { Link } from 'react-router';

import { AuditHistory } from '@/components/business/AuditHistory';
import { DeliveryTable, PartnerProfileCard } from '@/components/business/Delivery';
import { AsyncContent } from '@/components/common/AsyncContent';
import { BackLink } from '@/components/common/BackLink';
import { Button } from '@/components/common/Button';
import { Card } from '@/components/common/Card';
import { PageHeader } from '@/components/common/PageHeader';
import { PagedResults } from '@/components/common/PagedResults';
import { ReasonDialog } from '@/components/common/ReasonDialog';
import { useToast } from '@/context/ToastContext';
import {
  useChangePartnerStatus,
  usePartner,
  usePartnerAuditLogs,
  usePartnerDeliveries,
} from '@/hooks/api/deliveryHooks';
import { useIdParam } from '@/hooks/useIdParam';
import { NotFoundPage } from '@/pages/NotFound/NotFoundPage';
import type { Partner } from '@/types/delivery';
import { routeTo, ROUTES } from '@/utils/constants';
import { ERROR_MESSAGES } from '@/utils/errorMessages';

const PARTNER_DELIVERIES_PAGE_SIZE = 10;

/** One delivery partner: profile, their deliveries, and suspend / reactivate. */
export default function PartnerDetailsPage() {
  const userId = useIdParam('userId');
  return userId === null ? <NotFoundPage /> : <PartnerDetails userId={userId} />;
}

function PartnerDetails({ userId }: { userId: number }) {
  const partner = usePartner(userId);
  const auditLogs = usePartnerAuditLogs(userId);

  return (
    <div className="flex flex-col gap-6">
      <BackLink to={`${ROUTES.deliveries}?tab=partners`}>Back to delivery partners</BackLink>
      <AsyncContent
        isLoading={partner.isPending}
        error={partner.error}
        errorTitle={ERROR_MESSAGES.PARTNER_LOAD_FAILED}
        onRetry={() => void partner.refetch()}
        isRetrying={partner.isFetching}
      >
        {partner.data && (
          <>
            <PageHeader
              title={partner.data.fullName}
              subtitle={`Delivery partner #${partner.data.userId}`}
              actions={<PartnerActions partner={partner.data} />}
            />
            <div className="grid gap-6 xl:grid-cols-3">
              <div className="flex flex-col gap-6 xl:col-span-2">
                <PartnerProfileCard partner={partner.data} />
                <PartnerDeliveries userId={userId} />
              </div>
              <Card title="Change history" subtitle="Suspensions and reactivations">
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
          </>
        )}
      </AsyncContent>
    </div>
  );
}

/**
 * Suspend (any status but BUSY: a partner on a delivery is suspended after it is reassigned) or reactivate a
 * suspended partner, who comes back OFFLINE and goes online themselves.
 */
function PartnerActions({ partner }: { partner: Partner }) {
  const [open, setOpen] = useState(false);
  const changeStatus = useChangePartnerStatus();
  const { showToast } = useToast();
  const activate = partner.status === 'SUSPENDED';
  const isBusy = partner.status === 'BUSY';

  const submit = async (reason: string) => {
    await changeStatus.mutateAsync({ userId: partner.userId, activate, reason });
    showToast(`${partner.fullName} was ${activate ? 'reactivated' : 'suspended'}.`);
  };

  return (
    <div className="flex flex-col items-end gap-2">
      <Button
        variant={activate ? 'primary' : 'danger'}
        disabled={isBusy}
        onClick={() => setOpen(true)}
        leftIcon={
          activate ? (
            <UserCheck aria-hidden="true" className="size-4" />
          ) : (
            <UserX aria-hidden="true" className="size-4" />
          )
        }
      >
        {activate ? 'Reactivate' : 'Suspend'}
      </Button>
      {isBusy && partner.currentDeliveryId && (
        <p className="text-xs text-ink-soft">
          On delivery{' '}
          <Link
            to={routeTo.deliveryDetails(partner.currentDeliveryId)}
            className="text-brand-text hover:underline"
          >
            #{partner.currentDeliveryId}
          </Link>
          : reassign it first.
        </p>
      )}
      <ReasonDialog
        open={open}
        title={`${activate ? 'Reactivate' : 'Suspend'} ${partner.fullName}?`}
        description={
          activate
            ? 'The partner can go online and receive deliveries again (they come back offline).'
            : 'The partner goes offline and is not offered deliveries until an admin reactivates them.'
        }
        confirmLabel={activate ? 'Reactivate' : 'Suspend'}
        tone={activate ? 'primary' : 'danger'}
        onSubmit={submit}
        onClose={() => setOpen(false)}
      />
    </div>
  );
}

function PartnerDeliveries({ userId }: { userId: number }) {
  const [page, setPage] = useState(0);
  const deliveries = usePartnerDeliveries(userId, page, PARTNER_DELIVERIES_PAGE_SIZE);

  return (
    <Card title="Deliveries" subtitle="Newest first">
      <PagedResults
        query={deliveries}
        errorTitle={ERROR_MESSAGES.DELIVERIES_LOAD_FAILED}
        emptyTitle="No deliveries yet"
        filteredEmptyTitle="No deliveries yet"
        hasFilters={false}
        onClearFilters={() => setPage(0)}
        onPageChange={setPage}
        columns={8}
      >
        {(rows, isUpdating) => <DeliveryTable deliveries={rows} isUpdating={isUpdating} />}
      </PagedResults>
    </Card>
  );
}
