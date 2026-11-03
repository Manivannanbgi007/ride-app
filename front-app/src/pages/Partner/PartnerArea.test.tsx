import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { App } from '@/App';
import { sessionApi } from '@/api/sessionApi';
import { NOT_A_PARTNER_MESSAGE } from '@/hooks/api/authHooks';
import { partnerService } from '@/services/partnerService';
import { userService } from '@/services/userService';
import { makeStore } from '@/store/store';
import { CurrentAddress } from '@/test/CurrentAddress';
import { installFakeGeolocation, removeFakeGeolocation } from '@/test/fakeGeolocation';
import { adminProfile, delivery, partner } from '@/test/fixtures';
import { signedInPartnerStore } from '@/test/partnerTestUtils';
import { renderWithProviders } from '@/test/renderWithProviders';
import type { LoginResponse } from '@/types/storefront';

vi.mock('@/services/partnerService', () => ({
  partnerService: {
    getMe: vi.fn(),
    getCurrentDelivery: vi.fn(),
    updateProfile: vi.fn(),
    changeAvailability: vi.fn(),
    getDeliveries: vi.fn(),
    shareLocation: vi.fn(),
  },
}));
vi.mock('@/services/userService', () => ({ userService: { login: vi.fn(), register: vi.fn() } }));

const partnerLogin: LoginResponse = {
  userId: 31,
  email: 'ravi.rider@example.com',
  fullName: 'Ravi Kumar',
  role: 'DELIVERY_PARTNER',
  lastLoginAt: '2026-10-12T09:00:00Z',
  accessToken: 'test-access-token',
  tokenType: 'Bearer',
  expiresIn: 900,
};

const TIMEOUT = { timeout: 10_000 };

const fillSignIn = async (email: string, password: string) => {
  await userEvent.type(screen.getByLabelText('Email'), email);
  await userEvent.type(screen.getByLabelText('Password'), password);
  await userEvent.click(screen.getByRole('button', { name: 'Sign in' }));
};

beforeEach(() => {
  vi.clearAllMocks();
  sessionStorage.clear();
  vi.mocked(partnerService.getMe).mockResolvedValue(partner({ status: 'OFFLINE' }));
  vi.mocked(partnerService.getCurrentDelivery).mockResolvedValue(null);
});

