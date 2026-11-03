import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { ApiError } from '@/api/apiErrorHandler';
import { App } from '@/App';
import { dashboardService } from '@/services/dashboardService';
import { notificationService } from '@/services/notificationService';
import { userService } from '@/services/userService';
import { makeStore } from '@/store/store';
import { CurrentAddress } from '@/test/CurrentAddress';
import { adminProfile, dashboardResponse, monthlyRevenue } from '@/test/fixtures';
import { renderWithProviders } from '@/test/renderWithProviders';
import type { LoginResponse } from '@/types/storefront';

vi.mock('@/services/userService', () => ({
  userService: { login: vi.fn(), getMyProfile: vi.fn() },
}));
vi.mock('@/services/dashboardService', () => ({
  dashboardService: {
    getDashboard: vi.fn(),
    getRevenueReport: vi.fn(),
    getMonthlyRevenueComparison: vi.fn(),
  },
}));
vi.mock('@/services/notificationService', () => ({
  notificationService: { getUnreadCount: vi.fn() },
}));
vi.mock('@/services/assistantService', () => ({
  ASSISTANT_CHAT_TIMEOUT_MS: 180_000,
  assistantService: {
    chat: vi.fn(),
    getConversation: vi.fn().mockResolvedValue(null),
    deleteConversation: vi.fn(),
    confirmAction: vi.fn(),
    cancelAction: vi.fn(),
    getSuggestions: vi.fn().mockResolvedValue([]),
    getStatus: vi.fn().mockResolvedValue({ available: true, model: 'test', message: null }),
  },
}));

const adminLogin: LoginResponse = {
  userId: 1,
  email: 'admin@fooddelivery.local',
  fullName: 'Platform Administrator',
  role: 'PLATFORM_ADMIN',
  lastLoginAt: '2026-10-12T09:00:00Z',
  accessToken: 'test-access-token',
  tokenType: 'Bearer',
  expiresIn: 900,
};

const adminStore = () =>
  makeStore({
    session: {
      adminUser: {
        id: 1,
        role: 'PLATFORM_ADMIN',
        fullName: 'Platform Administrator',
        email: 'admin@fooddelivery.local',
      },
      customer: null,
      owner: null,
    },
  });

/** Nobody is signed in to any area */
const visitorStore = () => makeStore({ session: { adminUser: null, customer: null, owner: null } });

const fill = async (email: string, password: string) => {
  await userEvent.type(screen.getByLabelText('Email'), email);
  await userEvent.type(screen.getByLabelText('Password'), password);
  await userEvent.click(screen.getByRole('button', { name: 'Sign in' }));
};

beforeEach(() => {
  vi.clearAllMocks();
  sessionStorage.clear();
  vi.mocked(userService.getMyProfile).mockResolvedValue(adminProfile);
  vi.mocked(notificationService.getUnreadCount).mockResolvedValue(0);
  vi.mocked(dashboardService.getDashboard).mockResolvedValue(dashboardResponse());
  vi.mocked(dashboardService.getMonthlyRevenueComparison).mockResolvedValue(monthlyRevenue);
});

