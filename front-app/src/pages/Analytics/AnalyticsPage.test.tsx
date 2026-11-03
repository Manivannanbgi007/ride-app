import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import AnalyticsPage from '@/pages/Analytics/AnalyticsPage';
import { reportService } from '@/services/reportService';
import { dashboardResponse } from '@/test/fixtures';
import { chooseOption } from '@/test/chooseOption';
import { renderWithProviders } from '@/test/renderWithProviders';
import type { CustomerReport, OrderReport, RevenueReport } from '@/types/report';
import type * as CsvUtils from '@/utils/csvUtils';
import { downloadCsv } from '@/utils/csvUtils';
import { periodOf } from '@/utils/dateUtils';

vi.mock('@/services/reportService', () => ({
  reportService: {
    getOrderReport: vi.fn(),
    getRevenueReport: vi.fn(),
    getRestaurantReport: vi.fn(),
    getCustomerReport: vi.fn(),
    getDeliveryReport: vi.fn(),
  },
}));
vi.mock('@/utils/csvUtils', async (importOriginal) => ({
  ...(await importOriginal<typeof CsvUtils>()),
  downloadCsv: vi.fn(),
}));

const period = periodOf(7);
const { charts } = dashboardResponse();

const orderReport: OrderReport = {
  ...period,
  currency: 'INR',
  totalOrders: 12,
  completed: 9,
  cancelled: 2,
  pending: 1,
  averageOrderValue: 412.5,
  byStatus: { DELIVERED: 9, CANCELLED: 2, PREPARING: 1 },
  perDay: charts.ordersPerDay,
  byRestaurant: charts.restaurantPerformance,
  byCity: [{ city: 'Bengaluru', orders: 12, completed: 9, revenue: 3712.5 }],
  unavailable: [],
};

const revenueReport: RevenueReport = {
  ...period,
  currency: 'INR',
  groupBy: 'DAY',
  revenue: 3712.5,
  discounts: 150,
  averageOrderValue: 412.5,
  periods: [
    {
      label: period.toDate,
      from: period.toDate,
      to: period.toDate,
      orders: 3,
      completed: 2,
      revenue: 800,
    },
  ],
  byRestaurant: charts.restaurantPerformance,
  byPaymentMethod: { CASH_ON_DELIVERY: { orders: 4, completed: 3, revenue: 1200 } },
  onlinePayments: null,
  unavailable: ['payment-service'],
};

const customerReport: CustomerReport = {
  ...period,
  currency: 'INR',
  totalCustomers: 120,
  activeAccounts: 118,
  deactivatedAccounts: 2,
  newCustomers: 15,
  orderingCustomers: 40,
  notOrdering: 80,
  ordersPerCustomer: 1.75,
  orderFrequency: { '1': 25, '2-3': 12, '4-5': 2, '6+': 1 },
  topCustomers: [
    { customerId: 7, customerName: 'Priya Sharma', orders: 6, completed: 6, spent: 2400 },
  ],
  unavailable: [],
};

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(reportService.getOrderReport).mockResolvedValue(orderReport);
  vi.mocked(reportService.getRevenueReport).mockResolvedValue(revenueReport);
  vi.mocked(reportService.getCustomerReport).mockResolvedValue(customerReport);
});

describe('AnalyticsPage', () => {
  it("shows the order report of the dashboard's period and saves it as a spreadsheet", async () => {
    renderWithProviders(<AnalyticsPage />, { route: '/admin/analytics' });

    expect(await screen.findByText('₹412.50')).toBeInTheDocument();
    expect(reportService.getOrderReport).toHaveBeenCalledWith({ ...period, limit: 10 });
    expect(screen.getByRole('link', { name: 'Rider Kitchen' })).toHaveAttribute(
      'href',
      '/admin/restaurants/130',
    );
    expect(screen.getByLabelText('9 orders')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Save Report' }));
    expect(downloadCsv).toHaveBeenCalledWith(
      `orders-report_${period.fromDate}_${period.toDate}.csv`,
      expect.arrayContaining([['Orders', 12]]),
    );
  });

  it('asks for the revenue per week, and says which figures are missing', async () => {
    renderWithProviders(<AnalyticsPage />, { route: '/admin/analytics?tab=revenue' });

    expect(await screen.findByText('Some figures could not be loaded')).toBeInTheDocument();
    expect(screen.getByText(/Payments: not available right now/)).toBeInTheDocument();
    // Online payments are unknown, not zero
    const collected = screen.getByText('Collected online').closest('div');
    expect(within(collected!).getByText('—')).toBeInTheDocument();

    await chooseOption(screen.getByLabelText('Revenue per'), 'Per week');
    expect(reportService.getRevenueReport).toHaveBeenLastCalledWith(
      { ...period, limit: 10 },
      'WEEK',
    );
  });

  it('shows how often customers ordered, and the period follows "Filter Period"', async () => {
    renderWithProviders(<AnalyticsPage />, { route: '/admin/analytics?tab=customers' });

    expect(await screen.findByText('2 to 3 orders')).toBeInTheDocument();
    expect(screen.getByLabelText('12 customers')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Priya Sharma' })).toBeInTheDocument();

    await chooseOption(screen.getByLabelText('Filter Period'), 'Last 30 days');
    expect(reportService.getCustomerReport).toHaveBeenLastCalledWith({
      ...periodOf(30),
      limit: 10,
    });
  });
});
