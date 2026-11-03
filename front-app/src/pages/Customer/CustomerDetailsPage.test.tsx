import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Route, Routes } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import CustomerDetailsPage from '@/pages/Customer/CustomerDetailsPage';
import { orderService } from '@/services/orderService';
import { reviewService } from '@/services/reviewService';
import { userService } from '@/services/userService';
import { address, adminUser, orderSummary, pageOf } from '@/test/fixtures';
import { chooseOption, optionsOf } from '@/test/chooseOption';
import { renderWithProviders } from '@/test/renderWithProviders';

vi.mock('@/services/userService', () => ({
  userService: {
    getMyProfile: vi.fn(),
    searchUsers: vi.fn(),
    getUser: vi.fn(),
    changeStatus: vi.fn(),
    changeRole: vi.fn(),
    getAuditLogs: vi.fn(),
    getUserAddresses: vi.fn(),
  },
}));
vi.mock('@/services/orderService', () => ({
  orderService: {
    searchOrders: vi.fn(),
    getOrder: vi.fn(),
    cancelOrder: vi.fn(),
    getAuditLogs: vi.fn(),
    getCustomerSummary: vi.fn(),
    getCustomerTopFoods: vi.fn(),
  },
}));
vi.mock('@/services/reviewService', () => ({
  reviewService: { getCustomerLikedDishes: vi.fn() },
}));

const renderCustomer = (userId: number) =>
  renderWithProviders(
    <Routes>
      <Route path="/admin/customers/:userId" element={<CustomerDetailsPage />} />
      <Route path="/admin/orders" element={<p>Order list</p>} />
    </Routes>,
    { route: `/admin/customers/${userId}` },
  );

