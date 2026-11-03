import { useQueryClient } from '@tanstack/react-query';
import { Check, ChefHat, PackageCheck, XCircle } from 'lucide-react';
import { useState, type ReactNode } from 'react';

import { toApiError } from '@/api/apiErrorHandler';
import {
  OWNER_ORDER_ACTION_LABELS,
  OWNER_ORDER_MESSAGES,
  ownerOrderActionDone,
  ownerOrderActions,
} from '@/components/business/OwnerOrders/ownerOrderUtils';
import { Button, type ButtonProps } from '@/components/common/Button';
import { ReasonDialog } from '@/components/common/ReasonDialog';
import { useToast } from '@/context/ToastContext';
import { useOwnerId, useOwnerOrderAction } from '@/hooks/api/ownerHooks';
import { useOwnerRestaurant } from '@/layouts/owner/ownerRestaurantContext';
import type { OrderStatus } from '@/types/order';
import type { OwnerOrderAction } from '@/types/owner';
import { QUERY_KEYS } from '@/utils/queryKeys';

const ACTION_ICONS: Record<OwnerOrderAction, ReactNode> = {
  accept: <Check aria-hidden="true" className="size-4" />,
  reject: <XCircle aria-hidden="true" className="size-4" />,
  preparing: <ChefHat aria-hidden="true" className="size-4" />,
  ready: <PackageCheck aria-hidden="true" className="size-4" />,
};

const ACTION_VARIANTS: Record<OwnerOrderAction, ButtonProps['variant']> = {
  accept: 'primary',
  reject: 'outline',
  preparing: 'primary',
  ready: 'primary',
};

interface OwnerOrderActionsProps {
  orderId: number;
  status: OrderStatus;
  size?: 'sm' | 'md';
}

/**
 * The kitchen's next step(s) for an order: Accept / Reject, Start preparing, Mark ready (see ownerOrderActions;
 * nothing is shown once the order is not the restaurant's to move). Reject asks for a reason first, because the
 * customer reads it. A refusal from the server (the order was changed meanwhile) is shown as it is worded.
 */
export function OwnerOrderActions({ orderId, status, size = 'sm' }: OwnerOrderActionsProps) {
  const { restaurantId } = useOwnerRestaurant();
  const ownerId = useOwnerId();
  const queryClient = useQueryClient();
  const { showToast } = useToast();
  const run = useOwnerOrderAction(restaurantId);
  const [isRejecting, setRejecting] = useState(false);
  const actions = ownerOrderActions(status);

  if (actions.length === 0) {
    return null;
  }

  // Throws when the server refuses, so the reject dialog can show the message itself
  const perform = async (action: OwnerOrderAction, reason?: string) => {
    try {
      await run.mutateAsync({ orderId, action, reason });
      showToast(ownerOrderActionDone(action, orderId));
    } catch (error) {
      // 409: somebody (the customer, another device) already moved the order: load it as it is now
      if (toApiError(error).kind === 'conflict') {
        void queryClient.invalidateQueries({
          queryKey: QUERY_KEYS.owner.ordersAll(ownerId, restaurantId),
        });
      }
      throw error;
    }
  };

  const click = (action: OwnerOrderAction) => {
    if (action === 'reject') {
      setRejecting(true);
      return;
    }
    perform(action).catch((error: unknown) => showToast(toApiError(error).userMessage, 'error'));
  };

  return (
    <div className="flex flex-wrap items-center gap-2">
      {actions.map((action) => (
        <Button
          key={action}
          size={size}
          variant={ACTION_VARIANTS[action]}
          leftIcon={ACTION_ICONS[action]}
          aria-label={`${OWNER_ORDER_ACTION_LABELS[action]} order #${orderId}`}
          isLoading={run.isPending && run.variables.action === action}
          disabled={run.isPending}
          onClick={() => click(action)}
        >
          {OWNER_ORDER_ACTION_LABELS[action]}
        </Button>
      ))}
      {actions.includes('reject') && (
        <ReasonDialog
          open={isRejecting}
          title={`Reject order #${orderId}?`}
          description="The customer sees your reason, so say it kindly and clearly. This cannot be undone."
          confirmLabel="Reject order"
          tone="danger"
          reasonLabel="Reason for the customer"
          placeholder={OWNER_ORDER_MESSAGES.REJECT_REASON_PLACEHOLDER}
          requiredMessage={OWNER_ORDER_MESSAGES.REJECT_REASON_REQUIRED}
          onSubmit={(reason) => perform('reject', reason)}
          onClose={() => setRejecting(false)}
        />
      )}
    </div>
  );
}
