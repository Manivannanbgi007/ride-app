import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useLocation } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import OwnerDashboardPage from '@/pages/Owner/OwnerDashboardPage';
import { ownerService } from '@/services/ownerService';
import { ratingService } from '@/services/ratingService';
import { menuItem, orderSummary, pageOf } from '@/test/fixtures';
import { renderOwnerPage } from '@/test/ownerTestUtils';
import type { OrderSummary } from '@/types/order';
import type { HourPoint, OwnerOrderFilters, SalesSummary } from '@/types/owner';
import type * as CsvUtils from '@/utils/csvUtils';
import { downloadCsv } from '@/utils/csvUtils';
import { formatRange, presetRange, previousRangeOf } from '@/utils/dateRangeUtils';

vi.mock('@/services/ownerService', () => ({
  ownerService: {
    getOrders: vi.fn(),
    countOrders: vi.fn(),
    getSalesSummary: vi.fn(),
    getTopItems: vi.fn(),
    getMenuItems: vi.fn(),
    getOrder: vi.fn(),
    acceptOrder: vi.fn(),
    rejectOrder: vi.fn(),
    resubmitRestaurant: vi.fn(),
  },
}));
vi.mock('@/services/ratingService', () => ({
  ratingService: { getRestaurantRating: vi.fn() },
}));
vi.mock('@/utils/csvUtils', async (importOriginal) => ({
  ...(await importOriginal<typeof CsvUtils>()),
  downloadCsv: vi.fn(),
}));

const RESTAURANT_ID = 3;

const sales = (overrides: Partial<SalesSummary> = {}): SalesSummary => ({
  restaurantId: RESTAURANT_ID,
  fromDate: '2026-10-17',
  toDate: '2026-10-17',
  totalOrders: 10,
  ordersByStatus: { DELIVERED: 4, PLACED: 3, CANCELLED: 1, REJECTED: 1, PREPARING: 1 },
  acceptedOrdersValue: 1500,
  deliveredOrdersValue: 1000,
  currency: 'INR',
  ...overrides,
});

/** The new orders (status PLACED) and the latest of any status, as the API would answer each list */
const answerOrders = (waiting: OrderSummary[], latest: OrderSummary[]) =>
  vi
    .mocked(ownerService.getOrders)
    .mockImplementation((_id, filters: OwnerOrderFilters) =>
      Promise.resolve(filters.status === 'PLACED' ? pageOf(waiting) : pageOf(latest)),
    );

/** How many orders wait at each step, by the status asked for */
const answerCounts = (counts: Record<string, number>) =>
  vi
    .mocked(ownerService.countOrders)
    .mockImplementation((_id, filters: OwnerOrderFilters) =>
      Promise.resolve(counts[filters.status ?? ''] ?? 0),
    );

/** Shows the address, so a test can check what the page keeps in it. */
function Address() {
  return <output data-testid="address">{useLocation().search}</output>;
}

const renderDashboard = (route = '/owner') =>
  renderOwnerPage(
    <>
      <OwnerDashboardPage />
      <Address />
    </>,
    '/owner',
    { route },
  );

/** The overview's date range picker (its name starts with what it filters) */
const overviewDates = () => screen.getByRole('button', { name: /^Overview dates:/ });

/** Opens the overview's date range picker and chooses one of its quick ranges. */
async function chooseQuickRange(name: string) {
  await userEvent.click(overviewDates());
  const panel = screen.getByRole('dialog', { name: 'Choose: Overview dates' });
  await userEvent.click(within(panel).getByRole('button', { name }));
}

/** The last 7 days (the default) and the 7 days before them */
const week = { current: presetRange('7'), previous: previousRangeOf(presetRange('7')) };

