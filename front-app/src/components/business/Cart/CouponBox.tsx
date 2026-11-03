import { zodResolver } from '@hookform/resolvers/zod';
import { TicketPercent, X } from 'lucide-react';
import { useForm } from 'react-hook-form';
import { Link } from 'react-router';

import { toApiError } from '@/api/apiErrorHandler';
import { Button } from '@/components/common/Button';
import { Input } from '@/components/common/Input';
import { useToast } from '@/context/ToastContext';
import { useApplyCoupon, useRemoveCoupon } from '@/hooks/api/cartHooks';
import type { CartCoupon } from '@/types/shopping';
import { CUSTOMER_ROUTES } from '@/utils/constants';
import { formatCurrency } from '@/utils/formatUtils';
import { couponCodeSchema } from '@/utils/validationUtils';

interface CouponBoxProps {
  coupon: CartCoupon | null;
  currency: string;
}

/**
 * A coupon for this cart: type a code, or see the offers. Applied, it shows what it saves; a coupon that no
 * longer applies (the cart got smaller) says why, and stays until removed or the cart qualifies again.
 */
export function CouponBox({ coupon, currency }: CouponBoxProps) {
  const apply = useApplyCoupon();
  const remove = useRemoveCoupon();
  const { showToast } = useToast();
  const {
    register,
    handleSubmit,
    setError,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<{ code: string }>({
    resolver: zodResolver(couponCodeSchema),
    defaultValues: { code: '' },
  });

  const submit = handleSubmit(async ({ code }) => {
    try {
      const cart = await apply.mutateAsync(code);
      reset();
      if (cart.coupon?.applied) {
        showToast(
          `${cart.coupon.code} applied: you save ${formatCurrency(cart.coupon.discountAmount, currency)}.`,
        );
      }
    } catch (error) {
      // 422 carries promotion-service's reason ("Add items worth 120.00 more to use this coupon ...")
      setError('code', { message: toApiError(error).userMessage });
    }
  });

  if (coupon) {
    return (
      <div className="flex items-start gap-3 rounded-xl border border-dashed border-brand-500 bg-brand-50 p-4">
        <TicketPercent aria-hidden="true" className="mt-0.5 size-5 shrink-0 text-brand-text" />
        <div className="min-w-0 flex-1 text-sm">
          <p className="font-bold">{coupon.code}</p>
          {coupon.applied ? (
            <p className="text-brand-text-strong">
              You save {formatCurrency(coupon.discountAmount, currency)}
              {coupon.description ? ` · ${coupon.description}` : ''}
            </p>
          ) : (
            <p className="text-danger-text">Not applied: {coupon.reason}</p>
          )}
        </div>
        <Button
          variant="ghost"
          size="sm"
          isLoading={remove.isPending}
          onClick={() =>
            remove.mutate(undefined, {
              onError: (error) => showToast(toApiError(error).userMessage, 'error'),
            })
          }
          leftIcon={<X aria-hidden="true" className="size-4" />}
        >
          Remove
        </Button>
      </div>
    );
  }

  return (
    <form onSubmit={(event) => void submit(event)} noValidate className="flex flex-col gap-2">
      <div className="flex items-start gap-2">
        <div className="flex-1">
          <Input
            aria-label="Coupon code"
            placeholder="Coupon code"
            maxLength={30}
            autoCapitalize="characters"
            error={errors.code?.message}
            {...register('code')}
          />
        </div>
        <Button type="submit" variant="outline" isLoading={isSubmitting}>
          Apply
        </Button>
      </div>
      <Link
        to={CUSTOMER_ROUTES.offers}
        className="text-sm font-semibold text-brand-text hover:underline"
      >
        See today&apos;s offers
      </Link>
    </form>
  );
}
