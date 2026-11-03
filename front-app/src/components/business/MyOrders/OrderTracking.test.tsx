import { render, screen, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { DeliveryTracking } from '@/components/business/MyOrders/DeliveryTracking';
import { OrderHistory } from '@/components/business/MyOrders/OrderHistory';
import { OrderTrackingHero } from '@/components/business/MyOrders/OrderTrackingHero';
import { LiveStatusContext } from '@/realtime/liveStatusContext';
import type { LiveStatus } from '@/realtime/liveConnection';
import { orderDetails } from '@/test/fixtures';
import type { OrderStatus, TimelineEntry } from '@/types/order';
import type { OrderTracking } from '@/types/shopping';

const NOW = new Date('2026-10-09T10:30:00Z');

const entry = (status: OrderStatus, minute: number, note: string | null = null): TimelineEntry => ({
  status,
  actorType: 'SYSTEM',
  actorUserId: null,
  note,
  at: new Date(Date.parse('2026-10-09T10:00:00Z') + minute * 60_000).toISOString(),
});

const order = (status: OrderStatus, timeline: TimelineEntry[]) =>
  orderDetails({ status, createdAt: '2026-10-09T10:00:00Z', timeline });

const renderHero = (status: OrderStatus, timeline: TimelineEntry[], live: LiveStatus = 'closed') =>
  render(
    <MemoryRouter>
      <LiveStatusContext.Provider value={live}>
        <OrderTrackingHero order={order(status, timeline)} />
      </LiveStatusContext.Provider>
    </MemoryRouter>,
  );

describe('OrderTrackingHero', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date', 'setInterval', 'clearInterval'] });
    vi.setSystemTime(NOW);
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('says where the order is, what is happening, and how long it has been going', () => {
    renderHero('PREPARING', [entry('PLACED', 0), entry('ACCEPTED', 5), entry('PREPARING', 8)]);

    expect(screen.getByRole('heading', { name: 'Being prepared' })).toBeInTheDocument();
    expect(screen.getByText('Your food is being prepared.')).toBeInTheDocument();
    expect(screen.getByText('Placed 30 min ago')).toBeInTheDocument();
  });

  it('fills the route up to the current step and marks the steps before it done', () => {
    renderHero('PREPARING', [entry('PLACED', 0), entry('ACCEPTED', 5), entry('PREPARING', 8)]);

    const steps = within(screen.getByRole('list', { name: 'Order progress' })).getAllByRole(
      'listitem',
    );
    expect(steps.map((step) => step.textContent)).toEqual([
      expect.stringContaining('Placed (done)'),
      expect.stringContaining('Accepted (done)'),
      expect.stringContaining('Preparing (now)'),
      expect.stringContaining('Ready (next)'),
      expect.stringContaining('On the way (next)'),
      expect.stringContaining('Delivered (next)'),
    ]);
    expect(steps[2]).toHaveAttribute('aria-current', 'step');
  });

  it('says LIVE while the live connection is open, RECONNECTING while it comes back, and AUTO-REFRESH without one', () => {
    const timeline = [entry('PLACED', 0)];
    const { rerender } = renderHero('PLACED', timeline, 'open');
    expect(screen.getByText('LIVE')).toBeInTheDocument();

    rerender(
      <LiveStatusContext.Provider value="reconnecting">
        <OrderTrackingHero order={order('PLACED', timeline)} />
      </LiveStatusContext.Provider>,
    );
    expect(screen.getByText('RECONNECTING')).toBeInTheDocument();

    rerender(
      <LiveStatusContext.Provider value="closed">
        <OrderTrackingHero order={order('PLACED', timeline)} />
      </LiveStatusContext.Provider>,
    );
    expect(screen.getByText('AUTO-REFRESH')).toBeInTheDocument();
  });

  it('celebrates a delivered order with how long it took, and no LIVE badge', () => {
    renderHero('DELIVERED', [
      entry('PLACED', 0),
      entry('PREPARING', 8),
      entry('OUT_FOR_DELIVERY', 25),
      entry('DELIVERED', 38),
    ]);

    expect(screen.getByText('Delivered in 38 min')).toBeInTheDocument();
    expect(screen.getByText('Delivered. Enjoy your meal!')).toBeInTheDocument();
    expect(screen.queryByText('LIVE')).not.toBeInTheDocument();
    expect(screen.queryByText('AUTO-REFRESH')).not.toBeInTheDocument();
  });

  it('offers the live map while the order is being prepared or delivered', () => {
    renderHero('PREPARING', [entry('PLACED', 0), entry('ACCEPTED', 2), entry('PREPARING', 5)]);

    expect(screen.getByRole('link', { name: 'Track on map' })).toHaveAttribute(
      'href',
      '/customer/orders/42/track',
    );
  });
});

