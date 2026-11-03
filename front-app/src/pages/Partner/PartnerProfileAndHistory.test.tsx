import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import PartnerDeliveriesPage from '@/pages/Partner/PartnerDeliveriesPage';
import PartnerDeliveryDetailsPage from '@/pages/Partner/PartnerDeliveryDetailsPage';
import PartnerProfilePage from '@/pages/Partner/PartnerProfilePage';
import { partnerService } from '@/services/partnerService';
import { chooseOption } from '@/test/chooseOption';
import { delivery, deliverySummary, pageOf, partner } from '@/test/fixtures';
import { renderPartnerPage } from '@/test/partnerTestUtils';

vi.mock('@/services/partnerService', () => ({
  partnerService: {
    getMe: vi.fn(),
    updateProfile: vi.fn(),
    getDeliveries: vi.fn(),
    getDelivery: vi.fn(),
    getCurrentDelivery: vi.fn(),
    acceptDelivery: vi.fn(),
    declineDelivery: vi.fn(),
    pickUpDelivery: vi.fn(),
  },
}));

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(partnerService.getMe).mockResolvedValue(partner());
  // Nothing being worked on unless a test says so
  vi.mocked(partnerService.getCurrentDelivery).mockResolvedValue(null);
});

describe('the partner profile page', () => {
  it('checks the vehicle and city, then saves them', async () => {
    const blank = partner({
      vehicleType: null,
      vehicleNumber: null,
      city: null,
      readyToWork: false,
    });
    vi.mocked(partnerService.getMe).mockResolvedValue(blank);
    vi.mocked(partnerService.updateProfile).mockResolvedValue(partner());
    renderPartnerPage(<PartnerProfilePage />, '/partner/profile');

    await userEvent.click(await screen.findByRole('button', { name: 'Save and continue' }));
    expect(await screen.findByText('Please choose your vehicle.')).toBeInTheDocument();
    expect(screen.getByText('Please enter the vehicle number.')).toBeInTheDocument();
    expect(screen.getByText('Please enter the city you deliver in.')).toBeInTheDocument();
    expect(partnerService.updateProfile).not.toHaveBeenCalled();

    await userEvent.click(screen.getByRole('radio', { name: 'Motorcycle' }));
    await userEvent.type(screen.getByLabelText('Vehicle number'), 'KA01AB1234');
    await userEvent.type(screen.getByLabelText('City'), '  Bengaluru ');
    await userEvent.click(screen.getByRole('button', { name: 'Save and continue' }));

    expect(partnerService.updateProfile).toHaveBeenCalledWith({
      vehicleType: 'MOTORCYCLE',
      vehicleNumber: 'KA01AB1234',
      city: 'Bengaluru',
    });
    expect(
      await screen.findByText('All set: go online to receive your first delivery.'),
    ).toBeInTheDocument();
  });

  it('shows the account as the platform knows it', async () => {
    vi.mocked(partnerService.getMe).mockResolvedValue(partner({ completedDeliveries: 120 }));
    renderPartnerPage(<PartnerProfilePage />, '/partner/profile');

    expect(await screen.findByText('ravi.rider@example.com')).toBeInTheDocument();
    expect(screen.getByText('120')).toBeInTheDocument();
    expect(screen.getByLabelText('City')).toHaveValue('Bengaluru');
    // Nothing changed yet: nothing to save
    expect(screen.getByRole('button', { name: 'Save changes' })).toBeDisabled();
  });
});

