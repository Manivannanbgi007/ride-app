import { Plus } from 'lucide-react';
import { useState } from 'react';
import { Link, useLocation } from 'react-router';

import { toApiError } from '@/api/apiErrorHandler';
import { Button } from '@/components/common/Button';
import { ConfirmationDialog } from '@/components/common/ConfirmationDialog';
import { QuantityStepper } from '@/components/common/QuantityStepper';
import { buttonStyles } from '@/components/common/Button';
import { useToast } from '@/context/ToastContext';
import {
  useCart,
  useClearCart,
  useRemoveFromCart,
  useSetCartQuantity,
} from '@/hooks/api/cartHooks';
import { useAppSelector } from '@/store/hooks';
import { selectCustomer } from '@/store/slices/sessionSlice';
import type { MenuEntry } from '@/types/storefront';
import { customerRouteTo } from '@/utils/constants';
import { MAX_QUANTITY_PER_DISH } from '@/utils/shoppingUtils';

interface AddToCartProps {
  item: MenuEntry;
  restaurantId: number;
}

/**
 * "Add" next to a dish; once in the cart, − n +. Visitors are asked to sign in first (the cart belongs to a
 * customer). A cart holds one restaurant's dishes: adding from another restaurant offers to start a new cart.
 */
export function AddToCart({ item, restaurantId }: AddToCartProps) {
  const customer = useAppSelector(selectCustomer);
  const { pathname } = useLocation();
  const cart = useCart();
  const setQuantity = useSetCartQuantity();
  const remove = useRemoveFromCart();
  const clear = useClearCart();
  const { showToast } = useToast();
  const [confirmNewCart, setConfirmNewCart] = useState(false);

  if (!item.available) {
    return null;
  }
  if (!customer) {
    return (
      <Link
        to={customerRouteTo.signIn(pathname)}
        className={buttonStyles('secondary', 'sm')}
        aria-label={`Sign in to add ${item.name}`}
      >
        <Plus aria-hidden="true" className="size-4" />
        Add
      </Link>
    );
  }

  const line = cart.data?.items.find((cartLine) => cartLine.menuItemId === item.id);
  const fromOtherRestaurant =
    cart.data !== undefined &&
    cart.data.items.length > 0 &&
    cart.data.restaurantId !== restaurantId;

  const change = async (quantity: number) => {
    try {
      if (quantity === 0) {
        await remove.mutateAsync(item.id);
      } else {
        await setQuantity.mutateAsync({ menuItemId: item.id, quantity });
        if (quantity === 1) {
          showToast(`${item.name} is in your cart.`);
        }
      }
    } catch (error) {
      const apiError = toApiError(error);
      if (apiError.kind === 'conflict') {
        // Another restaurant's dishes arrived in the cart meanwhile (another tab)
        setConfirmNewCart(true);
      } else {
        showToast(apiError.userMessage, 'error');
      }
    }
  };

  const startNewCart = async () => {
    try {
      await clear.mutateAsync();
      await setQuantity.mutateAsync({ menuItemId: item.id, quantity: 1 });
      showToast(`Your new cart has ${item.name}.`);
    } catch (error) {
      showToast(toApiError(error).userMessage, 'error');
    } finally {
      setConfirmNewCart(false);
    }
  };

  return (
    <>
      {line ? (
        <QuantityStepper
          quantity={line.quantity}
          max={MAX_QUANTITY_PER_DISH}
          itemName={item.name}
          isBusy={setQuantity.isPending || remove.isPending}
          onChange={(quantity) => void change(quantity)}
        />
      ) : (
        <Button
          variant="secondary"
          size="sm"
          isLoading={setQuantity.isPending}
          disabled={cart.isPending}
          aria-label={`Add ${item.name}`}
          onClick={() => (fromOtherRestaurant ? setConfirmNewCart(true) : void change(1))}
          leftIcon={<Plus aria-hidden="true" className="size-4" />}
        >
          Add
        </Button>
      )}
      <ConfirmationDialog
        open={confirmNewCart}
        title="Start a new cart?"
        message="Your cart has dishes from another restaurant. A cart holds one restaurant's dishes: clear it and add this dish?"
        confirmLabel="Start a new cart"
        isLoading={clear.isPending || setQuantity.isPending}
        onConfirm={() => void startNewCart()}
        onCancel={() => setConfirmNewCart(false)}
      />
    </>
  );
}
