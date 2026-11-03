import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Route, Routes } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { ApiError } from '@/api/apiErrorHandler';
import { tokenStore } from '@/api/tokenStore';
import { SearchBox } from '@/components/business/Storefront';
import { AccountMenu } from '@/layouts/customer/AccountMenu';
import RegisterPage from '@/pages/Storefront/RegisterPage';
import RestaurantMenuPage from '@/pages/Storefront/RestaurantMenuPage';
import RestaurantsPage from '@/pages/Storefront/RestaurantsPage';
import SignInPage from '@/pages/Storefront/SignInPage';
import StoreSearchPage from '@/pages/Storefront/StoreSearchPage';
import { catalogService } from '@/services/catalogService';
import { ratingService } from '@/services/ratingService';
import { restaurantService } from '@/services/restaurantService';
import { searchService } from '@/services/searchService';
import { userService } from '@/services/userService';
import { makeStore } from '@/store/store';
import { pageOf } from '@/test/fixtures';
import { chooseOption } from '@/test/chooseOption';
import { renderWithProviders } from '@/test/renderWithProviders';
import type { LoginResponse, StoreRestaurant } from '@/types/storefront';

vi.mock('@/services/userService', () => ({
  userService: { login: vi.fn(), register: vi.fn(), getMyProfile: vi.fn() },
}));
vi.mock('@/services/restaurantService', () => ({
  restaurantService: { browseRestaurants: vi.fn(), getStoreRestaurant: vi.fn() },
}));
vi.mock('@/services/catalogService', () => ({
  catalogService: { getRestaurantMenu: vi.fn(), getStoreCategories: vi.fn() },
}));
vi.mock('@/services/searchService', () => ({
  searchService: { searchRestaurants: vi.fn(), searchFoods: vi.fn(), suggest: vi.fn() },
}));

const priyaLogin: LoginResponse = {
  userId: 7,
  email: 'priya@example.com',
  fullName: 'Priya Sharma',
  role: 'CUSTOMER',
  lastLoginAt: null,
  accessToken: 'test-access-token',
  tokenType: 'Bearer',
  expiresIn: 900,
};

const apiError = (status: number, kind: ApiError['kind'], userMessage: string) =>
  new ApiError({ kind, status, userMessage, technicalMessage: userMessage });

function spiceGarden(overrides: Partial<StoreRestaurant> = {}): StoreRestaurant {
  return {
    id: 3,
    name: 'Spice Garden',
    description: 'Biryani since 1990',
    cuisines: ['Biryani', 'Mughlai'],
    phone: '+919876500012',
    address: {
      line1: '12 MG Road',
      line2: null,
      city: 'Bengaluru',
      state: 'Karnataka',
      postalCode: '560001',
      country: 'India',
      latitude: null,
      longitude: null,
    },
    timeZone: 'Asia/Kolkata',
    operatingHours: [{ dayOfWeek: 'MONDAY', opensAt: '11:00:00', closesAt: '23:00:00' }],
    openNow: true,
    ...overrides,
  };
}

/** Renders a page at an address, with a marker page for where it may lead. */
function renderAt(route: string, path: string, page: React.ReactElement, store = makeStore()) {
  return renderWithProviders(
    <Routes>
      <Route path={path} element={page} />
      <Route path="/customer" element={<p>Home page</p>} />
      <Route path="/customer/dashboard" element={<p>Dashboard page</p>} />
      <Route path="/customer/restaurants/:id" element={<p>Menu page</p>} />
      <Route path="/customer/sign-in" element={<p>Sign-in page</p>} />
    </Routes>,
    { route, store },
  );
}

beforeEach(() => {
  vi.clearAllMocks();
  sessionStorage.clear();
  vi.mocked(catalogService.getStoreCategories).mockResolvedValue([
    { id: 3, name: 'Biryani', description: null, imageUrl: null },
  ]);
});

