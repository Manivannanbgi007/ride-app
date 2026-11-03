import { act, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Route, Routes } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { ApiError } from '@/api/apiErrorHandler';
import LiveTrackingPage from '@/pages/Storefront/LiveTrackingPage';
import { liveTrackingService } from '@/services/liveTrackingService';
import { simulateLiveTracking } from '@/services/mock/liveTrackingMock';
import { orderService } from '@/services/orderService';
import { makeStore } from '@/store/store';
import { orderDetails, priya } from '@/test/fixtures';
import { renderWithProviders } from '@/test/renderWithProviders';
import type { OrderStatus, TimelineEntry } from '@/types/order';
import type { OrderTracking } from '@/types/shopping';
import { logger } from '@/utils/logger';
import { QUERY_KEYS } from '@/utils/queryKeys';

vi.mock('@/services/liveTrackingService', () => ({
  liveTrackingService: { getLiveTracking: vi.fn() },
}));
vi.mock('@/services/orderService', () => ({ orderService: { getMyOrder: vi.fn() } }));

const NOW = Date.now();
const iso = (msAgo: number) => new Date(NOW - msAgo).toISOString();

/** The real delivery of the order (what delivery-service saved), from which the simulated ride is drawn */
function delivery(status: OrderTracking['status']): OrderTracking {
  return {
    orderId: 42,
    deliveryId: 11,
    status,
    restaurantName: 'Biryani House',
    dropoffAddress: null,
    partnerName: 'Ravi Kumar',
    partnerPhone: '+919845012345',
    vehicleType: 'MOTORCYCLE',
    cashToCollect: 0,
    currency: 'INR',
    readyAt: iso(300_000),
    acceptedAt: iso(240_000),
    pickedUpAt: status === 'PICKED_UP' || status === 'DELIVERED' ? iso(60_000) : null,
    deliveredAt: status === 'DELIVERED' ? iso(5_000) : null,
    cancelledAt: null,
    steps: [],
  };
}
const rideOf = (status: OrderTracking['status']) => simulateLiveTracking(delivery(status), NOW);

/** The order as order-service saved it: every step up to `status` in its timeline */
function order(status: OrderStatus, extra: Partial<Parameters<typeof orderDetails>[0]> = {}) {
  const steps: OrderStatus[] = [
    'PLACED',
    'ACCEPTED',
    'PREPARING',
    'READY_FOR_PICKUP',
    'OUT_FOR_DELIVERY',
    'DELIVERED',
  ];
  const reached = steps.slice(0, Math.max(steps.indexOf(status), 0) + 1);
  const timeline: TimelineEntry[] = reached.map((step, index) => ({
    status: step,
    actorType: 'SYSTEM',
    actorUserId: null,
    note: null,
    at: iso(600_000 - index * 60_000),
  }));
  return orderDetails({ id: 42, status, timeline, ...extra });
}

const renderTracking = () =>
  renderWithProviders(
    <Routes>
      <Route path="/customer/orders/:orderId/track" element={<LiveTrackingPage />} />
    </Routes>,
    {
      route: '/customer/orders/42/track',
      store: makeStore({ session: { adminUser: null, customer: priya } }),
    },
  );

/** The delivery status steps as read out: "Ready (now)", "Preparing (done)", ... */
async function statusSteps() {
  const status = await screen.findByRole('region', { name: 'Delivery status' });
  return within(status)
    .getAllByRole('listitem')
    .map((step) => step.textContent?.replace(/\d{1,2}:\d{2}\s?(am|pm|AM|PM)?$/, '').trim());
}

beforeEach(() => {
  vi.clearAllMocks();
  vi.spyOn(console, 'error').mockImplementation(() => undefined);
});

