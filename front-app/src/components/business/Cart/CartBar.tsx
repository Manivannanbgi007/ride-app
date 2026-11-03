import { ShoppingBag } from 'lucide-react';
import { Link } from 'react-router';

import { useCart } from '@/hooks/api/cartHooks';
import { CUSTOMER_ROUTES } from '@/utils/constants';
import { formatCurrency } from '@/utils/formatUtils';
import { cartItemCount } from '@/utils/shoppingUtils';

/** At the bottom of a restaurant's menu while its dishes are in the cart: how many, the total, "View cart". */
export function CartBar({ restaurantId }: { restaurantId: number }) {
  const cart = useCart();
  const count = cartItemCount(cart.data);
  if (!cart.data || count === 0 || cart.data.restaurantId !== restaurantId) {
    return null;
  }
  return (
    <div className="sticky bottom-4 z-10">
      <Link
        to={CUSTOMER_ROUTES.cart}
        className="flex items-center justify-between gap-4 rounded-2xl bg-brand-500 px-6 py-4 text-on-brand shadow-brand-lg transition-[translate,box-shadow,background-color] duration-200 hover:bg-brand-400 hover:shadow-[0_16px_32px_-10px_rgb(57_197_118/0.65)] motion-safe:hover:-translate-y-0.5"
      >
        <span className="flex items-center gap-3 font-semibold">
          <ShoppingBag aria-hidden="true" className="size-5" />
          {count} {count === 1 ? 'dish' : 'dishes'} ·{' '}
          {formatCurrency(cart.data.price.totalAmount, cart.data.price.currency)}
        </span>
        <span className="font-bold">View cart</span>
      </Link>
    </div>
  );
}
