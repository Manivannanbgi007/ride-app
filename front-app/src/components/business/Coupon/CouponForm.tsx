import { zodResolver } from '@hookform/resolvers/zod';
import { Controller, useForm, useWatch } from 'react-hook-form';

import { Alert } from '@/components/common/Alert';
import { Button } from '@/components/common/Button';
import { Card } from '@/components/common/Card';
import { DatePicker } from '@/components/common/DatePicker';
import { Input } from '@/components/common/Input';
import { Select } from '@/components/common/Select';
import { Textarea } from '@/components/common/Textarea';
import { DISCOUNT_TYPES, type Coupon } from '@/types/coupon';
import { DISCOUNT_TYPE_LABELS } from '@/utils/constants';
import { couponToForm } from '@/utils/couponUtils';
import {
  couponFormSchema,
  showServerErrors,
  type CouponForm as CouponFormValues,
} from '@/utils/validationUtils';

const TYPE_OPTIONS = DISCOUNT_TYPES.map((type) => ({
  value: type,
  label: DISCOUNT_TYPE_LABELS[type],
}));

const EMPTY_FORM: CouponFormValues = {
  code: '',
  description: '',
  discountType: 'PERCENTAGE',
  discountValue: '',
  maxDiscount: '',
  minOrderAmount: '',
  restaurantId: '',
  validFrom: '',
  validUntil: '',
  usageLimit: '',
  perCustomerLimit: '',
};

/** Every field the server may name in a refusal (400 "errors"). */
const FIELDS = Object.keys(EMPTY_FORM) as (keyof CouponFormValues)[];

interface CouponFormProps {
  /** The coupon to change; none: a new coupon (its code can then be chosen) */
  coupon?: Coupon;
  onSubmit: (form: CouponFormValues) => Promise<unknown>;
  onCancel: () => void;
}

/**
 * A coupon's terms, for a new coupon or a change. The rules (code format, at most 100 percent, a maximum only for
 * percentages, last day not before the first) are checked here first, and again by promotion-service.
 */
export function CouponForm({ coupon, onSubmit, onCancel }: CouponFormProps) {
  const {
    register,
    handleSubmit,
    setError,
    control,
    formState: { errors, isSubmitting },
  } = useForm<CouponFormValues>({
    resolver: zodResolver(couponFormSchema),
    defaultValues: coupon ? couponToForm(coupon) : EMPTY_FORM,
  });
  const discountType = useWatch({ control, name: 'discountType' });
  const [validFrom, validUntil] = useWatch({ control, name: ['validFrom', 'validUntil'] });
  const isPercentage = discountType === 'PERCENTAGE';

  const submit = handleSubmit(async (form) => {
    try {
      await onSubmit(form);
    } catch (error) {
      showServerErrors(error, setError, FIELDS);
    }
  });

  return (
    <form noValidate onSubmit={(event) => void submit(event)} className="flex flex-col gap-6">
      {errors.root?.server && <Alert tone="error">{errors.root.server.message}</Alert>}

      <Card title="The coupon">
        <div className="grid gap-4 sm:grid-cols-2">
          <Input
            label="Code"
            placeholder="WELCOME100"
            readOnly={coupon !== undefined}
            hint={
              coupon
                ? 'A code cannot change: customers may already have it.'
                : 'What customers type; saved in capitals'
            }
            error={errors.code?.message}
            className="font-mono uppercase"
            {...register('code')}
          />
          <Textarea
            label="Description"
            rows={2}
            maxLength={255}
            hint="Optional"
            error={errors.description?.message}
            {...register('description')}
          />
        </div>
      </Card>

      <Card title="Discount">
        <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
          <Select
            label="Type"
            options={TYPE_OPTIONS}
            error={errors.discountType?.message}
            {...register('discountType')}
          />
          <Input
            label={isPercentage ? 'Percent off' : 'Amount off (₹)'}
            inputMode="decimal"
            placeholder={isPercentage ? '20' : '100.00'}
            error={errors.discountValue?.message}
            {...register('discountValue')}
          />
          {isPercentage && (
            <Input
              label="At most (₹)"
              inputMode="decimal"
              placeholder="150.00"
              hint="Optional"
              error={errors.maxDiscount?.message}
              {...register('maxDiscount')}
            />
          )}
          <Input
            label="Minimum items total (₹)"
            inputMode="decimal"
            placeholder="499.00"
            hint="Optional"
            error={errors.minOrderAmount?.message}
            {...register('minOrderAmount')}
          />
        </div>
      </Card>

      <Card title="Where, when and how often">
        <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
          <Input
            label="Restaurant #"
            inputMode="numeric"
            hint="Empty: every restaurant"
            error={errors.restaurantId?.message}
            {...register('restaurantId')}
          />
          <Controller
            control={control}
            name="validFrom"
            render={({ field }) => (
              <DatePicker
                label="First day"
                placeholder="Choose a day"
                clearable={false}
                value={field.value}
                max={validUntil || undefined}
                rangeFrom={validFrom}
                rangeTo={validUntil}
                onChange={field.onChange}
                onBlur={field.onBlur}
                error={errors.validFrom?.message}
              />
            )}
          />
          <Controller
            control={control}
            name="validUntil"
            render={({ field }) => (
              <DatePicker
                label="Last day"
                placeholder="Choose a day"
                hint="Included"
                clearable={false}
                value={field.value}
                min={validFrom || undefined}
                rangeFrom={validFrom}
                rangeTo={validUntil}
                onChange={field.onChange}
                onBlur={field.onBlur}
                error={errors.validUntil?.message}
              />
            )}
          />
          <Input
            label="Uses in total"
            inputMode="numeric"
            hint="Empty: unlimited"
            error={errors.usageLimit?.message}
            {...register('usageLimit')}
          />
          <Input
            label="Uses per customer"
            inputMode="numeric"
            hint="Empty: unlimited"
            error={errors.perCustomerLimit?.message}
            {...register('perCustomerLimit')}
          />
        </div>
      </Card>

      <div className="flex justify-end gap-3">
        <Button variant="ghost" onClick={onCancel} disabled={isSubmitting}>
          Cancel
        </Button>
        <Button type="submit" isLoading={isSubmitting}>
          {coupon ? 'Save changes' : 'Create coupon'}
        </Button>
      </div>
    </form>
  );
}
