import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { ApiError } from '@/api/apiErrorHandler';
import OwnerOrderDetailsPage from '@/pages/Owner/OwnerOrderDetailsPage';
import { ownerService } from '@/services/ownerService';
import { orderDetails } from '@/test/fixtures';
import { renderOwnerPage } from '@/test/ownerTestUtils';

vi.mock('@/services/ownerService', () => ({
  ownerService: {
    getOrders: vi.fn(),
    countOrders: vi.fn(),
    getOrder: vi.fn(),
    acceptOrder: vi.fn(),
    rejectOrder: vi.fn(),
    startPreparing: vi.fn(),
    markReady: vi.fn(),
  },
}));

const RESTAURANT_ID = 3;

const renderDetails = (route = '/owner/orders/42') =>
  renderOwnerPage(<OwnerOrderDetailsPage />, '/owner/orders/:orderId', { route });

const apiError = (status: number, kind: ApiError['kind'], message: string) =>
  new ApiError({ kind, status, userMessage: message, technicalMessage: message });

beforeEach(() => {
  vi.clearAllMocks();
  vi.spyOn(console, 'error').mockImplementation(() => undefined);
  vi.mocked(ownerService.getOrder).mockResolvedValue(orderDetails());
});

describe('OwnerOrderDetailsPage: content', () => {
  it('shows the order, the customer, the items with their price and the timeline', async () => {
    vi.mocked(ownerService.getOrder).mockResolvedValue(
      orderDetails({ notes: 'No onions please', paymentMethod: 'CASH_ON_DELIVERY' }),
    );
    renderDetails();

    expect(await screen.findByRole('heading', { name: 'Order #42' })).toBeInTheDocument();
    expect(ownerService.getOrder).toHaveBeenCalledWith(RESTAURANT_ID, 42);
    expect(screen.getByRole('link', { name: 'Back to orders' })).toHaveAttribute(
      'href',
      '/owner/orders',
    );

    // Customer and delivery
    expect(screen.getByText('Priya Sharma')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: '+91 98765 43210' })).toHaveAttribute(
      'href',
      'tel:+919876543210',
    );
    expect(screen.getByText('12 MG Road, Bengaluru, KA, 560001')).toBeInTheDocument();
    expect(screen.getByText('No onions please')).toBeInTheDocument();
    expect(screen.getByText('Cash on delivery')).toBeInTheDocument();

    // Items and the price breakdown
    expect(screen.getByRole('cell', { name: 'Chicken Biryani' })).toBeInTheDocument();
    expect(screen.getByText('Delivery fee')).toBeInTheDocument();
    expect(screen.getByText('Coupon SAVE100')).toBeInTheDocument();

    // Timeline
    const timeline = screen.getByRole('heading', { name: 'Timeline' }).closest('section');
    expect(within(timeline as HTMLElement).getByText('Payment received')).toBeInTheDocument();
  });

  it('says the status in words for screen readers', async () => {
    renderDetails();

    expect(await screen.findByText('Order #42 is now Placed.')).toBeInTheDocument();
  });

  it('copes with an order without a customer name, phone or address', async () => {
    vi.mocked(ownerService.getOrder).mockResolvedValue(
      orderDetails({ customerName: null, customerPhone: null, deliveryAddress: null }),
    );
    renderDetails();

    expect(await screen.findByText('Customer #7')).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: /\+91/ })).not.toBeInTheDocument();
  });

  it('shows why a cancelled order was cancelled, and offers no step', async () => {
    vi.mocked(ownerService.getOrder).mockResolvedValue(
      orderDetails({
        status: 'CANCELLED',
        cancellationReason: 'Customer changed their mind',
        cancelledBy: 'CUSTOMER',
      }),
    );
    renderDetails();

    expect(await screen.findByText('Customer changed their mind')).toBeInTheDocument();
    expect(screen.getByText('Cancelled by customer')).toBeInTheDocument();
    expect(screen.queryByRole('button')).not.toBeInTheDocument();
  });
});