describe('SignInPage', () => {
  it('signs a customer in and goes back to the page that asked', async () => {
    vi.mocked(userService.login).mockResolvedValue(priyaLogin);
    const { store } = renderAt(
      '/customer/sign-in?returnTo=%2Fcustomer%2Frestaurants%2F3',
      '/customer/sign-in',
      <SignInPage />,
    );

    await userEvent.type(screen.getByLabelText('Email'), 'priya@example.com');
    await userEvent.type(screen.getByLabelText('Password'), 'Eater1234');
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }));

    expect(await screen.findByText('Menu page')).toBeInTheDocument();
    expect(userService.login).toHaveBeenCalledWith('priya@example.com', 'Eater1234', 'CUSTOMER');
    expect(store.getState().session.customer).toEqual({
      id: 7,
      role: 'CUSTOMER',
      fullName: 'Priya Sharma',
      email: 'priya@example.com',
    });
    expect(sessionStorage.getItem('front-app.customer')).toContain('"id":7');
    // The access token stays in memory only, never in the browser's storage
    expect(tokenStore.get('customer')).toMatchObject({ value: 'test-access-token', userId: 7 });
    expect(sessionStorage.getItem('front-app.customer')).not.toContain('test-access-token');
  });

  it('opens the dashboard after signing in when no page asked for it', async () => {
    vi.mocked(userService.login).mockResolvedValue(priyaLogin);
    renderAt('/customer/sign-in', '/customer/sign-in', <SignInPage />);

    await userEvent.type(screen.getByLabelText('Email'), 'priya@example.com');
    await userEvent.type(screen.getByLabelText('Password'), 'Eater1234');
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }));

    expect(await screen.findByText('Dashboard page')).toBeInTheDocument();
    expect(await screen.findByText('Welcome back, Priya!')).toBeInTheDocument();
  });

  it('says so when the email or password is wrong, or the account is deactivated', async () => {
    vi.mocked(userService.login)
      .mockRejectedValueOnce(apiError(401, 'unauthorized', 'Invalid email or password'))
      .mockRejectedValueOnce(
        apiError(403, 'forbidden', 'Account is deactivated. Contact support.'),
      );
    renderAt('/customer/sign-in', '/customer/sign-in', <SignInPage />);

    await userEvent.type(screen.getByLabelText('Email'), 'priya@example.com');
    await userEvent.type(screen.getByLabelText('Password'), 'wrong1234');
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }));
    expect(await screen.findByText(/email or password is not right/)).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }));
    expect(await screen.findByText(/This account is deactivated/)).toBeInTheDocument();
  });

  it('explains the refusal of user-service for an account of another kind', async () => {
    vi.mocked(userService.login).mockRejectedValue(
      new ApiError({
        kind: 'forbidden',
        status: 403,
        code: 'WRONG_ACCOUNT_TYPE',
        userMessage: 'Forbidden',
        technicalMessage: 'HTTP 403',
      }),
    );
    const { store } = renderAt('/customer/sign-in', '/customer/sign-in', <SignInPage />);

    await userEvent.type(screen.getByLabelText('Email'), 'test.owner@fooddelivery.local');
    await userEvent.type(screen.getByLabelText('Password'), 'Test@12345');
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }));

    expect(await screen.findByText(/not a customer account/)).toBeInTheDocument();
    expect(store.getState().session.customer).toBeNull();
  });

  it('does not sign in an admin account as a customer', async () => {
    vi.mocked(userService.login).mockResolvedValue({ ...priyaLogin, role: 'PLATFORM_ADMIN' });
    const { store } = renderAt('/customer/sign-in', '/customer/sign-in', <SignInPage />);

    await userEvent.type(screen.getByLabelText('Email'), 'admin@fooddelivery.local');
    await userEvent.type(screen.getByLabelText('Password'), 'Admin1234');
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }));

    expect(await screen.findByText(/not a customer account/)).toBeInTheDocument();
    expect(store.getState().session.customer).toBeNull();
  });
});

