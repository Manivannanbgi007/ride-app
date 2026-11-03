import { zodResolver } from '@hookform/resolvers/zod';
import { useState } from 'react';
import { useForm } from 'react-hook-form';

import { FormDialog } from '@/components/common/FormDialog';
import { Input } from '@/components/common/Input';
import { Textarea } from '@/components/common/Textarea';
import type { Payment } from '@/types/payment';
import { formatCurrency } from '@/utils/formatUtils';
import {
  REASON_MAX_LENGTH,
  refundFormSchema,
  showServerErrors,
  type RefundForm,
} from '@/utils/validationUtils';
import { newUuid } from '@/utils/idUtils';

interface RefundDialogProps {
  payment: Payment;
  /** What can still be refunded (amount minus refunds done or running) */
  refundable: number;
  /** amount null = everything that can still be refunded */
  onSubmit: (amount: number | null, reason: string, idempotencyKey: string) => Promise<unknown>;
  onClose: () => void;
}

/**
 * Refunds all or part of a payment. Open it only while needed ({open && <RefundDialog />}): each opening gets a
 * new idempotency key, and every attempt of that opening sends the same key, so a retry after a timeout returns
 * the first refund instead of refunding twice.
 */
export function RefundDialog({ payment, refundable, onSubmit, onClose }: RefundDialogProps) {
  const [idempotencyKey] = useState(() => newUuid());
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<RefundForm>({
    resolver: zodResolver(refundFormSchema(refundable)),
    defaultValues: { amount: '', reason: '' },
  });

  const submit = handleSubmit(async ({ amount, reason }) => {
    try {
      await onSubmit(amount === '' ? null : Number(amount), reason, idempotencyKey);
      onClose();
    } catch (error) {
      showServerErrors(error, setError, ['amount', 'reason']);
    }
  });

  return (
    <FormDialog
      open
      title={`Refund payment #${payment.id}`}
      submitLabel="Refund"
      tone="danger"
      isSubmitting={isSubmitting}
      serverError={errors.root?.server?.message}
      onSubmit={submit}
      onClose={onClose}
    >
      <p className="text-sm">
        The money goes back to the customer's {payment.instrument ?? 'payment method'} at once. Up
        to <span className="font-semibold">{formatCurrency(refundable, payment.currency)}</span> can
        be refunded.
      </p>
      <Input
        label="Amount (₹)"
        inputMode="decimal"
        placeholder={refundable.toFixed(2)}
        hint="Empty: refund everything that can still be refunded"
        error={errors.amount?.message}
        {...register('amount')}
      />
      <Textarea
        label="Reason"
        placeholder="Kept in the audit log and with the refund"
        maxLength={REASON_MAX_LENGTH}
        error={errors.reason?.message}
        {...register('reason')}
      />
    </FormDialog>
  );
}
