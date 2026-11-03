import { ShoppingBag } from 'lucide-react';

import { HeaderIconLink } from '@/components/common/HeaderIcon';
import { useCart } from '@/hooks/api/cartHooks';
import { useAppSelector } from '@/store/hooks';
import { selectCustomer } from '@/store/slices/sessionSlice';
import { CUSTOMER_ROUTES } from '@/utils/constants';
import { cartItemCount } from '@/utils/shoppingUtils';

/** The header's cart, with how many dishes it holds (signed-in customers only). */
export function CartButton() {
  const customer = useAppSelector(selectCustomer);
  const cart = useCart();
  if (!customer) {
    return null;
  }
  const count = cartItemCount(cart.data);
  return (
    <HeaderIconLink
      to={CUSTOMER_ROUTES.cart}
      label={count > 0 ? `Cart (${count} ${count === 1 ? 'dish' : 'dishes'})` : 'Cart'}
      icon={ShoppingBag}
      count={count}
      motion="nudge"
    />
  );
}