beforeEach(() => {
  vi.clearAllMocks();
  vi.spyOn(console, 'error').mockImplementation(() => undefined);
  vi.mocked(ownerService.getSalesSummary).mockResolvedValue(sales());
  vi.mocked(ownerService.getTopItems).mockResolvedValue({
    restaurantId: RESTAURANT_ID,
    from: '2026-10-17',
    to: '2026-10-17',
    items: [
      { menuItemId: 11, name: 'Chicken Biryani', quantity: 7, orders: 4, revenue: 2093 },
      { menuItemId: 12, name: 'Masala Dosa', quantity: 3, orders: 1, revenue: 447 },
    ],
  });
  vi.mocked(ownerService.getMenuItems).mockResolvedValue([
    menuItem({ id: 11 }),
    menuItem({ id: 12, available: false }),
    menuItem({ id: 13, status: 'DISABLED' }),
  ]);
  vi.mocked(ratingService.getRestaurantRating).mockResolvedValue({
    id: RESTAURANT_ID,
    average: 4.3,
    count: 12,
    distribution: { '1': 0, '2': 1, '3': 1, '4': 3, '5': 7 },
  });
  answerOrders([], []);
  answerCounts({});
});

describe('the owner dashboard', () => {
  it('greets the owner and shows the rating and the menu at a glance', async () => {
    renderDashboard();

    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent(/, Test!$/);
    const glance = within(await screen.findByRole('list', { name: /at a glance$/ }));
    expect(
      (await glance.findByText('Customer rating · 12 reviews')).previousElementSibling,
    ).toHaveTextContent('4.3 / 5');
    // One dish can be ordered; one is sold out and one hidden
    expect(
      (await glance.findByText('Dishes customers can order')).previousElementSibling,
    ).toHaveTextContent('1');
    expect(glance.getByText('Sold out · hidden').previousElementSibling).toHaveTextContent('1 · 1');
  });

  it('counts the orders waiting at each step, each card opening its tab', async () => {
    answerCounts({ PLACED: 2, ACCEPTED: 1, PREPARING: 0, READY_FOR_PICKUP: 3 });
    renderDashboard();

    const attention = within(await screen.findByRole('region', { name: 'Needs your attention' }));
    expect(
      await attention.findByRole('link', { name: 'New: 2 orders. Accept or reject them' }),
    ).toHaveAttribute('href', '/owner/orders');
    expect(
      attention.getByRole('link', { name: 'Accepted: 1 order. Start preparing them' }),
    ).toHaveAttribute('href', '/owner/orders?tab=accepted');
    expect(
      attention.getByRole('link', { name: 'Preparing: 0 orders. Nothing on the stove' }),
    ).toHaveAttribute('href', '/owner/orders?tab=preparing');
    expect(
      attention.getByRole('link', { name: 'Ready for pickup: 3 orders. Waiting for a rider' }),
    ).toHaveAttribute('href', '/owner/orders?tab=ready');
  });

  it('lists the new orders, oldest first, with Accept and Reject on each', async () => {
    answerOrders(
      [orderSummary({ id: 42, status: 'PLACED', customerName: 'Ana Rao' })],
      [orderSummary({ id: 42, status: 'PLACED', customerName: 'Ana Rao' })],
    );
    renderDashboard();

    const waiting = await screen.findByRole('list', { name: 'New orders' });
    expect(within(waiting).getByRole('button', { name: /Accept/ })).toBeInTheDocument();
    expect(within(waiting).getByRole('button', { name: /Reject/ })).toBeInTheDocument();
    expect(screen.getByText('1 new order')).toBeInTheDocument();
    expect(ownerService.getOrders).toHaveBeenCalledWith(
      RESTAURANT_ID,
      { status: 'PLACED', sort: 'createdAt,asc' },
      0,
      5,
    );
  });

  it('leaves the waiting list out while no order waits', async () => {
    answerOrders([], [orderSummary({ id: 41, status: 'DELIVERED', customerName: 'Priya Sharma' })]);
    renderDashboard();

    expect(await screen.findByRole('link', { name: 'Order #41' })).toHaveAttribute(
      'href',
      '/owner/orders/41',
    );
    expect(screen.queryByText('Waiting for your decision')).not.toBeInTheDocument();
  });

  it('shows the latest orders of any status, each leading to its page', async () => {
    answerOrders(
      [],
      [
        orderSummary({ id: 44, status: 'PREPARING', customerName: 'Ana Rao' }),
        orderSummary({ id: 43, status: 'DELIVERED', customerName: null, customerId: 9 }),
      ],
    );
    renderDashboard();

    const latest = await screen.findByRole('list', { name: 'Latest orders' });
    expect(within(latest).getByRole('link', { name: 'Order #44' })).toBeInTheDocument();
    expect(within(latest).getByText('Ana Rao')).toBeInTheDocument();
    expect(within(latest).getByText('Customer #9')).toBeInTheDocument();
    expect(ownerService.getOrders).toHaveBeenCalledWith(RESTAURANT_ID, {}, 0, 5);
  });

  it('ranks the top dishes of the overview days with what they sold', async () => {
    renderDashboard();

    const dishes = await screen.findByRole('list', { name: 'Top dishes' });
    const [first, second] = within(dishes).getAllByRole('listitem');
    expect(within(first).getByText('Chicken Biryani')).toBeInTheDocument();
    expect(within(first).getByText('7 sold · 4 orders')).toBeInTheDocument();
    expect(within(second).getByText('3 sold · 1 order')).toBeInTheDocument();
    expect(ownerService.getTopItems).toHaveBeenCalledWith(
      RESTAURANT_ID,
      week.current.fromDate,
      week.current.toDate,
    );
  });
});

