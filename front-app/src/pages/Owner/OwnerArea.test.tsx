import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { ApiError } from '@/api/apiErrorHandler';
import { App } from '@/App';
import { ownerService } from '@/services/ownerService';
import { userService } from '@/services/userService';
import { makeStore } from '@/store/store';
import { CurrentAddress } from '@/test/CurrentAddress';
import { adminProfile, orderSummary, pageOf, restaurantSummary } from '@/test/fixtures';
import { ownerUser } from '@/test/ownerTestUtils';
import { renderWithProviders } from '@/test/renderWithProviders';
import type { SalesSummary } from '@/types/owner';
import type { LoginResponse } from '@/types/storefront';
import { presetRange } from '@/utils/dateRangeUtils';

vi.mock('@/services/ownerService', () => ({
  ownerService: {
    getRestaurants: vi.fn(),
    getOrders: vi.fn(),
    countOrders: vi.fn(),
    getSalesSummary: vi.fn(),
    getTopItems: vi.fn(),
    getMenuItems: vi.fn(),
    resubmitRestaurant: vi.fn(),
  },
}));
vi.mock('@/services/userService', () => ({ userService: { login: vi.fn(), register: vi.fn() } }));
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

const ownerLogin: LoginResponse = {
  userId: 423,
  email: 'test.owner@fooddelivery.local',
  fullName: 'Test Owner',
  role: 'RESTAURANT_ADMIN',
  lastLoginAt: '2026-10-11T09:00:00Z',
  accessToken: 'test-access-token',
  tokenType: 'Bearer',
  expiresIn: 900,
};

const sales: SalesSummary = {
  restaurantId: 3,
  fromDate: '2026-10-11',
  toDate: '2026-10-11',
  totalOrders: 5,
  ordersByStatus: { DELIVERED: 3, PLACED: 2 },
  acceptedOrdersValue: 1200.5,
  deliveredOrdersValue: 900,
  currency: 'INR',
};

const signedInOwnerStore = () =>
  makeStore({ session: { adminUser: null, customer: null, owner: ownerUser } });

const fill = async (email: string, password: string) => {
  await userEvent.type(screen.getByLabelText('Email'), email);
  await userEvent.type(screen.getByLabelText('Password'), password);
  await userEvent.click(screen.getByRole('button', { name: 'Sign in' }));
};

beforeEach(() => {
  vi.clearAllMocks();
  sessionStorage.clear();
  vi.mocked(ownerService.getRestaurants).mockResolvedValue([
    restaurantSummary({ id: 3, name: 'Biryani House', ownerUserId: 423, status: 'ACTIVE' }),
  ]);
  vi.mocked(ownerService.getSalesSummary).mockResolvedValue(sales);
  vi.mocked(ownerService.getTopItems).mockResolvedValue({
    restaurantId: 3,
    from: '2026-10-11',
    to: '2026-10-11',
    items: [{ menuItemId: 11, name: 'Chicken Biryani', quantity: 7, orders: 4, revenue: 2093 }],
  });
  vi.mocked(ownerService.getOrders).mockResolvedValue(pageOf([]));
});

