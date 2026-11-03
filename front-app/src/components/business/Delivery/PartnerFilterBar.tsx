import { ClearFiltersButton } from '@/components/common/ClearFiltersButton';
import { SearchInput } from '@/components/common/SearchInput';
import { Select } from '@/components/common/Select';
import { PARTNER_STATUSES, type PartnerFilters } from '@/types/delivery';
import { PARTNER_STATUS_LABELS } from '@/utils/constants';
import { oneOf } from '@/utils/validationUtils';

const STATUS_OPTIONS = PARTNER_STATUSES.map((status) => ({
  value: status,
  label: PARTNER_STATUS_LABELS[status],
}));

interface PartnerFilterBarProps {
  filters: PartnerFilters;
  onChange: (changes: Partial<PartnerFilters>) => void;
  onClear: () => void;
}

/** The partner list's search and filters. */
export function PartnerFilterBar({ filters, onChange, onClear }: PartnerFilterBarProps) {
  return (
    <div className="grid items-start gap-4 sm:grid-cols-2 xl:grid-cols-[2fr_1fr_1fr_auto]">
      <SearchInput
        label="Search"
        placeholder="Name or phone"
        maxLength={100}
        value={filters.q ?? ''}
        onSearch={(text) => onChange({ q: text })}
      />
      <Select
        label="Status"
        placeholder="All statuses"
        options={STATUS_OPTIONS}
        value={filters.status ?? ''}
        onChange={(event) => onChange({ status: oneOf(event.target.value, PARTNER_STATUSES) })}
      />
      <SearchInput
        label="City"
        placeholder="Exact city"
        maxLength={100}
        value={filters.city ?? ''}
        onSearch={(text) => onChange({ city: text })}
      />
      <div className="flex items-end sm:pt-7">
        <ClearFiltersButton onClick={onClear} />
      </div>
    </div>
  );
}
