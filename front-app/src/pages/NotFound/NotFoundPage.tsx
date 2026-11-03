import { Link, useLocation } from 'react-router';

import { buttonStyles } from '@/components/common/Button';
import { CUSTOMER_ROUTES, OWNER_ROUTES, PARTNER_ROUTES, ROUTES } from '@/utils/constants';
import { areaOf, type AppArea } from '@/utils/sessionUtils';

/** Where "back" leads from a missing page, by the area its address is in. */
const WAY_BACK: Record<AppArea, { to: string; label: string }> = {
  admin: { to: ROUTES.dashboard, label: 'Go to the dashboard' },
  owner: { to: OWNER_ROUTES.home, label: 'Go to your restaurant' },
  partner: { to: PARTNER_ROUTES.home, label: 'Go to your deliveries' },
  customer: { to: CUSTOMER_ROUTES.home, label: 'Go to the home page' },
};

/** An address that does not exist; leads back to the start of the area it is in. */
export function NotFoundPage() {
  const { pathname } = useLocation();
  const wayBack = WAY_BACK[areaOf(pathname)];

  return (
    <div className="flex min-h-[60vh] flex-col items-center justify-center gap-4 px-4 text-center">
      <p className="text-7xl font-extrabold text-brand-500">404</p>
      <h1 className="text-2xl font-bold">Page not found</h1>
      <p className="max-w-md text-sm text-ink-soft">
        The page you are looking for does not exist or has moved.
      </p>
      <Link to={wayBack.to} className={buttonStyles()}>
        {wayBack.label}
      </Link>
    </div>
  );
}
