import {
  ChevronDown,
  ClipboardList,
  LayoutDashboard,
  LogOut,
  MapPin,
  MessageSquare,
  UserRound,
  type LucideIcon,
} from 'lucide-react';
import { useEffect, useId, useRef, useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router';

import { buttonStyles } from '@/components/common/Button';
import { HEADER_AVATAR, HEADER_CHIP, HEADER_ITEM } from '@/components/common/HeaderIcon';
import { useToast } from '@/context/ToastContext';
import { useSignOut } from '@/hooks/api/authHooks';
import { useAppSelector } from '@/store/hooks';
import { selectCustomer } from '@/store/slices/sessionSlice';
import { CUSTOMER_ROUTES, customerRouteTo } from '@/utils/constants';
import { initialsOf } from '@/utils/formatUtils';

const ACCOUNT_LINKS: readonly { to: string; label: string; icon: LucideIcon }[] = [
  { to: CUSTOMER_ROUTES.dashboard, label: 'My dashboard', icon: LayoutDashboard },
  { to: CUSTOMER_ROUTES.myOrders, label: 'My orders', icon: ClipboardList },
  { to: CUSTOMER_ROUTES.profile, label: 'My profile', icon: UserRound },
  { to: CUSTOMER_ROUTES.addresses, label: 'My addresses', icon: MapPin },
  { to: CUSTOMER_ROUTES.support, label: 'Support', icon: MessageSquare },
];

/**
 * The header's right side: "Sign in" and "Register" for visitors; for a signed-in customer their name, which
 * opens a small panel with their orders, profile, addresses and "Sign out".
 */
export function AccountMenu() {
  const customer = useAppSelector(selectCustomer);
  const { pathname, search } = useLocation();
  const navigate = useNavigate();
  const signOut = useSignOut();
  const { showToast } = useToast();
  const [isOpen, setIsOpen] = useState(false);
  const panelId = useId();
  const container = useRef<HTMLDivElement>(null);

  // Closes on a click elsewhere or Escape
  useEffect(() => {
    if (!isOpen) {
      return;
    }
    const onClick = (event: MouseEvent) => {
      if (!container.current?.contains(event.target as Node)) {
        setIsOpen(false);
      }
    };
    const onKey = (event: globalThis.KeyboardEvent) => {
      if (event.key === 'Escape') {
        setIsOpen(false);
      }
    };
    document.addEventListener('mousedown', onClick);
    document.addEventListener('keydown', onKey);
    return () => {
      document.removeEventListener('mousedown', onClick);
      document.removeEventListener('keydown', onKey);
    };
  }, [isOpen]);

  if (!customer) {
    const isAuthPage = pathname === CUSTOMER_ROUTES.signIn || pathname === CUSTOMER_ROUTES.register;
    return (
      <div className="flex items-center gap-2">
        <Link
          to={isAuthPage ? CUSTOMER_ROUTES.signIn : customerRouteTo.signIn(pathname + search)}
          className={buttonStyles('ghost', 'sm')}
        >
          Sign in
        </Link>
        <Link to={CUSTOMER_ROUTES.register} className={buttonStyles('primary', 'sm')}>
          Register
        </Link>
      </div>
    );
  }

  const leave = () => {
    setIsOpen(false);
    signOut();
    showToast('You are signed out.');
    void navigate(CUSTOMER_ROUTES.home);
  };

  return (
    <div ref={container} className="relative">
      <button
        type="button"
        aria-expanded={isOpen}
        aria-controls={panelId}
        onClick={() => setIsOpen((open) => !open)}
        className={HEADER_ITEM}
      >
        <span className={HEADER_CHIP}>
          <span aria-hidden="true" className={HEADER_AVATAR}>
            {initialsOf(customer.fullName)}
          </span>
          <span className="hidden text-sm font-semibold sm:block">
            <span className="sr-only">Account of </span>
            {customer.fullName}
          </span>
          <ChevronDown
            aria-hidden="true"
            className={`size-4 transition-transform duration-200 ${isOpen ? 'rotate-180' : ''}`}
          />
        </span>
      </button>
      <div
        id={panelId}
        hidden={!isOpen}
        className="absolute right-0 z-30 mt-2 w-64 rounded-2xl bg-surface p-2 shadow-lg"
      >
        <p className="px-3 py-2 text-xs text-ink-soft">
          Signed in as
          <span className="block truncate text-sm font-semibold text-ink">{customer.email}</span>
        </p>
        {ACCOUNT_LINKS.map(({ to, label, icon: Icon }) => (
          <Link
            key={to}
            to={to}
            onClick={() => setIsOpen(false)}
            className="flex w-full items-center gap-2 rounded-xl px-3 py-2 text-sm hover:bg-page"
          >
            <Icon aria-hidden="true" className="size-4" />
            {label}
          </Link>
        ))}
        <button
          type="button"
          onClick={leave}
          className="flex w-full items-center gap-2 rounded-xl px-3 py-2 text-left text-sm hover:bg-page"
        >
          <LogOut aria-hidden="true" className="size-4" />
          Sign out
        </button>
      </div>
    </div>
  );
}
