import { Link } from 'react-router';

import { RestaurantStatusBadge } from '@/components/business/StatusBadges';
import { Table, type TableColumn } from '@/components/common/Table';
import type { RestaurantSummary } from '@/types/restaurant';
import { routeTo } from '@/utils/constants';
import { formatDateTime } from '@/utils/dateUtils';
import { initialsOf } from '@/utils/formatUtils';

const COLUMNS: readonly TableColumn<RestaurantSummary>[] = [
  {
    header: 'Restaurant',
    render: (restaurant) => (
      <Link to={routeTo.restaurantDetails(restaurant.id)} className="group flex items-center gap-3">
        <span className="flex size-10 shrink-0 items-center justify-center rounded-full bg-brand-50 text-xs font-bold text-brand-text">
          {initialsOf(restaurant.name)}
        </span>
        <span>
          <span className="block font-semibold group-hover:text-brand-text group-hover:underline">
            {restaurant.name}
          </span>
          <span className="block text-xs text-ink-soft">{restaurant.cuisines.join(', ')}</span>
        </span>
      </Link>
    ),
  },
  { header: 'City', render: (restaurant) => restaurant.city },
  {
    header: 'Owner',
    render: (restaurant) => (
      <Link to={routeTo.customerDetails(restaurant.ownerUserId)} className="hover:underline">
        #{restaurant.ownerUserId}
      </Link>
    ),
  },
  {
    header: 'Status',
    render: (restaurant) => (
      <div className="flex flex-col items-start gap-1">
        <RestaurantStatusBadge status={restaurant.status} />
        {restaurant.statusReason && (
          <span className="max-w-56 truncate text-xs text-ink-soft" title={restaurant.statusReason}>
            {restaurant.statusReason}
          </span>
        )}
      </div>
    ),
  },
  { header: 'Submitted', render: (restaurant) => formatDateTime(restaurant.createdAt) },
  { header: 'Status changed', render: (restaurant) => formatDateTime(restaurant.statusChangedAt) },
];

interface RestaurantTableProps {
  restaurants: readonly RestaurantSummary[];
  isUpdating?: boolean;
}

/** Restaurants as a table; the name opens the restaurant, the owner number the owner's account. */
export function RestaurantTable({ restaurants, isUpdating }: RestaurantTableProps) {
  return (
    <Table
      caption="Restaurants"
      columns={COLUMNS}
      rows={restaurants}
      rowKey={(restaurant) => restaurant.id}
      isUpdating={isUpdating}
    />
  );
}
