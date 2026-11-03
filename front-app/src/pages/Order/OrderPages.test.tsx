import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Route, Routes } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { ApiError } from '@/api/apiErrorHandler';
import OrderDetailsPage from '@/pages/Order/OrderDetailsPage';
import OrderListPage from '@/pages/Order/OrderListPage';
import { orderService } from '@/services/orderService';
import { makeStore } from '@/store/store';
import { orderDetails, orderSummary, pageOf } from '@/test/fixtures';
import { chooseOption } from '@/test/chooseOption';
import { renderWithProviders } from '@/test/renderWithProviders';

vi.mock('@/services/orderService', () => ({
  orderService: {
    searchOrders: vi.fn(),
    getOrder: vi.fn(),
    cancelOrder: vi.fn(),
    getAuditLogs: vi.fn(),
    getCustomerSummary: vi.fn(),
  },
}));

beforeEach(() => {
  vi.clearAllMocks();
  vi.spyOn(console, 'error').mockImplementation(() => undefined);
});

describe('OrderListPage', () => {
  it('lists the orders and filters them by status, from the first page again', async () => {
    vi.mocked(orderService.searchOrders).mockResolvedValue(pageOf([orderSummary()], 45));
    renderWithProviders(<OrderListPage />);

    expect(await screen.findByRole('link', { name: '#42' })).toHaveAttribute(
      'href',
      '/admin/orders/42',
    );
    expect(screen.getByText('Biryani House')).toBeInTheDocument();
    expect(screen.getByText('Showing 1-20 of 45')).toBeInTheDocument();

    await chooseOption(screen.getByLabelText('Status'), 'Delivered');

    expect(orderService.searchOrders).toHaveBeenLastCalledWith({ status: 'DELIVERED' }, 0, 20);
  });

  it('goes to the next page', async () => {
    vi.mocked(orderService.searchOrders).mockResolvedValue(pageOf([orderSummary()], 45));
    renderWithProviders(<OrderListPage />);
    await screen.findByRole('link', { name: '#42' });

    await userEvent.click(screen.getByRole('button', { name: 'Next page' }));

    expect(orderService.searchOrders).toHaveBeenLastCalledWith({}, 1, 20);
  });

  it('says when nothing matches, and the filters can be cleared from there', async () => {
    vi.mocked(orderService.searchOrders).mockResolvedValue(pageOf([]));
    const store = makeStore({ orderList: { filters: { status: 'REJECTED' }, page: 0 } });
    renderWithProviders(<OrderListPage />, { store });

    const empty = await screen.findByText('No orders match these filters');
    await userEvent.click(
      within(empty.parentElement as HTMLElement).getByRole('button', { name: 'Clear filters' }),
    );

    expect(store.getState().orderList.filters).toEqual({});
  });

  it('does not ask the server for an impossible date range, and says why', () => {
    vi.mocked(orderService.searchOrders).mockResolvedValue(pageOf([orderSummary()]));
    // The calendars do not offer such a range; it can still come from filters kept earlier
    const store = makeStore({
      orderList: { filters: { fromDate: '2026-10-08', toDate: '2026-10-01' }, page: 0 },
    });
    renderWithProviders(<OrderListPage />, { store });

    expect(screen.getByText('The "to" date is before the "from" date.')).toBeInTheDocument();
    expect(orderService.searchOrders).not.toHaveBeenCalledWith(
      expect.objectContaining({ toDate: '2026-10-01' }),
      expect.anything(),
      expect.anything(),
    );
  });
});

