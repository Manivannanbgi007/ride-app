import { Navigate, useLocation } from 'react-router';

import { NotFoundPage } from '@/pages/NotFound/NotFoundPage';
import { CUSTOMER_BASE, LEGACY_CUSTOMER_PREFIXES } from '@/utils/constants';

/** True for "/orders" and "/orders/5", not for "/orders-of-a-customer". */
function isLegacyCustomerPath(pathname: string): boolean {
  return LEGACY_CUSTOMER_PREFIXES.some(
    (prefix) => pathname === prefix || pathname.startsWith(`${prefix}/`),
  );
}

/**
 * The customer area used to live at the root of the site. An old address (a bookmark, a link the assistant sent)
 * is sent on to the same page under /customer; any other unknown address is a "page not found".
 */
export function LegacyCustomerRedirect() {
  const { pathname, search, hash } = useLocation();
  if (isLegacyCustomerPath(pathname)) {
    return <Navigate to={`${CUSTOMER_BASE}${pathname}${search}${hash}`} replace />;
  }
  return <NotFoundPage />;
}
