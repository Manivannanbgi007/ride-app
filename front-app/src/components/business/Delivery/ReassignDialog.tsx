import { zodResolver } from '@hookform/resolvers/zod';
import { useState } from 'react';
import { useForm } from 'react-hook-form';

import { FormDialog } from '@/components/common/FormDialog';
import { Select } from '@/components/common/Select';
import { Textarea } from '@/components/common/Textarea';
import { usePartners } from '@/hooks/api/deliveryHooks';
import type { Delivery } from '@/types/delivery';
import {
  REASSIGN_REASON_MAX_LENGTH,
  reassignFormSchema,
  showServerErrors,
  type ReassignForm,
} from '@/utils/validationUtils';

/** The available partners offered (the list can be long in a big city). */
const PARTNERS_SHOWN = 50;

interface ReassignDialogProps {
  delivery: Delivery;
  /** partnerUserId null = back to the queue */
  onSubmit: (partnerUserId: number | null, reason: string) => Promise<unknown>;
  onClose: () => void;
}

/**
 * Gives a delivery that is not picked up yet to another AVAILABLE partner (who still has to accept it), or takes
 * it from its partner and puts it back in the queue for the assignment job. Partners of the pickup city are
 * offered first; "Every city" widens the list (the service itself does not require the same city).
 */
export function ReassignDialog({ delivery, onSubmit, onClose }: ReassignDialogProps) {
  const pickupCity = delivery.pickupAddress?.city ?? undefined;
  const [everyCity, setEveryCity] = useState(pickupCity === undefined);
  const partners = usePartners(
    { status: 'AVAILABLE', city: everyCity ? undefined : pickupCity },
    0,
    PARTNERS_SHOWN,
  );
  const hasPartner = delivery.partnerUserId !== null;
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<ReassignForm>({
    resolver: zodResolver(reassignFormSchema(hasPartner)),
    defaultValues: { partnerUserId: '', reason: '' },
  });

  const options = (partners.data?.content ?? [])
    .filter((partner) => partner.userId !== delivery.partnerUserId)
    .map((partner) => ({
      value: String(partner.userId),
      label: `${partner.fullName} (${partner.city ?? 'no city'})`,
    }));

  const submit = handleSubmit(async ({ partnerUserId, reason }) => {
    try {
      await onSubmit(partnerUserId === '' ? null : Number(partnerUserId), reason);
      onClose();
    } catch (error) {
      showServerErrors(error, setError, ['partnerUserId', 'reason']);
    }
  });

  return (
    <FormDialog
      open
      title={`Reassign delivery #${delivery.id}`}
      submitLabel="Reassign"
      isSubmitting={isSubmitting}
      serverError={errors.root?.server?.message}
      onSubmit={submit}
      onClose={onClose}
    >
      <p className="text-sm">
        {hasPartner
          ? `Now with ${delivery.partnerName ?? `partner #${delivery.partnerUserId}`}, who is free again afterwards and is not offered this delivery again.`
          : 'Nobody has the delivery yet.'}{' '}
        A new partner still has to accept it.
      </p>
      <Select
        label="Give it to"
        placeholder={hasPartner ? 'Nobody: back to the queue' : 'Choose an available partner'}
        options={options}
        hint={
          partners.isPending
            ? 'Loading the available partners...'
            : options.length === 0
              ? 'No partner is available right now.'
              : undefined
        }
        error={errors.partnerUserId?.message}
        {...register('partnerUserId')}
      />
      {pickupCity && (
        <label className="flex items-center gap-2 text-sm">
          <input
            type="checkbox"
            checked={everyCity}
            onChange={(event) => setEveryCity(event.target.checked)}
            className="size-4 accent-brand-500"
          />
          Partners of every city (not only {pickupCity})
        </label>
      )}
      <Textarea
        label="Reason"
        placeholder="Kept in the audit log and the delivery's history"
        maxLength={REASSIGN_REASON_MAX_LENGTH}
        error={errors.reason?.message}
        {...register('reason')}
      />
    </FormDialog>
  );
}
