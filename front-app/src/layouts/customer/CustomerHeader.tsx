import { LayoutDashboard } from 'lucide-react';
import { Link, NavLink } from 'react-router';

import { CartButton } from '@/components/business/Cart';
import { SearchBox } from '@/components/business/Storefront';
import { Badge } from '@/components/common/Badge';
import { HeaderIconLink } from '@/components/common/HeaderIcon';
import { ThemeToggle } from '@/components/common/ThemeToggle';
import { AccountMenu } from '@/layouts/customer/AccountMenu';
import { NotificationBell } from '@/layouts/customer/NotificationBell';
import { SupportLink } from '@/layouts/customer/SupportLink';
import { useAppSelector } from '@/store/hooks';
import { selectCustomer } from '@/store/slices/sessionSlice';
import { cn } from '@/utils/classNames';
import { CUSTOMER_ROUTES } from '@/utils/constants';
import { env } from '@/utils/env';

const NAV_LINKS = [
  { to: CUSTOMER_ROUTES.restaurants, label: 'Restaurants' },
  { to: CUSTOMER_ROUTES.offers, label: 'Offers' },
];

/** The signed-in customer's dashboard: a link in the main navigation (an icon next to the cart on a phone). */
const DASHBOARD_LINK = { to: CUSTOMER_ROUTES.dashboard, label: 'Dashboard' };

const navLinkStyles = ({ isActive }: { isActive: boolean }) =>
  cn(
    'rounded-xl px-3 py-2 text-sm font-semibold',
    isActive ? 'text-brand-text' : 'hover:text-brand-text',
  );

/**
 * The customer area's top bar: logo, Dashboard (signed in), Restaurants, Offers, search with suggestions, the cart
 * and the account.
 */
export function CustomerHeader() {
  const signedIn = useAppSelector(selectCustomer) !== null;
  return (
    // data-app-header: the toasts appear just under it (components/common/Toast)
    <header data-app-header className="sticky top-0 z-20 bg-surface shadow-card">
      <div className="mx-auto flex max-w-frame flex-wrap items-center gap-x-6 gap-y-3 px-4 py-3 sm:px-6 lg:px-8">
        <Link to={CUSTOMER_ROUTES.home} className="text-2xl font-extrabold tracking-tight">
          {env.appName}
          <span className="text-brand-500">.</span>
        </Link>
        <nav aria-label="Main" className="flex">
          {signedIn && (
            // On a phone the row has no room for it: the icon next to the cart stands in
            <NavLink
              to={DASHBOARD_LINK.to}
              className={(state) => cn(navLinkStyles(state), 'hidden items-center gap-1.5 md:flex')}
            >
              <LayoutDashboard aria-hidden="true" className="size-4" />
              {DASHBOARD_LINK.label}
            </NavLink>
          )}
          {NAV_LINKS.map((link) => (
            <NavLink key={link.to} to={link.to} className={navLinkStyles}>
              {link.label}
            </NavLink>
          ))}
        </nav>
        {env.features.environmentBadge && (
          <span className="hidden sm:block">
            <Badge tone="warning">{env.environmentName}</Badge>
          </span>
        )}
        <SearchBox className="order-last w-full md:order-none md:max-w-md md:flex-1" />
        <div className="ml-auto flex items-center gap-2">
          <ThemeToggle />
          {signedIn && (
            <span className="flex md:hidden">
              <HeaderIconLink
                to={DASHBOARD_LINK.to}
                label="My dashboard"
                icon={LayoutDashboard}
                motion="pop"
              />
            </span>
          )}
          <SupportLink />
          <NotificationBell />
          <CartButton />
          <AccountMenu />
        </div>
      </div>
    </header>
  );
}
