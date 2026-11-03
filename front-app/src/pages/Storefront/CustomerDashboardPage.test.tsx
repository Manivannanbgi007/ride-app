import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Route, Routes } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { ApiError } from '@/api/apiErrorHandler';
import {
  changeOf,
  daysIn,
  figuresOf,
  previousRangeOf,
  rangeOf,
} from '@/components/business/CustomerDashboard';
import CustomerDashboardPage from '@/pages/Storefront/CustomerDashboardPage';
import { catalogService } from '@/services/catalogService';
import { couponService } from '@/services/couponService';
import { orderService } from '@/services/orderService';
import { searchService } from '@/services/searchService';
import { userService } from '@/services/userService';
import { makeStore } from '@/store/store';
import { address, cart, emptyCart, orderSummary, pageOf, priya } from '@/test/fixtures';
import { renderWithProviders } from '@/test/renderWithProviders';
import type { CustomerOrderActivity, CustomerOrderSummary, FavouriteDish } from '@/types/order';
import type { FoodHit } from '@/types/storefront';

vi.mock('@/services/orderService', () => ({
  orderService: {
    getMySummary: vi.fn(),
    getMyActivity: vi.fn(),
    getMyFavouriteDishes: vi.fn(),
    getMyOrders: vi.fn(),
    getCart: vi.fn(),
    clearCart: vi.fn(),
    setCartQuantity: vi.fn(),
    removeFromCart: vi.fn(),
    applyCoupon: vi.fn(),
  },
}));
vi.mock('@/services/couponService', () => ({ couponService: { getOffers: vi.fn() } }));
vi.mock('@/services/searchService', () => ({
  searchService: { searchFoods: vi.fn(), searchRestaurants: vi.fn(), suggest: vi.fn() },
}));
vi.mock('@/services/catalogService', () => ({
  catalogService: { getStoreCategories: vi.fn() },
}));
vi.mock('@/services/userService', () => ({ userService: { getAddresses: vi.fn() } }));

// Sun 18 Oct 2026, noon on the customer's clock
const NOW = new Date(2026, 9, 18, 12, 0, 0);

const summary = (overrides: Partial<CustomerOrderSummary> = {}): CustomerOrderSummary => ({
  customerId: 7,
  totalOrders: 6,
  activeOrders: 1,
  deliveredOrders: 3,
  cancelledOrders: 1,
  rejectedOrders: 1,
  totalSpent: 2443.45,
  lastOrderAt: '2026-10-17T14:30:00Z',
  currency: 'INR',
  ...overrides,
});

function activity(fromDate: string, toDate: string, totalOrders = 3): CustomerOrderActivity {
  return {
    fromDate,
    toDate,
    totalOrders,
    ordersByStatus: totalOrders > 0 ? { DELIVERED: 2, PLACED: 1 } : {},
    spent: totalOrders > 0 ? 1200 : 0,
    currency: 'INR',
    perDay: [
      { date: fromDate, orders: 0, completed: 0, cancelled: 0, revenue: 0 },
      {
        date: toDate,
        orders: totalOrders,
        completed: totalOrders > 0 ? 2 : 0,
        cancelled: 0,
        revenue: totalOrders > 0 ? 1200 : 0,
      },
    ],
  };
}

const favourite = (overrides: Partial<FavouriteDish> = {}): FavouriteDish => ({
  menuItemId: 11,
  name: 'Chicken Biryani',
  restaurantId: 3,
  restaurantName: 'Biryani House',
  price: 299,
  foodType: 'NON_VEG',
  imageUrl: null,
  categoryName: 'Biryani',
  available: true,
  timesOrdered: 3,
  quantity: 5,
  lastOrderedAt: '2026-10-17T14:30:00Z',
  ...overrides,
});

const hit = (overrides: Partial<FoodHit> = {}): FoodHit => ({
  id: 21,
  name: 'Masala Dosa',
  description: 'Crisp, with chutney',
  price: 120,
  foodType: 'VEG',
  imageUrl: null,
  categoryId: 4,
  categoryName: 'South Indian',
  restaurantId: 5,
  restaurantName: 'Dosa Corner',
  restaurantCity: 'Bengaluru',
  ratingAverage: 4.7,
  ratingCount: 12,
  ...overrides,
});

const searchResults = (results: FoodHit[]) => ({
  query: null,
  totalHits: results.length,
  page: 0,
  size: 12,
  totalPages: 1,
  results,
  facets: { foodTypes: [], categories: [] },
});

