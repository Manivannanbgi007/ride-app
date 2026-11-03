import { SlidersHorizontal } from 'lucide-react';
import { useState } from 'react';

import { Button } from '@/components/common/Button';
import { ClearFiltersButton } from '@/components/common/ClearFiltersButton';
import { DateRangeFields } from '@/components/common/DateRangeFields';
import { SearchInput } from '@/components/common/SearchInput';
import { Select } from '@/components/common/Select';
import { DELIVERY_STATUSES, type DeliveryFilters } from '@/types/delivery';
import { DELIVERY_STATUS_LABELS } from '@/utils/constants';
import { idFilterError, oneOf, toIdFilter } from '@/utils/validationUtils';

const STATUS_OPTIONS = DELIVERY_STATUSES.map((status) => ({
  value: status,
  label: DELIVERY_STATUS_LABELS[status],
}));

type IdFilter = 'orderId' | 'partnerUserId' | 'customerId' | 'restaurantId';
/** Looked for less often: behind "More filters" (open by itself when one of them is set) */
const MORE: readonly IdFilter[] = ['partnerUserId', 'restaurantId', 'customerId'];

interface DeliveryFilterBarProps {
  filters: DeliveryFilters;
  onChange: (changes: Partial<DeliveryFilters>) => void;
  onClear: () => void;
}

/**
 * The delivery list's filters, compact: status, order number, pickup city and the days the food was ready in one
 * row; the partner, restaurant and customer numbers under "More filters".
 */
export function DeliveryFilterBar({ filters, onChange, onClear }: DeliveryFilterBarProps) {
  const moreSet = MORE.filter((key) => filters[key] !== undefined).length;
  const [moreOpen, setMoreOpen] = useState(moreSet > 0);
  const idBox = (label: string, key: IdFilter) => (
    <SearchInput
      label={label}
      inputMode="numeric"
      value={filters[key]?.toString() ?? ''}
      validate={idFilterError}
      onSearch={(text) => onChange({ [key]: toIdFilter(text) })}
    />
  );

  return (
    <div className="flex flex-col gap-3">
      <div className="grid items-start gap-3 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-[repeat(5,minmax(0,1fr))_auto]">
        <Select
          label="Status"
          placeholder="All statuses"
          options={STATUS_OPTIONS}
          value={filters.status ?? ''}
          onChange={(event) => onChange({ status: oneOf(event.target.value, DELIVERY_STATUSES) })}
        />
        {idBox('Order #', 'orderId')}
        <SearchInput
          label="Pickup city"
          placeholder="Exact city"
          maxLength={100}
          value={filters.city ?? ''}
          onSearch={(text) => onChange({ city: text })}
        />
        <DateRangeFields
          fromDate={filters.fromDate}
          toDate={filters.toDate}
          fromLabel="Ready from"
          toLabel="Ready until"
          onChange={onChange}
        />
        <div className="flex flex-wrap items-end gap-2 sm:pt-7">
          <Button
            variant={moreOpen || moreSet > 0 ? 'secondary' : 'outline'}
            size="md"
            aria-expanded={moreOpen}
            aria-controls="delivery-more-filters"
            leftIcon={<SlidersHorizontal aria-hidden="true" className="size-4" />}
            onClick={() => setMoreOpen((open) => !open)}
          >
            More{moreSet > 0 && ` (${moreSet})`}
          </Button>
          <ClearFiltersButton onClick={onClear} />
        </div>
      </div>
      {moreOpen && (
        <div
          id="delivery-more-filters"
          className="grid items-start gap-3 border-t border-line pt-3 sm:grid-cols-3 xl:grid-cols-[repeat(5,minmax(0,1fr))_auto]"
        >
          {idBox('Partner #', 'partnerUserId')}
          {idBox('Restaurant #', 'restaurantId')}
          {idBox('Customer #', 'customerId')}
        </div>
      )}
    </div>
  );
}