describe('OwnerOrderDetailsPage: loading, missing and error', () => {
  it('shows a placeholder while the order loads', () => {
    vi.mocked(ownerService.getOrder).mockReturnValue(new Promise(() => undefined));
    renderDetails();

    expect(screen.getByRole('status', { name: 'Loading' })).toBeInTheDocument();
  });

  it('shows the not-found page for an address that is not an order number', () => {
    renderDetails('/owner/orders/abc');

    expect(screen.getByText('Page not found')).toBeInTheDocument();
    expect(ownerService.getOrder).not.toHaveBeenCalled();
  });

  it("explains an order that does not exist (or is another restaurant's) and leads back to the list", async () => {
    vi.mocked(ownerService.getOrder).mockRejectedValue(
      apiError(404, 'notFound', 'Order 42 was not found.'),
    );
    renderDetails();

    expect(await screen.findByText('We could not find this order')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Back to orders' })).toBeInTheDocument();
  });

  it('explains a failure and tries again on request', async () => {
    vi.mocked(ownerService.getOrder)
      .mockRejectedValueOnce(apiError(500, 'server', 'Something went wrong on our side.'))
      .mockResolvedValue(orderDetails());
    renderDetails();

    expect(await screen.findByText('Unable to load this order')).toBeInTheDocument();
    expect(screen.getByText('Something went wrong on our side.')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Try again' }));

    expect(await screen.findByRole('heading', { name: 'Order #42' })).toBeInTheDocument();
  });
});

describe('OwnerOrderDetailsPage: the next step', () => {
  it('accepts a new order, and the page shows the order as it is now', async () => {
    vi.mocked(ownerService.acceptOrder).mockResolvedValue(orderDetails({ status: 'ACCEPTED' }));
    // The server's answer to the reload that follows the change
    vi.mocked(ownerService.getOrder)
      .mockResolvedValueOnce(orderDetails())
      .mockResolvedValue(orderDetails({ status: 'ACCEPTED' }));
    renderDetails();

    await userEvent.click(await screen.findByRole('button', { name: 'Accept order #42' }));

    expect(ownerService.acceptOrder).toHaveBeenCalledWith(RESTAURANT_ID, 42);
    expect(await screen.findByText('Order #42 accepted.')).toBeInTheDocument();
    // The buttons follow the status: accepted -> start preparing
    expect(
      await screen.findByRole('button', { name: 'Start preparing order #42' }),
    ).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Accept order #42' })).not.toBeInTheDocument();
    expect(screen.getByText('Order #42 is now Accepted.')).toBeInTheDocument();
  });

  it.each([
    ['ACCEPTED', 'Start preparing order #42', 'startPreparing'],
    ['PREPARING', 'Mark ready order #42', 'markReady'],
  ] as const)('%s: "%s" calls the service', async (status, button, method) => {
    vi.mocked(ownerService.getOrder).mockResolvedValue(orderDetails({ status }));
    vi.mocked(ownerService[method]).mockResolvedValue(orderDetails({ status: 'PREPARING' }));
    renderDetails();

    await userEvent.click(await screen.findByRole('button', { name: button }));

    expect(ownerService[method]).toHaveBeenCalledWith(RESTAURANT_ID, 42);
  });

  it.each(['READY_FOR_PICKUP', 'OUT_FOR_DELIVERY', 'DELIVERED', 'REJECTED'] as const)(
    'offers no step for an order that is %s',
    async (status) => {
      vi.mocked(ownerService.getOrder).mockResolvedValue(orderDetails({ status }));
      renderDetails();
      await screen.findByRole('heading', { name: 'Order #42' });

      expect(screen.queryByRole('button')).not.toBeInTheDocument();
    },
  );

  it('rejects with the reason the owner gives', async () => {
    vi.mocked(ownerService.rejectOrder).mockResolvedValue(orderDetails({ status: 'REJECTED' }));
    vi.mocked(ownerService.getOrder)
      .mockResolvedValueOnce(orderDetails())
      .mockResolvedValue(orderDetails({ status: 'REJECTED' }));
    renderDetails();
    await userEvent.click(await screen.findByRole('button', { name: 'Reject order #42' }));
    const dialog = screen.getByRole('dialog', { name: 'Reject order #42?' });

    await userEvent.click(within(dialog).getByRole('button', { name: 'Reject order' }));
    expect(
      await within(dialog).findByText('Please tell the customer why you cannot make this order.'),
    ).toBeInTheDocument();
    expect(ownerService.rejectOrder).not.toHaveBeenCalled();

    await userEvent.type(
      within(dialog).getByLabelText('Reason for the customer'),
      'Kitchen closed',
    );
    await userEvent.click(within(dialog).getByRole('button', { name: 'Reject order' }));

    await waitFor(() =>
      expect(ownerService.rejectOrder).toHaveBeenCalledWith(RESTAURANT_ID, 42, 'Kitchen closed'),
    );
    expect(await screen.findByText('Order #42 is now Rejected.')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /order #42/ })).not.toBeInTheDocument();
  });

  it('shows the refusal of the server and keeps the order as it was', async () => {
    vi.mocked(ownerService.acceptOrder).mockRejectedValue(
      apiError(409, 'conflict', 'Order 42 is already CANCELLED and cannot be accepted.'),
    );
    renderDetails();

    await userEvent.click(await screen.findByRole('button', { name: 'Accept order #42' }));

    expect(
      await screen.findByText('Order 42 is already CANCELLED and cannot be accepted.'),
    ).toBeInTheDocument();
    expect(screen.getByText('Order #42 is now Placed.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Accept order #42' })).toBeEnabled();
  });
});
