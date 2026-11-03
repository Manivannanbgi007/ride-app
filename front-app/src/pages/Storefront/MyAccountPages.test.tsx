import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Route, Routes } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { ApiError } from '@/api/apiErrorHandler';
import CustomerNotificationsPage from '@/pages/Storefront/CustomerNotificationsPage';
import MyOrdersPage from '@/pages/Storefront/MyOrdersPage';
import OrderPage from '@/pages/Storefront/OrderPage';
import ProfilePage from '@/pages/Storefront/ProfilePage';
import { deliveryService } from '@/services/deliveryService';
import { notificationService } from '@/services/notificationService';
import { orderService } from '@/services/orderService';
import { userService } from '@/services/userService';
import { makeStore } from '@/store/store';
import { orderDetails, orderSummary, pageOf, priya } from '@/test/fixtures';
import { chooseOption } from '@/test/chooseOption';
import { renderWithProviders } from '@/test/renderWithProviders';
import type { OrderDetails } from '@/types/order';
import type { OrderTracking } from '@/types/shopping';

vi.mock('@/services/orderService', () => ({
  orderService: { getMyOrders: vi.fn(), getMyOrder: vi.fn(), cancelMyOrder: vi.fn() },
}));
vi.mock('@/services/deliveryService', () => ({
  deliveryService: { getOrderTracking: vi.fn() },
}));
vi.mock('@/services/userService', () => ({
  userService: { getMyProfile: vi.fn(), updateMyProfile: vi.fn(), changeMyPassword: vi.fn() },
}));
vi.mock('@/services/notificationService', () => ({
  notificationService: {
    getInbox: vi.fn(),
    getUnreadCount: vi.fn(),
    markRead: vi.fn(),
    markAllRead: vi.fn(),
  },
}));

const apiError = (status: number, kind: ApiError['kind'], userMessage: string) =>
  new ApiError({ kind, status, userMessage, technicalMessage: userMessage });

const signedIn = () => makeStore({ session: { adminUser: null, customer: priya } });

function renderAt(route: string, path: string, page: React.ReactElement, store = signedIn()) {
  return renderWithProviders(
    <Routes>
      <Route path={path} element={page} />
      <Route path="/customer/orders/:orderId/pay" element={<p>Payment page</p>} />
    </Routes>,
    { route, store },
  );
}

const at = (minute: number) => `2026-10-08T10:${String(minute).padStart(2, '0')}:00Z`;

function placedOrder(overrides: Partial<OrderDetails> = {}): OrderDetails {
  return orderDetails({
    status: 'PLACED',
    paymentStatus: 'PAID',
    timeline: [
      {
        status: 'PAYMENT_PENDING',
        actorType: 'CUSTOMER',
        actorUserId: 7,
        note: 'Waiting for online payment',
        at: at(0),
      },
      {
        status: 'PLACED',
        actorType: 'SYSTEM',
        actorUserId: null,
        note: 'Payment 77 received',
        at: at(1),
      },
    ],
    ...overrides,
  });
}

const onTheWay: OrderTracking = {
  orderId: 42,
  deliveryId: 55,
  status: 'PICKED_UP',
  restaurantName: 'Biryani House',
  dropoffAddress: null,
  partnerName: 'Ravi Kumar',
  partnerPhone: '+919800000031',
  vehicleType: 'SCOOTER',
  cashToCollect: 0,
  currency: 'INR',
  readyAt: at(20),
  acceptedAt: at(21),
  pickedUpAt: at(30),
  deliveredAt: null,
  cancelledAt: null,
  steps: [
    { status: 'ACCEPTED', note: 'Ravi Kumar is on the way to the restaurant', at: at(21) },
    { status: 'PICKED_UP', note: 'Picked up from Biryani House', at: at(30) },
  ],
};

beforeEach(() => {
  vi.clearAllMocks();
});

