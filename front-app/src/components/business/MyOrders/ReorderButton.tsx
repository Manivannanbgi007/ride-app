import { RotateCcw } from 'lucide-react';
import { useState } from 'react';
import { useNavigate } from 'react-router';

import { Button, type ButtonVariant } from '@/components/common/Button';
import { ConfirmationDialog } from '@/components/common/ConfirmationDialog';
import { useToast } from '@/context/ToastContext';
import { useCart } from '@/hooks/api/cartHooks';
import { useReorder, type ReorderResult } from '@/hooks/api/customerDashboardHooks';
import type { OrderSummary } from '@/types/order';
import { CUSTOMER_ROUTES } from '@/utils/constants';

/** What reordering did, in one line for the toast. */
function messageOf(result: ReorderResult): { text: string; tone: 'success' | 'error' } {
  if (result.added === 0) {
    const reason = result.failed[0]?.reason ?? 'They are not available right now.';
    return { text: `None of these dishes could be added. ${reason}`, tone: 'error' };
  }
  const added = `${result.added} ${result.added === 1 ? 'dish' : 'dishes'} added to your cart.`;
  if (result.failed.length === 0) {
    return { text: added, tone: 'success' };
  }
  const names = result.failed.map((dish) => dish.name).join(', ');
  return { text: `${added} Not available right now: ${names}.`, tone: 'success' };
}

/**
 * "Reorder": puts a past order's dishes in the cart again (today's prices; the cart refuses what cannot be ordered
 * now) and opens the cart. A cart holding another restaurant's dishes is only emptied once the customer agrees.
 * Nothing is shown for an order whose dishes are unknown (an older answer without them).
 */
export function ReorderButton({
  order,
  variant = 'outline',
}: {
  order: OrderSummary;
  variant?: ButtonVariant;
}) {
  const cart = useCart();
  const reorder = useReorder();
  const navigate = useNavigate();
  const { showToast } = useToast();
  const [confirmNewCart, setConfirmNewCart] = useState(false);
  const items = order.items ?? [];

  if (items.length === 0) {
    return null;
  }

  const otherRestaurant =
    cart.data !== undefined &&
    cart.data.items.length > 0 &&
    cart.data.restaurantId !== order.restaurantId;

  const run = (replaceCart: boolean) =>
    reorder.mutate(
      { items, replaceCart },
      {
        onSuccess: (result) => {
          const message = messageOf(result);
          showToast(message.text, message.tone);
          if (result.added > 0) {
            void navigate(CUSTOMER_ROUTES.cart);
          }
        },
        onSettled: () => setConfirmNewCart(false),
      },
    );

  return (
    <>
      <Button
        variant={variant}
        size="sm"
        isLoading={reorder.isPending}
        disabled={cart.isPending}
        aria-label={`Reorder order #${order.id}`}
        leftIcon={<RotateCcw aria-hidden="true" className="size-4" />}
        onClick={() => (otherRestaurant ? setConfirmNewCart(true) : run(false))}
      >
        Reorder
      </Button>
      <ConfirmationDialog
        open={confirmNewCart}
        title="Start a new cart?"
        message={`Your cart has dishes from another restaurant. A cart holds one restaurant's dishes: empty it and add this order from ${order.restaurantName}?`}
        confirmLabel="Start a new cart"
        isLoading={reorder.isPending}
        onConfirm={() => run(true)}
        onCancel={() => setConfirmNewCart(false)}
      />
    </>
  );
}
