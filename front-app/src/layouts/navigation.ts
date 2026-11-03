import {
  BarChart3,
  CalendarDays,
  Bell,
  ClipboardList,
  CreditCard,
  FileClock,
  Home,
  LayoutGrid,
  MessageSquare,
  Search,
  Star,
  Store,
  TicketPercent,
  Truck,
  Undo2,
  Users,
  UtensilsCrossed,
  type LucideIcon,
} from 'lucide-react';

import { ROUTES } from '@/utils/constants';

export interface PageEntry {
  label: string;
  path: string;
  icon: LucideIcon;
  /** Not built yet: the page shows "coming soon" with this phase and description until then */
  comingIn?: string;
  description?: string;
}

/** The sidebar's element id (the header's toggle button names what it controls). */
export const SIDEBAR_ID = 'admin-sidebar';

/** The sidebar, top to bottom. A new admin page gets its line here and its route in App.tsx. */
export const NAVIGATION: readonly PageEntry[] = [
  { label: 'Dashboard', path: ROUTES.dashboard, icon: Home },
  {
    label: 'Order List',
    path: ROUTES.orders,
    icon: ClipboardList,
  },
  {
    label: 'Customers',
    path: ROUTES.customers,
    icon: Users,
  },
  {
    label: 'Foods',
    path: ROUTES.foods,
    icon: UtensilsCrossed,
  },
  {
    label: 'Restaurants',
    path: ROUTES.restaurants,
    icon: Store,
  },
  {
    label: 'Categories',
    path: ROUTES.categories,
    icon: LayoutGrid,
  },
  {
    label: 'Payments',
    path: ROUTES.payments,
    icon: CreditCard,
  },
  {
    label: 'Refunds',
    path: ROUTES.refunds,
    icon: Undo2,
  },
  {
    label: 'Deliveries',
    path: ROUTES.deliveries,
    icon: Truck,
  },
  {
    label: 'Coupons',
    path: ROUTES.coupons,
    icon: TicketPercent,
  },
  {
    label: 'Reviews',
    path: ROUTES.reviews,
    icon: Star,
  },
  {
    label: 'Calendar',
    path: ROUTES.calendar,
    icon: CalendarDays,
  },
  {
    label: 'Chat',
    path: ROUTES.chat,
    icon: MessageSquare,
  },
  {
    label: 'Analytics',
    path: ROUTES.analytics,
    icon: BarChart3,
  },
  {
    label: 'Audit Logs',
    path: ROUTES.auditLogs,
    icon: FileClock,
  },
];

/** Pages opened from the header instead of the sidebar. */
export const HEADER_PAGES: readonly PageEntry[] = [
  {
    label: 'Notifications',
    path: ROUTES.notifications,
    icon: Bell,
  },
  {
    label: 'Search',
    path: ROUTES.search,
    icon: Search,
  },
];