describe('the owner area: signing in', () => {
  it('sends a visitor to the owner sign-in, not to the customer one', async () => {
    renderWithProviders(
      <>
        <App />
        <CurrentAddress />
      </>,
      { route: '/owner/orders' },
    );

    expect(
      await screen.findByRole(
        'heading',
        { name: 'Restaurant sign in', level: 1 },
        { timeout: 10_000 },
      ),
    ).toBeInTheDocument();
    // The visitor comes back to the page they asked for once signed in
    expect(screen.getByTestId('address')).toHaveTextContent(
      /^\/owner\/sign-in\?returnTo=%2Fowner%2Forders$/,
    );
    expect(ownerService.getRestaurants).not.toHaveBeenCalled();
  });

  it('does not let a signed-in customer into the owner area', async () => {
    const customerStore = makeStore({
      session: {
        adminUser: null,
        customer: { id: 7, role: 'CUSTOMER', fullName: 'Priya Sharma', email: 'p@example.com' },
        owner: null,
      },
    });
    renderWithProviders(<App />, { route: '/owner', store: customerStore });

    expect(
      await screen.findByRole(
        'heading',
        { name: 'Restaurant sign in', level: 1 },
        { timeout: 10_000 },
      ),
    ).toBeInTheDocument();
  });

  it('lets a restaurant owner in and goes back to the page that asked for the sign-in', async () => {
    vi.mocked(userService.login).mockResolvedValue(ownerLogin);
    renderWithProviders(<App />, { route: '/owner/sign-in?returnTo=%2Fowner%2Forders' });
    await screen.findByRole('heading', { name: 'Restaurant sign in' }, { timeout: 10_000 });

    await fill('test.owner@fooddelivery.local', 'Test@12345');

    expect(userService.login).toHaveBeenCalledWith(
      'test.owner@fooddelivery.local',
      'Test@12345',
      'RESTAURANT_ADMIN',
    );
    // The orders page is not built in this test: what matters is the owner's frame around the address
    expect(
      await screen.findByRole('navigation', { name: 'Main' }, { timeout: 10_000 }),
    ).toBeInTheDocument();
    expect(sessionStorage.getItem('front-app.owner')).toContain('"RESTAURANT_ADMIN"');
  });

  it('refuses a customer account with a message of its own, and signs nobody in', async () => {
    vi.mocked(userService.login).mockResolvedValue({
      ...ownerLogin,
      userId: 7,
      role: 'CUSTOMER',
      fullName: 'Priya Sharma',
    });
    renderWithProviders(<App />, { route: '/owner/sign-in' });
    await screen.findByRole('heading', { name: 'Restaurant sign in' }, { timeout: 10_000 });

    await fill('priya@example.com', 'Priya1234');

    expect(await screen.findByText(/not a restaurant owner account/)).toBeInTheDocument();
    expect(sessionStorage.getItem('front-app.owner')).toBeNull();
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
    renderWithProviders(<App />, { route: '/owner/sign-in' });
    await screen.findByRole('heading', { name: 'Restaurant sign in' }, { timeout: 10_000 });

    await fill('test.owner@fooddelivery.local', 'wrong-password1');

    expect(await screen.findByRole('alert')).toHaveTextContent(/email or password/i);
  });
});

describe('the owner area: signing up', () => {
  const fillSignUp = async (email: string) => {
    await userEvent.type(screen.getByLabelText('Full name'), 'Test Owner');
    await userEvent.type(screen.getByLabelText('Email'), email);
    await userEvent.type(screen.getByLabelText('Password'), 'Owner1234!');
    await userEvent.type(screen.getByLabelText('Password again'), 'Owner1234!');
    await userEvent.click(screen.getByRole('button', { name: 'Create owner account' }));
  };

  it('offers an owner account from the sign-in page', async () => {
    renderWithProviders(<App />, { route: '/owner/sign-in' });

    expect(
      await screen.findByRole('link', { name: 'Create an owner account' }, { timeout: 10_000 }),
    ).toHaveAttribute('href', '/owner/register');
  });

  it('creates a restaurant owner account, signs it in and asks for the restaurant next', async () => {
    vi.mocked(userService.register).mockResolvedValue({
      ...adminProfile,
      id: 423,
      role: 'RESTAURANT_ADMIN',
      fullName: 'Test Owner',
    });
    vi.mocked(userService.login).mockResolvedValue(ownerLogin);
    vi.mocked(ownerService.getRestaurants).mockResolvedValue([]);
    renderWithProviders(<App />, { route: '/owner/register' });
    await screen.findByRole(
      'heading',
      { name: 'Become a restaurant partner', level: 1 },
      { timeout: 10_000 },
    );

    await fillSignUp('test.owner@fooddelivery.local');

    expect(
      await screen.findByRole('heading', { name: 'Register a restaurant' }, { timeout: 10_000 }),
    ).toBeInTheDocument();
    // The phone is optional for an owner: left out when empty
    expect(userService.register).toHaveBeenCalledWith({
      fullName: 'Test Owner',
      email: 'test.owner@fooddelivery.local',
      password: 'Owner1234!',
      phone: undefined,
      role: 'RESTAURANT_ADMIN',
    });
    expect(
      screen.getByText(
        'Welcome, Test! Now register your restaurant: the platform team reviews it.',
      ),
    ).toBeInTheDocument();
    expect(sessionStorage.getItem('front-app.owner')).toContain('"RESTAURANT_ADMIN"');
    // Typing a whole form takes a while
  }, 20_000);

  it('shows "already registered" next to the email', async () => {
    vi.mocked(userService.register).mockRejectedValue(
      new ApiError({
        kind: 'conflict',
        status: 409,
        userMessage: 'Email is already registered: test.owner@fooddelivery.local',
        technicalMessage: 'HTTP 409',
      }),
    );
    renderWithProviders(<App />, { route: '/owner/register' });
    await screen.findByRole(
      'heading',
      { name: 'Become a restaurant partner' },
      { timeout: 10_000 },
    );

    await fillSignUp('test.owner@fooddelivery.local');

    expect(
      await screen.findByText('This email already has an account: sign in instead.'),
    ).toBeInTheDocument();
    expect(userService.login).not.toHaveBeenCalled();
    expect(sessionStorage.getItem('front-app.owner')).toBeNull();
  }, 20_000);

  it('sends an owner who is signed in already to the dashboard', async () => {
    renderWithProviders(<App />, { route: '/owner/register', store: signedInOwnerStore() });

    expect(
      await screen.findByRole('navigation', { name: 'Main' }, { timeout: 10_000 }),
    ).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Create owner account' })).not.toBeInTheDocument();
  });
});

