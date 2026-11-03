import { useNavigate, useParams } from 'react-router';

import { CouponForm } from '@/components/business/Coupon';
import { AsyncContent } from '@/components/common/AsyncContent';
import { BackLink } from '@/components/common/BackLink';
import { PageHeader } from '@/components/common/PageHeader';
import { useToast } from '@/context/ToastContext';
import { useCoupon, useCreateCoupon, useUpdateCoupon } from '@/hooks/api/couponHooks';
import { useIdParam } from '@/hooks/useIdParam';
import { NotFoundPage } from '@/pages/NotFound/NotFoundPage';
import { ROUTES, routeTo } from '@/utils/constants';
import { couponFormToTerms } from '@/utils/couponUtils';
import { ERROR_MESSAGES } from '@/utils/errorMessages';
import type { CouponForm as CouponFormValues } from '@/utils/validationUtils';

/** /admin/coupons/new creates a coupon; /admin/coupons/42/edit changes coupon 42's terms. */
export default function CouponFormPage() {
  const hasId = useParams().couponId !== undefined;
  const couponId = useIdParam('couponId');
  if (!hasId) {
    return <NewCoupon />;
  }
  return couponId === null ? <NotFoundPage /> : <EditCoupon couponId={couponId} />;
}

function NewCoupon() {
  const navigate = useNavigate();
  const { showToast } = useToast();
  const createCoupon = useCreateCoupon();

  const create = async (form: CouponFormValues) => {
    const coupon = await createCoupon.mutateAsync({ code: form.code, ...couponFormToTerms(form) });
    showToast(`Coupon ${coupon.code} created: it can be used from ${coupon.validFrom}.`);
    void navigate(routeTo.couponDetails(coupon.id));
  };

  return (
    <div className="flex flex-col gap-6">
      <BackLink to={ROUTES.coupons}>Back to coupons</BackLink>
      <PageHeader
        title="New coupon"
        subtitle="Active as soon as it is created, from its first day"
      />
      <CouponForm onSubmit={create} onCancel={() => void navigate(ROUTES.coupons)} />
    </div>
  );
}

function EditCoupon({ couponId }: { couponId: number }) {
  const navigate = useNavigate();
  const { showToast } = useToast();
  const details = useCoupon(couponId);
  const updateCoupon = useUpdateCoupon();

  const save = async (form: CouponFormValues, version: number) => {
    const coupon = await updateCoupon.mutateAsync({
      couponId,
      coupon: { ...couponFormToTerms(form), version },
    });
    showToast(`Coupon ${coupon.code} saved.`);
    void navigate(routeTo.couponDetails(couponId));
  };

  return (
    <div className="flex flex-col gap-6">
      <BackLink to={routeTo.couponDetails(couponId)}>Back to the coupon</BackLink>
      <AsyncContent
        isLoading={details.isPending}
        error={details.error}
        errorTitle={ERROR_MESSAGES.COUPON_LOAD_FAILED}
        onRetry={() => void details.refetch()}
      >
        {details.data && (
          <>
            <PageHeader
              title={`Change ${details.data.coupon.code}`}
              subtitle="Orders already placed keep the discount they got"
            />
            <CouponForm
              coupon={details.data.coupon}
              onSubmit={(form) => save(form, details.data.coupon.version)}
              onCancel={() => void navigate(routeTo.couponDetails(couponId))}
            />
          </>
        )}
      </AsyncContent>
    </div>
  );
}
