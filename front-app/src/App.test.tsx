import { act, screen, waitFor, within } from '@testing-library/react';
import { Store } from 'lucide-react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { App } from '@/App';
import { ComingSoonPage } from '@/pages/ComingSoon/ComingSoonPage';
import { catalogService } from '@/services/catalogService';
import { dashboardService } from '@/services/dashboardService';
import { notificationService } from '@/services/notificationService';
import { restaurantService } from '@/services/restaurantService';
import { userService } from '@/services/userService';
import { makeStore } from '@/store/store';
import { customerSignedIn, customerSignedOut } from '@/store/slices/sessionSlice';
import { CurrentAddress } from '@/test/CurrentAddress';
import { adminProfile, dashboardResponse, monthlyRevenue, pageOf } from '@/test/fixtures';
import { renderWithProviders } from '@/test/renderWithProviders';

vi.mock('@/services/dashboardService', () => ({
  dashboardService: {
    getDashboard: vi.fn(),
    getRevenueReport: vi.fn(),
    getMonthlyRevenueComparison: vi.fn(),
  },
}));
vi.mock('@/services/userService', () => ({ userService: { getMyProfile: vi.fn() } }));
vi.mock('@/services/notificationService', () => ({
  notificationService: { getUnreadCount: vi.fn() },
}));
vi.mock('@/services/restaurantService', () => ({
  restaurantService: { browseRestaurants: vi.fn() },
}));
vi.mock('@/services/catalogService', () => ({
  catalogService: { getStoreCategories: vi.fn() },
}));

/** The admin area is for a signed-in administrator */
const adminStore = () =>
  makeStore({
    session: { adminUser: { id: 1, role: 'PLATFORM_ADMIN' }, customer: null, owner: null },
  });