describe('the owner area: signing out', () => {
  it('signs the owner out from the account menu, to a plain sign-in', async () => {
    renderWithProviders(
      <>
        <App />
        <CurrentAddress />
      </>,
      { route: '/owner', store: signedInOwnerStore() },
    );
    await screen.findByRole('heading', { level: 1, name: /, Test!$/ }, { timeout: 10_000 });

    await userEvent.click(screen.getByRole('button', { name: /Account of\s*Test Owner/ }));
    await userEvent.click(screen.getByRole('button', { name: 'Sign out' }));

    expect(
      await screen.findByRole(
        'heading',
        { name: 'Restaurant sign in', level: 1 },
        { timeout: 10_000 },
      ),
    ).toBeInTheDocument();
    // The address does not keep the page the owner left (?returnTo=)
    expect(screen.getByTestId('address')).toHaveTextContent(/^\/owner\/sign-in$/);
    expect(await screen.findByText('You are signed out.')).toBeInTheDocument();
  });
});

describe('the owner dashboard', () => {
  it('shows the restaurant, the figures of the last 7 days and the top dishes', async () => {
    renderWithProviders(<App />, { route: '/owner', store: signedInOwnerStore() });

    expect(
      await screen.findByRole('heading', { level: 1, name: /, Test!$/ }, { timeout: 10_000 }),
    ).toBeInTheDocument();
    expect(screen.getAllByText('Biryani House')[0]).toBeInTheDocument();
    const figures = within(await screen.findByRole('list', { name: 'Key figures' }));
    expect(figures.getByText('Orders').parentElement?.nextElementSibling).toHaveTextContent('5');
    expect(await screen.findByText('Chicken Biryani')).toBeInTheDocument();
    expect(screen.getByText('7 sold · 4 orders')).toBeInTheDocument();
    // Asked for the last 7 days by default, and the new orders are the oldest first
    const week = presetRange('7');
    expect(ownerService.getSalesSummary).toHaveBeenCalledWith(3, week.fromDate, week.toDate);
    expect(ownerService.getOrders).toHaveBeenCalledWith(
      3,
      { status: 'PLACED', sort: 'createdAt,asc' },
      0,
      5,
    );
  });

  it('lists the orders waiting for a decision, each leading to its page', async () => {
    vi.mocked(ownerService.getOrders).mockResolvedValue(
      pageOf([orderSummary({ id: 42, itemCount: 2, totalAmount: 548 })]),
    );
    renderWithProviders(<App />, { route: '/owner', store: signedInOwnerStore() });

    const waiting = await screen.findByRole('list', { name: 'New orders' }, { timeout: 10_000 });
    expect(within(waiting).getByRole('link', { name: '#42' })).toHaveAttribute(
      'href',
      '/owner/orders/42',
    );
    expect(screen.getByText('1 new order')).toBeInTheDocument();
  });

  it('says there is nothing to decide when no order is waiting', async () => {
    vi.mocked(ownerService.countOrders).mockResolvedValue(0);
    renderWithProviders(<App />, { route: '/owner', store: signedInOwnerStore() });

    expect(
      await screen.findByRole(
        'link',
        { name: 'New: 0 orders. No order is waiting' },
        { timeout: 10_000 },
      ),
    ).toHaveAttribute('href', '/owner/orders');
    expect(screen.queryByText('Waiting for your decision')).not.toBeInTheDocument();
  });

  it('shows why a restaurant is not live, and sends a rejected one for review again', async () => {
    vi.mocked(ownerService.getRestaurants).mockResolvedValue([
      restaurantSummary({
        id: 3,
        name: 'Biryani House',
        ownerUserId: 423,
        status: 'REJECTED',
        statusReason: 'The food licence is not readable',
      }),
    ]);
    vi.mocked(ownerService.resubmitRestaurant).mockResolvedValue({} as never);
    renderWithProviders(<App />, { route: '/owner', store: signedInOwnerStore() });

    expect(
      await screen.findByText('Your restaurant was not approved', undefined, { timeout: 10_000 }),
    ).toBeInTheDocument();
    expect(screen.getByText(/The food licence is not readable/)).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Send for review again' }));

    expect(ownerService.resubmitRestaurant).toHaveBeenCalledWith(3);
    expect(
      await screen.findByText('Your restaurant was sent for review again.'),
    ).toBeInTheDocument();
  });

  it('explains an API failure of the figures and offers to try again', async () => {
    // Only the overview's own days fail (the days before, for the comparison, load)
    const week = presetRange('7');
    vi.mocked(ownerService.getSalesSummary).mockImplementation((_id, fromDate) =>
      fromDate === week.fromDate
        ? Promise.reject(
            new ApiError({
              kind: 'server',
              status: 500,
              userMessage: 'Something went wrong on the server.',
              technicalMessage: 'boom',
            }),
          )
        : Promise.resolve(sales),
    );
    renderWithProviders(<App />, { route: '/owner', store: signedInOwnerStore() });

    expect(
      await screen.findByText('Unable to load your orders and sales', undefined, {
        timeout: 10_000,
      }),
    ).toBeInTheDocument();
    vi.mocked(ownerService.getSalesSummary).mockResolvedValue(sales);
    await userEvent.click(screen.getAllByRole('button', { name: /try again/i })[0]);

    expect(await screen.findByRole('list', { name: 'Key figures' })).toBeInTheDocument();
  });
});