describe('the partner delivery history', () => {
  it('lists the deliveries newest first and filters them by outcome', async () => {
    vi.mocked(partnerService.getDeliveries).mockResolvedValue(
      pageOf([
        deliverySummary({
          id: 55,
          orderId: 42,
          status: 'DELIVERED',
          deliveredAt: '2026-10-08T11:00:00Z',
        }),
        deliverySummary({ id: 56, orderId: 43, status: 'CANCELLED', cashToCollect: 120 }),
      ]),
    );
    renderPartnerPage(<PartnerDeliveriesPage />, '/partner/deliveries');

    expect(await screen.findByRole('link', { name: 'Order #42' })).toHaveAttribute(
      'href',
      '/partner/deliveries/55',
    );
    expect(screen.getByRole('link', { name: 'Order #43' })).toHaveAttribute(
      'href',
      '/partner/deliveries/56',
    );
    // Grouped by day
    expect(screen.getByRole('heading', { name: /^8 Oct 2026/ })).toBeInTheDocument();
    expect(screen.getByText('₹120.00')).toBeInTheDocument();
    // The latest deliveries are read at once; the tabs, dates and search then work on them
    expect(partnerService.getDeliveries).toHaveBeenCalledTimes(1);
    expect(partnerService.getDeliveries).toHaveBeenCalledWith(undefined, 0, 100);

    await userEvent.click(screen.getByRole('tab', { name: /^Delivered\s\(1\)$/ }));

    expect(screen.getByRole('link', { name: 'Order #42' })).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Order #43' })).not.toBeInTheDocument();
    expect(partnerService.getDeliveries).toHaveBeenCalledTimes(1);
  });

  it('sums up the deliveries and narrows them down by a search', async () => {
    vi.mocked(partnerService.getDeliveries).mockResolvedValue(
      pageOf([
        deliverySummary({
          id: 55,
          orderId: 42,
          status: 'DELIVERED',
          restaurantName: 'Spice Garden',
          cashToCollect: 250,
          deliveredAt: '2026-10-08T11:00:00Z',
        }),
        deliverySummary({
          id: 56,
          orderId: 43,
          status: 'DELIVERED',
          restaurantName: 'Pizza Hub',
          cashToCollect: 0,
          deliveredAt: '2026-10-08T12:00:00Z',
        }),
        deliverySummary({ id: 57, orderId: 44, status: 'CANCELLED', restaurantName: 'Pizza Hub' }),
      ]),
    );
    renderPartnerPage(<PartnerDeliveriesPage />, '/partner/deliveries');

    const summary = await screen.findByRole('region', { name: 'Summary' });
    expect(within(summary).getByText('₹250.00')).toBeInTheDocument();
    expect(within(summary).getByText('1 cash order')).toBeInTheDocument();

    await userEvent.type(screen.getByLabelText('Search'), 'pizza');

    expect(await screen.findByText('2 deliveries')).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Order #42' })).not.toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Order #43' })).toBeInTheDocument();
    expect(within(summary).queryByText('₹250.00')).not.toBeInTheDocument();

    await userEvent.type(screen.getByLabelText('Search'), 'xyz');
    expect(await screen.findByText('No deliveries match these filters')).toBeInTheDocument();

    // One click brings back the search, the dates and the tab
    await userEvent.click(screen.getAllByRole('button', { name: 'Clear filters' })[0]);
    expect(await screen.findByText('3 deliveries')).toBeInTheDocument();
    expect(screen.getByLabelText('Search')).toHaveValue('');
    expect(screen.getByRole('link', { name: 'Order #42' })).toBeInTheDocument();
  });

  it('splits the deliveries into pages of ten, newest first', async () => {
    vi.mocked(partnerService.getDeliveries).mockResolvedValue(
      pageOf(
        Array.from({ length: 12 }, (_, index) =>
          deliverySummary({
            id: 100 + index,
            orderId: 200 + index,
            status: 'DELIVERED',
            deliveredAt: '2026-10-08T11:00:00Z',
          }),
        ),
      ),
    );
    renderPartnerPage(<PartnerDeliveriesPage />, '/partner/deliveries');

    expect(await screen.findByRole('link', { name: 'Order #200' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Order #209' })).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Order #210' })).not.toBeInTheDocument();
    expect(screen.getByText('Showing 1-10 of 12')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Previous page' })).toBeDisabled();

    await userEvent.click(screen.getByRole('button', { name: 'Next page' }));

    expect(screen.getByRole('link', { name: 'Order #210' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Order #211' })).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Order #200' })).not.toBeInTheDocument();
    expect(screen.getByText('Showing 11-12 of 12')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Next page' })).toBeDisabled();
    // Everything is read once: turning a page asks the API for nothing
    expect(partnerService.getDeliveries).toHaveBeenCalledTimes(1);
  });

  it('shows what is still to do on its own tab, and the oldest first on request', async () => {
    vi.mocked(partnerService.getDeliveries).mockResolvedValue(
      pageOf([
        deliverySummary({ id: 57, orderId: 44, status: 'PICKED_UP' }),
        deliverySummary({
          id: 56,
          orderId: 43,
          status: 'DELIVERED',
          assignedAt: '2026-10-08T10:31:00Z',
          deliveredAt: '2026-10-08T10:55:00Z',
        }),
        deliverySummary({ id: 55, orderId: 42, status: 'CANCELLED' }),
      ]),
    );
    renderPartnerPage(<PartnerDeliveriesPage />, '/partner/deliveries');

    const list = await screen.findByRole('link', { name: 'Order #44' });
    // The status in the partner's words, and how long the delivered one took
    expect(list.closest('li')).toHaveTextContent('On the way');
    expect(screen.getByRole('link', { name: 'Order #43' }).closest('li')).toHaveTextContent(
      'Took 24 min',
    );
    const orderOf = () =>
      screen.getAllByRole('link', { name: /^Order #/ }).map((link) => link.textContent);
    expect(orderOf()).toEqual(['Order #44', 'Order #43', 'Order #42']);

    await chooseOption(screen.getByLabelText('Order'), 'Oldest first');
    expect(orderOf()).toEqual(['Order #42', 'Order #43', 'Order #44']);

    await userEvent.click(screen.getByRole('tab', { name: /^Active\s\(1\)$/ }));
    expect(orderOf()).toEqual(['Order #44']);
  });

  it('puts the delivery being worked on first, with its stops and the next step, and sends that step', async () => {
    vi.mocked(partnerService.getCurrentDelivery).mockResolvedValue(
      delivery({ status: 'ACCEPTED', cashToCollect: 250, paymentMethod: 'CASH_ON_DELIVERY' }),
    );
    vi.mocked(partnerService.pickUpDelivery).mockResolvedValue(delivery({ status: 'PICKED_UP' }));
    vi.mocked(partnerService.getDeliveries).mockResolvedValue(
      pageOf([deliverySummary({ status: 'ACCEPTED' })]),
    );
    renderPartnerPage(<PartnerDeliveriesPage />, '/partner/deliveries');

    const panel = within(
      await screen.findByRole('region', { name: 'Delivery in progress: order #42' }),
    );
    expect(panel.getByText('Go to pickup')).toBeInTheDocument();
    expect(panel.getByText('Collect ₹250.00')).toBeInTheDocument();
    expect(panel.getByText('12 MG Road, Bengaluru 560001')).toBeInTheDocument();
    expect(
      panel.getByRole('link', { name: 'Navigate to Spice Garden (opens in a new tab)' }),
    ).toHaveAttribute('href', expect.stringContaining('google.com/maps'));
    expect(panel.getByRole('link', { name: 'Call Priya Sharma' })).toHaveAttribute(
      'href',
      'tel:+919800000007',
    );
    expect(panel.getByRole('link', { name: 'Details of order #42' })).toHaveAttribute(
      'href',
      '/partner/deliveries/55',
    );
    // Only the step the status allows
    expect(panel.queryByRole('button', { name: /Accept delivery/ })).not.toBeInTheDocument();
    expect(panel.queryByRole('button', { name: /Mark as delivered/ })).not.toBeInTheDocument();

    await userEvent.click(panel.getByRole('button', { name: /I have picked up the food/ }));

    expect(await screen.findByText('Picked up: take it to Priya Sharma.')).toBeInTheDocument();
    expect(partnerService.pickUpDelivery).toHaveBeenCalledTimes(1);
    expect(partnerService.pickUpDelivery).toHaveBeenCalledWith(55);
  });

  it('asks before declining an offer, and declines it with the reason given', async () => {
    vi.mocked(partnerService.getCurrentDelivery).mockResolvedValue(
      delivery({ status: 'ASSIGNED', assignedAt: new Date().toISOString(), acceptedAt: null }),
    );
    vi.mocked(partnerService.declineDelivery).mockResolvedValue();
    vi.mocked(partnerService.getDeliveries).mockResolvedValue(
      pageOf([deliverySummary({ status: 'ASSIGNED' })]),
    );
    renderPartnerPage(<PartnerDeliveriesPage />, '/partner/deliveries');

    const panel = within(
      await screen.findByRole('region', { name: 'Delivery in progress: order #42' }),
    );
    expect(panel.getByText('New offer')).toBeInTheDocument();
    expect(panel.getByText(/Accept within/)).toBeInTheDocument();
    expect(panel.getByRole('button', { name: /Accept delivery/ })).toBeEnabled();

    await userEvent.click(panel.getByRole('button', { name: /Decline/ }));
    const dialog = within(await screen.findByRole('dialog', { name: 'Decline this delivery?' }));
    expect(partnerService.declineDelivery).not.toHaveBeenCalled();
    await userEvent.type(dialog.getByLabelText('Reason (optional)'), 'Too far');
    await userEvent.click(dialog.getByRole('button', { name: 'Decline' }));

    expect(
      await screen.findByText('Declined: the delivery goes to another partner.'),
    ).toBeInTheDocument();
    expect(partnerService.declineDelivery).toHaveBeenCalledWith(55, 'Too far');
    expect(partnerService.acceptDelivery).not.toHaveBeenCalled();
  });

  it('shows one delivery with its history, and the next step while it is in progress', async () => {
    vi.mocked(partnerService.getDelivery).mockResolvedValue(delivery({ status: 'ACCEPTED' }));
    renderPartnerPage(
      <PartnerDeliveryDetailsPage />,
      '/partner/deliveries/:deliveryId',
      '/partner/deliveries/55',
    );

    expect(
      await screen.findByRole('heading', { name: 'Delivery #55', level: 1 }),
    ).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'I have picked up the food' })).toBeInTheDocument();
    expect(screen.getByText('History')).toBeInTheDocument();
    expect(partnerService.getDelivery).toHaveBeenCalledWith(55);
  });

  it('sums up a delivered order: outcome, time, cash, route and every step with its time', async () => {
    const base = delivery();
    vi.mocked(partnerService.getDelivery).mockResolvedValue(
      delivery({
        status: 'DELIVERED',
        paymentMethod: 'CASH_ON_DELIVERY',
        cashToCollect: 292,
        cashCollected: 292,
        pickedUpAt: '2026-10-08T10:40:00Z',
        deliveredAt: '2026-10-08T10:50:00Z',
        timeline: [
          ...base.timeline,
          {
            ...base.timeline[2],
            event: 'PICKED_UP',
            status: 'PICKED_UP',
            at: '2026-10-08T10:40:00Z',
          },
          {
            ...base.timeline[2],
            event: 'DELIVERED',
            status: 'DELIVERED',
            note: 'Delivered, cash collected: 292.00',
            at: '2026-10-08T10:50:00Z',
          },
        ],
      }),
    );
    renderPartnerPage(
      <PartnerDeliveryDetailsPage />,
      '/partner/deliveries/:deliveryId',
      '/partner/deliveries/55',
    );

    const hero = await screen.findByRole('region', { name: 'Delivery #55' });
    expect(within(hero).getByText('Food ready to door')).toBeInTheDocument();
    // 10:30 food ready -> 10:50 delivered
    expect(within(hero).getByText('20 min')).toBeInTheDocument();
    expect(within(hero).getByText('Cash collected')).toBeInTheDocument();
    expect(within(hero).getByText('₹292.00')).toBeInTheDocument();
    expect(
      within(screen.getByRole('region', { name: 'Route' })).getByText('Spice Garden'),
    ).toBeInTheDocument();

    const history = screen.getByRole('region', { name: 'History' });
    const steps = within(history).getAllByRole('listitem');
    expect(steps.map((step) => step.querySelector('p')?.textContent)).toEqual([
      'Food ready',
      'Offered',
      'Accepted',
      'Picked up',
      'Delivered',
    ]);
    // 10:32 accepted -> 10:40 picked up
    expect(within(steps[2]).getByText('+8 min to the next step')).toBeInTheDocument();
    expect(within(steps[4]).getByText('Delivered, cash collected: 292.00')).toBeInTheDocument();
    // Nothing left to do
    expect(
      screen.queryByRole('button', { name: 'I have picked up the food' }),
    ).not.toBeInTheDocument();
  });

  it('says why a delivery was cancelled', async () => {
    const base = delivery();
    vi.mocked(partnerService.getDelivery).mockResolvedValue(
      delivery({
        status: 'CANCELLED',
        cancelledAt: '2026-10-08T10:35:00Z',
        cancellationReason: 'The customer cancelled the order',
        timeline: [
          ...base.timeline,
          {
            ...base.timeline[0],
            event: 'CANCELLED',
            status: 'CANCELLED',
            at: '2026-10-08T10:35:00Z',
          },
        ],
      }),
    );
    renderPartnerPage(
      <PartnerDeliveryDetailsPage />,
      '/partner/deliveries/:deliveryId',
      '/partner/deliveries/55',
    );

    const hero = await screen.findByRole('region', { name: 'Delivery #55' });
    expect(within(hero).getByText('The customer cancelled the order')).toBeInTheDocument();
    const steps = within(screen.getByRole('region', { name: 'History' })).getAllByRole('listitem');
    expect(steps.at(-1)).toHaveTextContent('Cancelled');
  });
});
