import { Link } from 'react-router';

import { useAppDispatch } from '@/store/hooks';
import { orderFiltersReplaced } from '@/store/slices/orderListSlice';
import { restaurantFiltersReplaced } from '@/store/slices/restaurantListSlice';
import type { CalendarEvent } from '@/types/calendar';
import { ROUTES, routeTo } from '@/utils/constants';

const LINK_CLASSES =
  'text-sm font-semibold text-brand-text hover:underline focus-visible:ring-2 focus-visible:ring-brand-500 focus-visible:outline-none rounded';

/**
 * Where an event leads: orders to the order list of that day, a coupon to its page, new restaurants to the
 * restaurant list (newest first). The lists are filtered through their Redux filters, as other pages do.
 */
export function EventLink({ event, date }: { event: CalendarEvent; date: string }) {
  const dispatch = useAppDispatch();

  if (event.kind === 'COUPON') {
    return event.ref ? (
      <Link to={routeTo.couponDetails(event.ref.couponId)} className={LINK_CLASSES}>
        View coupon
      </Link>
    ) : null;
  }
  if (event.type === 'RESTAURANTS') {
    return (
      <Link
        to={ROUTES.restaurants}
        onClick={() => dispatch(restaurantFiltersReplaced({ sort: 'createdAt,desc' }))}
        className={LINK_CLASSES}
      >
        View restaurants
      </Link>
    );
  }
  return (
    <Link
      to={ROUTES.orders}
      onClick={() => dispatch(orderFiltersReplaced({ fromDate: date, toDate: date }))}
      className={LINK_CLASSES}
    >
      View orders
    </Link>
  );
}
