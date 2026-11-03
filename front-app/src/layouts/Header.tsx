import { Bell, Gift, LogOut, Menu, MessageSquare, Search, UserRound } from 'lucide-react';
import { useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router';

import { Badge } from '@/components/common/Badge';
import {
  HEADER_AVATAR,
  HEADER_CHIP,
  HEADER_ITEM,
  HEADER_TILE,
  HeaderIconLink,
} from '@/components/common/HeaderIcon';
import { Input } from '@/components/common/Input';
import { ThemeToggle } from '@/components/common/ThemeToggle';
import { useTooltip } from '@/components/common/Tooltip';
import { useToast } from '@/context/ToastContext';
import { useLayout } from '@/context/LayoutContext';
import { useAdminSignOut } from '@/hooks/api/authHooks';
import { useChatUnreadCount } from '@/hooks/api/chatHooks';
import { useUnreadNotificationCount } from '@/hooks/api/notificationHooks';
import { useCurrentUser } from '@/hooks/api/userHooks';
import { useMediaQuery, WIDE_SCREEN } from '@/hooks/useMediaQuery';
import { useScrolled } from '@/hooks/useScrolled';
import { SIDEBAR_ID } from '@/layouts/navigation';
import { cn } from '@/utils/classNames';
import { ROUTES, routeTo } from '@/utils/constants';
import { env } from '@/utils/env';
import { initialsOf } from '@/utils/formatUtils';

/** The bar at the top: search, notifications, coupons, and who is acting. */
export function Header() {
  const { openSidebar } = useLayout();
  const isWide = useMediaQuery(WIDE_SCREEN);
  const navigate = useNavigate();
  const [searchText, setSearchText] = useState('');
  const { data: user } = useCurrentUser();
  const { data: unreadCount } = useUnreadNotificationCount();
  const { data: chatUnread } = useChatUnreadCount();
  const scrolled = useScrolled();

  const search = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const text = searchText.trim();
    if (text) {
      void navigate(routeTo.search(text));
    }
  };

  return (
    // Always at the top: the page scrolls under it. data-app-header: the toasts appear just under it
    <header
      data-app-header
      className={cn(
        'sticky top-0 z-20 flex flex-wrap items-center gap-4 px-4 py-4 transition-[background-color,box-shadow] duration-300 sm:px-8 lg:px-10',
        // Clear at the top of the page (the background shows through); frosted once the page moves under it
        scrolled
          ? 'border-b border-line/70 bg-page/70 shadow-[0_8px_24px_-16px_rgb(16_40_28/0.25)] backdrop-blur-xl'
          : 'border-b border-transparent bg-transparent',
      )}
    >
      {/* Wide screens have their toggle on the sidebar's edge (Sidebar.tsx) */}
      {!isWide && <MenuButton onOpen={openSidebar} />}

      <form
        role="search"
        onSubmit={search}
        className="order-last w-full md:order-none md:max-w-xl md:flex-1"
      >
        <Input
          aria-label="Search"
          placeholder="Search here"
          value={searchText}
          onChange={(event) => setSearchText(event.target.value)}
          rightIcon={<Search aria-hidden="true" className="size-5" />}
          className="border-transparent shadow-card"
        />
      </form>

      {env.features.environmentBadge && <Badge tone="warning">{env.environmentName}</Badge>}

      <div className="ml-auto flex items-center gap-4">
        <HeaderIconLink
          to={ROUTES.notifications}
          label={unreadCount ? `Notifications (${unreadCount} unread)` : 'Notifications'}
          icon={Bell}
          count={unreadCount}
          motion="ring"
          pulse
        />
        <HeaderIconLink
          to={ROUTES.chat}
          label={chatUnread ? `Chat (${chatUnread} unread)` : 'Chat'}
          icon={MessageSquare}
          count={chatUnread}
          motion="pop"
        />
        <HeaderIconLink to={ROUTES.coupons} label="Coupons" icon={Gift} motion="pop" />
        <ThemeToggle />
        <span aria-hidden="true" className="hidden h-10 w-px bg-line sm:block" />
        <UserChip id={user?.id} fullName={user?.fullName ?? 'Guest'} />
        <SignOutButton />
      </div>
    </header>
  );
}

/**
 * "Hello, name" and the avatar, in the header's tile style. It opens the acting admin's own account (the admin's
 * customer page shows their role, status and change history).
 */
function UserChip({ id, fullName }: { id?: number; fullName: string }) {
  const { trigger, tooltip, hide } = useTooltip({ label: 'Your account', icon: UserRound });
  const content = (
    <span className={HEADER_CHIP}>
      <span aria-hidden="true" className={HEADER_AVATAR}>
        {initialsOf(fullName)}
      </span>
      <span className="hidden text-sm sm:block">
        Hello, <span className="font-semibold">{fullName}</span>
      </span>
    </span>
  );
  return id === undefined ? (
    content
  ) : (
    <>
      <Link
        to={routeTo.customerDetails(id)}
        aria-label={`Your account: ${fullName}`}
        className={HEADER_ITEM}
        {...trigger}
        onClick={hide}
      >
        {content}
      </Link>
      {tooltip}
    </>
  );
}

/** "Sign out": forgets the admin and clears what they looked at; the admin pages then lead to the sign-in. */
function SignOutButton() {
  const { trigger, tooltip, hide } = useTooltip({ label: 'Sign out', icon: LogOut });
  const signOut = useAdminSignOut();
  const { showToast } = useToast();

  return (
    <>
      <button
        type="button"
        aria-label="Sign out"
        {...trigger}
        onClick={() => {
          hide();
          signOut();
          showToast('You are signed out.');
        }}
        className={HEADER_ITEM}
      >
        <span className={HEADER_TILE}>
          <LogOut
            aria-hidden="true"
            className="size-5 group-hover/header:motion-safe:animate-pop"
          />
        </span>
      </button>
      {tooltip}
    </>
  );
}

/** The menu button of small screens: opens the navigation over the page. */
function MenuButton({ onOpen }: { onOpen: () => void }) {
  const { trigger, tooltip, hide } = useTooltip({ label: 'Open the navigation', icon: Menu });
  return (
    <>
      <button
        type="button"
        aria-label="Open the navigation"
        aria-controls={SIDEBAR_ID}
        {...trigger}
        onClick={() => {
          hide();
          onOpen();
        }}
        className={HEADER_ITEM}
      >
        <span className={HEADER_TILE}>
          <Menu aria-hidden="true" className="size-5 group-hover/header:motion-safe:animate-pop" />
        </span>
      </button>
      {tooltip}
    </>
  );
}
