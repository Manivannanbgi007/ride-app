import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { OwnerOrderTable } from '@/components/business/OwnerOrders/OwnerOrderTable';
import { ownerService } from '@/services/ownerService';
import { menuItem, orderDetails, orderSummary } from '@/test/fixtures';
import { renderOwnerPage } from '@/test/ownerTestUtils';
import type { OrderSummary } from '@/types/order';

vi.mock('@/services/ownerService', () => ({
  ownerService: {
    getOrder: vi.fn(),
    acceptOrder: vi.fn(),
    rejectOrder: vi.fn(),
    getMenuItems: vi.fn(),
  },
}));

const RESTAURANT_ID = 3;

const delivered = (overrides: Partial<OrderSummary> = {}) =>
  orderSummary({ status: 'DELIVERED', customerName: 'Priya Sharma', ...overrides });

/** The order as the details read it: delivered 26 minutes after it reached the kitchen, with a note */
const deliveredDetails = orderDetails({
  status: 'DELIVERED',
  notes: 'Ring the bell twice',
  timeline: [
    {
      status: 'PLACED',
      actorType: 'CUSTOMER',
      actorUserId: 7,
      note: null,
      at: '2026-10-08T10:16:00Z',
    },
    {
      status: 'OUT_FOR_DELIVERY',
      actorType: 'DELIVERY_PARTNER',
      actorUserId: 9,
      note: null,
      at: '2026-10-08T10:30:00Z',
    },
    {
      status: 'DELIVERED',
      actorType: 'DELIVERY_PARTNER',
      actorUserId: 9,
      note: null,
      at: '2026-10-08T10:42:00Z',
    },
  ],
});

const renderList = (orders: OrderSummary[], label = 'Delivered orders') =>
  renderOwnerPage(
    <OwnerOrderTable restaurantId={RESTAURANT_ID} orders={orders} label={label} />,
    '/owner/orders',
  );

const rowOf = (orderId: number) =>
  screen.getByRole('link', { name: `#${orderId}` }).closest('li') as HTMLElement;

beforeEach(() => {
  vi.clearAllMocks();
  vi.spyOn(console, 'error').mockImplementation(() => undefined);
  vi.mocked(ownerService.getOrder).mockResolvedValue(deliveredDetails);
  // The menu: biryani with its own photo, dosa without one (its category's picture stands in)
  vi.mocked(ownerService.getMenuItems).mockResolvedValue([
    menuItem({ id: 11, name: 'Chicken Biryani', imageUrl: 'https://img.example.com/biryani.jpg' }),
    menuItem({ id: 12, name: 'Masala Dosa', imageUrl: null, categoryName: 'Biryani' }),
  ]);
});

/** The pictures' addresses in an element (the photos are decorative: their alt text is empty) */
const photosIn = (element: HTMLElement) =>
  [...element.querySelectorAll('img')].map((image) => image.getAttribute('src'));

