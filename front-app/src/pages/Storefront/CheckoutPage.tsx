import { zodResolver } from '@hookform/resolvers/zod';
import { Plus } from 'lucide-react';
import { useState } from 'react';
import { Controller, useForm } from 'react-hook-form';
import { Link, useNavigate } from 'react-router';

import { toApiError } from '@/api/apiErrorHandler';
import { AddressFormDialog } from '@/components/business/Address';
import { PriceSummary } from '@/components/business/Cart';
import { Alert } from '@/components/common/Alert';
import { AsyncContent } from '@/components/common/AsyncContent';
import { BackLink } from '@/components/common/BackLink';
import { Button } from '@/components/common/Button';
import { Card } from '@/components/common/Card';
import { EmptyState } from '@/components/common/EmptyState';
import { SkeletonLoader } from '@/components/common/Loader';
import { PageHeader } from '@/components/common/PageHeader';
import { RadioCards } from '@/components/common/RadioCards';
import { Textarea } from '@/components/common/Textarea';
import { useToast } from '@/context/ToastContext';
import { useAddresses } from '@/hooks/api/addressHooks';
import { useCart, usePlaceOrder } from '@/hooks/api/cartHooks';
import { useStoreRestaurant } from '@/hooks/api/storefrontHooks';
import { PAYMENT_METHODS, type PaymentMethod } from '@/types/order';
import type { Address, Cart } from '@/types/shopping';
import {
  ADDRESS_TYPE_LABELS,
  CUSTOMER_ROUTES,
  customerRouteTo,
  PAYMENT_METHOD_LABELS,
} from '@/utils/constants';
import { ERROR_MESSAGES } from '@/utils/errorMessages';
import { formatCurrency } from '@/utils/formatUtils';
import { addressLines, keepsIdempotencyKey } from '@/utils/shoppingUtils';
import { checkoutFormSchema, type CheckoutForm } from '@/utils/validationUtils';
import { newUuid } from '@/utils/idUtils';

/** Online first, cash last (as most customers pay). */
const METHOD_ORDER: readonly PaymentMethod[] = ['UPI', 'CARD', 'WALLET', 'CASH_ON_DELIVERY'];
const METHOD_HINTS: Record<PaymentMethod, string> = {
  UPI: 'Pay now with your UPI id',
  CARD: 'Pay now by debit or credit card',
  WALLET: 'Pay now from your wallet',
  CASH_ON_DELIVERY: 'Pay the delivery partner when the food arrives',
};

/** Checkout: where the food goes, how it is paid, a note; "Place order" turns the cart into an order. */
export default function CheckoutPage() {
  const cart = useCart();
  const addresses = useAddresses();

  return (
    <div className="flex flex-col gap-6">
      <BackLink to={CUSTOMER_ROUTES.cart}>Back to the cart</BackLink>
      <PageHeader title="Checkout" />
      <AsyncContent
        isLoading={cart.isPending || addresses.isPending}
        error={cart.error ?? addresses.error}
        errorTitle={
          cart.error ? ERROR_MESSAGES.CART_LOAD_FAILED : ERROR_MESSAGES.ADDRESSES_LOAD_FAILED
        }
        onRetry={() => void Promise.all([cart.refetch(), addresses.refetch()])}
        loading={<SkeletonLoader lines={8} className="rounded-2xl bg-surface p-6" />}
        isEmpty={cart.data?.items.length === 0}
        empty={
          <EmptyState
            title="Your cart is empty"
            action={
              <Link
                to={CUSTOMER_ROUTES.restaurants}
                className="text-sm font-semibold text-brand-text hover:underline"
              >
                Find food
              </Link>
            }
          />
        }
      >
        {cart.data && addresses.data && (
          <CheckoutForm cart={cart.data} addresses={addresses.data} />
        )}
      </AsyncContent>
    </div>
  );
}