describe('App routes', () => {
  beforeEach(() => {
    vi.mocked(userService.getMyProfile).mockResolvedValue(adminProfile);
    vi.mocked(notificationService.getUnreadCount).mockResolvedValue(4);
    vi.mocked(dashboardService.getDashboard).mockResolvedValue(dashboardResponse());
    vi.mocked(dashboardService.getMonthlyRevenueComparison).mockResolvedValue(monthlyRevenue);
    vi.mocked(restaurantService.browseRestaurants).mockResolvedValue(
      pageOf([
        { id: 3, name: 'Spice Garden', cuisines: ['Biryani'], city: 'Bengaluru', openNow: true },
      ]),
    );
    vi.mocked(catalogService.getStoreCategories).mockResolvedValue([
      { id: 3, name: 'Biryani', description: 'Biryani and pulao', imageUrl: null },
    ]);
  });

  it('opens the customer home page at /customer', async () => {
    renderWithProviders(<App />, { route: '/customer' });

    expect(
      await screen.findByRole('heading', { name: /Hungry\?/, level: 1 }, { timeout: 10_000 }),
    ).toBeInTheDocument();
    expect(await screen.findByRole('link', { name: /Spice Garden/ })).toHaveAttribute(
      'href',
      '/customer/restaurants/3',
    );
    expect(screen.getByRole('link', { name: 'Sign in' })).toBeInTheDocument();
    // "See all" for the categories, next to the one for the restaurants
    expect(screen.getByRole('link', { name: 'See all categories' })).toHaveAttribute(
      'href',
      '/customer/categories',
    );
    expect(screen.getByRole('link', { name: 'See all restaurants' })).toHaveAttribute(
      'href',
      '/customer/restaurants',
    );
    // The admin's sidebar is not part of the customer area
    expect(screen.queryByRole('link', { name: 'Dashboard' })).not.toBeInTheDocument();
  });

  it('opens the admin dashboard, inside the admin layout, at /admin', async () => {
    renderWithProviders(<App />, { route: '/admin', store: adminStore() });

    // The dashboard is lazy-loaded: its first import (with the chart library) takes a moment
    expect(
      await screen.findByRole('heading', { name: 'Dashboard', level: 1 }, { timeout: 10_000 }),
    ).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Dashboard' })).toHaveAttribute('aria-current', 'page');
    expect(
      await screen.findByRole('link', { name: 'Notifications (4 unread)' }),
    ).toBeInTheDocument();
    expect(await screen.findByText('Platform Administrator')).toBeInTheDocument();
    // The user chip opens the admin's own account
    expect(
      screen.getByRole('link', { name: 'Your account: Platform Administrator' }),
    ).toHaveAttribute('href', '/admin/customers/1');
    // The first import of the dashboard (with the chart library) can be slow while the whole suite runs
  }, 15_000);

  it('leads the root address to the customer home page', async () => {
    renderWithProviders(<App />, { route: '/' });

    expect(
      await screen.findByRole('heading', { name: /Hungry?/, level: 1 }, { timeout: 10_000 }),
    ).toBeInTheDocument();
  });

  it('sends an address from before /customer on to the same page under /customer', async () => {
    renderWithProviders(<App />, { route: '/sign-in?returnTo=%2Fcustomer%2Fcart' });

    expect(
      await screen.findByRole('heading', { name: 'Sign in', level: 1 }, { timeout: 10_000 }),
    ).toBeInTheDocument();
  });

  it('does not treat an address that only starts like a customer page as an old one', () => {
    renderWithProviders(<App />, { route: '/orders-of-a-customer' });

    expect(screen.getByRole('heading', { name: 'Page not found' })).toBeInTheDocument();
  });

  it('sends a visitor, and a saved sign-in of a non-customer account, to sign in first', async () => {
    const store = makeStore();
    store.dispatch(
      customerSignedIn({ id: 1, role: 'PLATFORM_ADMIN', fullName: 'An Admin', email: 'a@b.test' }),
    );
    renderWithProviders(<App />, { route: '/customer/cart', store });

    expect(
      await screen.findByRole('heading', { name: 'Sign in', level: 1 }, { timeout: 10_000 }),
    ).toBeInTheDocument();
  });

  it('sends a customer signed out on one of their pages to the storefront, without ?returnTo=', async () => {
    const store = makeStore();
    store.dispatch(
      customerSignedIn({ id: 7, role: 'CUSTOMER', fullName: 'Priya Sharma', email: 'p@b.test' }),
    );
    renderWithProviders(
      <>
        <App />
        <CurrentAddress />
      </>,
      { route: '/customer/account/profile', store },
    );
    // The page itself is shown (its code loads lazily)
    await screen.findByRole('heading', { name: 'My profile', level: 1 }, { timeout: 10_000 });

    // Signed out without the account menu's own way home (in another tab, or the session ended)
    act(() => {
      store.dispatch(customerSignedOut());
    });

    await waitFor(() => expect(screen.getByTestId('address')).toHaveTextContent(/^\/customer$/));
  });

  it('says when a page of the design is built (a page planned for a later phase)', () => {
    renderWithProviders(
      <ComingSoonPage
        page={{
          label: 'Restaurant app',
          path: '/',
          icon: Store,
          comingIn: 'a later phase',
          description: 'Restaurant owners manage their menu.',
        }}
      />,
    );

    expect(screen.getByRole('heading', { name: 'Restaurant app' })).toBeInTheDocument();
    expect(screen.getByText('Coming in a later phase')).toBeInTheDocument();
  });

  it('shows "page not found" in the area of the address, with the way back to its start', () => {
    const { unmount } = renderWithProviders(<App />, {
      route: '/admin/does-not-exist',
      store: adminStore(),
    });
    expect(screen.getByRole('heading', { name: 'Page not found' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Go to the dashboard' })).toBeInTheDocument();
    unmount();

    renderWithProviders(<App />, { route: '/does-not-exist' });
    expect(screen.getByRole('heading', { name: 'Page not found' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Go to the home page' })).toHaveAttribute(
      'href',
      '/customer',
    );
    const mainNavigation = screen.getByRole('navigation', { name: 'Main' });
    expect(within(mainNavigation).getByRole('link', { name: 'Restaurants' })).toBeInTheDocument();
  });
});
