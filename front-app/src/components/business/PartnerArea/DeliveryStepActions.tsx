import { CheckCircle2, PackageCheck, X } from 'lucide-react';
import { useState } from 'react';

import { toApiError } from '@/api/apiErrorHandler';
import { Button } from '@/components/common/Button';
import { ConfirmationDialog } from '@/components/common/ConfirmationDialog';
import { Input } from '@/components/common/Input';
import { useToast } from '@/context/ToastContext';
import { usePartnerDeliveryStep } from '@/hooks/api/partnerHooks';
import { useNow } from '@/hooks/useNow';
import type { Delivery } from '@/types/delivery';
import type { PartnerDeliveryAction } from '@/types/partner';
import { cn } from '@/utils/classNames';
import { DEFAULT_CURRENCY } from '@/utils/constants';
import { formatCurrency } from '@/utils/formatUtils';
import { collectsCash, nextPartnerStep, offerSecondsLeft } from '@/utils/partnerAreaUtils';

interface DeliveryStepActionsProps {
  delivery: Delivery;
  /** Regular buttons side by side (a list's panel) instead of the ticket's large, full width ones */
  compact?: boolean;
}

/**
 * The partner's next step for a delivery, as buttons: accept or decline an offer (declining asks first, with an
 * optional reason), pick up the food, hand it over (asking first, and for cash on delivery confirming the exact amount
 * collected). Only the step the delivery's status allows is shown; while a step is being sent the buttons wait, so it
 * cannot be sent twice. delivery-service checks every step again: a refusal (the offer went to someone else, the
 * order was cancelled) is explained, and the screens read the delivery again either way. Nothing for a finished one.
 */
export function DeliveryStepActions({ delivery, compact = false }: DeliveryStepActionsProps) {
  const step = usePartnerDeliveryStep();
  const { showToast } = useToast();
  const now = useNow(1000);
  const [dialog, setDialog] = useState<'decline' | 'deliver' | null>(null);
  const [declineReason, setDeclineReason] = useState('');
  const next = nextPartnerStep(delivery.status);
  const secondsLeft = offerSecondsLeft(delivery, now);
  const currency = delivery.currency ?? DEFAULT_CURRENCY;
  const cash = collectsCash(delivery);
  const amount = formatCurrency(delivery.cashToCollect, currency);
  const restaurant = delivery.restaurantName ?? 'the restaurant';
  const customer = delivery.customerName ?? 'the customer';

  if (next === null) {
    return null;
  }

  const size = compact ? 'md' : 'lg';
  const main = compact ? 'flex-1 sm:flex-none' : 'w-full sm:w-auto sm:flex-1';
  const icon = compact ? 'size-4' : 'size-5';

  const run = (action: PartnerDeliveryAction, done: string) => {
    step.mutate(
      {
        deliveryId: delivery.id,
        action,
        reason: action === 'decline' ? declineReason.trim() || undefined : undefined,
        cashCollected: action === 'deliver' && cash ? delivery.cashToCollect : null,
      },
      {
        onSuccess: () => {
          setDialog(null);
          setDeclineReason('');
          showToast(done);
        },
        onError: (error) => {
          setDialog(null);
          showToast(toApiError(error).userMessage, 'error');
        },
      },
    );
  };

  return (
    <>
      <div className={cn('flex gap-3', compact ? 'flex-wrap' : 'flex-col sm:flex-row')}>
        {next === 'accept' && (
          <>
            <Button
              size={size}
              className={main}
              leftIcon={<CheckCircle2 aria-hidden="true" className={icon} />}
              isLoading={step.isPending && step.variables?.action === 'accept'}
              disabled={step.isPending || secondsLeft === 0}
              onClick={() => run('accept', `Accepted: head to ${restaurant}.`)}
            >
              Accept delivery
            </Button>
            <Button
              variant="outline"
              size={size}
              className={compact ? undefined : 'w-full sm:w-auto'}
              leftIcon={<X aria-hidden="true" className={icon} />}
              disabled={step.isPending}
              onClick={() => setDialog('decline')}
            >
              Decline
            </Button>
          </>
        )}
        {next === 'pickup' && (
          <Button
            size={size}
            className={main}
            leftIcon={<PackageCheck aria-hidden="true" className={icon} />}
            isLoading={step.isPending}
            onClick={() => run('pickup', `Picked up: take it to ${customer}.`)}
          >
            I have picked up the food
          </Button>
        )}
        {next === 'deliver' && (
          <Button
            size={size}
            className={main}
            leftIcon={<CheckCircle2 aria-hidden="true" className={icon} />}
            disabled={step.isPending}
            onClick={() => setDialog('deliver')}
          >
            Mark as delivered
          </Button>
        )}
      </div>

      <ConfirmationDialog
        open={dialog === 'decline'}
        title="Decline this delivery?"
        message={
          <div className="flex flex-col gap-3">
            <p>It goes to another partner, and it will not be offered to you again.</p>
            <Input
              label="Reason (optional)"
              maxLength={300}
              placeholder="Vehicle problem, too far, ..."
              value={declineReason}
              onChange={(event) => setDeclineReason(event.target.value)}
            />
          </div>
        }
        confirmLabel="Decline"
        tone="danger"
        isLoading={step.isPending}
        onConfirm={() => run('decline', 'Declined: the delivery goes to another partner.')}
        onCancel={() => setDialog(null)}
      />
      <ConfirmationDialog
        open={dialog === 'deliver'}
        title={cash ? 'Cash collected?' : 'Delivered?'}
        message={
          cash
            ? `Confirm you collected exactly ${amount} from ${customer} and handed over the food.`
            : `Confirm you handed the food to ${customer}. The order was paid online: there is nothing to collect.`
        }
        confirmLabel={cash ? `Yes, collected ${amount}` : 'Yes, delivered'}
        isLoading={step.isPending}
        onConfirm={() =>
          run('deliver', 'Delivered. Well done! You are available for the next delivery.')
        }
        onCancel={() => setDialog(null)}
      />
    </>
  );
}
