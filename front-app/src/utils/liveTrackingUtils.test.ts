import { describe, expect, it } from 'vitest';

import { orderDetails } from '@/test/fixtures';
import type { OrderStatus } from '@/types/order';
import { isSimulated, trackedOrderStatus, trackingSteps } from '@/utils/liveTrackingUtils';

const statesOf = (status: OrderStatus, phase?: Parameters<typeof trackingSteps>[1]) =>
  trackingSteps(orderDetails({ status }), phase).map((step) => `${step.status}:${step.state}`);

describe('the delivery status beside the live map', () => {
  it('follows the saved order status, step by step', () => {
    expect(statesOf('PREPARING')).toEqual([
      'PLACED:done',
      'ACCEPTED:done',
      'PREPARING:current',
      'READY_FOR_PICKUP:upcoming',
      'OUT_FOR_DELIVERY:upcoming',
      'DELIVERED:upcoming',
    ]);
    expect(statesOf('OUT_FOR_DELIVERY')).toEqual([
      'PLACED:done',
      'ACCEPTED:done',
      'PREPARING:done',
      'READY_FOR_PICKUP:done',
      'OUT_FOR_DELIVERY:current',
      'DELIVERED:upcoming',
    ]);
    // Delivered: every step done, none current
    expect(statesOf('DELIVERED').every((step) => step.endsWith(':done'))).toBe(true);
  });

  it('is never behind the live delivery (order-service hears of a pickup a moment later)', () => {
    expect(trackedOrderStatus('READY_FOR_PICKUP', 'TO_CUSTOMER')).toBe('OUT_FOR_DELIVERY');
    expect(trackedOrderStatus('OUT_FOR_DELIVERY', 'DELIVERED')).toBe('DELIVERED');
    expect(statesOf('READY_FOR_PICKUP', 'TO_CUSTOMER')[4]).toBe('OUT_FOR_DELIVERY:current');
  });

  it('never goes back because of the live delivery, and leaves a cancelled order as it is', () => {
    // The map still shows the partner at the restaurant: the order's own later status counts
    expect(trackedOrderStatus('DELIVERED', 'AT_RESTAURANT')).toBe('DELIVERED');
    expect(trackedOrderStatus('OUT_FOR_DELIVERY', 'TO_RESTAURANT')).toBe('OUT_FOR_DELIVERY');
    expect(trackedOrderStatus('PREPARING', undefined)).toBe('PREPARING');
    expect(trackedOrderStatus('CANCELLED', 'TO_CUSTOMER')).toBe('CANCELLED');
  });

  it('shows when each step was reached, from the order timeline', () => {
    const steps = trackingSteps(orderDetails({ status: 'PLACED' }));
    expect(steps[0]).toEqual({ status: 'PLACED', at: '2026-10-08T10:16:00Z', state: 'current' });
  });

  it('tells simulated positions from real GPS ones (GPS when the backend says nothing)', () => {
    expect(isSimulated({ source: 'SIMULATED' })).toBe(true);
    expect(isSimulated({ source: 'GPS' })).toBe(false);
    expect(isSimulated({})).toBe(false);
  });
});
