import { ClearFiltersButton } from '@/components/common/ClearFiltersButton';
import { SearchInput } from '@/components/common/SearchInput';
import { Select } from '@/components/common/Select';
import { USER_ROLES, USER_STATUSES, type UserFilters } from '@/types/user';
import { CUSTOMER_SORT_OPTIONS, USER_ROLE_LABELS, USER_STATUS_LABELS } from '@/utils/constants';
import { oneOf } from '@/utils/validationUtils';

const ROLE_OPTIONS = USER_ROLES.map((role) => ({ value: role, label: USER_ROLE_LABELS[role] }));
const STATUS_OPTIONS = USER_STATUSES.map((status) => ({
  value: status,
  label: USER_STATUS_LABELS[status],
}));

interface CustomerFilterBarProps {
  filters: UserFilters;
  onChange: (changes: Partial<UserFilters>) => void;
  onClear: () => void;
}

/** The customer list's search, filters and order. */
export function CustomerFilterBar({ filters, onChange, onClear }: CustomerFilterBarProps) {
  return (
    <div className="grid items-start gap-4 sm:grid-cols-2 xl:grid-cols-[2fr_1fr_1fr_1fr_auto]">
      <SearchInput
        label="Search"
        placeholder="Name, email or phone"
        maxLength={100}
        value={filters.search ?? ''}
        onSearch={(text) => onChange({ search: text })}
      />
      <Select
        label="Role"
        placeholder="All roles"
        options={ROLE_OPTIONS}
        value={filters.role ?? ''}
        onChange={(event) => onChange({ role: oneOf(event.target.value, USER_ROLES) })}
      />
      <Select
        label="Status"
        placeholder="All statuses"
        options={STATUS_OPTIONS}
        value={filters.status ?? ''}
        onChange={(event) => onChange({ status: oneOf(event.target.value, USER_STATUSES) })}
      />
      <Select
        label="Sort by"
        options={CUSTOMER_SORT_OPTIONS}
        value={filters.sort}
        onChange={(event) => onChange({ sort: event.target.value })}
      />
      <div className="flex items-end sm:pt-7">
        <ClearFiltersButton onClick={onClear} />
      </div>
    </div>
  );
}
