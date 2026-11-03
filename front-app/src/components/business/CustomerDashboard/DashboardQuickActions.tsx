import {
  ClipboardList,
  LifeBuoy,
  MapPin,
  Navigation,
  ShoppingBag,
  Store,
  TicketPercent,
  type LucideIcon,
} from 'lucide-react';
import { Link } from 'react-router';

import { useCart } from '@/hooks/api/cartHooks';
import { useDashboardOrders } from '@/hooks/api/customerDashboardHooks';
import { cn } from '@/utils/classNames';
import { CUSTOMER_ROUTES, customerRouteTo } from '@/utils/constants';
import { cartItemCount, canTrackLive } from '@/utils/shoppingUtils';

import { PALETTE, type PaletteColour } from './palette';

interface QuickAction {
  to: string;
  icon: LucideIcon;
  label: string;
  /** A second line on wider screens */
  hint: string;
  colour: PaletteColour;
  /** A number on the icon (the dishes in the cart) */
  count?: number;
  /** The link's name when the label alone does not say enough (which order, how many dishes) */
  name?: string;
}

/**
 * The customer's everyday shortcuts, right under the banner: find food, the orders, the cart (with its number of
 * dishes), offers, addresses and help, plus "Track order" while an order is on its way (to its live map once the
 * restaurant has accepted it, before that to the order itself). Each tile is one link, big enough for a thumb.
 */
export function DashboardQuickActions() {
  const cart = useCart();
  const { active } = useDashboardOrders();
  const count = cartItemCount(cart.data);
  // The newest order still on its way (an unpaid one has its own "Pay now" in "On its way")
  const onItsWay = active.find((order) => order.status !== 'PAYMENT_PENDING');

  const actions: QuickAction[] = [
    {
      to: CUSTOMER_ROUTES.restaurants,
      icon: Store,
      label: 'Restaurants',
      hint: 'Open near you',
      colour: 'green',
    },
    ...(onItsWay
      ? [
          {
            to: canTrackLive(onItsWay.status)
              ? customerRouteTo.orderLiveTracking(onItsWay.id)
              : customerRouteTo.orderDetails(onItsWay.id),
            icon: Navigation,
            label: 'Track order',
            hint: `Order #${onItsWay.id}`,
            name: `Track order #${onItsWay.id}`,
            colour: 'teal' as const,
          },
        ]
      : []),
    {
      to: CUSTOMER_ROUTES.myOrders,
      icon: ClipboardList,
      label: 'My orders',
      hint: 'All of them',
      colour: 'violet',
    },
    {
      to: CUSTOMER_ROUTES.cart,
      icon: ShoppingBag,
      label: 'Cart',
      hint: count > 0 ? `${count} ${count === 1 ? 'dish' : 'dishes'}` : 'Empty',
      colour: 'sunset',
      count,
      name: count > 0 ? `Cart, ${count} ${count === 1 ? 'dish' : 'dishes'}` : 'Cart, empty',
    },
    {
      to: CUSTOMER_ROUTES.offers,
      icon: TicketPercent,
      label: 'Offers',
      hint: 'Coupons today',
      colour: 'amber',
    },
    {
      to: CUSTOMER_ROUTES.addresses,
      icon: MapPin,
      label: 'Addresses',
      hint: 'Where food goes',
      colour: 'sky',
    },
    {
      to: CUSTOMER_ROUTES.support,
      icon: LifeBuoy,
      label: 'Help',
      hint: 'Talk to us',
      colour: 'rose',
    },
  ];

  return (
    <nav aria-label="Quick actions">
      <ul className="grid grid-cols-4 gap-2 sm:grid-cols-[repeat(auto-fit,minmax(6.5rem,1fr))] sm:gap-3">
        {actions.map(({ to, icon: Icon, label, hint, colour, count: badge, name }) => (
          <li key={label}>
            <Link
              to={to}
              aria-label={name ?? label}
              className={cn(
                'group flex h-full min-h-16 flex-col items-center justify-center gap-1.5 rounded-2xl px-2 py-2.5 text-center ring-1 ring-line/50 transition',
                'hover:shadow-md focus-visible:ring-4 focus-visible:ring-brand-500/30 focus-visible:outline-none',
                'md:flex-row md:justify-start md:gap-3 md:px-3 md:text-left',
                PALETTE[colour].soft,
              )}
            >
              <span
                aria-hidden="true"
                className={cn(
                  'relative flex size-9 shrink-0 items-center justify-center rounded-xl text-white shadow-sm',
                  PALETTE[colour].fill,
                )}
              >
                <Icon className="size-[18px]" />
                {badge ? (
                  <span className="absolute -top-1.5 -right-1.5 flex h-5 min-w-5 items-center justify-center rounded-full bg-surface px-1 text-[10px] font-bold text-ink shadow ring-1 ring-line">
                    {badge > 99 ? '99+' : badge}
                  </span>
                ) : null}
              </span>
              <span className="flex min-w-0 flex-col">
                <span className="text-xs font-bold text-ink sm:text-sm">{label}</span>
                <span className="hidden truncate text-xs text-ink-soft md:block">{hint}</span>
              </span>
            </Link>
          </li>
        ))}
      </ul>
    </nav>
  );
}
