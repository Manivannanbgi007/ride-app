import { Link, useLocation, useNavigate } from 'react-router';

import { toApiError } from '@/api/apiErrorHandler';
import { Button, buttonStyles, type ButtonVariant } from '@/components/common/Button';
import { useToast } from '@/context/ToastContext';
import { useApplyCoupon } from '@/hooks/api/cartHooks';
import { useAppSelector } from '@/store/hooks';
import { selectCustomer } from '@/store/slices/sessionSlice';
import type { Offer } from '@/types/shopping';
import { CUSTOMER_ROUTES, customerRouteTo } from '@/utils/constants';
import { formatCurrency } from '@/utils/formatUtils';

interface UseOfferProps {
  offer: Offer;
  cartHasItems: boolean;
  variant?: ButtonVariant;
  className?: string;
}

/**
 * What a customer can do with an offer right now: sign in first; with an empty cart, go and find food (the
 * restaurant's menu for a restaurant's own offer); otherwise apply it to the cart (the cart says whether it fits).
 */
export function UseOffer({ offer, cartHasItems, variant = 'primary', className }: UseOfferProps) {
  const customer = useAppSelector(selectCustomer);
  const { pathname } = useLocation();
  const apply = useApplyCoupon();
  const navigate = useNavigate();
  const { showToast } = useToast();

  if (!customer) {
    return (
      <Link
        to={customerRouteTo.signIn(pathname)}
        className={buttonStyles(variant, 'sm', className)}
      >
        Sign in to use
      </Link>
    );
  }
  if (!cartHasItems) {
    return (
      <Link
        to={
          offer.restaurantId === null
            ? CUSTOMER_ROUTES.restaurants
            : customerRouteTo.restaurantMenu(offer.restaurantId)
        }
        className={buttonStyles(variant, 'sm', className)}
      >
        Order now
      </Link>
    );
  }
  return (
    <Button
      variant={variant}
      size="sm"
      className={className}
      isLoading={apply.isPending}
      onClick={() =>
        apply.mutate(offer.code, {
          onSuccess: (cart) => {
            const coupon = cart.coupon;
            showToast(
              coupon?.applied
                ? `${coupon.code} applied: you save ${formatCurrency(coupon.discountAmount, cart.price.currency)}.`
                : `${offer.code} is in your cart.`,
            );
            void navigate(CUSTOMER_ROUTES.cart);
          },
          // The reason from promotion-service: "Add items worth 120.00 more to use this coupon ..."
          onError: (error) => showToast(toApiError(error).userMessage, 'error'),
        })
      }
    >
      Apply to cart
    </Button>
  );
}
