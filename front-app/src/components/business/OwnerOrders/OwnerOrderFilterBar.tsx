import {
  OWNER_ORDER_SORT_LABELS,
  OWNER_ORDER_SORTS,
  type OwnerOrderSort,
} from '@/components/business/OwnerOrders/ownerOrderUtils';
import { ClearFiltersButton } from '@/components/common/ClearFiltersButton';
import { DateRangePicker } from '@/components/common/DateRangePicker';
import { Select } from '@/components/common/Select';
import type { DateRange } from '@/utils/dateRangeUtils';

const SORT_OPTIONS = OWNER_ORDER_SORTS.map((sort) => ({
  value: sort,
  label: OWNER_ORDER_SORT_LABELS[sort],
}));

interface OwnerOrderFilterBarProps {
  /** The days the orders were placed on; undefined: any day */
  range?: DateRange;
  sort: OwnerOrderSort;
  onRangeChange: (range: DateRange | undefined) => void;
  onSortChange: (sort: OwnerOrderSort) => void;
  onClear: () => void;
}

/**
 * The days the orders were placed on (a ready-made range, one day or a custom range; "Any day" by default) and
 * their order. The status is chosen with the tabs above, not here; "Clear filters" forgets the days and the order.
 */
export function OwnerOrderFilterBar({
  range,
  sort,
  onRangeChange,
  onSortChange,
  onClear,
}: OwnerOrderFilterBarProps) {
  return (
    <div className="grid items-end gap-4 sm:grid-cols-[minmax(0,1fr)_13rem] lg:grid-cols-[minmax(0,24rem)_13rem_auto]">
      <DateRangePicker
        label="Placed on"
        showLabel
        value={range}
        emptyLabel="Any day"
        onChange={(next) => onRangeChange(next)}
      />
      <Select
        label="Order"
        options={SORT_OPTIONS}
        value={sort}
        onChange={(event) => onSortChange(event.target.value as OwnerOrderSort)}
      />
      <div className="flex sm:col-span-2 lg:col-span-1">
        <ClearFiltersButton onClick={onClear} />
      </div>
    </div>
  );
}