describe('CustomerDetailsPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(userService.getUser).mockResolvedValue(adminUser());
    vi.mocked(userService.getAuditLogs).mockResolvedValue(pageOf([]));
    vi.mocked(userService.getUserAddresses).mockResolvedValue([address()]);
    vi.mocked(orderService.getCustomerTopFoods).mockResolvedValue({
      customerId: 7,
      period: 'MONTHLY',
      from: '2026-09-09',
      items: [],
    });
    vi.mocked(reviewService.getCustomerLikedDishes).mockResolvedValue({
      customerId: 7,
      period: 'ALL',
      ratingCount: 0,
      dishes: [],
    });
    vi.mocked(orderService.getCustomerSummary).mockResolvedValue({
      customerId: 7,
      totalOrders: 12,
      activeOrders: 1,
      deliveredOrders: 9,
      cancelledOrders: 2,
      rejectedOrders: 0,
      totalSpent: 4520.5,
      lastOrderAt: '2026-10-08T10:15:00Z',
      currency: 'INR',
    });
    vi.mocked(orderService.searchOrders).mockResolvedValue(pageOf([orderSummary()]));
  });

  it("shows the account and the customer's orders", async () => {
    renderCustomer(7);

    expect(
      await screen.findByRole('heading', { name: 'Customer Detail', level: 1 }),
    ).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Priya Sharma', level: 2 })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'priya@example.com' })).toHaveAttribute(
      'href',
      'mailto:priya@example.com',
    );
    expect(screen.getByRole('link', { name: '+91 98765 43210' })).toHaveAttribute(
      'href',
      'tel:+91 98765 43210',
    );
    expect(screen.getByText('Active')).toBeInTheDocument();
    expect(await screen.findByText(/12 MG Road, Bengaluru 560001/)).toBeInTheDocument();
    // The spend card and the order summary both show what was spent
    expect(await screen.findAllByText('₹4,520.50')).toHaveLength(2);
    expect(screen.getByText('Most Ordered Food')).toBeInTheDocument();
    expect(screen.getByText('Most Liked Food')).toBeInTheDocument();
    expect(await screen.findByRole('link', { name: '#42' })).toBeInTheDocument();
    expect(orderService.searchOrders).toHaveBeenCalledWith({ customerId: 7 }, 0, 5);
  });

  it('says so when no address is saved, and shows a missing phone as muted text', async () => {
    vi.mocked(userService.getUserAddresses).mockResolvedValue([]);
    vi.mocked(userService.getUser).mockResolvedValue(adminUser({ phone: null }));
    renderCustomer(7);

    expect(await screen.findByText('No address saved')).toBeInTheDocument();
    expect(screen.getByText('No phone')).toBeInTheDocument();
  });

  it('does not block the page when the address cannot be loaded', async () => {
    vi.mocked(userService.getUserAddresses).mockRejectedValue(new Error('boom'));
    renderCustomer(7);

    expect(
      await screen.findByRole('heading', { name: 'Priya Sharma', level: 2 }),
    ).toBeInTheDocument();
    await screen.findAllByText('₹4,520.50');
    expect(screen.queryByText('No address saved')).not.toBeInTheDocument();
  });

  it('shows an inactive customer as Inactive and offers Activate', async () => {
    vi.mocked(userService.getUser).mockResolvedValue(adminUser({ status: 'INACTIVE' }));
    renderCustomer(7);

    expect(await screen.findByRole('button', { name: 'Activate' })).toBeEnabled();
    expect(screen.getByText('Inactive')).toBeInTheDocument();
  });

  it('shows no order sections for users who are not customers', async () => {
    vi.mocked(userService.getUser).mockResolvedValue(
      adminUser({ role: 'RESTAURANT_ADMIN', fullName: 'Owner One' }),
    );
    renderCustomer(7);

    expect(await screen.findByRole('heading', { name: 'Owner One', level: 2 })).toBeInTheDocument();
    expect(screen.queryByText('Most Ordered Food')).not.toBeInTheDocument();
    expect(screen.queryByText('Total spent')).not.toBeInTheDocument();
    expect(screen.queryByText('Latest orders')).not.toBeInTheDocument();
    expect(orderService.getCustomerSummary).not.toHaveBeenCalled();
    expect(orderService.getCustomerTopFoods).not.toHaveBeenCalled();
    expect(reviewService.getCustomerLikedDishes).not.toHaveBeenCalled();
  });

  it('opens the order list filtered to this customer', async () => {
    const { store } = renderCustomer(7);

    await userEvent.click(
      await screen.findByRole('button', { name: 'All orders of this customer' }),
    );

    expect(screen.getByText('Order list')).toBeInTheDocument();
    expect(store.getState().orderList.filters).toEqual({ customerId: 7 });
  });

  it('deactivates the account with a reason', async () => {
    vi.mocked(userService.changeStatus).mockResolvedValue(adminUser({ status: 'INACTIVE' }));
    renderCustomer(7);

    await userEvent.click(await screen.findByRole('button', { name: 'Deactivate' }));
    const dialog = screen.getByRole('dialog');
    await userEvent.type(within(dialog).getByLabelText('Reason'), 'Fraud reports');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Deactivate' }));

    expect(userService.changeStatus).toHaveBeenCalledWith(7, false, 'Fraud reports');
    expect(await screen.findByText('Priya Sharma was deactivated.')).toBeInTheDocument();
  });

  it('changes the role to one the user does not have yet', async () => {
    vi.mocked(userService.changeRole).mockResolvedValue(adminUser({ role: 'RESTAURANT_ADMIN' }));
    renderCustomer(7);

    await userEvent.click(await screen.findByRole('button', { name: 'Change role' }));
    const dialog = screen.getByRole('dialog');
    const roles = within(dialog).getByLabelText('New role');
    expect(await optionsOf(roles)).not.toContain('Customer');

    await chooseOption(roles, 'Restaurant owner');
    await userEvent.type(within(dialog).getByLabelText('Reason'), 'Opened a restaurant');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Change role' }));

    expect(userService.changeRole).toHaveBeenCalledWith(
      7,
      'RESTAURANT_ADMIN',
      'Opened a restaurant',
    );
  });

  it('does not let admins deactivate their own account or change its role', async () => {
    // The tests act as user 1 (vite.config.ts)
    vi.mocked(userService.getUser).mockResolvedValue(
      adminUser({ id: 1, fullName: 'Platform Administrator', role: 'PLATFORM_ADMIN' }),
    );
    renderCustomer(1);

    expect(await screen.findByRole('button', { name: 'Deactivate' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Change role' })).toBeDisabled();
    expect(screen.getByText(/This is your own account/)).toBeInTheDocument();
    // Orders are shown only for customers
    expect(orderService.getCustomerSummary).not.toHaveBeenCalled();
  });
});
