import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Route, Routes } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import OffersPage from '@/pages/Storefront/OffersPage';
import { couponService } from '@/services/couponService';
import { orderService } from '@/services/orderService';
import { ratingService } from '@/services/ratingService';
import { restaurantService } from '@/services/restaurantService';
import { makeStore } from '@/store/store';
import { cart, emptyCart, pageOf, priya } from '@/test/fixtures';
import { renderWithProviders } from '@/test/renderWithProviders';
import type { Offer } from '@/types/shopping';
import { formatCountdown, offerEndsAt } from '@/utils/offerUtils';

vi.mock('@/services/couponService', () => ({ couponService: { getOffers: vi.fn() } }));
vi.mock('@/services/orderService', () => ({
  orderService: { getCart: vi.fn(), applyCoupon: vi.fn() },
}));
vi.mock('@/services/restaurantService', () => ({
  restaurantService: { browseRestaurants: vi.fn() },
}));
vi.mock('@/services/ratingService', () => ({
  ratingService: { getRestaurantRatings: vi.fn() },
}));

const NOW = new Date(2026, 9, 11, 12, 0, 0);

const offer = (code: string, overrides: Partial<Offer> = {}): Offer => ({
  code,
  description: `${code} description`,
  summary: `${code} summary`,
  minOrderAmount: 0,
  restaurantId: null,
  validUntil: '2026-12-31',
  ...overrides,
});

const renderOffers = (store = makeStore({ session: { adminUser: null, customer: priya } })) =>
  renderWithProviders(
    <Routes>
      <Route path="/customer/offers" element={<OffersPage />} />
    </Routes>,
    { route: '/customer/offers', store },
  );

/** The tickets of the coupon list, by what each gives */
const tickets = async () =>
  within(await screen.findByRole('list', { name: 'Coupons' })).getAllByRole('article');
const ticket = (code: string) => screen.findByRole('article', { name: `${code} summary` });

beforeEach(() => {
  vi.useFakeTimers({ toFake: ['Date'] });
  vi.setSystemTime(NOW);
  vi.mocked(orderService.getCart).mockResolvedValue(emptyCart);
  vi.mocked(restaurantService.browseRestaurants).mockResolvedValue(pageOf([]));
  vi.mocked(ratingService.getRestaurantRatings).mockResolvedValue([]);
});

afterEach(() => {
  vi.useRealTimers();
  vi.clearAllMocks();
});