describe('the overview dates', () => {
  it('shows the last 7 days by default, compared with the 7 days before', async () => {
    renderDashboard();

    expect(overviewDates()).toHaveTextContent('Last 7 days');
    expect(overviewDates()).toHaveTextContent(formatRange(week.current));
    expect(
      screen.getByText(`7 days, compared with ${formatRange(week.previous)}`),
    ).toBeInTheDocument();
    await waitFor(() =>
      expect(ownerService.getSalesSummary).toHaveBeenCalledWith(
        RESTAURANT_ID,
        week.previous.fromDate,
        week.previous.toDate,
      ),
    );
    expect(ownerService.getSalesSummary).toHaveBeenCalledWith(
      RESTAURANT_ID,
      week.current.fromDate,
      week.current.toDate,
    );
    // The overview and the days before: nothing else asks for figures
    expect(ownerService.getSalesSummary).toHaveBeenCalledTimes(2);
  });

  it('keeps a quick range in the address, and the figures and top dishes follow it', async () => {
    renderDashboard();
    await screen.findByRole('list', { name: 'Key figures' });

    await chooseQuickRange('Last 30 days');

    const month = presetRange('30');
    expect(screen.getByTestId('address')).toHaveTextContent('?range=30');
    expect(overviewDates()).toHaveTextContent('Last 30 days');
    await waitFor(() =>
      expect(ownerService.getSalesSummary).toHaveBeenCalledWith(
        RESTAURANT_ID,
        month.fromDate,
        month.toDate,
      ),
    );
    const before = previousRangeOf(month);
    expect(ownerService.getSalesSummary).toHaveBeenCalledWith(
      RESTAURANT_ID,
      before.fromDate,
      before.toDate,
    );
    await waitFor(() =>
      expect(ownerService.getTopItems).toHaveBeenCalledWith(
        RESTAURANT_ID,
        month.fromDate,
        month.toDate,
      ),
    );

    // Back to the default: the address is short again
    await chooseQuickRange('Last 7 days');
    expect(screen.getByTestId('address')).toBeEmptyDOMElement();
  });

  it('keeps days picked in the calendar in the address', async () => {
    renderDashboard('/owner?from=2026-10-01&to=2026-10-09');
    await screen.findByRole('list', { name: 'Key figures' });

    await userEvent.click(overviewDates());
    const panel = within(screen.getByRole('dialog', { name: 'Choose: Overview dates' }));
    await userEvent.click(panel.getByRole('button', { name: 'Monday, 12 October 2026' }));
    await userEvent.click(panel.getByRole('button', { name: 'Thursday, 15 October 2026' }));
    await userEvent.click(panel.getByRole('button', { name: 'Apply' }));

    expect(screen.getByTestId('address')).toHaveTextContent('?from=2026-10-12&to=2026-10-15');
    await waitFor(() =>
      expect(ownerService.getSalesSummary).toHaveBeenCalledWith(
        RESTAURANT_ID,
        '2026-10-12',
        '2026-10-15',
      ),
    );
    // The 4 days before
    expect(ownerService.getSalesSummary).toHaveBeenCalledWith(
      RESTAURANT_ID,
      '2026-10-08',
      '2026-10-11',
    );
  });

  it('opens on the days of the address', async () => {
    renderDashboard('/owner?range=last-month');

    expect(overviewDates()).toHaveTextContent('Last month');
    const lastMonth = presetRange('last-month');
    await waitFor(() =>
      expect(ownerService.getSalesSummary).toHaveBeenCalledWith(
        RESTAURANT_ID,
        lastMonth.fromDate,
        lastMonth.toDate,
      ),
    );
  });

  it('explains days in a link that cannot be used, and shows the last 7 days instead', async () => {
    renderDashboard('/owner?from=2026-10-09&to=2026-10-01');

    expect(await screen.findByText('The dates in this link could not be used')).toBeInTheDocument();
    expect(
      screen.getByText(/The last day is before the first day\. Showing the last 7 days instead\./),
    ).toBeInTheDocument();
    expect(overviewDates()).toHaveTextContent('Last 7 days');
    expect(ownerService.getSalesSummary).not.toHaveBeenCalledWith(
      RESTAURANT_ID,
      '2026-10-09',
      '2026-10-01',
    );
  });

  it('draws a single day hour by hour, compared with the day before', async () => {
    const hour = (at: number, orders: number): HourPoint => ({
      hour: at,
      orders,
      completed: orders,
      cancelled: 0,
      revenue: orders * 250,
    });
    vi.mocked(ownerService.getSalesSummary).mockImplementation((_id, fromDate, toDate) =>
      Promise.resolve(
        sales({
          fromDate,
          toDate,
          perHour: Array.from({ length: 24 }, (_, at) =>
            hour(at, at === 13 ? 3 : at === 20 ? 2 : 0),
          ),
        }),
      ),
    );
    renderDashboard('/owner?from=2026-10-16&to=2026-10-16');

    expect(overviewDates()).toHaveTextContent('Single day');
    expect(overviewDates()).toHaveTextContent('Fri, 16 Oct 2026');
    expect(screen.getByText('One day, compared with Thu, 15 Oct 2026')).toBeInTheDocument();
    expect(
      await screen.findByRole('img', {
        name: /^Orders and sales per hour, Fri, 16 Oct 2026: .*; busiest hour 1 pm – 2 pm with 3\.$/,
      }),
    ).toBeInTheDocument();
    expect(screen.getByText('Hour by hour')).toBeInTheDocument();
    await waitFor(() =>
      expect(ownerService.getSalesSummary).toHaveBeenCalledWith(
        RESTAURANT_ID,
        '2026-10-15',
        '2026-10-15',
      ),
    );
  });
});

