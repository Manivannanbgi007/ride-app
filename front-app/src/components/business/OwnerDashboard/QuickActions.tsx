import {
  ChevronRight,
  ClipboardList,
  MessageSquareText,
  Store,
  UtensilsCrossed,
  type LucideIcon,
} from 'lucide-react';
import { Link } from 'react-router';

import { OWNER_ROUTES } from '@/utils/constants';
import { stagger } from '@/utils/motion';

const ACTIONS: readonly { to: string; icon: LucideIcon; label: string; hint: string }[] = [
  {
    to: OWNER_ROUTES.menu,
    icon: UtensilsCrossed,
    label: 'Manage the menu',
    hint: 'Dishes, prices, sold out',
  },
  {
    to: OWNER_ROUTES.restaurant,
    icon: Store,
    label: 'Restaurant details',
    hint: 'Opening hours, delivery, contact',
  },
  {
    to: `${OWNER_ROUTES.orders}?tab=delivered`,
    icon: ClipboardList,
    label: 'Order history',
    hint: 'Delivered, rejected, cancelled',
  },
  {
    to: OWNER_ROUTES.reviews,
    icon: MessageSquareText,
    label: 'Customer reviews',
    hint: 'What customers say',
  },
];

/** The owner's everyday shortcuts: the menu, the restaurant's details, past orders and reviews. */
export function QuickActions() {
  return (
    <nav aria-label="Quick actions">
      <ul className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
        {ACTIONS.map(({ to, icon: Icon, label, hint }, index) => (
          <li key={to} style={stagger(index)} className="stagger motion-safe:animate-rise-in">
            <Link
              to={to}
              className="group flex h-full items-center gap-3 rounded-2xl bg-surface p-4 text-ink shadow-card ring-1 ring-line/70 transition duration-200 hover:ring-brand-200 focus-visible:ring-4 focus-visible:ring-brand-500/30 focus-visible:outline-none"
            >
              <span
                aria-hidden="true"
                className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-brand-50 text-brand-text-strong"
              >
                <Icon className="size-5" />
              </span>
              <span className="flex min-w-0 flex-1 flex-col">
                <span className="text-sm font-semibold">{label}</span>
                <span className="text-xs text-ink-soft">{hint}</span>
              </span>
              <ChevronRight
                aria-hidden="true"
                className="size-4 shrink-0 text-ink-soft transition-transform group-hover:translate-x-0.5"
              />
            </Link>
          </li>
        ))}
      </ul>
    </nav>
  );
}
