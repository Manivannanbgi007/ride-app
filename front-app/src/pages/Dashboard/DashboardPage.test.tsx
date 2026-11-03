import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { ApiError } from '@/api/apiErrorHandler';
import DashboardPage from '@/pages/Dashboard/DashboardPage';
import { LiveStatusContext } from '@/realtime/liveStatusContext';
import { dashboardService } from '@/services/dashboardService';
import { deliveryService } from '@/services/deliveryService';
import { userService } from '@/services/userService';
import { pickDate } from '@/test/pickDate';
import {
  adminProfile,
  dashboardResponse,
  deliverySummary,
  monthlyRevenue,
  orderActivityResponse,
  pageOf,
} from '@/test/fixtures';
import { chooseOption } from '@/test/chooseOption';
import { renderWithProviders } from '@/test/renderWithProviders';
import type { DeliveryReport } from '@/types/delivery';
import type * as CsvUtils from '@/utils/csvUtils';
import { downloadCsv } from '@/utils/csvUtils';
import { ERROR_MESSAGES } from '@/utils/errorMessages';

vi.mock('@/services/dashboardService', () => ({
  dashboardService: {
    getDashboard: vi.fn(),
    getOrderActivity: vi.fn(),
    getRevenueReport: vi.fn(),
    getMonthlyRevenueComparison: vi.fn(),
  },
}));
vi.mock('@/services/deliveryService', () => ({
  deliveryService: {
    getSummary: vi.fn(),
    searchDeliveries: vi.fn(),
    getPartnerAvailability: vi.fn(),
  },
}));
vi.mock('@/services/userService', () => ({ userService: { getMyProfile: vi.fn() } }));
vi.mock('@/utils/csvUtils', async (importOriginal) => ({
  ...(await importOriginal<typeof CsvUtils>()),
  downloadCsv: vi.fn(),
}));

function deliveryReport(overrides: Partial<DeliveryReport> = {}): DeliveryReport {
  return {
    fromDate: '2026-10-08',
    toDate: '2026-10-08',
    byStatus: { PENDING: 2, ASSIGNED: 1, ACCEPTED: 3, PICKED_UP: 4, DELIVERED: 9, CANCELLED: 1 },
    total: 20,
    delivered: 9,
    cancelled: 1,
    averageDeliveryMinutes: 32,
    delayedNow: 3,
    ...overrides,
  };
}

const unavailable = new ApiError({
  kind: 'unavailable',
  status: 503,
  userMessage: ERROR_MESSAGES.SERVICE_UNAVAILABLE,
  technicalMessage: 'GET /api/v1/admin/dashboard failed with 503',
});