describe('OrderListPage: the status menu', () => {
  const auditEntry = {
    auditId: 9,
    adminUserId: 1,
    action: 'ORDER_CANCELLED_BY_ADMIN',
    entityType: 'ORDER',
    entityId: '42',
    oldValue: 'PLACED',
    newValue: 'CANCELLED',
    reason: 'Customer support: duplicate order',
    ipAddress: null,
    requestId: null,
    timestamp: '2026-10-08T11:00:00Z',
  };

  const openMenu = async (orderId = 42) => {
    await userEvent.click(
      await screen.findByRole('button', { name: `Actions for order #${orderId}` }),
    );
    return screen.getByRole('menu', { name: `Actions for order #${orderId}` });
  };

  beforeEach(() => {
    vi.mocked(orderService.getOrder).mockResolvedValue(orderDetails());
    vi.mocked(orderService.getAuditLogs).mockResolvedValue(pageOf([auditEntry]));
  });

  it("shows the order's status as the way into its actions, with only what is allowed for this order", async () => {
    vi.mocked(orderService.searchOrders).mockResolvedValue(
      pageOf([
        orderSummary({ id: 42, status: 'PREPARING', paymentMethod: 'UPI' }),
        orderSummary({ id: 43, status: 'DELIVERED', paymentMethod: 'CASH_ON_DELIVERY' }),
      ]),
    );
    renderWithProviders(<OrderListPage />, { route: '/admin/orders' });

    const preparing = await openMenu(42);
    expect(
      within(preparing)
        .getAllByRole('menuitem')
        .map((item) => item.textContent),
    ).toEqual(['Open order', 'History', 'Payments', 'Customer', 'Cancel order']);

    await userEvent.keyboard('{Escape}');
    const delivered = await openMenu(43);
    // Delivered: nothing to cancel; cash on delivery: no online payment to show
    expect(
      within(delivered)
        .getAllByRole('menuitem')
        .map((item) => item.textContent),
    ).toEqual(['Open order', 'History', 'Customer']);
    expect(within(delivered).getByRole('menuitem', { name: 'Open order' })).toHaveAttribute(
      'href',
      '/admin/orders/43',
    );
    expect(within(delivered).getByRole('menuitem', { name: 'Customer' })).toHaveAttribute(
      'href',
      '/admin/customers/7',
    );
  });

  it('opens the status badge as a menu too', async () => {
    vi.mocked(orderService.searchOrders).mockResolvedValue(
      pageOf([orderSummary({ status: 'PLACED' })]),
    );
    renderWithProviders(<OrderListPage />, { route: '/admin/orders' });

    await userEvent.click(await screen.findByRole('button', { name: 'Placed' }));

    expect(screen.getByRole('menu', { name: 'Actions for order #42' })).toBeInTheDocument();
  });

  it('shows the history: every status with when and by whom, and what admins did and why', async () => {
    vi.mocked(orderService.searchOrders).mockResolvedValue(pageOf([orderSummary()]));
    renderWithProviders(<OrderListPage />, { route: '/admin/orders' });
    expect(orderService.getOrder).not.toHaveBeenCalled(); // not read before it is asked for

    const menu = await openMenu();
    await userEvent.click(within(menu).getByRole('menuitem', { name: 'History' }));

    const dialog = await screen.findByRole('dialog', { name: 'Order #42 history' });
    expect(await within(dialog).findByText('Payment received')).toBeInTheDocument();
    expect(within(dialog).getByText(/by customer #7/)).toBeInTheDocument();
    expect(await within(dialog).findByText('Order cancelled by admin')).toBeInTheDocument();
    expect(within(dialog).getByText('"Customer support: duplicate order"')).toBeInTheDocument();
    expect(within(dialog).getByRole('link', { name: 'Open order' })).toHaveAttribute(
      'href',
      '/admin/orders/42',
    );
    expect(orderService.getOrder).toHaveBeenCalledWith(42);

    // The dialog's own X and the Close button in its footer
    const [closeX, closeButton] = within(dialog).getAllByRole('button', { name: 'Close' });
    expect(closeX).toBeInTheDocument();
    await userEvent.click(closeButton);
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('says so when the history cannot be loaded, and loads it again on "Try again"', async () => {
    vi.mocked(orderService.searchOrders).mockResolvedValue(pageOf([orderSummary()]));
    vi.mocked(orderService.getOrder)
      .mockRejectedValueOnce(
        new ApiError({
          kind: 'unavailable',
          status: 503,
          userMessage: 'Service unavailable',
          technicalMessage: 'GET failed with 503',
        }),
      )
      .mockResolvedValue(orderDetails());
    renderWithProviders(<OrderListPage />, { route: '/admin/orders' });
    const menu = await openMenu();
    await userEvent.click(within(menu).getByRole('menuitem', { name: 'History' }));

    const dialog = await screen.findByRole('dialog', { name: 'Order #42 history' });
    const alert = await within(dialog).findByRole('alert');
    expect(alert).toHaveTextContent('Service unavailable');

    await userEvent.click(within(alert).getByRole('button', { name: /try again/i }));

    expect(await within(dialog).findByText('Payment received')).toBeInTheDocument();
  });

  it('asks for a reason before cancelling, then cancels, says so and closes', async () => {
    vi.mocked(orderService.searchOrders).mockResolvedValue(pageOf([orderSummary()]));
    vi.mocked(orderService.cancelOrder).mockResolvedValue(
      orderDetails({
        status: 'CANCELLED',
        cancellationReason: 'Restaurant closed',
        cancelledBy: 'ADMIN',
      }),
    );
    renderWithProviders(<OrderListPage />, { route: '/admin/orders' });
    const menu = await openMenu();
    await userEvent.click(within(menu).getByRole('menuitem', { name: 'Cancel order' }));
    const dialog = await screen.findByRole('dialog', { name: 'Cancel order #42?' });

    await userEvent.click(within(dialog).getByRole('button', { name: 'Cancel order' }));
    expect(await within(dialog).findByText(/Please give a reason/)).toBeInTheDocument();
    expect(orderService.cancelOrder).not.toHaveBeenCalled();

    await userEvent.type(within(dialog).getByLabelText('Reason'), 'Restaurant closed');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Cancel order' }));

    expect(orderService.cancelOrder).toHaveBeenCalledWith(42, 'Restaurant closed');
    expect(await screen.findByText('Order #42 was cancelled.')).toBeInTheDocument();
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it("keeps the dialog open with the server's reason when it refuses the cancel", async () => {
    vi.mocked(orderService.searchOrders).mockResolvedValue(pageOf([orderSummary()]));
    vi.mocked(orderService.cancelOrder).mockRejectedValue(
      new ApiError({
        kind: 'conflict',
        status: 409,
        userMessage: 'Order 42 is OUT_FOR_DELIVERY and can no longer be cancelled',
        technicalMessage: 'PUT failed with 409',
      }),
    );
    renderWithProviders(<OrderListPage />, { route: '/admin/orders' });
    const menu = await openMenu();
    await userEvent.click(within(menu).getByRole('menuitem', { name: 'Cancel order' }));
    const dialog = await screen.findByRole('dialog', { name: 'Cancel order #42?' });

    await userEvent.type(within(dialog).getByLabelText('Reason'), 'Too late?');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Cancel order' }));

    expect(await within(dialog).findByText(/can no longer be cancelled/)).toBeInTheDocument();
    expect(screen.getByRole('dialog', { name: 'Cancel order #42?' })).toBeInTheDocument();
  });

  it('has no menu for somebody who is not a platform admin (only the badge)', async () => {
    vi.mocked(orderService.searchOrders).mockResolvedValue(pageOf([orderSummary()]));
    // The customer area acts as the signed-in customer (none here): no admin, no actions
    renderWithProviders(<OrderListPage />, { route: '/orders-of-a-customer' });

    expect(await screen.findByText('Placed')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Actions for order/ })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Placed' })).not.toBeInTheDocument();
  });
});

describe('OrderDetailsPage', () => {
  const renderAt = (route: string) =>
    renderWithProviders(
      <Routes>
        <Route path="/admin/orders/:orderId" element={<OrderDetailsPage />} />
      </Routes>,
      { route },
    );

  beforeEach(() => {
    vi.mocked(orderService.getOrder).mockResolvedValue(orderDetails());
    vi.mocked(orderService.getAuditLogs).mockResolvedValue(pageOf([]));
  });

  it('shows what was ordered, the price, the timeline and who ordered it', async () => {
    renderAt('/admin/orders/42');

    expect(await screen.findByRole('heading', { name: 'Order #42', level: 1 })).toBeInTheDocument();
    expect(screen.getByText('Chicken Biryani')).toBeInTheDocument();
    expect(screen.getByText('- ₹100.00')).toBeInTheDocument();
    expect(screen.getAllByText('₹562.90').length).toBeGreaterThan(0);
    expect(screen.getByText('Payment received')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Priya Sharma' })).toHaveAttribute(
      'href',
      '/admin/customers/7',
    );
    expect(await screen.findByText('No admin changes yet')).toBeInTheDocument();
  });

  it('cancels the order with a reason, and says so', async () => {
    vi.mocked(orderService.cancelOrder).mockResolvedValue(
      orderDetails({
        status: 'CANCELLED',
        cancellationReason: 'Restaurant closed',
        cancelledBy: 'ADMIN',
      }),
    );
    renderAt('/admin/orders/42');
    await userEvent.click(await screen.findByRole('button', { name: 'Cancel order' }));
    const dialog = screen.getByRole('dialog', { name: 'Cancel order #42?' });

    // No reason: refused in the browser, nothing is sent
    await userEvent.click(within(dialog).getByRole('button', { name: 'Cancel order' }));
    expect(await within(dialog).findByText(/Please give a reason/)).toBeInTheDocument();
    expect(orderService.cancelOrder).not.toHaveBeenCalled();

    await userEvent.type(within(dialog).getByLabelText('Reason'), 'Restaurant closed');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Cancel order' }));

    expect(orderService.cancelOrder).toHaveBeenCalledWith(42, 'Restaurant closed');
    expect(await screen.findByText('Order #42 was cancelled.')).toBeInTheDocument();
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it("keeps the dialog open with the server's reason when it refuses", async () => {
    vi.mocked(orderService.cancelOrder).mockRejectedValue(
      new ApiError({
        kind: 'conflict',
        status: 409,
        userMessage: 'Order 42 is OUT_FOR_DELIVERY and can no longer be cancelled',
        technicalMessage: 'PUT failed with 409',
      }),
    );
    renderAt('/admin/orders/42');
    await userEvent.click(await screen.findByRole('button', { name: 'Cancel order' }));
    const dialog = screen.getByRole('dialog');

    await userEvent.type(within(dialog).getByLabelText('Reason'), 'Customer asked');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Cancel order' }));

    expect(await within(dialog).findByRole('alert')).toHaveTextContent(
      'can no longer be cancelled',
    );
    expect(screen.getByRole('dialog')).toBeInTheDocument();
  });

  it('offers no cancelling once the order is on its way', async () => {
    vi.mocked(orderService.getOrder).mockResolvedValue(
      orderDetails({ status: 'OUT_FOR_DELIVERY' }),
    );
    renderAt('/admin/orders/42');

    await screen.findByRole('heading', { name: 'Order #42', level: 1 });
    expect(screen.queryByRole('button', { name: 'Cancel order' })).not.toBeInTheDocument();
  });

  it('shows "page not found" for an address that is not an order, without asking the server', () => {
    renderAt('/admin/orders/abc');

    expect(screen.getByRole('heading', { name: 'Page not found' })).toBeInTheDocument();
    expect(orderService.getOrder).not.toHaveBeenCalled();
  });
});