describe('the delivery partner area: signing in', () => {
  it('sends a visitor to the partner sign-in, not to the customer one', async () => {
    renderWithProviders(<App />, { route: '/partner/deliveries' });

    expect(
      await screen.findByRole('heading', { name: 'Delivery partner sign in', level: 1 }, TIMEOUT),
    ).toBeInTheDocument();
    expect(partnerService.getMe).not.toHaveBeenCalled();
  });

  it('does not let a signed-in customer or restaurant owner into the partner area', async () => {
    const store = makeStore({
      session: {
        adminUser: null,
        customer: { id: 7, role: 'CUSTOMER', fullName: 'Priya Sharma', email: 'p@example.com' },
        owner: {
          id: 423,
          role: 'RESTAURANT_ADMIN',
          fullName: 'Test Owner',
          email: 'o@example.com',
        },
        partner: null,
      },
    });
    renderWithProviders(<App />, { route: '/partner', store });

    expect(
      await screen.findByRole('heading', { name: 'Delivery partner sign in', level: 1 }, TIMEOUT),
    ).toBeInTheDocument();
  });

  it('lets a delivery partner in and goes back to the page that asked for the sign-in', async () => {
    vi.mocked(userService.login).mockResolvedValue(partnerLogin);
    vi.mocked(partnerService.getDeliveries).mockResolvedValue({
      content: [],
      page: 0,
      size: 20,
      totalElements: 0,
      totalPages: 0,
      first: true,
      last: true,
    });
    renderWithProviders(<App />, { route: '/partner/sign-in?returnTo=%2Fpartner%2Fdeliveries' });
    await screen.findByRole('heading', { name: 'Delivery partner sign in' }, TIMEOUT);

    await fillSignIn('ravi.rider@example.com', 'Rider1234');

    expect(
      await screen.findByRole('heading', { name: 'My deliveries', level: 1 }, TIMEOUT),
    ).toBeInTheDocument();
    expect(JSON.parse(sessionStorage.getItem('front-app.partner') ?? 'null')).toMatchObject({
      id: 31,
      role: 'DELIVERY_PARTNER',
    });
  });

  it('refuses an account that is not a delivery partner', async () => {
    vi.mocked(userService.login).mockResolvedValue({ ...partnerLogin, role: 'CUSTOMER' });
    renderWithProviders(<App />, { route: '/partner/sign-in' });
    await screen.findByRole('heading', { name: 'Delivery partner sign in' }, TIMEOUT);

    await fillSignIn('priya@example.com', 'Eater1234');

    expect(await screen.findByText(NOT_A_PARTNER_MESSAGE)).toBeInTheDocument();
    expect(sessionStorage.getItem('front-app.partner')).toBeNull();
  });

  it('signs a new partner up as a delivery partner and asks for the vehicle and city next', async () => {
    vi.mocked(userService.register).mockResolvedValue({
      ...adminProfile,
      id: 31,
      role: 'DELIVERY_PARTNER',
      fullName: 'Ravi Kumar',
    });
    vi.mocked(userService.login).mockResolvedValue(partnerLogin);
    vi.mocked(partnerService.getMe).mockResolvedValue(
      partner({
        status: 'OFFLINE',
        vehicleType: null,
        vehicleNumber: null,
        city: null,
        readyToWork: false,
      }),
    );
    renderWithProviders(<App />, { route: '/partner/register' });
    await screen.findByRole('heading', { name: 'Become a delivery partner' }, TIMEOUT);

    await userEvent.type(screen.getByLabelText('Full name'), 'Ravi Kumar');
    await userEvent.type(screen.getByLabelText('Email'), 'ravi.rider@example.com');
    await userEvent.click(screen.getByRole('button', { name: 'Create partner account' }));
    // The phone is required for partners (customers call them)
    expect(await screen.findByText(/Please enter your phone number/)).toBeInTheDocument();

    await userEvent.type(screen.getByLabelText('Phone'), '+919800000031');
    await userEvent.type(screen.getByLabelText('Password'), 'Rider1234!');
    await userEvent.type(screen.getByLabelText('Password again'), 'Rider1234!');
    await userEvent.click(screen.getByRole('button', { name: 'Create partner account' }));

    expect(
      await screen.findByRole('heading', { name: 'Vehicle and city' }, TIMEOUT),
    ).toBeInTheDocument();
    expect(userService.register).toHaveBeenCalledWith({
      fullName: 'Ravi Kumar',
      email: 'ravi.rider@example.com',
      phone: '+919800000031',
      password: 'Rider1234!',
      role: 'DELIVERY_PARTNER',
    });
    // Typing a whole form takes a while
  }, 20_000);

  it('signs the partner out from the account menu', async () => {
    sessionStorage.setItem(
      'front-app.partner',
      JSON.stringify({
        id: 31,
        role: 'DELIVERY_PARTNER',
        fullName: 'Ravi Kumar',
        email: 'r@example.com',
      }),
    );
    renderWithProviders(
      <>
        <App />
        <CurrentAddress />
      </>,
      { route: '/partner', store: signedInPartnerStore() },
    );
    await screen.findByRole('heading', { name: /, Ravi$/, level: 1 }, TIMEOUT);

    await userEvent.click(screen.getByRole('button', { name: /Account of\s*Ravi Kumar/ }));
    await userEvent.click(screen.getByRole('button', { name: 'Sign out' }));

    expect(
      await screen.findByRole('heading', { name: 'Delivery partner sign in' }, TIMEOUT),
    ).toBeInTheDocument();
    // A plain sign-in: the address does not keep the page the partner left (?returnTo=)
    expect(screen.getByTestId('address')).toHaveTextContent(/^\/partner\/sign-in$/);
    expect(sessionStorage.getItem('front-app.partner')).toBeNull();
    // The server ends the session too (and removes the partner area's refresh cookie)
    expect(sessionApi.logout).toHaveBeenCalledWith('DELIVERY_PARTNER');
  });
});

describe('the delivery partner area: live location', () => {
  afterEach(() => {
    removeFakeGeolocation();
  });

  it('shares the position while a delivery is on its way, and says so on the card and in the header', async () => {
    const phone = installFakeGeolocation();
    vi.mocked(partnerService.getMe).mockResolvedValue(partner({ status: 'BUSY' }));
    vi.mocked(partnerService.getCurrentDelivery).mockResolvedValue(
      delivery({ id: 55, status: 'PICKED_UP' }),
    );
    vi.mocked(partnerService.shareLocation).mockResolvedValue(undefined);
    renderWithProviders(<App />, { route: '/partner', store: signedInPartnerStore() });

    expect(await screen.findByText('Finding your location', {}, TIMEOUT)).toBeInTheDocument();
    await waitFor(() => expect(phone.activeWatches()).toBe(1));
    phone.report({ lat: 12.9716, lng: 77.5946 });

    expect(
      await screen.findByText('Sharing your live location with the customer'),
    ).toBeInTheDocument();
    expect(screen.getByText('Sharing location')).toBeInTheDocument();
    expect(partnerService.shareLocation).toHaveBeenCalledWith(
      55,
      expect.objectContaining({ latitude: 12.9716, longitude: 77.5946 }),
    );
  });

  it('asks nothing of the phone while the partner only has an offer', async () => {
    const phone = installFakeGeolocation();
    vi.mocked(partnerService.getMe).mockResolvedValue(partner({ status: 'BUSY' }));
    vi.mocked(partnerService.getCurrentDelivery).mockResolvedValue(
      delivery({ status: 'ASSIGNED' }),
    );
    renderWithProviders(<App />, { route: '/partner', store: signedInPartnerStore() });

    expect(await screen.findByText('New delivery offer', {}, TIMEOUT)).toBeInTheDocument();
    expect(phone.geolocation.watchPosition).not.toHaveBeenCalled();
    expect(screen.queryByText('Sharing location')).not.toBeInTheDocument();
  });
});