describe('an owner without a restaurant', () => {
  it('is asked to register one, and can still sign out', async () => {
    vi.mocked(ownerService.getRestaurants).mockResolvedValue([]);
    renderWithProviders(<App />, { route: '/owner', store: signedInOwnerStore() });

    expect(
      await screen.findByText('You have no restaurant yet', undefined, { timeout: 10_000 }),
    ).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Register a restaurant' })).toHaveAttribute(
      'href',
      '/owner/restaurant/new',
    );
    // The frame stays: the account menu has "Sign out"
    await userEvent.click(screen.getByRole('button', { name: /Account of\s*Test Owner/ }));
    expect(screen.getByText('test.owner@fooddelivery.local')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Sign out' })).toBeInTheDocument();
  });

  it('shows the registration form with its own validation messages', async () => {
    vi.mocked(ownerService.getRestaurants).mockResolvedValue([]);
    renderWithProviders(<App />, { route: '/owner/restaurant/new', store: signedInOwnerStore() });

    expect(
      await screen.findByRole('heading', { name: 'Register a restaurant' }, { timeout: 10_000 }),
    ).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Send for review' }));

    expect(await screen.findByText('Please enter the name.')).toBeInTheDocument();
    expect(screen.getByText('Enter at least one cuisine, e.g. Biryani.')).toBeInTheDocument();
    expect(screen.getByText('10 to 15 digits, optionally starting with +.')).toBeInTheDocument();
  });
});

describe('a missing page in the owner area', () => {
  it('shows "page not found" with the way back to the owner dashboard', async () => {
    renderWithProviders(<App />, { route: '/owner/does-not-exist', store: signedInOwnerStore() });

    expect(
      await screen.findByRole('heading', { name: 'Page not found' }, { timeout: 10_000 }),
    ).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Go to your restaurant' })).toHaveAttribute(
      'href',
      '/owner',
    );
  });
});
