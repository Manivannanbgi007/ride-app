import { ChefHat, ChevronLeft, ChevronRight, Plus, type LucideIcon } from 'lucide-react';
import { Link, NavLink } from 'react-router';

import { useTooltip } from '@/components/common/Tooltip';
import { useLayout } from '@/context/LayoutContext';
import { useChatUnreadCount } from '@/hooks/api/chatHooks';
import { useMediaQuery, WIDE_SCREEN } from '@/hooks/useMediaQuery';
import { Footer } from '@/layouts/Footer';
import { NAVIGATION, SIDEBAR_ID } from '@/layouts/navigation';
import { cn } from '@/utils/classNames';
import { ROUTES } from '@/utils/constants';
import { env } from '@/utils/env';

/** The open sidebar's width: 18rem less 5%, to leave the pages more room. */
const OPEN_WIDTH = 'w-[17.1rem]';
/** The fixed width of what only the open sidebar shows (so it does not re-wrap while the sidebar narrows). */
const OPEN_CONTENT_WIDTH = 'w-[14.1rem]';

/**
 * The navigation on the left.
 * Wide screens: part of the page; the header's toggle narrows it to a rail of icons (with the page widening) and
 * back, sliding smoothly; in the rail each icon shows its title beside it on hover or keyboard focus.
 * Small screens: slides in over the page when the header's menu button is pressed, and closes again after a click.
 */
export function Sidebar() {
  const { isSidebarOpen, closeSidebar, isSidebarCollapsed, toggleSidebarCollapsed } = useLayout();
  const isWide = useMediaQuery(WIDE_SCREEN);
  // The rail exists on wide screens only; the small-screen menu is always the full one
  const rail = isSidebarCollapsed && isWide;
  // Conversations with messages nobody on the staff has read: the number on "Chat"
  const { data: chatUnread = 0 } = useChatUnreadCount();

  return (
    <>
      {isSidebarOpen && (
        <div
          aria-hidden="true"
          className="fixed inset-0 z-30 bg-scrim/40 lg:hidden"
          onClick={closeSidebar}
        />
      )}
      <aside
        id={SIDEBAR_ID}
        className={cn(
          'fixed inset-y-0 left-0 z-40 flex shrink-0 flex-col overflow-x-hidden overflow-y-auto border-r border-line bg-surface/90 backdrop-blur-xl',
          // Still scrolls, but with no scrollbar along the edge the toggle sits on
          '[scrollbar-width:none] [&::-webkit-scrollbar]:hidden',
          'transition-[translate,width] duration-300 ease-in-out lg:sticky lg:top-0 lg:h-screen lg:translate-x-0',
          OPEN_WIDTH,
          rail && 'lg:w-20',
          isSidebarOpen ? 'translate-x-0' : '-translate-x-full',
        )}
      >
        <Brand rail={rail} onNavigate={closeSidebar} />

        <nav aria-label="Main" className="flex flex-col gap-1">
          {NAVIGATION.map((entry) => (
            <NavItem
              key={entry.path}
              label={entry.label}
              path={entry.path}
              icon={entry.icon}
              rail={rail}
              badge={entry.path === ROUTES.chat ? chatUnread : 0}
              onNavigate={closeSidebar}
            />
          ))}
        </nav>

        <div
          aria-hidden={rail || undefined}
          className={cn(
            'mx-6 mt-10 mb-8 transition-[opacity,visibility] duration-200',
            OPEN_CONTENT_WIDTH,
            rail && 'invisible opacity-0',
          )}
        >
          <div className="flex items-center gap-3 rounded-2xl bg-linear-to-br from-brand-600 to-brand-800 p-5 text-white shadow-card">
            <div className="flex-1">
              <p className="text-xs leading-relaxed">
                Keep the menus tidy: review the dishes on offer.
              </p>
              <Link
                to={ROUTES.foods}
                onClick={closeSidebar}
                className="mt-3 inline-flex items-center gap-1 rounded-lg bg-white px-3 py-1.5 text-xs font-semibold text-on-accent hover:bg-brand-50"
              >
                <Plus aria-hidden="true" className="size-3.5" />
                Review Menus
              </Link>
            </div>
            <span className="flex size-14 shrink-0 items-center justify-center rounded-full bg-white/20">
              <ChefHat aria-hidden="true" className="size-8" />
            </span>
          </div>
        </div>

        <div
          className={cn(
            'mt-auto transition-[opacity,visibility] duration-200',
            OPEN_WIDTH,
            rail && 'invisible opacity-0',
          )}
        >
          <Footer />
        </div>
      </aside>
      {isWide && <EdgeToggle collapsed={isSidebarCollapsed} onToggle={toggleSidebarCollapsed} />}
    </>
  );
}

/**
 * The wide-screen toggle: a round button on the sidebar's right edge that narrows it to its icons and opens it
 * again. It sits outside the sidebar (which clips its own overflow) and follows the edge as the width slides.
 */
