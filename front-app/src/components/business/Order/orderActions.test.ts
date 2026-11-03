import { describe, expect, it } from 'vitest';

import { orderActionsFor } from '@/components/business/Order/orderActions';
import { ADMIN_CANCELLABLE_STATUSES, ORDER_STATUSES } from '@/types/order';
import { USER_ROLES } from '@/types/user';

describe('orderActionsFor', () => {
  it('offers a platform admin the order, its history, its customer and, for an online payment, its payments', () => {
    expect(
      orderActionsFor({ status: 'DELIVERED', paymentMethod: 'UPI' }, 'PLATFORM_ADMIN'),
    ).toEqual(['open', 'history', 'payments', 'customer']);
  });

  it('has no payments for cash on delivery (there is no online payment to see)', () => {
    expect(
      orderActionsFor({ status: 'DELIVERED', paymentMethod: 'CASH_ON_DELIVERY' }, 'PLATFORM_ADMIN'),
    ).toEqual(['open', 'history', 'customer']);
  });

  it('offers cancel last, and only while the order may still be cancelled (the server checks it again)', () => {
    for (const status of ORDER_STATUSES) {
      const actions = orderActionsFor({ status, paymentMethod: 'UPI' }, 'PLATFORM_ADMIN');

      expect(actions.includes('cancel')).toBe(ADMIN_CANCELLABLE_STATUSES.includes(status));
      if (actions.includes('cancel')) {
        expect(actions.at(-1)).toBe('cancel');
      }
    }
    expect(
      orderActionsFor({ status: 'OUT_FOR_DELIVERY', paymentMethod: 'UPI' }, 'PLATFORM_ADMIN'),
    ).not.toContain('cancel');
  });

  it('offers nothing to anybody who is not a platform admin, or to nobody', () => {
    for (const role of USER_ROLES.filter((candidate) => candidate !== 'PLATFORM_ADMIN')) {
      expect(orderActionsFor({ status: 'PLACED', paymentMethod: 'UPI' }, role)).toEqual([]);
    }
    expect(orderActionsFor({ status: 'PLACED', paymentMethod: 'UPI' }, undefined)).toEqual([]);
  });

  it('never offers an edit or a delete: an order changes through its workflow and is never deleted', () => {
    const all = ORDER_STATUSES.flatMap((status) =>
      orderActionsFor({ status, paymentMethod: 'UPI' }, 'PLATFORM_ADMIN'),
    );

    expect(all).not.toContain('edit');
    expect(all).not.toContain('delete');
  });
});