describe('the order and sales overview', () => {
  const day = (
    date: string,
    orders: number,
    completed: number,
    cancelled: number,
    revenue: number,
  ) => ({ date, orders, completed, cancelled, revenue });
  const days = [
    day(week.current.fromDate, 0, 0, 0, 0),
    day('2026-10-16', 6, 4, 1, 1000),
    day(week.current.toDate, 4, 0, 1, 0),
  ];

  /** This week (10 orders, ₹1,000 from 4 delivered) and the week before (5 orders, ₹800 from 2 delivered) */
  const answerWeeks = ({ failBefore = false, failNow = false } = {}) =>
    vi.mocked(ownerService.getSalesSummary).mockImplementation((_id, fromDate, toDate) => {
      if (fromDate === week.previous.fromDate) {
        return failBefore
          ? Promise.reject(new Error('down'))
          : Promise.resolve(
              sales({
                fromDate,
                toDate,
                totalOrders: 5,
                ordersByStatus: { DELIVERED: 2, PLACED: 3 },
                deliveredOrdersValue: 800,
              }),
            );
      }
      if (fromDate === week.current.fromDate && failNow) {
        return Promise.reject(new Error('down'));
      }
      return Promise.resolve(sales({ fromDate, toDate, perDay: days }));
    });

  /** A key figure's tile, by its name */
  const tileOf = async (label: string) => {
    const figures = await screen.findByRole('list', { name: 'Key figures' });
    const tile = within(figures)
      .getAllByRole('listitem')
      .find((item) => within(item).queryByText(label, { exact: true }));
    if (!tile) {
      throw new Error(`No figure named ${label}`);
    }
    return tile;
  };

  it("compares the range's sales, orders and average order with the days before", async () => {
    answerWeeks();
    renderDashboard();

    const salesTile = await tileOf('Sales');
    expect(within(salesTile).getByText('₹1,000.00')).toBeInTheDocument();
    // The arrow's direction is spoken (hidden text before the percentage)
    expect(await within(salesTile).findByText('25%')).toHaveTextContent('Up 25%');
    expect(within(salesTile).getByText('from ₹800.00')).toBeInTheDocument();

    const ordersTile = await tileOf('Orders');
    expect(within(ordersTile).getByText('10')).toBeInTheDocument();
    expect(within(ordersTile).getByText('100%')).toHaveTextContent('Up 100%');

    // ₹1,000 over 4 delivered orders, against ₹800 over 2
    const averageTile = await tileOf('Average order');
    expect(within(averageTile).getByText('₹250.00')).toBeInTheDocument();
    expect(within(averageTile).getByText('38%')).toHaveTextContent('Down 38%');
    expect(within(averageTile).getByText('from ₹400.00')).toBeInTheDocument();

    // 2 of the 10 orders cancelled or rejected
    const cancelledTile = await tileOf('Cancellations');
    expect(within(cancelledTile).getByText('2')).toBeInTheDocument();
    expect(within(cancelledTile).getByText('20% of the orders')).toBeInTheDocument();
  });

  it('draws the days, names the busiest, and the legend switches a figure off and on', async () => {
    answerWeeks();
    renderDashboard();

    expect(
      await screen.findByRole('img', {
        name: /^Orders and sales per day, .*: 10 orders and ₹1,000\.00 in sales; busiest day 16 Oct 2026 with 6\.$/,
      }),
    ).toBeInTheDocument();
    expect(screen.getByText('Busiest day:').parentElement).toHaveTextContent(
      'Busiest day:Fri, 16 Oct 2026· 6 orders',
    );
    const legend = within(screen.getByRole('group', { name: 'Shown in the chart' }));
    // The range's counts next to the names: 4 delivered, 2 cancelled or rejected, the rest in progress
    expect(legend.getByRole('button', { name: 'Delivered 4' })).toHaveAttribute(
      'aria-pressed',
      'true',
    );
    expect(legend.getByRole('button', { name: 'In progress 4' })).toBeInTheDocument();
    expect(legend.getByRole('button', { name: 'Cancelled or rejected 2' })).toBeInTheDocument();

    const salesToggle = legend.getByRole('button', { name: 'Sales' });
    await userEvent.click(salesToggle);
    expect(salesToggle).toHaveAttribute('aria-pressed', 'false');
    await userEvent.click(salesToggle);
    expect(salesToggle).toHaveAttribute('aria-pressed', 'true');
  });

  it('shows where the orders are now, each status opening its orders of the same days', async () => {
    answerWeeks();
    renderDashboard();

    const statuses = await screen.findByRole('list', { name: 'Orders by status' });
    const rows = within(statuses).getAllByRole('listitem');
    // In the order an order moves: placed, preparing, delivered, cancelled, rejected (none accepted)
    expect(rows.map((row) => row.textContent)).toEqual([
      'Placed330%',
      'Preparing110%',
      'Delivered440%',
      'Cancelled110%',
      'Rejected110%',
    ]);
    const days = `from=${week.current.fromDate}&to=${week.current.toDate}`;
    expect(within(rows[0]).getByRole('link')).toHaveAttribute('href', `/owner/orders?${days}`);
    expect(within(rows[2]).getByRole('link', { name: /^Delivered: 4 orders/ })).toHaveAttribute(
      'href',
      `/owner/orders?${days}&tab=delivered`,
    );
  });

  it('says so when the days had no orders, and offers a longer range', async () => {
    vi.mocked(ownerService.getSalesSummary).mockImplementation((_id, fromDate, toDate) =>
      Promise.resolve(
        sales({
          fromDate,
          toDate,
          totalOrders: 0,
          ordersByStatus: {},
          acceptedOrdersValue: 0,
          deliveredOrdersValue: 0,
          perDay: [day(toDate ?? '', 0, 0, 0, 0)],
        }),
      ),
    );
    renderDashboard();

    expect(
      await screen.findByText(`No orders in ${formatRange(week.current)}`),
    ).toBeInTheDocument();
    expect(screen.getByText('No orders in this range.')).toBeInTheDocument();
    expect(screen.queryByRole('group', { name: 'Shown in the chart' })).not.toBeInTheDocument();
    expect(
      await within(await tileOf('Orders')).findByText('None before either'),
    ).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Show the last 90 days' }));
    expect(screen.getByTestId('address')).toHaveTextContent('?range=90');
  });

  it('says when the overview cannot be loaded, with a way to try again', async () => {
    answerWeeks({ failNow: true });
    renderDashboard();

    expect(
      await screen.findByText('Unable to load your orders and sales', undefined, {
        timeout: 10_000,
      }),
    ).toBeInTheDocument();
    answerWeeks();
    await userEvent.click(screen.getByRole('button', { name: /try again/i }));

    expect(within(await tileOf('Sales')).getByText('₹1,000.00')).toBeInTheDocument();
  });

  it('shows the figures without a comparison when the days before cannot be loaded', async () => {
    answerWeeks({ failBefore: true });
    renderDashboard();

    const salesTile = await tileOf('Sales');
    expect(
      await within(salesTile).findByText('Comparison unavailable', undefined, {
        timeout: 10_000,
      }),
    ).toBeInTheDocument();
    expect(within(salesTile).getByText('₹1,000.00')).toBeInTheDocument();
  });

  it('saves the days as a report', async () => {
    answerWeeks();
    renderDashboard();

    await screen.findByRole('group', { name: 'Shown in the chart' });
    await userEvent.click(screen.getByRole('button', { name: 'Save report' }));

    expect(downloadCsv).toHaveBeenCalledWith(
      `orders-and-sales_${week.current.fromDate}_${week.current.toDate}.csv`,
      [
        [
          'Date',
          'Orders',
          'Delivered',
          'In progress',
          'Cancelled or rejected',
          'Sales (delivered)',
        ],
        [week.current.fromDate, 0, 0, 0, 0, 0],
        ['2026-10-16', 6, 4, 1, 1, 1000],
        [week.current.toDate, 4, 0, 3, 1, 0],
      ],
    );
  });
});