describe('RegisterPage', () => {
  const fillIn = async () => {
    await userEvent.type(screen.getByLabelText('Full name'), 'Priya Sharma');
    await userEvent.type(screen.getByLabelText('Email'), 'priya@example.com');
    await userEvent.type(screen.getByLabelText('Password'), 'Eater1234');
    await userEvent.type(screen.getByLabelText('Password again'), 'Eater1234');
    await userEvent.click(screen.getByRole('button', { name: 'Create account' }));
  };

  it('creates the account and signs in with it', async () => {
    vi.mocked(userService.register).mockResolvedValue({
      id: 7,
      email: 'priya@example.com',
      fullName: 'Priya Sharma',
      phone: null,
      role: 'CUSTOMER',
      status: 'ACTIVE',
      createdAt: '2026-10-08T10:00:00Z',
      lastLoginAt: null,
    });
    vi.mocked(userService.login).mockResolvedValue(priyaLogin);
    const { store } = renderAt('/customer/register', '/customer/register', <RegisterPage />);

    await fillIn();

    expect(await screen.findByText('Home page')).toBeInTheDocument();
    expect(userService.register).toHaveBeenCalledWith({
      fullName: 'Priya Sharma',
      email: 'priya@example.com',
      password: 'Eater1234',
      phone: undefined,
    });
    expect(store.getState().session.customer?.id).toBe(7);
  });

  it('points to sign-in when the email already has an account', async () => {
    vi.mocked(userService.register).mockRejectedValue(
      apiError(409, 'conflict', 'Email is already registered: priya@example.com'),
    );
    renderAt('/customer/register', '/customer/register', <RegisterPage />);

    await fillIn();

    expect(await screen.findByText(/already has an account: sign in instead/)).toBeInTheDocument();
    expect(userService.login).not.toHaveBeenCalled();
  });
});

