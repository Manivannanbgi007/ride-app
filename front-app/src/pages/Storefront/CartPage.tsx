import { ShoppingBag } from 'lucide-react';
import { useState } from 'react';
import { Link, useNavigate } from 'react-router';

import { toApiError } from '@/api/apiErrorHandler';
import { CartLines, CouponBox, PriceSummary } from '@/components/business/Cart';
import { Alert } from '@/components/common/Alert';
import { AsyncContent } from '@/components/common/AsyncContent';
import { Badge } from '@/components/common/Badge';
import { Button } from '@/components/common/Button';
import { Card } from '@/components/common/Card';
import { ConfirmationDialog } from '@/components/common/ConfirmationDialog';
import { EmptyState } from '@/components/common/EmptyState';
import { SkeletonLoader } from '@/components/common/Loader';
import { PageHeader } from '@/components/common/PageHeader';
import { buttonStyles } from '@/components/common/Button';
import { useToast } from '@/context/ToastContext';
import {
  useCart,
  useClearCart,
  useRemoveFromCart,
  useSetCartQuantity,
} from '@/hooks/api/cartHooks';
import { useStoreRestaurant } from '@/hooks/api/storefrontHooks';
import type { Cart } from '@/types/shopping';
import { CUSTOMER_ROUTES, customerRouteTo } from '@/utils/constants';
import { ERROR_MESSAGES } from '@/utils/errorMessages';

/** The cart: its dishes, the coupon, the price, and the way to checkout. */
export default function CartPage() {
  const cart = useCart();
  return (
    <div className="flex flex-col gap-6">
      <PageHeader title="Your cart" />
      <AsyncContent
        isLoading={cart.isPending}
        error={cart.error}
        isEmpty={cart.data?.items.length === 0}
        errorTitle={ERROR_MESSAGES.CART_LOAD_FAILED}
        onRetry={() => void cart.refetch()}
        isRetrying={cart.isFetching}
        loading={<SkeletonLoader lines={6} className="rounded-2xl bg-surface p-6" />}
        empty={
          <EmptyState
            icon={ShoppingBag}
            title="Your cart is empty"
            message="Add dishes from a restaurant's menu."
            action={
              <Link to={CUSTOMER_ROUTES.restaurants} className={buttonStyles()}>
                Find food
              </Link>
            }
          />
        }
      >
        {cart.data && <CartContents cart={cart.data} />}
      </AsyncContent>
    </div>
  );
}

function CartContents({ cart }: { cart: Cart }) {
  const navigate = useNavigate();
  const { showToast } = useToast();
  const setQuantity = useSetCartQuantity();
  const remove = useRemoveFromCart();
  const clear = useClearCart();
  const [confirmClear, setConfirmClear] = useState(false);
  const [busyItemId, setBusyItemId] = useState<number>();

  const changeQuantity = async (menuItemId: number, quantity: number) => {
    setBusyItemId(menuItemId);
    try {
      await (quantity === 0
        ? remove.mutateAsync(menuItemId)
        : setQuantity.mutateAsync({ menuItemId, quantity }));
    } catch (error) {
      showToast(toApiError(error).userMessage, 'error');
    } finally {
      setBusyItemId(undefined);
    }
  };

  return (
    <div className="grid gap-6 lg:grid-cols-[1fr_24rem]">
      <div className="flex flex-col gap-6">
        <Card
          title="Dishes"
          actions={
            <Button variant="ghost" size="sm" onClick={() => setConfirmClear(true)}>
              Clear cart
            </Button>
          }
        >
          {cart.restaurantId !== null && <CartRestaurant restaurantId={cart.restaurantId} />}
          <CartLines
            lines={cart.items}
            currency={cart.price.currency}
            busyItemId={busyItemId}
            onChangeQuantity={(menuItemId, quantity) => void changeQuantity(menuItemId, quantity)}
          />
        </Card>
        {cart.problems.length > 0 && (
          <Alert tone="warning" title="Before you check out">
            <ul className="list-disc pl-5">
              {cart.problems.map((problem) => (
                <li key={problem}>{problem}</li>
              ))}
            </ul>
          </Alert>
        )}
      </div>
      <div className="flex flex-col gap-6 lg:sticky lg:top-24 lg:self-start">
        <Card title="Coupon">
          <CouponBox coupon={cart.coupon} currency={cart.price.currency} />
        </Card>
        <Card title="Price">
          <div className="flex flex-col gap-5">
            <PriceSummary
              price={cart.price}
              couponCode={cart.coupon?.applied ? cart.coupon.code : null}
              showFreeDeliveryHint
            />
            <Button
              disabled={!cart.orderable}
              onClick={() => void navigate(CUSTOMER_ROUTES.checkout)}
            >
              Checkout
            </Button>
          </div>
        </Card>
      </div>
      <ConfirmationDialog
        open={confirmClear}
        title="Clear your cart?"
        message="Every dish and the coupon are taken out of the cart."
        confirmLabel="Clear cart"
        tone="danger"
        isLoading={clear.isPending}
        onConfirm={() =>
          clear.mutate(undefined, {
            onSettled: () => setConfirmClear(false),
            onError: (error) => showToast(toApiError(error).userMessage, 'error'),
          })
        }
        onCancel={() => setConfirmClear(false)}
      />
    </div>
  );
}

/** Which restaurant the dishes are from (the cart only knows its number), whether it is open, "add more". */
function CartRestaurant({ restaurantId }: { restaurantId: number }) {
  const restaurant = useStoreRestaurant(restaurantId);
  if (!restaurant.data) {
    return null;
  }
  return (
    <div className="mb-2 flex flex-wrap items-center justify-between gap-3 rounded-xl bg-page px-4 py-3">
      <p className="flex items-center gap-2 font-semibold">
        {restaurant.data.name}
        <Badge tone={restaurant.data.openNow ? 'success' : 'neutral'}>
          {restaurant.data.openNow ? 'Open now' : 'Closed now'}
        </Badge>
      </p>
      <Link
        to={customerRouteTo.restaurantMenu(restaurantId)}
        className="text-sm font-semibold text-brand-text hover:underline"
      >
        Add more dishes
      </Link>
    </div>
  );
}
