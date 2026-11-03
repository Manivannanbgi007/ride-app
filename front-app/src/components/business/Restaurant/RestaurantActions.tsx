import { useState } from 'react';

import { Button, type ButtonProps } from '@/components/common/Button';
import { ReasonDialog } from '@/components/common/ReasonDialog';
import type { RestaurantAction, RestaurantDetails } from '@/types/restaurant';
import { allowedRestaurantActions } from '@/utils/restaurantUtils';

/** How each action is shown and explained. The owner sees the reason. */
const ACTION_TEXTS: Record<
  RestaurantAction,
  { label: string; variant: NonNullable<ButtonProps['variant']>; description: string }
> = {
  approve: {
    label: 'Approve',
    variant: 'primary',
    description:
      'The documents are in order. The restaurant is approved; activate it to make it visible to customers.',
  },
  reject: {
    label: 'Reject',
    variant: 'danger',
    description:
      'The owner is told the reason and can correct the details and submit the restaurant again.',
  },
  activate: {
    label: 'Activate',
    variant: 'primary',
    description: 'The restaurant goes live: customers can find it and order from it.',
  },
  deactivate: {
    label: 'Deactivate',
    variant: 'outline',
    description:
      'Customers can no longer find or order from the restaurant until it is activated again.',
  },
  suspend: {
    label: 'Suspend',
    variant: 'danger',
    description:
      'For a policy problem: the restaurant disappears for customers until an admin activates it again.',
  },
};

interface RestaurantActionsProps {
  restaurant: RestaurantDetails;
  onAction: (action: RestaurantAction, reason: string) => Promise<unknown>;
}

/** Buttons for the status changes allowed now; each one asks for the reason first. */
export function RestaurantActions({ restaurant, onAction }: RestaurantActionsProps) {
  const [openAction, setOpenAction] = useState<RestaurantAction | null>(null);
  const actions = allowedRestaurantActions(restaurant.status);

  if (actions.length === 0) {
    return null;
  }

  return (
    <div className="flex flex-wrap gap-3">
      {actions.map((action) => (
        <Button
          key={action}
          variant={ACTION_TEXTS[action].variant}
          onClick={() => setOpenAction(action)}
        >
          {ACTION_TEXTS[action].label}
        </Button>
      ))}
      {openAction && (
        <ReasonDialog
          open
          title={`${ACTION_TEXTS[openAction].label} ${restaurant.name}?`}
          description={ACTION_TEXTS[openAction].description}
          confirmLabel={ACTION_TEXTS[openAction].label}
          tone={ACTION_TEXTS[openAction].variant === 'danger' ? 'danger' : 'primary'}
          reasonLabel="Reason (the owner sees it)"
          onSubmit={(reason) => onAction(openAction, reason)}
          onClose={() => setOpenAction(null)}
        />
      )}
    </div>
  );
}
