import { Clock } from 'lucide-react';

import { Button } from '@/components/common/Button';
import { ClearFiltersButton } from '@/components/common/ClearFiltersButton';
import { SearchInput } from '@/components/common/SearchInput';
import { Select } from '@/components/common/Select';
import { RESTAURANT_STATUSES, type RestaurantFilters } from '@/types/restaurant';
import { RESTAURANT_SORT_OPTIONS, RESTAURANT_STATUS_LABELS } from '@/utils/constants';
import { oneOf } from '@/utils/validationUtils';

const STATUS_OPTIONS = RESTAURANT_STATUSES.map((status) => ({
  value: status,
  label: RESTAURANT_STATUS_LABELS[status],
}));

interface RestaurantFilterBarProps {
  filters: RestaurantFilters;
  onChange: (changes: Partial<RestaurantFilters>) => void;
  onClear: () => void;
}

/** The restaurant list's search, filters and order, with a shortcut to the approvals waiting. */
export function RestaurantFilterBar({ filters, onChange, onClear }: RestaurantFilterBarProps) {
  return (
    <div className="grid items-start gap-4 sm:grid-cols-2 xl:grid-cols-[2fr_1fr_1fr_1fr_auto]">
      <SearchInput
        label="Search"
        placeholder="Name, business name or city"
        maxLength={100}
        value={filters.search ?? ''}
        onSearch={(text) => onChange({ search: text })}
      />
      <Select
        label="Status"
        placeholder="All statuses"
        options={STATUS_OPTIONS}
        value={filters.status ?? ''}
        onChange={(event) => onChange({ status: oneOf(event.target.value, RESTAURANT_STATUSES) })}
      />
      <SearchInput
        label="City"
        placeholder="Exact city"
        maxLength={100}
        value={filters.city ?? ''}
        onSearch={(text) => onChange({ city: text })}
      />
      <Select
        label="Sort by"
        options={RESTAURANT_SORT_OPTIONS}
        value={filters.sort}
        onChange={(event) => onChange({ sort: event.target.value })}
      />
      <div className="flex flex-wrap items-end gap-2 sm:pt-7">
        <Button
          variant="secondary"
          onClick={() => onChange({ status: 'PENDING', sort: 'createdAt,asc' })}
          leftIcon={<Clock aria-hidden="true" className="size-4" />}
        >
          Approvals waiting
        </Button>
        <ClearFiltersButton onClick={onClear} />
      </div>
    </div>
  );
}