describe('OwnerOrderTable', () => {
  it('shows who ordered, when, how much and how it was paid, for each order', () => {
    renderList([
      delivered(),
      delivered({
        id: 43,
        customerName: null,
        customerId: 8,
        paymentMethod: 'CASH_ON_DELIVERY',
        paymentStatus: 'PENDING',
        itemCount: 1,
        totalAmount: 199,
      }),
    ]);

    expect(screen.getByRole('list', { name: 'Delivered orders' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: '#42' })).toHaveAttribute('href', '/owner/orders/42');
    const first = within(rowOf(42));
    expect(first.getByText('Priya Sharma')).toBeInTheDocument();
    expect(first.getByText('2 items')).toBeInTheDocument();
    expect(first.getByText('UPI')).toBeInTheDocument();
    expect(first.getByText('Paid')).toBeInTheDocument();
    expect(first.getByText('₹562.90')).toBeInTheDocument();
    expect(first.getByText('Delivered')).toBeInTheDocument();
    expect(rowOf(42).querySelector('time')).toHaveAttribute('dateTime', '2026-10-08T10:15:00Z');

    // An older order without the customer's name still says whose it is
    const second = within(rowOf(43));
    expect(second.getByText('Customer #8')).toBeInTheDocument();
    expect(second.getByText('1 item')).toBeInTheDocument();
    expect(second.getByText('Cash on delivery')).toBeInTheDocument();
    expect(second.getByText('Pending')).toBeInTheDocument();
    expect(second.getByText('₹199.00')).toBeInTheDocument();
  });

  it('keeps a long name whole for whoever hovers it, and its initials in the circle', () => {
    const name = 'Venkataramanan Subramaniam Krishnamurthy Iyer-Raghavendran';
    renderList([delivered({ customerName: name })]);

    expect(within(rowOf(42)).getByText(name)).toHaveAttribute('title', name);
    expect(within(rowOf(42)).getByText('VS')).toBeInTheDocument();
  });

  it('opens the details of an order only when asked: what was in it, the delivery and the customer', async () => {
    renderList([delivered()]);
    const toggle = screen.getByRole('button', { name: 'Show the details of order #42' });
    expect(toggle).toHaveAttribute('aria-expanded', 'false');
    expect(ownerService.getOrder).not.toHaveBeenCalled();

    await userEvent.click(toggle);

    expect(ownerService.getOrder).toHaveBeenCalledWith(RESTAURANT_ID, 42);
    expect(await screen.findByRole('heading', { name: 'What was ordered' })).toBeInTheDocument();
    const hide = screen.getByRole('button', { name: 'Hide the details of order #42' });
    expect(hide).toHaveAttribute('aria-expanded', 'true');
    expect(document.getElementById(hide.getAttribute('aria-controls') ?? '')).toBeInTheDocument();
    expect(screen.getByText('Chicken Biryani')).toBeInTheDocument();
    expect(screen.getByText('2 × ₹299.00')).toBeInTheDocument();
    expect(screen.getByText('Coupon SAVE100')).toBeInTheDocument();
    const delivery = screen.getByRole('region', { name: 'Delivery' });
    expect(within(delivery).getByText(/^Delivered \d/)).toBeInTheDocument();
    expect(within(delivery).getByText('26 min from order to door')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: '+91 98765 43210' })).toHaveAttribute(
      'href',
      'tel:+919876543210',
    );
    expect(screen.getByText('12 MG Road, Bengaluru, KA, 560001')).toBeInTheDocument();
    expect(screen.getByText('Ring the bell twice')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Open the order page' })).toHaveAttribute(
      'href',
      '/owner/orders/42',
    );

    await userEvent.click(hide);

    expect(screen.queryByRole('heading', { name: 'What was ordered' })).not.toBeInTheDocument();
  });

  it('says when the details could not be read, and reads them again on request', async () => {
    vi.mocked(ownerService.getOrder).mockRejectedValueOnce(new Error('down'));
    renderList([delivered()]);

    await userEvent.click(screen.getByRole('button', { name: 'Show the details of order #42' }));

    expect(await screen.findByText('The order could not be loaded')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Try again' }));
    expect(await screen.findByRole('heading', { name: 'What was ordered' })).toBeInTheDocument();
  });

  it("offers the kitchen's next step on an order that has one, and none on a finished order", () => {
    renderList(
      [
        orderSummary({ id: 42, status: 'PLACED', customerName: 'Priya Sharma' }),
        delivered({ id: 43 }),
      ],
      'New orders',
    );

    expect(screen.getByRole('list', { name: 'New orders' })).toBeInTheDocument();
    expect(within(rowOf(42)).getByRole('button', { name: 'Accept order #42' })).toBeEnabled();
    expect(within(rowOf(42)).getByRole('button', { name: 'Reject order #42' })).toBeEnabled();
    // A finished order: only its details button
    expect(within(rowOf(43)).getAllByRole('button')).toHaveLength(1);
    expect(
      within(rowOf(43)).getByRole('button', { name: 'Show the details of order #43' }),
    ).toBeInTheDocument();
  });

  it('says who turned an order down or cancelled it, and why', async () => {
    vi.mocked(ownerService.getOrder).mockResolvedValue(
      orderDetails({
        status: 'REJECTED',
        cancelledBy: 'RESTAURANT',
        cancellationReason: 'We have run out of rice',
      }),
    );
    renderList([orderSummary({ status: 'REJECTED' })], 'Rejected orders');

    await userEvent.click(screen.getByRole('button', { name: 'Show the details of order #42' }));

    const state = await screen.findByRole('region', { name: 'Rejected' });
    expect(within(state).getByText(/^Rejected by restaurant/)).toBeInTheDocument();
    expect(within(state).getByText('Reason: We have run out of rice')).toBeInTheDocument();
  });

  it('says where an order in the kitchen is now, and since when', async () => {
    vi.mocked(ownerService.getOrder).mockResolvedValue(orderDetails({ status: 'PREPARING' }));
    renderList([orderSummary({ status: 'PREPARING' })], 'Preparing orders');

    await userEvent.click(screen.getByRole('button', { name: 'Show the details of order #42' }));

    const state = await screen.findByRole('region', { name: 'Where the order is' });
    expect(within(state).getByText('Now: Preparing')).toBeInTheDocument();
    expect(within(state).getByText(/^Since /)).toBeInTheDocument();
  });

  it('shows what each order is made of, with the photos of the menu', async () => {
    renderList([
      delivered({
        id: 42,
        items: [
          { menuItemId: 11, name: 'Chicken Biryani', foodType: 'NON_VEG', quantity: 2 },
          { menuItemId: 12, name: 'Masala Dosa', foodType: 'VEG', quantity: 1 },
          { menuItemId: 99, name: 'Old Special', foodType: 'VEG', quantity: 1 },
        ],
      }),
    ]);

    const dishes = screen.getByRole('list', { name: 'Dishes of order #42' });
    const [biryani, dosa, gone] = within(dishes).getAllByRole('listitem');
    expect(biryani).toHaveTextContent('Chicken Biryani×2, quantity 2');
    // Its own photo; the dosa shows its category's; a dish no longer on the menu has a plain tile
    await waitFor(() => expect(photosIn(biryani)).toEqual(['https://img.example.com/biryani.jpg']));
    expect(photosIn(dosa)).toEqual(['/images/categories/biryani.jpg']);
    expect(photosIn(gone)).toEqual([]);
    expect(ownerService.getMenuItems).toHaveBeenCalledWith(RESTAURANT_ID);
  });

  it('shows the first four dishes and counts the others', () => {
    renderList([
      delivered({
        id: 42,
        items: [1, 2, 3, 4, 5, 6].map((id) => ({
          menuItemId: id,
          name: `Dish ${id}`,
          foodType: 'VEG',
          quantity: 1,
        })),
      }),
    ]);

    const dishes = within(screen.getByRole('list', { name: 'Dishes of order #42' }));
    expect(dishes.getAllByRole('listitem')).toHaveLength(5);
    expect(dishes.getByText('+2 more')).toBeInTheDocument();
  });

  it('leaves the dishes out of an answer that has none (an older order-service)', () => {
    renderList([delivered({ id: 42 })]);

    expect(screen.queryByRole('list', { name: 'Dishes of order #42' })).not.toBeInTheDocument();
  });

  it('shows the photo of each dish in the details', async () => {
    renderList([delivered({ id: 42 })]);

    await userEvent.click(screen.getByRole('button', { name: 'Show the details of order #42' }));

    const items = await screen.findByRole('region', { name: 'What was ordered' });
    await waitFor(() => expect(photosIn(items)).toContain('https://img.example.com/biryani.jpg'));
  });
});