describe('LiveTrackingPage', () => {
  it('shows when the food arrives, the trip, and the real partner to call', async () => {
    vi.mocked(orderService.getMyOrder).mockResolvedValue(order('OUT_FOR_DELIVERY'));
    vi.mocked(liveTrackingService.getLiveTracking).mockResolvedValue(rideOf('PICKED_UP'));
    renderTracking();

    expect(await screen.findByText('Your food is on the way')).toBeInTheDocument();
    expect(liveTrackingService.getLiveTracking).toHaveBeenCalledWith(42);
    expect(orderService.getMyOrder).toHaveBeenCalledWith(42);
    expect(screen.getByText(/^Arriving around .* to go$/)).toBeInTheDocument();

    const trip = screen.getByRole('region', { name: 'The trip' });
    expect(within(trip).getByText('Biryani House')).toBeInTheDocument();
    expect(within(trip).getAllByRole('listitem')[0]).toHaveTextContent('Picked up');

    const partner = screen.getByRole('region', { name: 'Your delivery partner' });
    expect(within(partner).getByRole('link', { name: 'Call Ravi Kumar' })).toHaveAttribute(
      'href',
      'tel:+919845012345',
    );
  });

  it('shows the saved order status step by step, never the simulated ride', async () => {
    vi.mocked(orderService.getMyOrder).mockResolvedValue(order('READY_FOR_PICKUP'));
    vi.mocked(liveTrackingService.getLiveTracking).mockResolvedValue(rideOf('ACCEPTED'));
    renderTracking();

    expect(await statusSteps()).toEqual([
      'Placed (done)',
      'Accepted (done)',
      'Preparing (done)',
      'Ready (now)',
      'On the way (to come)',
      'Delivered (to come)',
    ]);
  });

  it('once the food is picked up, never falls back to "being prepared" (a late, older answer)', async () => {
    const warn = vi.spyOn(logger, 'warn').mockImplementation(() => undefined);
    vi.mocked(orderService.getMyOrder).mockResolvedValue(order('OUT_FOR_DELIVERY'));
    vi.mocked(liveTrackingService.getLiveTracking).mockResolvedValue(rideOf('PICKED_UP'));
    const { queryClient } = renderTracking();
    expect((await statusSteps())[4]).toBe('On the way (now)');

    // An outdated answer (a slow request that started before the pickup) arrives last
    vi.mocked(orderService.getMyOrder).mockResolvedValue(order('PREPARING'));
    vi.mocked(liveTrackingService.getLiveTracking).mockResolvedValue(rideOf('ACCEPTED'));
    await act(() => queryClient.refetchQueries());

    expect(await statusSteps()).toContain('On the way (now)');
    expect(await statusSteps()).toContain('Preparing (done)');
    expect(screen.getByText('Your food is on the way')).toBeInTheDocument();
    expect(warn).toHaveBeenCalledWith(
      'Ignored an outdated order status: PREPARING arrived after OUT_FOR_DELIVERY',
    );
  });

  it('stays delivered: after a reload, and whatever arrives later', async () => {
    vi.spyOn(logger, 'warn').mockImplementation(() => undefined);
    vi.mocked(orderService.getMyOrder).mockResolvedValue(order('DELIVERED'));
    vi.mocked(liveTrackingService.getLiveTracking).mockResolvedValue(rideOf('DELIVERED'));
    const first = renderTracking();

    expect(await screen.findByText('Your food has arrived')).toBeInTheDocument();
    expect(screen.getByText(/^Arrived at /)).toBeInTheDocument();
    expect((await statusSteps()).every((step) => step?.endsWith('(done)'))).toBe(true);
    expect(screen.queryByRole('region', { name: 'Your delivery partner' })).not.toBeInTheDocument();

    vi.mocked(orderService.getMyOrder).mockResolvedValue(order('PREPARING'));
    vi.mocked(liveTrackingService.getLiveTracking).mockResolvedValue(rideOf('ACCEPTED'));
    await act(() => first.queryClient.refetchQueries());
    expect(screen.getByText('Your food has arrived')).toBeInTheDocument();

    // A reload starts from nothing and asks the server again: the saved status is delivered
    first.unmount();
    vi.mocked(orderService.getMyOrder).mockResolvedValue(order('DELIVERED'));
    vi.mocked(liveTrackingService.getLiveTracking).mockResolvedValue(rideOf('DELIVERED'));
    renderTracking();
    expect(await screen.findByText('Your food has arrived')).toBeInTheDocument();
    expect((await statusSteps()).at(-1)).toBe('Delivered (done)');
  });

  it('follows the delivery when it is a moment ahead of the order (the event is on its way)', async () => {
    vi.mocked(orderService.getMyOrder).mockResolvedValue(order('READY_FOR_PICKUP'));
    vi.mocked(liveTrackingService.getLiveTracking).mockResolvedValue(rideOf('PICKED_UP'));
    renderTracking();

    expect((await statusSteps())[4]).toBe('On the way (now)');
  });

  it('draws the restaurant, the home and the partner on OpenStreetMap, and labels a simulation', async () => {
    vi.mocked(orderService.getMyOrder).mockResolvedValue(order('READY_FOR_PICKUP'));
    vi.mocked(liveTrackingService.getLiveTracking).mockResolvedValue(rideOf('ACCEPTED'));
    renderTracking();

    const map = await screen.findByRole('region', { name: 'Live map of your delivery' });
    expect(await within(map).findByTitle('Biryani House')).toBeInTheDocument();
    expect(within(map).getByTitle('Your delivery address')).toBeInTheDocument();
    expect(within(map).getByTitle('Ravi Kumar, your delivery partner')).toBeInTheDocument();
    expect(within(map).getByRole('link', { name: 'OpenStreetMap' })).toHaveAttribute(
      'href',
      'https://www.openstreetmap.org/copyright',
    );
    // A simulated ride never passes for the partner's real position
    expect(within(map).getByText('Simulated ride')).toBeInTheDocument();
    expect(
      screen.getByText(/Sample positions and an estimated time for demonstration/),
    ).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Replay' })).not.toBeInTheDocument();
  });

  it('shows real GPS positions without the simulation labels', async () => {
    vi.mocked(orderService.getMyOrder).mockResolvedValue(order('READY_FOR_PICKUP'));
    vi.mocked(liveTrackingService.getLiveTracking).mockResolvedValue({
      ...rideOf('ACCEPTED'),
      source: 'GPS',
    });
    renderTracking();

    expect(await screen.findByText(/^Ravi is (heading to|picking up)/)).toBeInTheDocument();
    expect(screen.queryByText('Simulated ride')).not.toBeInTheDocument();
    expect(screen.queryByText('Simulated')).not.toBeInTheDocument();
  });

  it('shows the real steps while no partner is on the way yet', async () => {
    vi.mocked(orderService.getMyOrder).mockResolvedValue(order('PREPARING'));
    vi.mocked(liveTrackingService.getLiveTracking).mockRejectedValue(
      new ApiError({
        kind: 'notFound',
        status: 404,
        userMessage: 'Not found',
        technicalMessage: 'no delivery',
      }),
    );
    renderTracking();

    expect(await screen.findByText('No delivery partner on the way yet')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'See your order' })).toHaveAttribute(
      'href',
      '/customer/orders/42',
    );
    expect((await statusSteps())[2]).toBe('Preparing (now)');
  });

  it('says so when the order was cancelled, without a live map, and stops asking for one', async () => {
    vi.mocked(orderService.getMyOrder).mockResolvedValue(
      order('PREPARING', { status: 'CANCELLED', cancellationReason: 'Kitchen closed early' }),
    );
    const { queryClient } = renderTracking();

    expect(await screen.findByText('This order was cancelled')).toBeInTheDocument();
    expect(screen.getByText('Kitchen closed early')).toBeInTheDocument();
    expect(
      screen.queryByRole('region', { name: 'Live map of your delivery' }),
    ).not.toBeInTheDocument();
    // Asked at most once, together with the order (before it was known to be cancelled), then switched off
    expect(vi.mocked(liveTrackingService.getLiveTracking).mock.calls.length).toBeLessThanOrEqual(1);
    const live = queryClient
      .getQueryCache()
      .find({ queryKey: QUERY_KEYS.customer.liveTracking(7, 42) });
    await waitFor(() => expect(live?.isDisabled()).toBe(true));
  });

  it('offers a retry when the order cannot be loaded', async () => {
    vi.mocked(orderService.getMyOrder).mockRejectedValue(
      new ApiError({
        kind: 'server',
        status: 500,
        userMessage: 'Server error',
        technicalMessage: '500',
      }),
    );
    vi.mocked(liveTrackingService.getLiveTracking).mockResolvedValue(rideOf('ACCEPTED'));
    renderTracking();

    expect(await screen.findByRole('button', { name: /try again/i })).toBeInTheDocument();
  });
});