const apiError = (status: number, kind: ApiError['kind'], userMessage: string) =>
  new ApiError({ kind, status, userMessage, technicalMessage: userMessage });

function renderDashboard() {
  return renderWithProviders(
    <Routes>
      <Route path="/customer/dashboard" element={<CustomerDashboardPage />} />
      <Route path="/customer/cart" element={<p>Cart page</p>} />
    </Routes>,
    {
      route: '/customer/dashboard',
      store: makeStore({ session: { adminUser: null, customer: priya } }),
    },
  );
}

const section = (name: string | RegExp) => screen.findByRole('region', { name });
/** Whether `first` comes before `second` on the page */
const isBefore = (first: Element, second: Element) =>
  Boolean(first.compareDocumentPosition(second) & Node.DOCUMENT_POSITION_FOLLOWING);

beforeEach(() => {
  vi.useFakeTimers({ toFake: ['Date'] });
  vi.setSystemTime(NOW);
  vi.mocked(orderService.getMySummary).mockResolvedValue(summary());
  vi.mocked(orderService.getMyActivity).mockImplementation((from, to) =>
    Promise.resolve(activity(from, to)),
  );
  vi.mocked(orderService.getMyFavouriteDishes).mockResolvedValue([favourite()]);
  vi.mocked(orderService.getMyOrders).mockResolvedValue(pageOf([]));
  vi.mocked(orderService.getCart).mockResolvedValue(emptyCart);
  vi.mocked(couponService.getOffers).mockResolvedValue([]);
  vi.mocked(searchService.searchFoods).mockResolvedValue(searchResults([]));
  vi.mocked(catalogService.getStoreCategories).mockResolvedValue([]);
  vi.mocked(userService.getAddresses).mockResolvedValue([
    address({ defaultAddress: true, line1: '12 MG Road' }),
  ]);
});

afterEach(() => {
  vi.useRealTimers();
  vi.clearAllMocks();
});

describe('activity periods and figures', () => {
  it('counts today in, and starts "this month" on its first day', () => {
    expect(rangeOf('week', NOW)).toEqual({ fromDate: '2026-10-12', toDate: '2026-10-18' });
    expect(rangeOf('month', NOW)).toEqual({ fromDate: '2026-09-19', toDate: '2026-10-18' });
    expect(rangeOf('calendar', NOW)).toEqual({ fromDate: '2026-10-01', toDate: '2026-10-18' });
  });

  it('compares a period with the one of the same length just before it', () => {
    const october = { fromDate: '2026-10-01', toDate: '2026-10-18' };
    expect(daysIn(october)).toBe(18);
    expect(previousRangeOf(october)).toEqual({ fromDate: '2026-09-13', toDate: '2026-09-30' });
    // Across the turn of a year
    expect(previousRangeOf({ fromDate: '2027-01-01', toDate: '2027-01-07' })).toEqual({
      fromDate: '2026-12-25',
      toDate: '2026-12-31',
    });
    expect(changeOf(3, 2)).toBe(50);
    expect(changeOf(1, 4)).toBe(-75);
    expect(changeOf(3, 0)).toBeNull();
  });

  it('reads the charts’ figures from the totals, and adds the spending up day by day', () => {
    const figures = figuresOf({
      ...activity('2026-10-16', '2026-10-18'),
      perDay: [
        { date: '2026-10-16', orders: 2, completed: 1, cancelled: 1, revenue: 500 },
        { date: '2026-10-17', orders: 0, completed: 0, cancelled: 0, revenue: 0 },
        { date: '2026-10-18', orders: 1, completed: 1, cancelled: 0, revenue: 700 },
      ],
    });
    expect(figures.days.map((day) => day.spentSoFar)).toEqual([500, 500, 1200]);
    expect(figures.days[0]).toMatchObject({ delivered: 1, cancelled: 1, other: 0 });
    expect(figures.daysWithOrders).toBe(2);
    expect(figures.busiestDay?.date).toBe('2026-10-16');
    expect(figures.biggestSpendDay?.date).toBe('2026-10-18');
    expect(figures.averageOrder).toBe(600);
  });
});

