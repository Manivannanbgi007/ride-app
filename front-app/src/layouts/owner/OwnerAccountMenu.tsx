import { ChevronDown, LogOut, ShoppingBag } from 'lucide-react';
import { useEffect, useId, useRef, useState } from 'react';
import { Link, useNavigate } from 'react-router';

import { HEADER_AVATAR, HEADER_CHIP, HEADER_ITEM } from '@/components/common/HeaderIcon';
import { useToast } from '@/context/ToastContext';
import { useOwnerSignOut } from '@/hooks/api/authHooks';
import { useAppSelector } from '@/store/hooks';
import { selectOwner } from '@/store/slices/sessionSlice';
import { CUSTOMER_ROUTES, OWNER_ROUTES } from '@/utils/constants';
import { initialsOf } from '@/utils/formatUtils';

/** The owner's name in the header; it opens a small panel with their e-mail, the storefront and "Sign out". */
export function OwnerAccountMenu() {
  const owner = useAppSelector(selectOwner);
  const navigate = useNavigate();
  const signOut = useOwnerSignOut();
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

  if (!owner) {
    return null;
  }

  const leave = () => {
    setIsOpen(false);
    signOut();
    showToast('You are signed out.');
    void navigate(OWNER_ROUTES.signIn);
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
            {initialsOf(owner.fullName)}
          </span>
          <span className="hidden text-sm font-semibold sm:block">
            <span className="sr-only">Account of </span>
            {owner.fullName}
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
          <span className="block truncate text-sm font-semibold text-ink">{owner.email}</span>
        </p>
        <Link
          to={CUSTOMER_ROUTES.home}
          onClick={() => setIsOpen(false)}
          className="flex w-full items-center gap-2 rounded-xl px-3 py-2 text-sm hover:bg-page"
        >
          <ShoppingBag aria-hidden="true" className="size-4" />
          Customer storefront
        </Link>
        <button
          type="button"
          onClick={leave}
          className="flex w-full cursor-pointer items-center gap-2 rounded-xl px-3 py-2 text-sm hover:bg-page"
        >
          <LogOut aria-hidden="true" className="size-4" />
          Sign out
        </button>
      </div>
    </div>
  );
}