describe('the admin area: signing in', () => {
  it('sends a visitor to the admin sign-in, and remembers the page they wanted', async () => {
    renderWithProviders(<App />, { route: '/admin/orders', store: visitorStore() });

    expect(
      await screen.findByRole('heading', { name: 'Admin sign in', level: 1 }, { timeout: 10_000 }),
    ).toBeInTheDocument();
  });

  it('does not let a signed-in customer or restaurant owner into the admin area', async () => {
    const store = makeStore({
      session: {
        adminUser: null,
        customer: { id: 7, role: 'CUSTOMER', fullName: 'Priya Sharma', email: 'p@example.com' },
        owner: { id: 3, role: 'RESTAURANT_ADMIN', fullName: 'Owner', email: 'o@example.com' },
      },
    });
    renderWithProviders(<App />, { route: '/admin/dashboard', store });

    expect(
      await screen.findByRole('heading', { name: 'Admin sign in' }, { timeout: 10_000 }),
    ).toBeInTheDocument();
  });

  it('lets an administrator in, keeps them signed in for the tab, and shows the page they wanted', async () => {
    vi.mocked(userService.login).mockResolvedValue(adminLogin);
    renderWithProviders(<App />, {
      route: '/admin/sign-in?returnTo=%2Fadmin%2Fdashboard',
      store: visitorStore(),
    });
    await screen.findByRole('heading', { name: 'Admin sign in' }, { timeout: 10_000 });

    await fill('admin@fooddelivery.local', 'ChangeMe123');

    expect(userService.login).toHaveBeenCalledWith(
      'admin@fooddelivery.local',
      'ChangeMe123',
      'PLATFORM_ADMIN',
    );
    expect(
      await screen.findByRole('heading', { name: 'Dashboard', level: 1 }, { timeout: 15_000 }),
    ).toBeInTheDocument();
    expect(sessionStorage.getItem('front-app.admin')).toContain('"PLATFORM_ADMIN"');
  }, 20_000);

  it('refuses a customer account with a message of its own, and signs nobody in', async () => {
    vi.mocked(userService.login).mockResolvedValue({
      ...adminLogin,
      userId: 7,
      role: 'CUSTOMER',
      fullName: 'Priya Sharma',
    });
    renderWithProviders(<App />, { route: '/admin/sign-in', store: visitorStore() });
    await screen.findByRole('heading', { name: 'Admin sign in' }, { timeout: 10_000 });

    await fill('priya@example.com', 'Priya1234');

    expect(await screen.findByText(/not an administrator account/)).toBeInTheDocument();
    expect(sessionStorage.getItem('front-app.admin')).toBeNull();
  });

  it('says so when the password is wrong', async () => {
    vi.mocked(userService.login).mockRejectedValue(
      new ApiError({
        kind: 'unauthorized',
        status: 401,
        userMessage: 'Unauthorized',
        technicalMessage: 'unauthorized',
      }),
    );
    renderWithProviders(<App />, { route: '/admin/sign-in', store: visitorStore() });
    await screen.findByRole('heading', { name: 'Admin sign in' }, { timeout: 10_000 });

    await fill('admin@fooddelivery.local', 'wrong-password1');

    expect(await screen.findByRole('alert')).toHaveTextContent(/email or password/i);
  });
});

describe('the admin area: signing out', () => {
  it('has a Sign out button in the header that ends the session and leads to a plain sign-in', async () => {
    sessionStorage.setItem(
      'front-app.admin',
      JSON.stringify({
        id: 1,
        role: 'PLATFORM_ADMIN',
        fullName: 'Platform Administrator',
        email: 'a@b.test',
      }),
    );
    renderWithProviders(
      <>
        <App />
        <CurrentAddress />
      </>,
      { route: '/admin/dashboard', store: adminStore() },
    );

    const signOut = await screen.findByRole('button', { name: 'Sign out' }, { timeout: 15_000 });
    await userEvent.click(signOut);

    // Back at the sign-in, without the page the last admin left in the address
    expect(
      await screen.findByRole('heading', { name: 'Admin sign in', level: 1 }, { timeout: 10_000 }),
    ).toBeInTheDocument();
    expect(screen.getByTestId('address')).toHaveTextContent(/^\/admin\/sign-in$/);
    expect(await screen.findByText('You are signed out.')).toBeInTheDocument();
    expect(sessionStorage.getItem('front-app.admin')).toBeNull();
  }, 25_000);

  it('does not let the signed-out admin back into an admin page', async () => {
    const store = adminStore();
    renderWithProviders(<App />, { route: '/admin/dashboard', store });
    await userEvent.click(
      await screen.findByRole('button', { name: 'Sign out' }, { timeout: 15_000 }),
    );
    await screen.findByRole('heading', { name: 'Admin sign in' }, { timeout: 10_000 });

    // Nobody is signed in to the admin area any more
    expect(store.getState().session.adminUser).toBeNull();
  }, 25_000);
});
