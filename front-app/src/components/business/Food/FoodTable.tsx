import { Store } from 'lucide-react';
import { Link } from 'react-router';

import { FoodThumbnail } from '@/components/business/Food/FoodThumbnail';
import { MenuItemStatusBadge, StockBadge } from '@/components/business/StatusBadges';
import { FoodTypeMark } from '@/components/business/Storefront/FoodTypeMark';
import { Table, type TableColumn } from '@/components/common/Table';
import type { MenuItem } from '@/types/catalog';
import { DEFAULT_CURRENCY, FOOD_TYPE_LABELS, routeTo } from '@/utils/constants';
import { formatDateTime } from '@/utils/dateUtils';
import { categoryImageOf } from '@/utils/foodImages';
import { formatCurrency } from '@/utils/formatUtils';

const COLUMNS: readonly TableColumn<MenuItem>[] = [
  {
    header: 'Item',
    render: (item) => (
      <Link to={routeTo.foodDetails(item.id)} className="group flex items-center gap-3">
        <span className="block overflow-hidden rounded-2xl shadow-sm ring-1 ring-line/60">
          <span className="block transition-transform duration-500 motion-safe:group-hover:scale-110">
            <FoodThumbnail item={item} fallbackSrc={categoryImageOf(item.categoryName)} />
          </span>
        </span>
        <span>
          <span className="block font-semibold group-hover:text-brand-text group-hover:underline">
            {item.name}
          </span>
          <span className="mt-0.5 flex items-center gap-1.5 text-xs text-ink-soft">
            {/* The word follows: the mark is only a picture of it */}
            <span aria-hidden="true" className="flex">
              <FoodTypeMark foodType={item.foodType} />
            </span>
            {FOOD_TYPE_LABELS[item.foodType]}
          </span>
        </span>
      </Link>
    ),
  },
  { header: 'Category', render: (item) => item.categoryName },
  {
    header: 'Restaurant',
    render: (item) => (
      <span className="flex items-center gap-1.5 text-ink-soft">
        <Store aria-hidden="true" className="size-3.5 shrink-0" />
        {item.restaurantName ?? `#${item.restaurantId}`}
      </span>
    ),
  },
  {
    header: 'Price',
    align: 'right',
    render: (item) => (
      <span className="inline-flex rounded-full bg-brand-50 px-2.5 py-1 font-bold whitespace-nowrap text-brand-text-strong tabular-nums">
        {formatCurrency(item.price, DEFAULT_CURRENCY)}
      </span>
    ),
  },
  { header: 'Stock', render: (item) => <StockBadge available={item.available} /> },
  { header: 'Listing', render: (item) => <MenuItemStatusBadge status={item.status} /> },
  {
    header: 'Changed',
    render: (item) => (
      <span className="whitespace-nowrap text-ink-soft">{formatDateTime(item.updatedAt)}</span>
    ),
  },
];

interface FoodTableProps {
  items: readonly MenuItem[];
  isUpdating?: boolean;
}

/** Menu items as a table; the name opens the item. */
export function FoodTable({ items, isUpdating }: FoodTableProps) {
  return (
    <Table
      caption="Menu items"
      columns={COLUMNS}
      rows={items}
      rowKey={(item) => item.id}
      isUpdating={isUpdating}
    />
  );
}