function CheckoutForm({ cart, addresses }: { cart: Cart; addresses: Address[] }) {
  const navigate = useNavigate();
  const { showToast } = useToast();
  const placeOrder = usePlaceOrder();
  const [addingAddress, setAddingAddress] = useState(false);
  // One key per checkout attempt: a retry after a timeout returns the order already created, never a second one
  const [idempotencyKey, setIdempotencyKey] = useState(() => newUuid());
  const defaultAddress = addresses.find((address) => address.defaultAddress) ?? addresses[0];
  const {
    control,
    register,
    handleSubmit,
    setValue,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<CheckoutForm>({
    resolver: zodResolver(checkoutFormSchema),
    defaultValues: {
      addressId: defaultAddress ? String(defaultAddress.id) : '',
      paymentMethod: 'UPI',
      notes: '',
    },
  });

  const submit = handleSubmit(async ({ addressId, paymentMethod, notes }) => {
    try {
      const order = await placeOrder.mutateAsync({
        request: { addressId: Number(addressId), paymentMethod, notes: notes || undefined },
        idempotencyKey,
      });
      if (order.status === 'PAYMENT_PENDING') {
        void navigate(customerRouteTo.orderPayment(order.id), { replace: true });
      } else {
        showToast(`Order #${order.id} is placed: pay the partner when it arrives.`);
        void navigate(customerRouteTo.orderDetails(order.id), { replace: true });
      }
    } catch (error) {
      if (!keepsIdempotencyKey(error)) {
        setIdempotencyKey(newUuid());
      }
      setError('root.server', { message: toApiError(error).userMessage });
    }
  });

  return (
    <>
      <form
        onSubmit={(event) => void submit(event)}
        noValidate
        className="grid gap-6 lg:grid-cols-[1fr_24rem]"
      >
        <div className="flex flex-col gap-6">
          <Card
            title="Deliver to"
            actions={
              <Button
                variant="ghost"
                size="sm"
                onClick={() => setAddingAddress(true)}
                disabled={addresses.length >= 10}
                leftIcon={<Plus aria-hidden="true" className="size-4" />}
              >
                New address
              </Button>
            }
          >
            {addresses.length === 0 ? (
              <div className="flex flex-col items-start gap-3">
                <p className="text-sm text-ink-soft">You have no saved address yet.</p>
                <Button variant="outline" onClick={() => setAddingAddress(true)}>
                  Add your address
                </Button>
                {errors.addressId && (
                  <p className="text-xs text-danger-500">{errors.addressId.message}</p>
                )}
              </div>
            ) : (
              <Controller
                control={control}
                name="addressId"
                render={({ field }) => (
                  <RadioCards
                    legend="Delivery address"
                    columns={2}
                    value={field.value}
                    onChange={field.onChange}
                    error={errors.addressId?.message}
                    options={addresses.map((address) => ({
                      value: String(address.id),
                      label: `${ADDRESS_TYPE_LABELS[address.type]}${address.defaultAddress ? ' (default)' : ''}`,
                      description: addressLines(address).join(', '),
                    }))}
                  />
                )}
              />
            )}
          </Card>
          <Card title="Payment">
            <Controller
              control={control}
              name="paymentMethod"
              render={({ field }) => (
                <RadioCards
                  legend="How do you pay?"
                  columns={2}
                  value={field.value}
                  onChange={field.onChange}
                  error={errors.paymentMethod?.message}
                  options={METHOD_ORDER.filter((method) => PAYMENT_METHODS.includes(method)).map(
                    (method) => ({
                      value: method,
                      label: PAYMENT_METHOD_LABELS[method],
                      description: METHOD_HINTS[method],
                    }),
                  )}
                />
              )}
            />
          </Card>
          <Card title="Note for the restaurant">
            <Textarea
              aria-label="Note for the restaurant"
              placeholder="Less spicy, no onions, ring the bell twice... (optional)"
              maxLength={500}
              rows={3}
              error={errors.notes?.message}
              {...register('notes')}
            />
          </Card>
        </div>
        <div className="flex flex-col gap-4 lg:sticky lg:top-24 lg:self-start">
          <Card title="Your order">
            <ul className="mb-4 flex flex-col gap-1 text-sm">
              {cart.items.map((line) => (
                <li key={line.menuItemId} className="flex justify-between gap-3">
                  <span>
                    {line.quantity} × {line.name ?? `Dish #${line.menuItemId}`}
                  </span>
                  <span>{formatCurrency(line.lineTotal, cart.price.currency)}</span>
                </li>
              ))}
            </ul>
            <PriceSummary
              price={cart.price}
              couponCode={cart.coupon?.applied ? cart.coupon.code : null}
            />
          </Card>
          {cart.restaurantId !== null && <ClosedWarning restaurantId={cart.restaurantId} />}
          {!cart.orderable && (
            <Alert tone="warning" title="Your cart needs a change">
              {cart.problems.join(' ')}{' '}
              <Link to={CUSTOMER_ROUTES.cart} className="font-semibold underline">
                Go to the cart
              </Link>
            </Alert>
          )}
          {errors.root?.server && (
            <Alert tone="error" title="The order was not placed">
              {errors.root.server.message}
            </Alert>
          )}
          <Button type="submit" isLoading={isSubmitting} disabled={!cart.orderable}>
            Place order · {formatCurrency(cart.price.totalAmount, cart.price.currency)}
          </Button>
        </div>
      </form>
      {/* Outside the checkout form: its own form's submit must not reach the checkout */}
      {addingAddress && (
        <AddressFormDialog
          isFirst={addresses.length === 0}
          onSaved={(address) => setValue('addressId', String(address.id), { shouldValidate: true })}
          onClose={() => setAddingAddress(false)}
        />
      )}
    </>
  );
}

/** A closed restaurant takes no orders (checkout says so too): warned before the customer tries. */
function ClosedWarning({ restaurantId }: { restaurantId: number }) {
  const restaurant = useStoreRestaurant(restaurantId);
  if (!restaurant.data || restaurant.data.openNow) {
    return null;
  }
  return (
    <Alert tone="warning" title={`${restaurant.data.name} is closed right now`}>
      It takes orders during its opening hours.{' '}
      <Link to={customerRouteTo.restaurantMenu(restaurantId)} className="font-semibold underline">
        See the hours
      </Link>
    </Alert>
  );
}