describe('RestaurantMenuPage', () => {
  beforeEach(() => {
    vi.mocked(catalogService.getRestaurantMenu).mockResolvedValue({
      restaurantId: 3,
      restaurantName: 'Spice Garden',
      sections: [
        {
          categoryId: 3,
          categoryName: 'Biryani',
          items: [
            {
              id: 1,
              name: 'Veg Biryani',
              description: null,
              price: 220,
              foodType: 'VEG',
              imageUrl: null,
              available: true,
            },
            {
              id: 2,
              name: 'Chicken Biryani',
              description: 'Hyderabadi',
              price: 299,
              foodType: 'NON_VEG',
              imageUrl: null,
              available: false,
            },
          ],
        },
      ],
    });
  });

  it('shows the restaurant rating, each dish rating next to its price, and the reviews at the end', async () => {
    vi.mocked(restaurantService.getStoreRestaurant).mockResolvedValue(spiceGarden());
    vi.mocked(ratingService.getDishRatings).mockResolvedValue([{ id: 1, average: 4.5, count: 8 }]);
    vi.mocked(ratingService.getRestaurantRating).mockResolvedValue({
      id: 3,
      average: 4.2,
      count: 5,
      distribution: { '1': 0, '2': 0, '3': 1, '4': 2, '5': 2 },
    });
    vi.mocked(ratingService.getRestaurantReviews).mockResolvedValue({
      content: [
        {
          id: 1,
          reviewerName: 'Priya S.',
          rating: 5,
          comment: 'Best biryani in town',
          tags: [],
          dishes: [],
          createdAt: '2026-06-24T10:15:00Z',
          updatedAt: '2026-06-24T10:15:00Z',
        },
      ],
      page: 0,
      size: 5,
      totalElements: 1,
      totalPages: 1,
      first: true,
      last: true,
    });
    renderAt(
      '/customer/restaurants/3',
      '/customer/restaurants/:restaurantId',
      <RestaurantMenuPage />,
    );

    // The header: the restaurant's average and count, leading to the reviews
    const summary = await screen.findByRole('link', { name: /4\.2/ });
    expect(summary).toHaveAttribute('href', '#reviews');
    expect(summary).toHaveTextContent('(5 reviews)');
    // The dish with ratings shows them; the other says it has none
    const rated = await screen.findByRole('img', { name: 'Rated 4.5 out of 5, 8 reviews' });
    expect(rated).toBeVisible();
    expect(screen.getByRole('img', { name: 'No ratings yet' })).toBeVisible();
    expect(ratingService.getDishRatings).toHaveBeenCalledWith([1, 2]);
    // The reviews section
    expect(await screen.findByText('Best biryani in town')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Ratings and reviews' })).toBeInTheDocument();
  });

  it('opens what customers wrote about a rated dish, and offers it only for dishes with ratings', async () => {
    vi.mocked(restaurantService.getStoreRestaurant).mockResolvedValue(spiceGarden());
    vi.mocked(ratingService.getDishRatings).mockResolvedValue([{ id: 1, average: 4.5, count: 8 }]);
    vi.mocked(ratingService.getDishRating).mockResolvedValue({
      id: 1,
      average: 4.5,
      count: 8,
      distribution: { '1': 0, '2': 0, '3': 1, '4': 2, '5': 5 },
    });
    vi.mocked(ratingService.getDishReviews).mockResolvedValue({
      content: [
        {
          id: 9,
          reviewerName: 'Ravi K.',
          rating: 5,
          comment: 'Fluffy rice, lovely spice',
          tags: [],
          dishes: [{ menuItemId: 1, dishName: 'Veg Biryani', rating: 5 }],
          createdAt: '2026-10-10T10:15:00Z',
          updatedAt: '2026-10-10T10:15:00Z',
        },
      ],
      page: 0,
      size: 5,
      totalElements: 1,
      totalPages: 1,
      first: true,
      last: true,
    });
    renderAt(
      '/customer/restaurants/3',
      '/customer/restaurants/:restaurantId',
      <RestaurantMenuPage />,
    );

    const read = await screen.findByRole('button', { name: 'Read the reviews of Veg Biryani' });
    // Chicken Biryani has no ratings: nothing to read
    expect(
      screen.queryByRole('button', { name: 'Read the reviews of Chicken Biryani' }),
    ).not.toBeInTheDocument();

    await userEvent.click(read);

    const dialog = within(await screen.findByRole('dialog', { name: 'Reviews of Veg Biryani' }));
    expect(await dialog.findByText('Fluffy rice, lovely spice')).toBeInTheDocument();
    expect(ratingService.getDishReviews).toHaveBeenCalledWith(1, 0, 5);
  });

  it('jumps to a category from its chip, and the menu search narrows the dishes and the chips', async () => {
    vi.mocked(restaurantService.getStoreRestaurant).mockResolvedValue(spiceGarden());
    renderAt(
      '/customer/restaurants/3',
      '/customer/restaurants/:restaurantId',
      <RestaurantMenuPage />,
    );

    const chips = within(await screen.findByRole('navigation', { name: 'Menu categories' }));
    expect(chips.getByRole('link', { name: /Biryani/ })).toHaveAttribute('href', '#menu-section-3');

    await userEvent.type(screen.getByRole('searchbox', { name: 'Search this menu' }), 'zzz');

    expect(await screen.findByText('No dishes match')).toBeInTheDocument();
    expect(screen.queryByRole('navigation', { name: 'Menu categories' })).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Show the whole menu' }));

    expect(await screen.findByText('Veg Biryani')).toBeInTheDocument();
    expect(screen.getByRole('searchbox', { name: 'Search this menu' })).toHaveValue('');
  });

  it("shows the restaurant, its hours and its menu; 'Veg only' hides the rest", async () => {
    vi.mocked(restaurantService.getStoreRestaurant).mockResolvedValue(spiceGarden());
    renderAt(
      '/customer/restaurants/3',
      '/customer/restaurants/:restaurantId',
      <RestaurantMenuPage />,
    );

    expect(
      await screen.findByRole('heading', { name: 'Spice Garden', level: 1 }),
    ).toBeInTheDocument();
    expect(screen.getByText('Open now')).toBeInTheDocument();
    expect(screen.getByText('11:00 am - 11:00 pm')).toBeInTheDocument();
    expect(await screen.findByText('Chicken Biryani')).toBeInTheDocument();
    expect(screen.getByText('Sold out')).toBeInTheDocument();
    expect(screen.getByText('₹220.00')).toBeInTheDocument();

    await userEvent.click(screen.getByLabelText('Veg only'));
    expect(screen.queryByText('Chicken Biryani')).not.toBeInTheDocument();
    expect(screen.getByText('Veg Biryani')).toBeInTheDocument();
  });

  it('says when the restaurant is closed right now', async () => {
    vi.mocked(restaurantService.getStoreRestaurant).mockResolvedValue(
      spiceGarden({ openNow: false }),
    );
    renderAt(
      '/customer/restaurants/3',
      '/customer/restaurants/:restaurantId',
      <RestaurantMenuPage />,
    );

    expect(await screen.findByText('Closed right now')).toBeInTheDocument();
  });
});

describe('RestaurantsPage', () => {
  it('lists restaurants and filters them by city', async () => {
    vi.mocked(restaurantService.browseRestaurants).mockResolvedValue(
      pageOf([
        { id: 3, name: 'Spice Garden', cuisines: ['Biryani'], city: 'Bengaluru', openNow: false },
      ]),
    );
    renderAt('/customer/restaurants', '/customer/restaurants', <RestaurantsPage />);

    const card = await screen.findByRole('link', { name: /Spice Garden/ });
    expect(within(card).getByText('Closed')).toBeInTheDocument();
    expect(restaurantService.browseRestaurants).toHaveBeenCalledWith({}, 0, 12);

    await userEvent.type(screen.getByLabelText('City'), 'Pune');
    await waitFor(() =>
      expect(restaurantService.browseRestaurants).toHaveBeenLastCalledWith({ city: 'Pune' }, 0, 12),
    );
  });

  it('shows each restaurant rating from the real reviews, and "No ratings yet" for one without', async () => {
    vi.mocked(restaurantService.browseRestaurants).mockResolvedValue(
      pageOf([
        { id: 3, name: 'Spice Garden', cuisines: ['Biryani'], city: 'Bengaluru', openNow: true },
        { id: 4, name: 'Dosa Corner', cuisines: ['South Indian'], city: 'Pune', openNow: true },
      ]),
    );
    vi.mocked(ratingService.getRestaurantRatings).mockResolvedValue([
      { id: 3, average: 4.3, count: 12 },
    ]);
    renderAt('/customer/restaurants', '/customer/restaurants', <RestaurantsPage />);

    const rated = await screen.findByRole('link', { name: /Spice Garden/ });
    expect(await within(rated).findByText('4.3')).toBeInTheDocument();
    expect(
      within(rated).getByRole('img', { name: 'Rated 4.3 out of 5, 12 reviews' }),
    ).toBeVisible();
    const unrated = screen.getByRole('link', { name: /Dosa Corner/ });
    expect(within(unrated).getByRole('img', { name: 'No ratings yet' })).toBeVisible();
    // One request for the whole page, not one per card
    expect(ratingService.getRestaurantRatings).toHaveBeenCalledTimes(1);
    expect(ratingService.getRestaurantRatings).toHaveBeenCalledWith([3, 4]);
  });

  it('shows no stars, and no error, when the ratings cannot be loaded', async () => {
    vi.mocked(restaurantService.browseRestaurants).mockResolvedValue(
      pageOf([
        { id: 3, name: 'Spice Garden', cuisines: ['Biryani'], city: 'Bengaluru', openNow: true },
      ]),
    );
    vi.mocked(ratingService.getRestaurantRatings).mockRejectedValue(
      apiError(500, 'server', 'Something went wrong on the server.'),
    );
    renderAt('/customer/restaurants', '/customer/restaurants', <RestaurantsPage />);

    const card = await screen.findByRole('link', { name: /Spice Garden/ });
    await waitFor(() => expect(ratingService.getRestaurantRatings).toHaveBeenCalled());
    expect(within(card).queryByRole('img', { name: /rating|Rated/i })).not.toBeInTheDocument();
    expect(screen.queryByText('Unable to load the ratings')).not.toBeInTheDocument();
  });
});

describe('StoreSearchPage', () => {
  it('finds restaurants, narrows them by cuisine, and finds dishes', async () => {
    vi.mocked(searchService.searchRestaurants).mockResolvedValue({
      query: 'biryani',
      totalHits: 1,
      page: 0,
      size: 12,
      totalPages: 1,
      results: [
        {
          id: 3,
          name: 'Spice Garden',
          description: null,
          cuisines: ['Biryani'],
          city: 'Bengaluru',
          addressLine: '12 MG Road, Bengaluru',
          distanceKm: null,
        },
      ],
      facets: { cuisines: [{ value: 'Biryani', count: 1 }] },
    });
    vi.mocked(searchService.searchFoods).mockResolvedValue({
      query: 'biryani',
      totalHits: 1,
      page: 0,
      size: 12,
      totalPages: 1,
      results: [
        {
          id: 2,
          name: 'Chicken Biryani',
          description: null,
          price: 299,
          foodType: 'NON_VEG',
          imageUrl: null,
          categoryId: 3,
          categoryName: 'Biryani',
          restaurantId: 3,
          restaurantName: 'Spice Garden',
          restaurantCity: 'Bengaluru',
        },
      ],
      facets: { foodTypes: [], categories: [] },
    });
    renderAt('/customer/search?q=biryani', '/customer/search', <StoreSearchPage />);

    expect(await screen.findByRole('link', { name: /Spice Garden/ })).toBeInTheDocument();
    expect(searchService.searchRestaurants).toHaveBeenCalledWith({ q: 'biryani' }, 0, 12);

    await userEvent.click(screen.getByRole('button', { name: /Biryani/, pressed: false }));
    await waitFor(() =>
      expect(searchService.searchRestaurants).toHaveBeenLastCalledWith(
        { q: 'biryani', cuisine: 'Biryani' },
        0,
        12,
      ),
    );

    await userEvent.click(screen.getByRole('tab', { name: 'Dishes' }));
    expect(await screen.findByText('Chicken Biryani')).toBeInTheDocument();
    await chooseOption(screen.getByLabelText('Type'), 'Veg');
    await waitFor(() =>
      expect(searchService.searchFoods).toHaveBeenLastCalledWith(
        { q: 'biryani', foodType: 'VEG' },
        0,
        12,
      ),
    );
  });
});

describe('SearchBox', () => {
  it('suggests as you type, and the arrow keys and Enter open a suggestion', async () => {
    vi.mocked(searchService.suggest).mockResolvedValue({
      query: 'bir',
      restaurants: [{ id: 3, name: 'Biryani House', detail: 'Bengaluru' }],
      foods: [{ id: 9, name: 'Chicken Biryani', detail: 'Spice Garden' }],
      categories: [],
    });
    renderAt('/customer', '/customer', <SearchBox />);

    const box = screen.getByRole('combobox', { name: 'Search for restaurants and dishes' });
    await userEvent.type(box, 'bir');
    expect(await screen.findByRole('option', { name: /Biryani House/ })).toBeInTheDocument();
    expect(searchService.suggest).toHaveBeenCalledWith('bir');

    await userEvent.keyboard('{ArrowDown}');
    expect(screen.getByRole('option', { name: /Biryani House/ })).toHaveAttribute(
      'aria-selected',
      'true',
    );
    await userEvent.keyboard('{Enter}');
    expect(await screen.findByText('Menu page')).toBeInTheDocument();
  });
});

describe('AccountMenu', () => {
  it('offers sign-in to visitors, and signs a customer out', async () => {
    const store = makeStore({
      session: {
        adminUser: null,
        customer: { id: 7, role: 'CUSTOMER', fullName: 'Priya Sharma', email: 'priya@example.com' },
      },
    });
    renderAt('/customer/restaurants', '/customer/restaurants', <AccountMenu />, store);

    await userEvent.click(screen.getByRole('button', { name: /Priya Sharma/ }));
    expect(screen.getByText('priya@example.com')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Sign out' }));

    expect(await screen.findByText('Home page')).toBeInTheDocument();
    expect(store.getState().session.customer).toBeNull();
    expect(await screen.findByText('You are signed out.')).toBeInTheDocument();
  });
});
