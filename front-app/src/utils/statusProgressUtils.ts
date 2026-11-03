import { replaceEqualDeep } from '@tanstack/react-query';

import type { DeliveryStatus } from '@/types/delivery';
import type { LiveTrackingPhase } from '@/types/liveTracking';
import type { OrderStatus } from '@/types/order';
import { logger } from '@/utils/logger';

/**
 * How far along an order (or its delivery) is: a higher step is later. A final status (delivered, cancelled) is
 * never left again: the services do not allow it either.
 */
export interface StatusProgress {
  status: string;
  step: number;
  final: boolean;
}

/**
 * order-service only moves an order forward (OrderStatusTransitions); cancelled and rejected can follow any step
 * before the pickup, and end it like delivered does.
 */
const ORDER_STEPS: Record<OrderStatus, number> = {
  PAYMENT_PENDING: 0,
  PLACED: 1,
  ACCEPTED: 2,
  PREPARING: 3,
  READY_FOR_PICKUP: 4,
  OUT_FOR_DELIVERY: 5,
  DELIVERED: 6,
  CANCELLED: 6,
  REJECTED: 6,
};
const FINAL_ORDER_STATUSES: readonly OrderStatus[] = ['DELIVERED', 'CANCELLED', 'REJECTED'];

export function orderStatusProgress({ status }: { status: OrderStatus }): StatusProgress {
  return { status, step: ORDER_STEPS[status], final: FINAL_ORDER_STATUSES.includes(status) };
}

/**
 * Before the pickup a delivery may go back and forth (an offer declined or not answered, an admin reassigning it):
 * those statuses are one step. The pickup cannot be undone, and delivered or cancelled is the end.
 */
const DELIVERY_STEPS: Record<DeliveryStatus, number> = {
  PENDING: 0,
  ASSIGNED: 0,
  ACCEPTED: 0,
  PICKED_UP: 1,
  DELIVERED: 2,
  CANCELLED: 2,
};

export function deliveryStatusProgress({ status }: { status: DeliveryStatus }): StatusProgress {
  return {
    status,
    step: DELIVERY_STEPS[status],
    final: status === 'DELIVERED' || status === 'CANCELLED',
  };
}

/** The live map: on the way to (or at) the restaurant may repeat with another partner; after the pickup, never. */
const LIVE_PHASE_STEPS: Record<LiveTrackingPhase, number> = {
  TO_RESTAURANT: 0,
  AT_RESTAURANT: 0,
  TO_CUSTOMER: 1,
  DELIVERED: 2,
};

export function livePhaseProgress({ phase }: { phase: LiveTrackingPhase }): StatusProgress {
  return { status: phase, step: LIVE_PHASE_STEPS[phase], final: phase === 'DELIVERED' };
}

/** Whether `next` would take the screen back: to an earlier step, or away from a final status. */
export function isOlderThan(next: StatusProgress, shown: StatusProgress): boolean {
  return next.step < shown.step || (shown.final && next.status !== shown.status);
}

/**
 * React Query structural sharing that never moves backward. Answers can arrive late or out of order (a slow
 * request overtaken by a newer one, a refresh on focus racing a live-update refetch): an answer behind the data
 * already shown is ignored, so "picked up" never falls back to "being prepared" and "delivered" stays delivered.
 * Any other answer is taken as usual (unchanged parts keep their identity, as React Query's default does).
 */
export function forwardOnly<T>(progressOf: (data: T) => StatusProgress, what: string) {
  return (oldData: unknown, newData: unknown): unknown => {
    if (oldData === undefined || newData === undefined) {
      return newData;
    }
    const shown = progressOf(oldData as T);
    const next = progressOf(newData as T);
    if (isOlderThan(next, shown)) {
      logger.warn(`Ignored an outdated ${what}: ${next.status} arrived after ${shown.status}`);
      return oldData;
    }
    return replaceEqualDeep(oldData, newData);
  };
}