describe('OffersPage', () => {
  it('lists every coupon as a ticket, with the one ending soonest up front', async () => {
    vi.mocked(couponService.getOffers).mockResolvedValue([
      offer('LATER'),
      offer('SOONEST', { validUntil: '2026-10-13' }),
      offer('MIDDLE', { validUntil: '2026-11-20' }),
    ]);
    renderOffers();

    expect(await tickets()).toHaveLength(3);
    const spotlight = within(await screen.findByRole('region', { name: 'Ending soonest' }));
    expect(spotlight.getByText('SOONEST summary')).toBeInTheDocument();
    expect(screen.getByText('3 coupons you can use today')).toBeInTheDocument();

    // The hero's chips jump to the sections below
    const chips = within(screen.getByRole('navigation', { name: 'Sections of this page' }));
    expect(chips.getByRole('link', { name: 'All coupons' })).toHaveAttribute(
      'href',
      '#all-coupons',
    );
    expect(chips.getByRole('link', { name: 'Seasonal picks' })).toHaveAttribute(
      'href',
      '#seasonal-picks',
    );
  });

  it('says what each coupon asks for, and copies its code', async () => {
    vi.mocked(couponService.getOffers).mockResolvedValue([
      offer('SAVE50', { minOrderAmount: 299 }),
    ]);
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    const writeText = vi.spyOn(navigator.clipboard, 'writeText').mockResolvedValue(undefined);
    renderOffers();

    const save = within(await ticket('SAVE50'));
    expect(save.getByText('SAVE50 description')).toBeInTheDocument();
    expect(save.getByText(/Min\. order ₹299\.00/)).toBeInTheDocument();
    expect(save.getByText('All restaurants')).toBeInTheDocument();
    await user.click(save.getByRole('button', { name: 'Copy code SAVE50' }));

    expect(writeText).toHaveBeenCalledWith('SAVE50');
    expect(await screen.findByText('Copied SAVE50')).toBeInTheDocument();
  });

  it('counts down the coupons ending within a week, and shows only those under "Ending soon"', async () => {
    vi.mocked(couponService.getOffers).mockResolvedValue([
      offer('LATER'),
      offer('TODAY', { validUntil: '2026-10-11' }),
      offer('FRIDAY', { validUntil: '2026-10-16' }),
    ]);
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    renderOffers();

    const today = within(await ticket('TODAY'));
    expect(today.getByText('Ends today')).toBeInTheDocument();
    expect(today.getByRole('timer', { name: 'Time left for TODAY' })).toHaveTextContent(
      formatCountdown(offerEndsAt('2026-10-11') - NOW.getTime()),
    );
    const friday = within(await ticket('FRIDAY'));
    expect(friday.getByRole('timer').textContent).toMatch(/^\d+d \d{2}:\d{2}:\d{2}$/);
    // Months away: its last day, no clock
    const later = within(await ticket('LATER'));
    expect(later.queryByRole('timer')).not.toBeInTheDocument();
    expect(later.getByText(/Valid until/)).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: /Ending soon/ }));

    await waitFor(async () => expect(await tickets()).toHaveLength(2));
    expect(screen.getByRole('button', { name: /Ending soon/ })).toHaveAttribute(
      'aria-pressed',
      'true',
    );
    expect(screen.queryByRole('article', { name: 'LATER summary' })).not.toBeInTheDocument();
  });

  it("tells a restaurant's own coupon apart, under its own tab", async () => {
    vi.mocked(couponService.getOffers).mockResolvedValue([
      offer('EVERYWHERE'),
      offer('SPICE100', { restaurantId: 7 }),
    ]);
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    renderOffers();

    expect(within(await ticket('SPICE100')).getByText('Restaurant special')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: /Restaurant specials/ }));

    await waitFor(async () => expect(await tickets()).toHaveLength(1));
    expect(screen.getByRole('article', { name: 'SPICE100 summary' })).toBeInTheDocument();
  });

  it('marks a restaurant that has a coupon of its own', async () => {
    vi.mocked(couponService.getOffers).mockResolvedValue([
      offer('SPICE100', { restaurantId: 7, summary: '100.00 off' }),
    ]);
    vi.mocked(restaurantService.browseRestaurants).mockResolvedValue(
      pageOf([
        {
          id: 7,
          name: 'Spice Garden',
          cuisines: ['North Indian'],
          city: 'Bengaluru',
          openNow: true,
        },
        { id: 8, name: 'Pizza Palace', cuisines: ['Italian'], city: 'Bengaluru', openNow: true },
      ]),
    );
    renderOffers();

    const restaurants = await screen.findByRole('region', { name: 'Restaurants to use them at' });
    const spice = await within(restaurants).findByRole('link', { name: /Spice Garden/ });
    expect(spice).toHaveTextContent('100.00 off');
    expect(within(restaurants).getByRole('link', { name: /Pizza Palace/ })).not.toHaveTextContent(
      '100.00 off',
    );
  });

  it('sends a customer with an empty cart to order first', async () => {
    vi.mocked(couponService.getOffers).mockResolvedValue([offer('SAVE50')]);
    renderOffers();

    expect(
      await screen.findByText('Add dishes to your cart, then apply a coupon here in one tap.'),
    ).toBeInTheDocument();
    expect(within(await ticket('SAVE50')).getByRole('link', { name: 'Order now' })).toHaveAttribute(
      'href',
      '/customer/restaurants',
    );
    expect(screen.getByRole('link', { name: 'Go to my cart' })).toBeInTheDocument();
  });

  it('applies a coupon straight to a cart that has dishes', async () => {
    vi.mocked(orderService.getCart).mockResolvedValue(cart());
    vi.mocked(orderService.applyCoupon).mockResolvedValue(
      cart({
        coupon: {
          code: 'SAVE50',
          description: null,
          applied: true,
          discountAmount: 50,
          reason: null,
        },
      }),
    );
    vi.mocked(couponService.getOffers).mockResolvedValue([offer('SAVE50')]);
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    renderOffers();

    expect(await screen.findByText(/Your cart is ready/)).toBeInTheDocument();
    await user.click(
      await within(await ticket('SAVE50')).findByRole('button', { name: 'Apply to cart' }),
    );

    await waitFor(() => expect(orderService.applyCoupon).toHaveBeenCalledWith('SAVE50'));
    // The cart's restaurant was asked for: its own coupons are listed too
    expect(couponService.getOffers).toHaveBeenCalledWith(3);
  });

  it('invites visitors to sign in to use a coupon', async () => {
    vi.mocked(couponService.getOffers).mockResolvedValue([offer('SAVE50')]);
    renderOffers(makeStore());

    expect(
      await screen.findByText('Sign in to apply a coupon to your cart in one tap.'),
    ).toBeInTheDocument();
    expect(
      within(await ticket('SAVE50')).getByRole('link', { name: 'Sign in to use' }),
    ).toBeInTheDocument();
  });

  it('still offers the seasonal picks when there are no coupons today', async () => {
    vi.mocked(couponService.getOffers).mockResolvedValue([]);
    renderOffers(makeStore());

    expect(await screen.findByText('No coupons today')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Browse restaurants' })).toHaveAttribute(
      'href',
      '/customer/restaurants',
    );
    const picks = screen.getByRole('region', { name: 'Seasonal picks' });
    expect(within(picks).getByRole('link', { name: /Weekend feast/ })).toHaveAttribute(
      'href',
      '/customer/search?q=biryani',
    );
    expect(screen.getByRole('link', { name: 'Create an account' })).toBeInTheDocument();
  });
});
