import {
  Ban,
  Bike,
  ChefHat,
  CircleCheck,
  CircleX,
  Hourglass,
  Inbox,
  LayoutList,
  PackageCheck,
  SlidersHorizontal,
  ThumbsUp,
  type LucideIcon,
} from 'lucide-react';
import { useState } from 'react';

import { Button } from '@/components/common/Button';
import { ClearFiltersButton } from '@/components/common/ClearFiltersButton';
import { DateRangeFields } from '@/components/common/DateRangeFields';
import { QuickFilters, type QuickFilter } from '@/components/common/QuickFilters';
import { SearchInput } from '@/components/common/SearchInput';
import { Select } from '@/components/common/Select';
import {
  ORDER_STATUSES,
  PAYMENT_STATUSES,
  type OrderFilters,
  type OrderStatus,
} from '@/types/order';
import { ORDER_STATUS_LABELS, PAYMENT_STATUS_LABELS } from '@/utils/constants';
import { idFilterError, oneOf, toIdFilter } from '@/utils/validationUtils';

const STATUS_OPTIONS = ORDER_STATUSES.map((status) => ({
  value: status,
  label: ORDER_STATUS_LABELS[status],
}));
const PAYMENT_OPTIONS = PAYMENT_STATUSES.map((status) => ({
  value: status,
  label: PAYMENT_STATUS_LABELS[status],
}));

type MoreFilter = 'customerId' | 'restaurantId';
const MORE_FILTERS: readonly MoreFilter[] = ['customerId', 'restaurantId'];

interface OrderFilterBarProps {
  filters: OrderFilters;
  onChange: (changes: Partial<OrderFilters>) => void;
  onClear: () => void;
}

/**
 * The order list's filters, compact: the order number, status, payment and the days placed in one row; the customer
 * and restaurant numbers under "More" (open by itself when one of them is set). Text boxes search once typing stops;
 * the lists and dates at once.
 */
export function OrderFilterBar({ filters, onChange, onClear }: OrderFilterBarProps) {
  const moreSet = MORE_FILTERS.filter((key) => filters[key] !== undefined).length;
  const [moreOpen, setMoreOpen] = useState(moreSet > 0);

  return (
    <div className="flex flex-col gap-3">
      <div className="grid items-start gap-3 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-[repeat(5,minmax(0,1fr))_auto]">
        <SearchInput
          label="Order #"
          placeholder="e.g. 42"
          inputMode="numeric"
          value={filters.orderId?.toString() ?? ''}
          validate={idFilterError}
          onSearch={(text) => onChange({ orderId: toIdFilter(text) })}
        />
        <Select
          label="Status"
          placeholder="All statuses"
          options={STATUS_OPTIONS}
          value={filters.status ?? ''}
          onChange={(event) => onChange({ status: oneOf(event.target.value, ORDER_STATUSES) })}
        />
        <Select
          label="Payment"
          placeholder="All payments"
          options={PAYMENT_OPTIONS}
          value={filters.paymentStatus ?? ''}
          onChange={(event) =>
            onChange({ paymentStatus: oneOf(event.target.value, PAYMENT_STATUSES) })
          }
        />
        <DateRangeFields
          fromDate={filters.fromDate}
          toDate={filters.toDate}
          fromLabel="Placed from"
          toLabel="Placed until"
          onChange={onChange}
        />
        <div className="flex flex-wrap items-end gap-2 sm:pt-7">
          <Button
            variant={moreOpen || moreSet > 0 ? 'secondary' : 'outline'}
            size="md"
            aria-expanded={moreOpen}
            aria-controls="order-more-filters"
            leftIcon={<SlidersHorizontal aria-hidden="true" className="size-4" />}
            onClick={() => setMoreOpen((open) => !open)}
          >
            More<span className="sr-only"> filters</span>
            {moreSet > 0 && ` (${moreSet})`}
          </Button>
          <ClearFiltersButton onClick={onClear} />
        </div>
      </div>
      {moreOpen && (
        <div
          id="order-more-filters"
          className="grid items-start gap-3 border-t border-line pt-3 motion-safe:animate-rise-in sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-[repeat(5,minmax(0,1fr))_auto]"
        >
          <SearchInput
            label="Customer #"
            placeholder="Customer's user id"
            inputMode="numeric"
            value={filters.customerId?.toString() ?? ''}
            validate={idFilterError}
            onSearch={(text) => onChange({ customerId: toIdFilter(text) })}
          />
          <SearchInput
            label="Restaurant #"
            placeholder="Restaurant id"
            inputMode="numeric"
            value={filters.restaurantId?.toString() ?? ''}
            validate={idFilterError}
            onSearch={(text) => onChange({ restaurantId: toIdFilter(text) })}
          />
        </div>
      )}
    </div>
  );
}

/** Every status's icon, for its chip. */
const STATUS_ICONS: Record<OrderStatus, LucideIcon> = {
  PAYMENT_PENDING: Hourglass,
  PLACED: Inbox,
  ACCEPTED: ThumbsUp,
  PREPARING: ChefHat,
  READY_FOR_PICKUP: PackageCheck,
  OUT_FOR_DELIVERY: Bike,
  DELIVERED: CircleCheck,
  CANCELLED: CircleX,
  REJECTED: Ban,
};

/** The statuses with a chip of their own: new, in the kitchen, on the road and how orders ended. */
const CHIP_STATUSES: readonly OrderStatus[] = [
  'PLACED',
  'PREPARING',
  'OUT_FOR_DELIVERY',
  'DELIVERED',
  'CANCELLED',
  'REJECTED',
];

interface OrderStatusChipsProps {
  /** The chosen status; undefined = all */
  status?: OrderStatus;
  /** How many orders the list found, shown on the chosen chip (when known) */
  count?: number;
  onChange: (status: OrderStatus | undefined) => void;
}

/**
 * One-click status filters above the order list: "All" and the statuses looked for most. A status chosen in the
 * filters' list that has no chip gets one while it is chosen, so the chips always say what is shown.
 */
export function OrderStatusChips({ status, count, onChange }: OrderStatusChipsProps) {
  const statuses =
    status && !CHIP_STATUSES.includes(status) ? [status, ...CHIP_STATUSES] : CHIP_STATUSES;
  const items: QuickFilter<OrderStatus | 'ALL'>[] = [
    { value: 'ALL', label: 'All', icon: LayoutList },
    ...statuses.map((value) => ({
      value,
      label: ORDER_STATUS_LABELS[value],
      icon: STATUS_ICONS[value],
    })),
  ];
  const chosen = status ?? 'ALL';

  return (
    <QuickFilters
      label="Order status"
      items={items.map((item) => (item.value === chosen ? { ...item, count } : item))}
      value={chosen}
      onChange={(value) => onChange(value === 'ALL' ? undefined : value)}
    />
  );
}
