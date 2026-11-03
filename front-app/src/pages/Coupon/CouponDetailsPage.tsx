import { Pause, Pencil, Play } from 'lucide-react';
import { useState } from 'react';
import { useNavigate } from 'react-router';

import { AuditHistory } from '@/components/business/AuditHistory';
import { CouponTermsCard, CouponUsageCard, RedemptionTable } from '@/components/business/Coupon';
import { AsyncContent } from '@/components/common/AsyncContent';
import { BackLink } from '@/components/common/BackLink';
import { Button } from '@/components/common/Button';
import { Card } from '@/components/common/Card';
import { PageHeader } from '@/components/common/PageHeader';
import { PagedResults } from '@/components/common/PagedResults';
import { ReasonDialog } from '@/components/common/ReasonDialog';
import { Select } from '@/components/common/Select';
import { useToast } from '@/context/ToastContext';
import {
  useChangeCouponStatus,
  useCoupon,
  useCouponAuditLogs,
  useCouponRedemptions,
} from '@/hooks/api/couponHooks';
import { useIdParam } from '@/hooks/useIdParam';
import { NotFoundPage } from '@/pages/NotFound/NotFoundPage';
import { REDEMPTION_STATUSES, type Coupon, type RedemptionStatus } from '@/types/coupon';
import { REDEMPTION_STATUS_LABELS, ROUTES, routeTo } from '@/utils/constants';
import { ERROR_MESSAGES } from '@/utils/errorMessages';
import { oneOf } from '@/utils/validationUtils';

const USES_PER_PAGE = 10;
const STATUS_OPTIONS = REDEMPTION_STATUSES.map((status) => ({
  value: status,
  label: REDEMPTION_STATUS_LABELS[status],
}));

/** One coupon: its terms, how it is used, every use, and the admin's actions (change, pause, resume). */
export default function CouponDetailsPage() {
  const couponId = useIdParam('couponId');
  return couponId === null ? <NotFoundPage /> : <CouponDetailsView couponId={couponId} />;
}

function CouponDetailsView({ couponId }: { couponId: number }) {
  const details = useCoupon(couponId);
  const auditLogs = useCouponAuditLogs(couponId);

  return (
    <div className="flex flex-col gap-6">
      <BackLink to={ROUTES.coupons}>Back to coupons</BackLink>
      <AsyncContent
        isLoading={details.isPending}
        error={details.error}
        errorTitle={ERROR_MESSAGES.COUPON_LOAD_FAILED}
        onRetry={() => void details.refetch()}
        isRetrying={details.isFetching}
      >
        {details.data && (
          <>
            <PageHeader
              title={details.data.coupon.code}
              subtitle={`Coupon #${details.data.coupon.id}`}
              actions={<CouponActions coupon={details.data.coupon} />}
            />
            <div className="grid gap-6 xl:grid-cols-3">
              <div className="flex flex-col gap-6 xl:col-span-2">
                <CouponTermsCard coupon={details.data.coupon} />
                <CouponUses couponId={couponId} />
              </div>
              <div className="flex flex-col gap-6">
                <CouponUsageCard details={details.data} />
                <Card title="Change history" subtitle="Admin actions on this coupon">
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

/** Change the terms, or pause (no new uses from now on) / resume the coupon. */
function CouponActions({ coupon }: { coupon: Coupon }) {
  const navigate = useNavigate();
  const { showToast } = useToast();
  const changeStatus = useChangeCouponStatus();
  const [isDialogOpen, setDialogOpen] = useState(false);
  const pause = coupon.status === 'ACTIVE';

  return (
    <div className="flex flex-wrap gap-3">
      <Button
        variant="outline"
        onClick={() => void navigate(routeTo.couponEdit(coupon.id))}
        leftIcon={<Pencil aria-hidden="true" className="size-4" />}
      >
        Change terms
      </Button>
      <Button
        variant={pause ? 'danger' : 'primary'}
        onClick={() => setDialogOpen(true)}
        leftIcon={
          pause ? (
            <Pause aria-hidden="true" className="size-4" />
          ) : (
            <Play aria-hidden="true" className="size-4" />
          )
        }
      >
        {pause ? 'Pause' : 'Resume'}
      </Button>
      <ReasonDialog
        open={isDialogOpen}
        title={`${pause ? 'Pause' : 'Resume'} ${coupon.code}?`}
        description={
          pause
            ? 'Customers cannot use the coupon from now on. Orders already placed keep their discount.'
            : 'Customers can use the coupon again (within its dates and limits).'
        }
        confirmLabel={pause ? 'Pause' : 'Resume'}
        tone={pause ? 'danger' : 'primary'}
        onSubmit={async (reason) => {
          await changeStatus.mutateAsync({ couponId: coupon.id, activate: !pause, reason });
          showToast(`${coupon.code} is ${pause ? 'paused' : 'active again'}.`);
        }}
        onClose={() => setDialogOpen(false)}
      />
    </div>
  );
}

/** Every use of the coupon, newest first, with a status filter (kept on this page only). */
function CouponUses({ couponId }: { couponId: number }) {
  const [status, setStatus] = useState<RedemptionStatus | undefined>();
  const [page, setPage] = useState(0);
  const uses = useCouponRedemptions(couponId, status, page, USES_PER_PAGE);

  return (
    <Card
      title="Uses"
      actions={
        <Select
          aria-label="Status of the uses"
          placeholder="Every use"
          options={STATUS_OPTIONS}
          value={status ?? ''}
          onChange={(event) => {
            setStatus(oneOf(event.target.value, REDEMPTION_STATUSES));
            setPage(0);
          }}
        />
      }
    >
      <PagedResults
        query={uses}
        errorTitle={ERROR_MESSAGES.REDEMPTIONS_LOAD_FAILED}
        emptyTitle="Nobody has used this coupon yet"
        filteredEmptyTitle="No uses with this status"
        hasFilters={status !== undefined}
        onClearFilters={() => setStatus(undefined)}
        onPageChange={setPage}
      >
        {(rows, isUpdating) => <RedemptionTable uses={rows} isUpdating={isUpdating} />}
      </PagedResults>
    </Card>
  );
}