describe('DeliveryTracking', () => {
  const tracking = (overrides: Partial<OrderTracking> = {}): OrderTracking => ({
    orderId: 42,
    deliveryId: 5,
    status: 'PICKED_UP',
    restaurantName: 'Biryani House',
    dropoffAddress: null,
    partnerName: 'Ravi Kumar',
    partnerPhone: '+919800000031',
    vehicleType: 'MOTORCYCLE',
    cashToCollect: 0,
    currency: 'INR',
    readyAt: '2026-10-09T10:20:00Z',
    acceptedAt: '2026-10-09T10:22:00Z',
    pickedUpAt: '2026-10-09T10:30:00Z',
    deliveredAt: null,
    cancelledAt: null,
    steps: [
      {
        status: 'ACCEPTED',
        note: 'Ravi is on the way to the restaurant',
        at: '2026-10-09T10:22:00Z',
      },
      { status: 'PICKED_UP', note: 'Picked up from Biryani House', at: '2026-10-09T10:30:00Z' },
    ],
    ...overrides,
  });

  it('shows the partner, the stage the delivery is at, and its steps', () => {
    render(<DeliveryTracking tracking={tracking()} />);

    expect(screen.getByText('RK')).toBeInTheDocument();
    expect(screen.getByText(/Motorcycle/)).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Call Ravi' })).toHaveAttribute(
      'href',
      'tel:+919800000031',
    );
    const stages = within(screen.getByRole('list', { name: 'Delivery stages' })).getAllByRole(
      'listitem',
    );
    expect(stages.map((stage) => stage.textContent)).toEqual([
      expect.stringContaining('Finding a partner (done)'),
      expect.stringContaining('To the restaurant (done)'),
      expect.stringContaining('On the way to you (now)'),
      expect.stringContaining('Delivered (next)'),
    ]);
    expect(screen.getByText('Picked up from Biryani House')).toBeInTheDocument();
  });

  it('says it is still looking for a partner, and reminds of the cash to have ready', () => {
    render(
      <DeliveryTracking
        tracking={tracking({
          status: 'PENDING',
          partnerName: null,
          partnerPhone: null,
          vehicleType: null,
          cashToCollect: 562.9,
          steps: [],
        })}
      />,
    );

    expect(screen.getByText('Finding a delivery partner')).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: /Call/ })).not.toBeInTheDocument();
    expect(screen.getByText(/Cash on delivery: please have/)).toBeInTheDocument();
  });

  it('shows a cancelled delivery without the stages', () => {
    render(<DeliveryTracking tracking={tracking({ status: 'CANCELLED', steps: [] })} />);

    expect(screen.queryByRole('list', { name: 'Delivery stages' })).not.toBeInTheDocument();
  });
});

describe('OrderHistory', () => {
  it('lists everything that happened, newest first, with the newest marked', () => {
    render(
      <OrderHistory
        timeline={[
          entry('PLACED', 0, 'Payment 12 received'),
          entry('ACCEPTED', 5),
          entry('CANCELLED', 9),
        ]}
      />,
    );

    const items = screen.getAllByRole('listitem');
    expect(items[0]).toHaveTextContent('Latest');
    expect(items[0]).toHaveTextContent('Cancelled');
    expect(items[2]).toHaveTextContent('Payment 12 received');
    expect(screen.getAllByText('Latest')).toHaveLength(1);
  });
});