describe('MyOrdersPage', () => {
  it('lists the orders with "Pay now" for an unpaid one, and filters by status', async () => {
    vi.mocked(orderService.getMyOrders).mockResolvedValue(
      pageOf([
        orderSummary({ id: 50, status: 'PAYMENT_PENDING', restaurantName: 'Spice Garden' }),
        orderSummary({ id: 42, status: 'DELIVERED' }),
      ]),
    );
    renderAt('/customer/orders', '/customer/orders', <MyOrdersPage />);

    expect(await screen.findByRole('link', { name: /Spice Garden, order #50/ })).toHaveAttribute(
      'href',
      '/customer/orders/50',
    );
    expect(screen.getByRole('link', { name: 'Pay now' })).toHaveAttribute(
      'href',
      '/customer/orders/50/pay',
    );
    // The badge (the status filter offers the same words)
    expect(screen.getByText('Waiting for your payment', { selector: 'span' })).toBeInTheDocument();
    expect(orderService.getMyOrders).toHaveBeenCalledWith(undefined, 0, 10);

    await chooseOption(screen.getByLabelText('Show'), 'Delivered');
    await waitFor(() =>
      expect(orderService.getMyOrders).toHaveBeenLastCalledWith('DELIVERED', 0, 10),
    );
  });
});

describe('OrderPage', () => {
  it('cancels a paid order before the restaurant accepts, with a reason', async () => {
    vi.mocked(orderService.getMyOrder).mockResolvedValue(placedOrder());
    vi.mocked(orderService.cancelMyOrder).mockResolvedValue(
      placedOrder({
        status: 'CANCELLED',
        cancellationReason: 'Ordered by mistake',
        cancelledBy: 'CUSTOMER',
      }),
    );
    renderAt('/customer/orders/42', '/customer/orders/:orderId', <OrderPage />);

    expect(await screen.findByText('Payment 77 received')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Cancel order' }));
    const dialog = screen.getByRole('dialog', { name: 'Cancel order #42?' });
    expect(within(dialog).getByText(/What you paid online is refunded/)).toBeInTheDocument();

    await userEvent.click(within(dialog).getByRole('button', { name: 'Cancel order' }));
    expect(
      await within(dialog).findByText('Please tell us why (one line is enough).'),
    ).toBeInTheDocument();

    await userEvent.type(
      within(dialog).getByLabelText('Why are you cancelling?'),
      'Ordered by mistake',
    );
    await userEvent.click(within(dialog).getByRole('button', { name: 'Cancel order' }));

    expect(orderService.cancelMyOrder).toHaveBeenCalledWith(42, 'Ordered by mistake');
    expect(
      await screen.findByText('Order #42 is cancelled. What you paid is refunded.'),
    ).toBeInTheDocument();
  });

  it('explains in the dialog when the restaurant accepted meanwhile', async () => {
    vi.mocked(orderService.getMyOrder).mockResolvedValue(placedOrder());
    vi.mocked(orderService.cancelMyOrder).mockRejectedValue(
      apiError(
        409,
        'conflict',
        'This order can no longer be cancelled (status ACCEPTED). Please contact support.',
      ),
    );
    renderAt('/customer/orders/42', '/customer/orders/:orderId', <OrderPage />);

    await userEvent.click(await screen.findByRole('button', { name: 'Cancel order' }));
    const dialog = screen.getByRole('dialog');
    await userEvent.type(within(dialog).getByLabelText('Why are you cancelling?'), 'Too late');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Cancel order' }));

    expect(await within(dialog).findByText(/can no longer be cancelled/)).toBeInTheDocument();
  });

  it('follows the delivery once the food is on its way: the steps, the partner and their phone', async () => {
    vi.mocked(orderService.getMyOrder).mockResolvedValue(
      placedOrder({
        status: 'OUT_FOR_DELIVERY',
        timeline: [
          { status: 'PLACED', actorType: 'SYSTEM', actorUserId: null, note: null, at: at(1) },
          {
            status: 'ACCEPTED',
            actorType: 'RESTAURANT',
            actorUserId: 9,
            note: 'Accepted by the restaurant',
            at: at(5),
          },
          { status: 'PREPARING', actorType: 'RESTAURANT', actorUserId: 9, note: null, at: at(6) },
          {
            status: 'READY_FOR_PICKUP',
            actorType: 'RESTAURANT',
            actorUserId: 9,
            note: null,
            at: at(20),
          },
          {
            status: 'READY_FOR_PICKUP',
            actorType: 'SYSTEM',
            actorUserId: null,
            note: 'Delivery assigned to Ravi Kumar',
            at: at(21),
          },
          {
            status: 'OUT_FOR_DELIVERY',
            actorType: 'DELIVERY_PARTNER',
            actorUserId: 31,
            note: 'Picked up by Ravi Kumar',
            at: at(30),
          },
        ],
      }),
    );
    vi.mocked(deliveryService.getOrderTracking).mockResolvedValue(onTheWay);
    renderAt('/customer/orders/42', '/customer/orders/:orderId', <OrderPage />);

    const progress = await screen.findByRole('list', { name: 'Order progress' });
    expect(
      within(progress)
        .getByText(/On the way/)
        .closest('li'),
    ).toHaveAttribute('aria-current', 'step');
    expect(await screen.findByRole('link', { name: 'Call Ravi' })).toHaveAttribute(
      'href',
      'tel:+919800000031',
    );
    expect(screen.getByText('Picked up from Biryani House')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Cancel order' })).not.toBeInTheDocument();
    expect(deliveryService.getOrderTracking).toHaveBeenCalledWith(42);
  });

  it('says the delivery is about to start when delivery-service does not know it yet', async () => {
    vi.mocked(orderService.getMyOrder).mockResolvedValue(
      placedOrder({ status: 'READY_FOR_PICKUP' }),
    );
    vi.mocked(deliveryService.getOrderTracking).mockRejectedValue(
      apiError(404, 'notFound', 'No delivery for order 42 yet'),
    );
    renderAt('/customer/orders/42', '/customer/orders/:orderId', <OrderPage />);

    expect(await screen.findByText(/The delivery starts in a moment/)).toBeInTheDocument();
  });
});

describe('ProfilePage', () => {
  beforeEach(() => {
    vi.mocked(userService.getMyProfile).mockResolvedValue({
      id: 7,
      email: 'priya@example.com',
      fullName: 'Priya Sharma',
      phone: null,
      role: 'CUSTOMER',
      status: 'ACTIVE',
      createdAt: '2026-09-01T10:00:00Z',
      lastLoginAt: null,
    });
  });

  it('saves the name and phone, and the header follows the new name', async () => {
    vi.mocked(userService.updateMyProfile).mockResolvedValue({
      ...(await userService.getMyProfile()),
      fullName: 'Priya S. Rao',
      phone: '+919800000007',
    });
    const { store } = renderAt(
      '/customer/account/profile',
      '/customer/account/profile',
      <ProfilePage />,
    );

    const name = await screen.findByLabelText('Full name');
    await userEvent.clear(name);
    await userEvent.type(name, 'Priya S. Rao');
    await userEvent.type(screen.getByLabelText('Phone (optional)'), '+919800000007');
    await userEvent.click(screen.getByRole('button', { name: 'Save details' }));

    expect(userService.updateMyProfile).toHaveBeenCalledWith({
      fullName: 'Priya S. Rao',
      phone: '+919800000007',
    });
    expect(await screen.findByText('Your details are saved.')).toBeInTheDocument();
    expect(store.getState().session.customer?.fullName).toBe('Priya S. Rao');
  });

  it('says so when the current password is wrong', async () => {
    vi.mocked(userService.changeMyPassword).mockRejectedValue(
      apiError(422, 'validation', 'Current password is incorrect'),
    );
    renderAt('/customer/account/profile', '/customer/account/profile', <ProfilePage />);

    await userEvent.type(await screen.findByLabelText('Current password'), 'Wrong1234');
    await userEvent.type(screen.getByLabelText('New password'), 'Better1234');
    await userEvent.type(screen.getByLabelText('New password again'), 'Better1234');
    await userEvent.click(screen.getByRole('button', { name: 'Change password' }));

    expect(userService.changeMyPassword).toHaveBeenCalledWith({
      currentPassword: 'Wrong1234',
      newPassword: 'Better1234',
    });
    expect(await screen.findByText('This is not your current password.')).toBeInTheDocument();
  });
});

describe('CustomerNotificationsPage', () => {
  it("shows the customer's notifications with links to their own order pages", async () => {
    vi.mocked(notificationService.getUnreadCount).mockResolvedValue(1);
    vi.mocked(notificationService.getInbox).mockResolvedValue(
      pageOf([
        {
          id: 5,
          type: 'ORDER_OUT_FOR_DELIVERY',
          title: 'Order #42 is on its way',
          message: 'Ravi is bringing your food.',
          orderId: 42,
          sentAt: at(30),
          readAt: null,
        },
      ]),
    );
    renderAt('/customer/notifications', '/customer/notifications', <CustomerNotificationsPage />);

    expect(await screen.findByText('Order #42 is on its way')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Order #42' })).toHaveAttribute(
      'href',
      '/customer/orders/42',
    );
  });
});