function EdgeToggle({ collapsed, onToggle }: { collapsed: boolean; onToggle: () => void }) {
  const Icon = collapsed ? ChevronRight : ChevronLeft;
  const label = collapsed ? 'Expand the menu' : 'Collapse the menu';
  const { trigger, tooltip, hide } = useTooltip({ label, icon: Icon, placement: 'right' });
  return (
    <>
      <button
        type="button"
        aria-label={label}
        aria-controls={SIDEBAR_ID}
        aria-expanded={!collapsed}
        {...trigger}
        onClick={() => {
          hide();
          onToggle();
        }}
        className={cn(
          // Level with the logo, centred on the sidebar's edge
          'fixed top-[3.875rem] z-40 flex size-8 -translate-x-1/2 -translate-y-1/2 items-center justify-center rounded-full border border-line bg-surface text-brand-text shadow-card',
          'transition-[left,background-color,color,border-color] duration-300 ease-in-out hover:border-brand-500 hover:bg-brand-500 hover:text-on-brand motion-safe:active:scale-95',
          'focus-visible:ring-4 focus-visible:ring-brand-500/30 focus-visible:outline-none',
          collapsed ? 'left-20' : 'left-[17.1rem]',
        )}
      >
        <Icon aria-hidden="true" className="size-4" />
      </button>
      {tooltip}
    </>
  );
}

/** The application's name; in the rail a small mark with its first letter. */
function Brand({ rail, onNavigate }: { rail: boolean; onNavigate: () => void }) {
  return (
    <Link
      to={ROUTES.dashboard}
      onClick={onNavigate}
      aria-label={`${env.appName} Food Delivery Admin: dashboard`}
      className="relative block h-32 shrink-0"
    >
      <span
        className={cn(
          'absolute top-10 left-8 whitespace-nowrap transition-opacity duration-200',
          rail && 'opacity-0',
        )}
      >
        <span className="block text-4xl font-extrabold tracking-tight">
          {env.appName}
          <span className="text-brand-500">.</span>
        </span>
        <span className="mt-1 block text-sm text-ink-soft">Food Delivery Admin</span>
      </span>
      <span
        aria-hidden="true"
        className={cn(
          'absolute top-10 left-[1.125rem] flex size-11 items-center justify-center rounded-xl bg-brand-500 text-xl font-extrabold text-on-brand shadow-brand transition-[opacity,scale] duration-300',
          rail ? 'scale-100 opacity-100' : 'scale-75 opacity-0',
        )}
      >
        {env.appName.charAt(0)}
      </span>
    </Link>
  );
}

interface NavItemProps {
  label: string;
  path: string;
  icon: LucideIcon;
  /** The sidebar is a rail of icons: the title shows beside the icon on hover or focus */
  rail: boolean;
  /** A small number on the line (unread conversations); hidden when 0 */
  badge?: number;
  onNavigate: () => void;
}

/**
 * One line of the navigation. The icon keeps its place whether the sidebar is open or a rail (the title fades), so
 * nothing jumps while it slides. The title stays in the link for screen readers in both states.
 */
function NavItem({ label, path, icon: Icon, rail, badge = 0, onNavigate }: NavItemProps) {
  // In the rail the title appears beside the icon (the shared tooltip, drawn over the page)
  const { trigger, tooltip, hide } = useTooltip({
    label,
    icon: Icon,
    placement: 'right',
    enabled: rail,
  });

  return (
    <>
      <NavLink
        to={path}
        {...trigger}
        onClick={() => {
          hide();
          onNavigate();
        }}
        className={({ isActive }) =>
          cn(
            'relative mx-3 flex items-center gap-4 rounded-xl px-[1.125rem] py-3 text-sm font-medium transition-colors',
            // The green bar at the edge of the sidebar marks the open page (as in the design)
            'transition-[background-color,color,box-shadow,translate] duration-200',
            isActive
              ? 'bg-brand-500 font-semibold text-on-brand shadow-brand'
              : 'text-ink-soft hover:bg-brand-50 hover:text-brand-text-strong motion-safe:hover:translate-x-0.5',
          )
        }
      >
        <Icon aria-hidden="true" className="size-5 shrink-0" />
        <span
          className={cn('whitespace-nowrap transition-opacity duration-200', rail && 'opacity-0')}
        >
          {label}
        </span>
        {badge > 0 && (
          <span
            className={cn(
              'flex h-5 min-w-5 items-center justify-center rounded-full bg-danger-500 px-1 text-[10px] font-bold text-white',
              rail ? 'absolute top-1.5 right-1.5 ring-2 ring-surface' : 'ml-auto',
            )}
          >
            <span aria-hidden="true">{badge > 99 ? '99+' : badge}</span>
            <span className="sr-only">{`, ${badge} unread`}</span>
          </span>
        )}
      </NavLink>
      {tooltip}
    </>
  );
}