describe('LiveTrackingPage, around the map', () => {
  const notFound = () =>
    new ApiError({
      kind: 'notFound',
      status: 404,
      userMessage: 'Not found',
      technicalMessage: 'no delivery',
    });

  it('sums up the order: dishes, total, payment and address, with the way to details and support', async () => {
    vi.mocked(orderService.getMyOrder).mockResolvedValue(order('OUT_FOR_DELIVERY'));
    vi.mocked(liveTrackingService.getLiveTracking).mockResolvedValue(rideOf('PICKED_UP'));
    renderTracking();

    const summary = await screen.findByRole('region', { name: 'Your order' });
    expect(within(summary).getByRole('heading', { name: 'Order #42' })).toBeInTheDocument();
    expect(within(summary).getByText('Chicken Biryani')).toBeInTheDocument();
    expect(within(summary).getByText('2×')).toBeInTheDocument();
    expect(within(summary).getByText(/562\.90/)).toBeInTheDocument();
    expect(within(summary).getByText('Paid')).toBeInTheDocument();
    expect(within(summary).getByText(/12 MG Road, Bengaluru 560001/)).toBeInTheDocument();

    const more = screen.getByRole('navigation', { name: 'More about this order' });
    expect(within(more).getByRole('link', { name: /Order details/ })).toHaveAttribute(
      'href',
      '/customer/orders/42',
    );
    expect(within(more).getByRole('link', { name: /Help & support/ })).toHaveAttribute(
      'href',
      '/customer/support',
    );
  });

  it('folds a long order after three dishes and opens it on request', async () => {
    const items = ['Paneer Tikka', 'Garlic Naan', 'Dal Makhani', 'Jeera Rice', 'Gulab Jamun'].map(
      (name, index) => ({
        menuItemId: index + 1,
        name,
        foodType: 'VEG',
        unitPrice: 100,
        quantity: 1,
        lineTotal: 100,
      }),
    );
    vi.mocked(orderService.getMyOrder).mockResolvedValue(order('OUT_FOR_DELIVERY', { items }));
    vi.mocked(liveTrackingService.getLiveTracking).mockResolvedValue(rideOf('PICKED_UP'));
    renderTracking();

    const dishes = await screen.findByRole('list', { name: 'Ordered dishes' });
    expect(within(dishes).getAllByRole('listitem')).toHaveLength(3);
    const toggle = screen.getByRole('button', { name: /Show all 5 dishes/ });
    expect(toggle).toHaveAttribute('aria-expanded', 'false');

    await userEvent.click(toggle);

    expect(within(dishes).getAllByRole('listitem')).toHaveLength(5);
    expect(screen.getByRole('button', { name: /Show fewer/ })).toHaveAttribute(
      'aria-expanded',
      'true',
    );
  });

  it('shows the arrival time from the delivery in the status band, with the order and restaurant', async () => {
    vi.mocked(orderService.getMyOrder).mockResolvedValue(order('OUT_FOR_DELIVERY'));
    const ride = rideOf('PICKED_UP');
    vi.mocked(liveTrackingService.getLiveTracking).mockResolvedValue(ride);
    renderTracking();

    const band = await screen.findByRole('region', { name: 'Order status' });
    expect(within(band).getByRole('heading', { level: 1 })).toHaveTextContent(
      'Order #42 · Biryani House',
    );
    expect(within(band).getByText('Arriving in')).toBeInTheDocument();
    expect(within(band).getByText(String(ride.etaMinutes))).toBeInTheDocument();
    expect(
      within(screen.getByRole('region', { name: 'Your delivery partner' })).getByText(
        'Picked up · on the way to you',
      ),
    ).toBeInTheDocument();
  });

  it('says who delivered it once the food has arrived', async () => {
    vi.spyOn(logger, 'warn').mockImplementation(() => undefined);
    vi.mocked(orderService.getMyOrder).mockResolvedValue(order('DELIVERED'));
    vi.mocked(liveTrackingService.getLiveTracking).mockResolvedValue(rideOf('DELIVERED'));
    renderTracking();

    const trip = await screen.findByRole('region', { name: 'The trip' });
    expect(within(trip).getByText('Delivered by Ravi')).toBeInTheDocument();
  });

  it('while the food is ready but no partner has accepted, says a partner is being found, with no name or time', async () => {
    vi.mocked(orderService.getMyOrder).mockResolvedValue(order('READY_FOR_PICKUP'));
    vi.mocked(liveTrackingService.getLiveTracking).mockRejectedValue(notFound());
    renderTracking();

    const partner = await screen.findByRole('region', { name: 'Your delivery partner' });
    expect(within(partner).getByText('Finding a delivery partner')).toBeInTheDocument();
    expect(within(partner).queryByRole('link')).not.toBeInTheDocument();
    expect(screen.queryByText('Arriving in')).not.toBeInTheDocument();
    expect(screen.getByText(/^Placed at /)).toBeInTheDocument();
  });

  it('when the live position is missing on the way, says so instead of drawing a guessed one', async () => {
    vi.mocked(orderService.getMyOrder).mockResolvedValue(order('OUT_FOR_DELIVERY'));
    vi.mocked(liveTrackingService.getLiveTracking).mockRejectedValue(notFound());
    renderTracking();

    expect(await screen.findByText('Live location is not available right now')).toBeInTheDocument();
    expect(
      screen.queryByRole('region', { name: 'Live map of your delivery' }),
    ).not.toBeInTheDocument();
    expect(screen.queryByText('Arriving in')).not.toBeInTheDocument();
    expect((await statusSteps())[4]).toBe('On the way (now)');
  });
});