describe('DashboardPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.spyOn(console, 'error').mockImplementation(() => undefined);
    vi.mocked(userService.getMyProfile).mockResolvedValue(adminProfile);
    vi.mocked(dashboardService.getMonthlyRevenueComparison).mockResolvedValue(monthlyRevenue);
    vi.mocked(deliveryService.getSummary).mockResolvedValue(deliveryReport());
    vi.mocked(deliveryService.searchDeliveries).mockResolvedValue(
      pageOf([
        deliverySummary({ id: 55, orderId: 42, status: 'PICKED_UP', minutesSinceReady: 50 }),
        deliverySummary({
          id: 56,
          orderId: 43,
          status: 'PENDING',
          partnerUserId: null,
          partnerName: null,
          minutesSinceReady: 3,
        }),
      ]),
    );
    vi.mocked(deliveryService.getPartnerAvailability).mockResolvedValue({
      byStatus: { OFFLINE: 4, AVAILABLE: 6, BUSY: 5, SUSPENDED: 1 },
      total: 16,
    });
  });

  it("greets the admin and shows the period's figures", async () => {
    vi.mocked(dashboardService.getDashboard).mockResolvedValue(dashboardResponse());
    renderWithProviders(<DashboardPage />, { route: '/admin/dashboard' });

    // The KPI row (the overview below shows the same period's figures)
    const kpis = within(await screen.findByRole('list', { name: "The period's orders" }));
    expect(kpis.getByText('300')).toBeInTheDocument();
    expect(kpis.getByText('₹18,591.10')).toBeInTheDocument();
    expect(screen.getByText('Rider Kitchen')).toBeInTheDocument();
    expect(
      await screen.findByText('Hi, Platform Administrator. Welcome back to Front-App Admin!'),
    ).toBeInTheDocument();
    expect(dashboardService.getDashboard).toHaveBeenCalledWith(7);
  });

  it('explains a failure and loads again on "Try again"', async () => {
    vi.mocked(dashboardService.getDashboard)
      .mockRejectedValueOnce(unavailable)
      .mockResolvedValue(dashboardResponse());
    renderWithProviders(<DashboardPage />, { route: '/admin/dashboard' });

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent(ERROR_MESSAGES.DASHBOARD_LOAD_FAILED);
    expect(alert).toHaveTextContent(ERROR_MESSAGES.SERVICE_UNAVAILABLE);

    await userEvent.click(within(alert).getByRole('button', { name: /try again/i }));

    expect(
      within(await screen.findByRole('list', { name: "The period's orders" })).getByText('300'),
    ).toBeInTheDocument();
    expect(dashboardService.getDashboard).toHaveBeenCalledTimes(2);
  });

  it('names the parts that could not be loaded and shows their figures as unknown', async () => {
    vi.mocked(dashboardService.getDashboard).mockResolvedValue(
      dashboardResponse({ unavailable: ['order-service'] }),
    );
    renderWithProviders(<DashboardPage />, { route: '/admin/dashboard' });

    expect(await screen.findByText('Some figures could not be loaded')).toBeInTheDocument();
    expect(screen.getByText(/Orders: not available right now/)).toBeInTheDocument();
    expect(screen.getAllByText('—').length).toBeGreaterThanOrEqual(4);
  });

  it('loads another period when it is chosen, and remembers the choice', async () => {
    vi.mocked(dashboardService.getDashboard).mockResolvedValue(dashboardResponse());
    const { store } = renderWithProviders(<DashboardPage />, { route: '/admin/dashboard' });
    await screen.findByRole('list', { name: "The period's orders" });

    await chooseOption(screen.getByLabelText('Filter Period'), 'Last 30 days');

    expect(dashboardService.getDashboard).toHaveBeenLastCalledWith(30);
    expect(store.getState().reportPeriod.days).toBe(30);
  });

  describe('Order & Sales Overview', () => {
    beforeEach(() => {
      vi.mocked(dashboardService.getDashboard).mockResolvedValue(dashboardResponse());
      vi.mocked(dashboardService.getOrderActivity).mockResolvedValue(orderActivityResponse());
    });

    const renderDashboard = async () => {
      renderWithProviders(<DashboardPage />, { route: '/admin/dashboard' });
      await screen.findByRole('list', { name: "The period's orders" });
    };

    /** The overview's card (the section its heading is in) */
    const overview = () =>
      screen
        .getByRole('heading', { name: 'Order & Sales Overview' })
        .closest('section') as HTMLElement;

    /** The days shown, as the card's subtitle names them */
    const findCaption = (text: string) =>
      within(overview()).findByText(text, { selector: '[aria-live="polite"]' });

    /** A key figure's tile, by its name */
    const tileOf = (label: string) => {
      const tile = within(screen.getByRole('list', { name: 'Key figures' }))
        .getAllByRole('listitem')
        .find((item) => within(item).queryByText(label, { exact: true }));
      if (!tile) {
        throw new Error(`No figure named ${label}`);
      }
      return tile;
    };

    /** A rate's row, by its name */
    const rateOf = (label: string) =>
      within(screen.getByRole('list', { name: 'Order rates' }))
        .getByText(label)
        .closest('li') as HTMLElement;

    it("opens on the dashboard's own period with its figures, and asks for nothing more", async () => {
      await renderDashboard();

      expect(screen.getByRole('button', { name: 'Last 7 days' })).toHaveAttribute(
        'aria-pressed',
        'true',
      );
      expect(await findCaption('2 Oct 2026 - 8 Oct 2026')).toBeInTheDocument();
      // 300 orders, 50 of them delivered for ₹18,591.10
      expect(within(tileOf('Sales')).getByText('₹18,591.10')).toBeInTheDocument();
      expect(within(tileOf('Orders')).getByText('300')).toBeInTheDocument();
      expect(within(tileOf('Orders')).getByText('150 a day')).toBeInTheDocument();
      expect(within(tileOf('Average order')).getByText('₹371.82')).toBeInTheDocument();
      // An older answer without the refunded money: unknown, not zero
      expect(within(tileOf('Online refunds')).getByText('—')).toBeInTheDocument();
      expect(within(tileOf('Online refunds')).getByText('5 refunded in full')).toBeInTheDocument();

      expect(within(rateOf('Delivered')).getByText('17%')).toBeInTheDocument();
      expect(within(rateOf('Delivered')).getByText('50 of 300 orders')).toBeInTheDocument();
      expect(within(rateOf('Cancelled or rejected')).getByText('50%')).toBeInTheDocument();
      expect(within(rateOf('Payment success')).getByText('87%')).toBeInTheDocument();
      expect(
        within(rateOf('Payment success')).getByText('87 of 100 online payments'),
      ).toBeInTheDocument();
      expect(dashboardService.getOrderActivity).not.toHaveBeenCalled();
    });

    it("asks for the last 30 days, and goes back to the dashboard's period without asking again", async () => {
      await renderDashboard();

      await userEvent.click(screen.getByRole('button', { name: 'Last 30 days' }));
      expect(dashboardService.getOrderActivity).toHaveBeenLastCalledWith(
        '2026-09-09',
        '2026-10-08',
      );
      expect(await findCaption('9 Sept 2026 - 8 Oct 2026')).toBeInTheDocument();
      // The figures, rates and chart follow the chosen days: 20 orders, 8 delivered, 4 cancelled, 9 of 10 paid
      expect(await within(overview()).findByText('₹800.00')).toBeInTheDocument();
      expect(within(tileOf('Orders')).getByText('20')).toBeInTheDocument();
      expect(within(rateOf('Delivered')).getByText('40%')).toBeInTheDocument();
      expect(within(rateOf('Cancelled or rejected')).getByText('20%')).toBeInTheDocument();
      expect(within(rateOf('Payment success')).getByText('90%')).toBeInTheDocument();

      await userEvent.click(screen.getByRole('button', { name: 'Last 7 days' }));
      expect(await findCaption('2 Oct 2026 - 8 Oct 2026')).toBeInTheDocument();
      expect(within(tileOf('Orders')).getByText('300')).toBeInTheDocument();
      expect(dashboardService.getOrderActivity).toHaveBeenCalledTimes(1);
    });

    it('shows one chosen day', async () => {
      await renderDashboard();

      await userEvent.click(screen.getByRole('button', { name: 'Day' }));
      expect(dashboardService.getOrderActivity).toHaveBeenLastCalledWith(
        '2026-10-08',
        '2026-10-08',
      );

      await pickDate('Day', '2026-10-07');
      expect(dashboardService.getOrderActivity).toHaveBeenLastCalledWith(
        '2026-10-07',
        '2026-10-07',
      );
      expect(await findCaption('7 Oct 2026')).toBeInTheDocument();
    });

    it('shows the week of the chosen day, Monday to Sunday, ending today for the running week', async () => {
      await renderDashboard();

      await userEvent.click(screen.getByRole('button', { name: 'Week' }));

      // Thursday 8 Oct: the week began on Monday 5 Oct and has not ended yet
      expect(dashboardService.getOrderActivity).toHaveBeenLastCalledWith(
        '2026-10-05',
        '2026-10-08',
      );
      expect(await findCaption('5 Oct 2026 - 8 Oct 2026')).toBeInTheDocument();

      await userEvent.click(screen.getByRole('button', { name: 'Previous week' }));
      expect(dashboardService.getOrderActivity).toHaveBeenLastCalledWith(
        '2026-09-28',
        '2026-10-04',
      );
      expect(screen.getByRole('button', { name: 'Next week' })).toBeEnabled();
    });

    it('opens the month on the current one, and cannot go beyond it', async () => {
      await renderDashboard();

      await userEvent.click(screen.getByRole('button', { name: 'Month' }));
      expect(dashboardService.getOrderActivity).toHaveBeenLastCalledWith(
        '2026-10-01',
        '2026-10-08',
      );
      expect(screen.getByRole('button', { name: 'Next month' })).toBeDisabled();

      await chooseOption(screen.getByLabelText('Month'), 'September');
      expect(dashboardService.getOrderActivity).toHaveBeenLastCalledWith(
        '2026-09-01',
        '2026-09-30',
      );
      expect(await findCaption('September 2026')).toBeInTheDocument();
      expect(screen.getByRole('button', { name: 'Next month' })).toBeEnabled();
    });

    it('shows the online refunds of the chosen days, without taking them off the sales', async () => {
      vi.mocked(dashboardService.getOrderActivity).mockResolvedValue(
        orderActivityResponse({
          paymentOutcome: { successful: 9, failed: 1, refunded: 1, refundedAmount: 250 },
        }),
      );
      await renderDashboard();

      await userEvent.click(screen.getByRole('button', { name: 'Week' }));

      expect(await within(overview()).findByText('₹250.00')).toBeInTheDocument();
      expect(within(tileOf('Online refunds')).getByText('1 refunded in full')).toBeInTheDocument();
      expect(within(tileOf('Sales')).getByText('₹800.00')).toBeInTheDocument();
    });

    it('draws the days, with a legend that switches each figure on and off', async () => {
      await renderDashboard();

      expect(
        screen.getByRole('img', {
          name: 'Orders and sales per day from 7 Oct 2026 to 8 Oct 2026: 300 orders and ₹18,591.10 in sales; busiest day 8 Oct 2026 with 200.',
        }),
      ).toBeInTheDocument();
      const legend = within(screen.getByRole('group', { name: 'Shown in the chart' }));
      expect(legend.getAllByRole('button').map((button) => button.textContent)).toEqual([
        'Sales',
        'Delivered 50',
        'In progress 100',
        'Cancelled or rejected 150',
      ]);

      await userEvent.click(legend.getByRole('button', { name: 'Sales' }));
      expect(legend.getByRole('button', { name: 'Sales' })).toHaveAttribute(
        'aria-pressed',
        'false',
      );
    });

    it('saves the days shown as a report', async () => {
      await renderDashboard();

      await userEvent.click(screen.getByRole('button', { name: 'Save Report' }));

      expect(downloadCsv).toHaveBeenCalledWith('orders-per-day_2026-10-02_2026-10-08.csv', [
        [
          'Date',
          'Orders',
          'Delivered',
          'In progress',
          'Cancelled or rejected',
          'Sales (delivered)',
        ],
        ['2026-10-07', 100, 20, 30, 50, 3000],
        ['2026-10-08', 200, 30, 70, 100, 15591.1],
      ]);
    });

    it('says so when the chosen days have no orders, and shows no rates', async () => {
      vi.mocked(dashboardService.getOrderActivity).mockResolvedValue(
        orderActivityResponse({
          ordersPerDay: [{ date: '2026-10-08', orders: 0, completed: 0, cancelled: 0, revenue: 0 }],
          paymentOutcome: { successful: 0, failed: 0, refunded: 0 },
        }),
      );
      await renderDashboard();

      await userEvent.click(screen.getByRole('button', { name: 'Day' }));

      expect(await screen.findByText('No orders in this period')).toBeInTheDocument();
      expect(
        within(screen.getByRole('list', { name: 'Order rates' })).getAllByText('—'),
      ).toHaveLength(3);
    });

    it('keeps the rest of the dashboard when the chosen days fail, and tries again', async () => {
      vi.mocked(dashboardService.getOrderActivity)
        .mockRejectedValueOnce(unavailable)
        .mockResolvedValue(orderActivityResponse());
      await renderDashboard();

      await userEvent.click(screen.getByRole('button', { name: 'Week' }));

      const alert = await screen.findByRole('alert');
      expect(alert).toHaveTextContent(ERROR_MESSAGES.ORDER_ACTIVITY_LOAD_FAILED);
      expect(screen.getByText('Rider Kitchen')).toBeInTheDocument();
      // The range can still be changed while the figures are missing
      expect(screen.getByRole('button', { name: 'Week' })).toHaveAttribute('aria-pressed', 'true');

      await userEvent.click(within(alert).getByRole('button', { name: /try again/i }));
      expect(await within(overview()).findByText('₹800.00')).toBeInTheDocument();
    });

    it('names an unavailable service instead of showing zeros', async () => {
      vi.mocked(dashboardService.getOrderActivity).mockResolvedValue(
        orderActivityResponse({
          ordersPerDay: [],
          paymentOutcome: null,
          unavailable: ['order-service', 'payment-service'],
        }),
      );
      await renderDashboard();

      await userEvent.click(screen.getByRole('button', { name: 'Week' }));

      expect(await screen.findByText('Some figures could not be loaded')).toBeInTheDocument();
      expect(within(tileOf('Sales')).getByText('—')).toBeInTheDocument();
      expect(
        within(tileOf('Online refunds')).getByText('Payments not available'),
      ).toBeInTheDocument();
      expect(screen.queryByRole('group', { name: 'Shown in the chart' })).not.toBeInTheDocument();
    });
  });

  describe('Live Delivery Tracking', () => {
    it("shows today's deliveries by stage, the partners and the newest deliveries", async () => {
      vi.mocked(dashboardService.getDashboard).mockResolvedValue(dashboardResponse());
      renderWithProviders(<DashboardPage />, { route: '/admin/dashboard' });

      expect(await screen.findByText('Live Delivery Tracking')).toBeInTheDocument();
      const stages = await screen.findByRole('list', { name: "Today's deliveries by stage" });
      expect(
        within(stages)
          .getAllByRole('listitem')
          .map((stage) => stage.textContent),
      ).toEqual(['2Waiting', '1Offered', '3To pickup', '4On the way', '9Delivered']);
      // Without a live connection (WebSocket) the card refreshes by its own timer, and says so
      expect(screen.getByText('AUTO-REFRESH')).toBeInTheDocument();
      expect(screen.getByText('3 late now')).toBeInTheDocument();
      expect(screen.getByText('6')).toBeInTheDocument(); // partners free
      expect(screen.getByRole('link', { name: 'Order #42' })).toHaveAttribute(
        'href',
        '/admin/deliveries/55',
      );
      // On the road for 50 minutes: late; and one still without a partner
      expect(screen.getByText('50 min (late)')).toBeInTheDocument();
      expect(screen.getByText('No partner yet')).toBeInTheDocument();
      expect(screen.getByRole('progressbar', { name: 'Order #42 progress' })).toHaveAttribute(
        'aria-valuenow',
        '80',
      );
    });

    it('says LIVE only while the live connection is open, and RECONNECTING while it is coming back', async () => {
      vi.mocked(dashboardService.getDashboard).mockResolvedValue(dashboardResponse());
      const { rerender } = renderWithProviders(
        <LiveStatusContext.Provider value="open">
          <DashboardPage />
        </LiveStatusContext.Provider>,
        { route: '/admin/dashboard' },
      );

      expect(await screen.findByText('LIVE')).toBeInTheDocument();

      rerender(
        <LiveStatusContext.Provider value="reconnecting">
          <DashboardPage />
        </LiveStatusContext.Provider>,
      );
      expect(await screen.findByText('RECONNECTING')).toBeInTheDocument();
      expect(screen.queryByText('LIVE')).not.toBeInTheDocument();
    });

    it('says so when there are no deliveries today', async () => {
      vi.mocked(dashboardService.getDashboard).mockResolvedValue(dashboardResponse());
      vi.mocked(deliveryService.getSummary).mockResolvedValue(
        deliveryReport({ byStatus: {} as DeliveryReport['byStatus'], total: 0, delayedNow: 0 }),
      );
      vi.mocked(deliveryService.searchDeliveries).mockResolvedValue(pageOf([]));
      renderWithProviders(<DashboardPage />, { route: '/admin/dashboard' });

      expect(await screen.findByText('No deliveries today yet')).toBeInTheDocument();
    });

    it('keeps the rest of the dashboard when the deliveries fail, and loads them again', async () => {
      vi.mocked(dashboardService.getDashboard).mockResolvedValue(dashboardResponse());
      vi.mocked(deliveryService.getSummary)
        .mockRejectedValueOnce(unavailable)
        .mockResolvedValue(deliveryReport());
      renderWithProviders(<DashboardPage />, { route: '/admin/dashboard' });

      const alert = await screen.findByText(ERROR_MESSAGES.LIVE_DELIVERIES_LOAD_FAILED);
      expect(screen.getByText('Rider Kitchen')).toBeInTheDocument();

      await userEvent.click(
        within(alert.closest('[role="alert"]') as HTMLElement).getByRole('button', {
          name: /try again/i,
        }),
      );
      expect(await screen.findByText('AUTO-REFRESH')).toBeInTheDocument();
    });
  });
});
