import { describe, expect, it } from 'vitest';

import customerListReducer, {
  customerFiltersChanged,
  customerFiltersCleared,
} from '@/store/slices/customerListSlice';
import foodListReducer, { foodFiltersCleared, foodPageChanged } from '@/store/slices/foodListSlice';
import orderListReducer, {
  orderFiltersChanged,
  orderFiltersReplaced,
  orderPageChanged,
} from '@/store/slices/orderListSlice';

describe('list slices', () => {
  it('a changed filter starts the list again at the first page, and an emptied filter disappears', () => {
    let state = orderListReducer(
      undefined,
      orderFiltersChanged({ status: 'PLACED', customerId: 7 }),
    );
    state = orderListReducer(state, orderPageChanged(3));
    state = orderListReducer(state, orderFiltersChanged({ status: undefined }));

    expect(state).toEqual({ filters: { customerId: 7 }, page: 0 });
  });

  it('replacing the filters drops the old ones ("all orders of this customer")', () => {
    const filtered = orderListReducer(undefined, orderFiltersChanged({ status: 'PLACED' }));

    expect(orderListReducer(filtered, orderFiltersReplaced({ customerId: 9 })).filters).toEqual({
      customerId: 9,
    });
  });

  it('clearing the filters keeps the default sort order', () => {
    const customers = customerListReducer(
      undefined,
      customerFiltersChanged({ search: 'priya', sort: 'fullName,asc' }),
    );
    expect(customerListReducer(customers, customerFiltersCleared()).filters).toEqual({
      sort: 'createdAt,desc',
    });

    const foods = foodListReducer(undefined, foodPageChanged(2));
    expect(foodListReducer(foods, foodFiltersCleared())).toEqual({
      filters: { sort: 'updatedAt,desc' },
      page: 0,
    });
  });
});
