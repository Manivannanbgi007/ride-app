import { afterEach, describe, expect, it, vi } from 'vitest';

import { logger } from '@/utils/logger';
import {
  deliveryStatusProgress,
  forwardOnly,
  livePhaseProgress,
  orderStatusProgress,
} from '@/utils/statusProgressUtils';

const keepOrder = forwardOnly(orderStatusProgress, 'order status');
const keepDelivery = forwardOnly(deliveryStatusProgress, 'delivery status');
const keepPhase = forwardOnly(livePhaseProgress, 'live tracking phase');

afterEach(() => {
  vi.restoreAllMocks();
});

describe('forward-only answers (no status ever moves backward)', () => {
  it('takes a newer order status, and ignores (and logs) an older one that arrives late', () => {
    const warn = vi.spyOn(logger, 'warn').mockImplementation(() => undefined);
    const preparing = { id: 25, status: 'PREPARING' as const, updatedAt: 'a' };
    const pickedUp = { id: 25, status: 'OUT_FOR_DELIVERY' as const, updatedAt: 'b' };

    expect(keepOrder(preparing, pickedUp)).toEqual(pickedUp);
    // The slow answer of an earlier request lands after the newer one: the screen keeps "on the way"
    expect(keepOrder(pickedUp, preparing)).toBe(pickedUp);
    expect(warn).toHaveBeenCalledWith(
      'Ignored an outdated order status: PREPARING arrived after OUT_FOR_DELIVERY',
    );
  });

  it('keeps delivered (and cancelled) for good', () => {
    vi.spyOn(logger, 'warn').mockImplementation(() => undefined);
    const delivered = { status: 'DELIVERED' as const };
    const cancelled = { status: 'CANCELLED' as const };

    expect(keepOrder(delivered, { status: 'OUT_FOR_DELIVERY' })).toBe(delivered);
    expect(keepOrder(delivered, { status: 'PREPARING' })).toBe(delivered);
    expect(keepOrder(delivered, cancelled)).toBe(delivered);
    expect(keepOrder(cancelled, { status: 'PLACED' })).toBe(cancelled);
    // A cancellation before the pickup is a real step forward
    expect(keepOrder({ status: 'PLACED' }, cancelled)).toEqual(cancelled);
  });

  it('takes the same status again with its new details, keeping unchanged parts as they were', () => {
    const before = {
      status: 'OUT_FOR_DELIVERY' as const,
      timeline: [{ at: '1' }],
      partner: { name: 'Ravi' },
    };
    const after = {
      status: 'OUT_FOR_DELIVERY' as const,
      timeline: [{ at: '1' }, { at: '2' }],
      partner: { name: 'Ravi' },
    };

    const kept = keepOrder(before, after) as typeof after;
    expect(kept).toEqual(after);
    expect(kept.partner).toBe(before.partner);
  });

  it('takes the first answer as it is', () => {
    const first = { status: 'PLACED' as const };
    expect(keepOrder(undefined, first)).toBe(first);
  });

  it('lets a delivery go back and forth before the pickup, never after it', () => {
    vi.spyOn(logger, 'warn').mockImplementation(() => undefined);
    const accepted = { status: 'ACCEPTED' as const };
    const pending = { status: 'PENDING' as const };
    const pickedUp = { status: 'PICKED_UP' as const };

    // An offer declined or an admin reassigning it: back to waiting is real
    expect(keepDelivery(accepted, pending)).toEqual(pending);
    expect(keepDelivery(pickedUp, accepted)).toBe(pickedUp);
    expect(keepDelivery({ status: 'DELIVERED' }, pickedUp)).toEqual({ status: 'DELIVERED' });
  });

  it('never moves the map back to before the pickup, nor away from delivered', () => {
    vi.spyOn(logger, 'warn').mockImplementation(() => undefined);
    const toCustomer = { phase: 'TO_CUSTOMER' as const };
    const delivered = { phase: 'DELIVERED' as const };

    expect(keepPhase({ phase: 'AT_RESTAURANT' }, { phase: 'TO_RESTAURANT' })).toEqual({
      phase: 'TO_RESTAURANT',
    });
    expect(keepPhase(toCustomer, { phase: 'TO_RESTAURANT' })).toBe(toCustomer);
    expect(keepPhase(delivered, toCustomer)).toBe(delivered);
  });
});