describe('CustomerDashboardPage', () => {
  it('greets the customer and shows where the food goes and their all-time totals', async () => {
    renderDashboard();

    expect(
      await screen.findByRole('heading', { level: 1, name: /Good afternoon, Priya/ }),
    ).toBeInTheDocument();
    expect(await screen.findByText(/12 MG Road/)).toBeInTheDocument();

    const totals = within(await section('Your orders · all time'));
    expect(totals.getByText('Orders placed')).toBeInTheDocument();
    expect(totals.getByText('6')).toBeInTheDocument();
    expect(totals.getByText('3')).toBeInTheDocument();
    expect(totals.getByText('2 cancelled or declined')).toBeInTheDocument();
    expect(totals.getByText('₹2,443.45')).toBeInTheDocument();
    // The customer is never named in the request: the server knows who is asking
    expect(orderService.getMySummary).toHaveBeenCalledWith();
  });

  it('puts the quick actions right under the banner, with the cart and its dishes', async () => {
    vi.mocked(orderService.getCart).mockResolvedValue(cart());
    renderDashboard();

    const banner = await section(/Good afternoon/);
    const quick = await screen.findByRole('navigation', { name: 'Quick actions' });
    expect(isBefore(banner, quick)).toBe(true);
    // Nothing but the banner comes before them
    expect(isBefore(quick, await section('Your frequent picks'))).toBe(true);
    expect(isBefore(quick, await section('Your orders · all time'))).toBe(true);

    const actions = within(quick);
    expect(actions.getByRole('link', { name: 'Restaurants' })).toHaveAttribute(
      'href',
      '/customer/restaurants',
    );
    expect(actions.getByRole('link', { name: 'My orders' })).toHaveAttribute(
      'href',
      '/customer/orders',
    );
    expect(await actions.findByRole('link', { name: 'Cart, 2 dishes' })).toHaveAttribute(
      'href',
      '/customer/cart',
    );
    expect(actions.getByRole('link', { name: 'Addresses' })).toHaveAttribute(
      'href',
      '/customer/account/addresses',
    );
    expect(actions.getByRole('link', { name: 'Help' })).toHaveAttribute(
      'href',
      '/customer/support',
    );
    // No order is going: nothing to track
    expect(actions.queryByRole('link', { name: /Track order/ })).not.toBeInTheDocument();
  });

  it('shows the frequent picks, most ordered first, at today’s prices, and adds one to the cart', async () => {
    vi.mocked(orderService.getMyFavouriteDishes).mockResolvedValue([
      favourite(),
      favourite({ menuItemId: 13, name: 'Paneer Tikka', timesOrdered: 2, price: 249 }),
      favourite({ menuItemId: 12, name: 'Mutton Biryani', available: false, timesOrdered: 2 }),
      favourite({
        menuItemId: 14,
        name: 'Old Special',
        price: null,
        available: false,
        timesOrdered: 2,
      }),
      // Ordered once: not a frequent pick
      favourite({ menuItemId: 15, name: 'Raita', timesOrdered: 1 }),
    ]);
    vi.mocked(orderService.setCartQuantity).mockResolvedValue(cart());
    renderDashboard();

    const picks = within(await section('Your frequent picks'));
    const cards = within(await picks.findByRole('list', { name: 'Frequent picks' }));
    expect(cards.getAllByRole('article').map((card) => card.getAttribute('aria-label'))).toEqual([
      'Chicken Biryani',
      'Paneer Tikka',
      'Mutton Biryani',
      'Old Special',
    ]);
    expect(picks.getByText('Ordered 3 times')).toBeInTheDocument();
    expect(picks.getByText('₹299.00')).toBeInTheDocument();
    expect(orderService.getMyFavouriteDishes).toHaveBeenCalledWith(8);

    // A dish that cannot be ordered now has no "Add", and points to its restaurant's menu instead
    const soldOut = within(picks.getByRole('article', { name: 'Mutton Biryani' }));
    expect(soldOut.getByText('Not available right now')).toBeInTheDocument();
    expect(soldOut.queryByRole('button', { name: /Add/ })).not.toBeInTheDocument();
    expect(
      soldOut.getByRole('link', { name: "See Biryani House's menu instead of Mutton Biryani" }),
    ).toHaveAttribute('href', '/customer/restaurants/3');
    const gone = within(picks.getByRole('article', { name: 'Old Special' }));
    expect(gone.getByText('No longer on the menu')).toBeInTheDocument();
    expect(gone.queryByRole('button', { name: /Add/ })).not.toBeInTheDocument();

    // "Add" goes through the cart: the server prices it and checks it can be ordered
    await userEvent.click(picks.getByRole('button', { name: 'Add Chicken Biryani' }));
    await waitFor(() => expect(orderService.setCartQuantity).toHaveBeenCalledWith(11, 1));
    expect(await screen.findByText('Chicken Biryani is in your cart.')).toBeInTheDocument();
  });

  it('says what the frequent picks will be until a dish was ordered twice', async () => {
    vi.mocked(orderService.getMyFavouriteDishes).mockResolvedValue([
      favourite({ timesOrdered: 1 }),
    ]);
    renderDashboard();

    const picks = within(await section('Your frequent picks'));
    expect(
      await picks.findByText(/Your frequent picks will appear here once you have ordered/),
    ).toBeInTheDocument();
    expect(picks.queryByRole('list', { name: 'Frequent picks' })).not.toBeInTheDocument();
  });

  it('suggests top rated dishes, leaving out the customer’s own frequent picks', async () => {
    vi.mocked(searchService.searchFoods).mockResolvedValue(
      searchResults([hit({ id: 11, name: 'Chicken Biryani' }), hit()]),
    );
    renderDashboard();

    const suggestions = within(await section('You may like'));
    expect(suggestions.getByText('Top rated by customers right now')).toBeInTheDocument();
    expect(await suggestions.findByRole('button', { name: 'Add Masala Dosa' })).toBeEnabled();
    // Chicken Biryani is a frequent pick (ordered 3 times): it has its own row
    await waitFor(() =>
      expect(
        suggestions.queryByRole('article', { name: 'Chicken Biryani' }),
      ).not.toBeInTheDocument(),
    );
    expect(searchService.searchFoods).toHaveBeenCalledWith({ sort: 'RATING' }, 0, 12);
  });

  it('puts an order that is going first, with its progress and what to do', async () => {
    vi.mocked(orderService.getMyOrders).mockResolvedValue(
      pageOf([
        orderSummary({ id: 50, status: 'OUT_FOR_DELIVERY', restaurantName: 'Spice Garden' }),
        orderSummary({ id: 51, status: 'PAYMENT_PENDING', paymentStatus: 'PENDING' }),
        orderSummary({
          id: 42,
          status: 'DELIVERED',
          items: [{ menuItemId: 11, name: 'Chicken Biryani', foodType: 'NON_VEG', quantity: 2 }],
        }),
      ]),
    );
    renderDashboard();

    const going = await section('Your orders in progress');
    const inProgress = within(going);
    expect(isBefore(going, await section('Your frequent picks'))).toBe(true);
    expect(
      await inProgress.findByRole('progressbar', { name: 'Progress of order #50' }),
    ).toHaveAttribute('aria-valuetext', 'Step 5 of 6: On the way');
    expect(inProgress.getByRole('link', { name: 'Track order #50 on the map' })).toHaveAttribute(
      'href',
      '/customer/orders/50/track',
    );
    expect(inProgress.getByRole('link', { name: 'Pay now for order #51' })).toHaveAttribute(
      'href',
      '/customer/orders/51/pay',
    );
    // The quick actions lead to the order on its way too
    expect(
      within(screen.getByRole('navigation', { name: 'Quick actions' })).getByRole('link', {
        name: 'Track order #50',
      }),
    ).toHaveAttribute('href', '/customer/orders/50/track');

    // Recent orders: the finished ones only
    const recent = within(await section('Recent orders'));
    expect(recent.getAllByRole('article')).toHaveLength(1);
    expect(recent.getByRole('button', { name: 'Reorder order #42' })).toBeInTheDocument();
    expect(recent.getByRole('link', { name: 'View all orders' })).toHaveAttribute(
      'href',
      '/customer/orders',
    );
    expect(orderService.getMyOrders).toHaveBeenCalledWith(undefined, 0, 8);
  });

  it('says so when there are no orders at all, and when nothing is on offer', async () => {
    vi.mocked(orderService.getMySummary).mockResolvedValue(
      summary({
        totalOrders: 0,
        activeOrders: 0,
        deliveredOrders: 0,
        cancelledOrders: 0,
        rejectedOrders: 0,
        totalSpent: 0,
        lastOrderAt: null,
      }),
    );
    vi.mocked(orderService.getMyActivity).mockImplementation((from, to) =>
      Promise.resolve(activity(from, to, 0)),
    );
    vi.mocked(orderService.getMyFavouriteDishes).mockResolvedValue([]);
    renderDashboard();

    expect(await screen.findByText('No orders in this period.')).toBeInTheDocument();
    expect(await screen.findByText(/No orders yet/)).toBeInTheDocument();
    expect(await screen.findByText(/No coupons today/)).toBeInTheDocument();
    expect(screen.queryByRole('region', { name: /in progress/ })).not.toBeInTheDocument();
  });

  it('shows what went wrong, and loads the totals again on "Try again"', async () => {
    vi.mocked(orderService.getMySummary).mockRejectedValueOnce(
      apiError(503, 'network', 'The service is not reachable right now.'),
    );
    renderDashboard();

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('Unable to load your order totals');
    await userEvent.click(within(alert).getByRole('button', { name: 'Try again' }));

    expect(await section('Your orders · all time')).toBeInTheDocument();
    expect(orderService.getMySummary).toHaveBeenCalledTimes(2);
  });

  it('reorders: the dishes go into the cart at the quantity ordered, then the cart opens', async () => {
    vi.mocked(orderService.getMyOrders).mockResolvedValue(
      pageOf([
        orderSummary({
          id: 42,
          status: 'DELIVERED',
          items: [
            { menuItemId: 11, name: 'Chicken Biryani', foodType: 'NON_VEG', quantity: 2 },
            { menuItemId: 12, name: 'Mutton Biryani', foodType: 'NON_VEG', quantity: 1 },
          ],
        }),
      ]),
    );
    vi.mocked(orderService.setCartQuantity).mockImplementation((menuItemId) =>
      menuItemId === 12
        ? Promise.reject(apiError(409, 'conflict', 'Mutton Biryani is sold out.'))
        : Promise.resolve(cart()),
    );
    renderDashboard();

    await userEvent.click(await screen.findByRole('button', { name: 'Reorder order #42' }));

    expect(await screen.findByText('Cart page')).toBeInTheDocument();
    expect(orderService.setCartQuantity).toHaveBeenCalledWith(11, 2);
    expect(orderService.setCartQuantity).toHaveBeenCalledWith(12, 1);
    expect(orderService.clearCart).not.toHaveBeenCalled();
    expect(
      await screen.findByText(
        '1 dish added to your cart. Not available right now: Mutton Biryani.',
      ),
    ).toBeInTheDocument();
  });

  it('asks before replacing a cart that holds another restaurant’s dishes', async () => {
    vi.mocked(orderService.getCart).mockResolvedValue(cart({ restaurantId: 9 }));
    vi.mocked(orderService.getMyOrders).mockResolvedValue(
      pageOf([
        orderSummary({
          id: 42,
          status: 'DELIVERED',
          items: [{ menuItemId: 11, name: 'Chicken Biryani', foodType: 'NON_VEG', quantity: 2 }],
        }),
      ]),
    );
    vi.mocked(orderService.clearCart).mockResolvedValue();
    vi.mocked(orderService.setCartQuantity).mockResolvedValue(cart());
    renderDashboard();

    const reorder = await screen.findByRole('button', { name: 'Reorder order #42' });
    await waitFor(() => expect(reorder).toBeEnabled());
    await userEvent.click(reorder);
    const dialog = within(await screen.findByRole('dialog', { name: 'Start a new cart?' }));
    expect(orderService.setCartQuantity).not.toHaveBeenCalled();

    await userEvent.click(dialog.getByRole('button', { name: 'Start a new cart' }));
    expect(await screen.findByText('Cart page')).toBeInTheDocument();
    expect(orderService.clearCart).toHaveBeenCalledTimes(1);
    expect(orderService.setCartQuantity).toHaveBeenCalledWith(11, 2);
  });

  it('shows the first coupons of the day, compact, with a way to all of them', async () => {
    vi.mocked(couponService.getOffers).mockResolvedValue(
      ['WELCOME100', 'FEAST20', 'LATENIGHT75', 'SWEET10'].map((code) => ({
        code,
        description: `${code} description`,
        summary: `${code} summary`,
        minOrderAmount: 0,
        restaurantId: null,
        validUntil: '2026-12-31',
      })),
    );
    renderDashboard();

    const offers = within(await section(/Offers for you/));
    const coupons = within(await offers.findByRole('list', { name: 'Coupons' }));
    expect(coupons.getAllByRole('article')).toHaveLength(3);
    const first = within(coupons.getByRole('article', { name: 'WELCOME100 summary' }));
    expect(first.getByText('Any order')).toBeInTheDocument();
    expect(first.getByRole('button', { name: 'Copy code WELCOME100' })).toBeInTheDocument();
    expect(offers.getByText(/4 you can use today/)).toBeInTheDocument();
    expect(offers.getByRole('link', { name: 'All offers' })).toHaveAttribute(
      'href',
      '/customer/offers',
    );
    // Without dishes in the cart only the platform's own coupons are asked for
    expect(couponService.getOffers).toHaveBeenCalledWith(null);
  });

  it('shows the last 30 days in figures, compared with the 30 days before', async () => {
    // 3 orders now, 2 in the 30 days before
    vi.mocked(orderService.getMyActivity).mockImplementation((from, to) =>
      Promise.resolve(activity(from, to, from === '2026-08-20' ? 2 : 3)),
    );
    renderDashboard();

    const charts = within(await section(/Your order activity/));
    expect(charts.getByText(/19 Sept – 18 Oct 2026/)).toBeInTheDocument();
    const figures = within(await charts.findByRole('list', { name: 'The period in figures' }));
    expect(figures.getByText('Orders').closest('li')).toHaveTextContent('3');
    expect(figures.getByText('Spent').closest('li')).toHaveTextContent('₹1,200.00');
    expect(figures.getByText('Average order').closest('li')).toHaveTextContent('₹600.00');
    expect(await figures.findByText('Up 50% on the previous 30 days')).toBeInTheDocument();
    expect(figures.getByText('Same as the previous 30 days')).toBeInTheDocument();
    expect(orderService.getMyActivity).toHaveBeenCalledWith('2026-09-19', '2026-10-18');
    expect(orderService.getMyActivity).toHaveBeenCalledWith('2026-08-20', '2026-09-18');

    const byStatus = within(charts.getByRole('list', { name: 'Orders by status' }));
    // Each group: its number of orders, then its share of the period's orders
    expect(byStatus.getByText('Delivered').closest('li')).toHaveTextContent(/Delivered\s*2\s*67%/);
    expect(byStatus.getByText('In progress').closest('li')).toHaveTextContent(
      /In progress\s*1\s*33%/,
    );
    const highlights = within(charts.getByRole('list', { name: 'Highlights' }));
    expect(highlights.getByText('1 of 30 days')).toBeInTheDocument();
    expect(highlights.getByText('Busiest day:').closest('li')).toHaveTextContent(
      'Busiest day:Sun, 18 Oct, 3 orders',
    );

    // One chart, switched to the orders per day
    await userEvent.click(charts.getByRole('tab', { name: 'Orders' }));
    const parts = within(charts.getByRole('list', { name: 'Orders in the period' }));
    expect(parts.getByText('Delivered').closest('li')).toHaveTextContent('Delivered2');
  });

  it('asks the server again for another period, and for the period before it', async () => {
    renderDashboard();

    const charts = within(await section(/Your order activity/));
    await charts.findByRole('list', { name: 'The period in figures' });

    await userEvent.click(charts.getByRole('tab', { name: 'Last 7 days' }));
    await waitFor(() =>
      expect(orderService.getMyActivity).toHaveBeenCalledWith('2026-10-12', '2026-10-18'),
    );
    expect(orderService.getMyActivity).toHaveBeenCalledWith('2026-10-05', '2026-10-11');
    expect(await charts.findByText('12 Oct – 18 Oct 2026')).toBeInTheDocument();

    await userEvent.click(charts.getByRole('tab', { name: 'This month' }));
    await waitFor(() =>
      expect(orderService.getMyActivity).toHaveBeenCalledWith('2026-10-01', '2026-10-18'),
    );
    // This month so far is 18 days: compared with the 18 days before it
    expect(orderService.getMyActivity).toHaveBeenCalledWith('2026-09-13', '2026-09-30');
  });

  it('still shows the period when the one before cannot be loaded, and says so', async () => {
    vi.mocked(orderService.getMyActivity).mockImplementation((from, to) =>
      from === '2026-08-20'
        ? Promise.reject(apiError(503, 'network', 'The service is not reachable right now.'))
        : Promise.resolve(activity(from, to)),
    );
    renderDashboard();

    const charts = within(await section(/Your order activity/));
    expect(
      await charts.findByText('The previous 30 days could not be loaded to compare with.'),
    ).toBeInTheDocument();
    expect(charts.getByText('Spent').closest('li')).toHaveTextContent('₹1,200.00');
  });
});
