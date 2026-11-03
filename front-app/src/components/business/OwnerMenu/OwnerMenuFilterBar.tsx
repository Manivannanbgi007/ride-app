import {
  FOOD_TYPES,
  hasMenuFilters,
  MENU_SORT_LABELS,
  MENU_SORTS,
  type MenuSort,
  type OwnerMenuFilters,
} from '@/components/business/OwnerMenu/ownerMenuUtils';
import { ClearFiltersButton } from '@/components/common/ClearFiltersButton';
import { SearchInput } from '@/components/common/SearchInput';
import { Select, type SelectOption } from '@/components/common/Select';
import { FOOD_TYPE_LABELS } from '@/utils/constants';
import { FOOD_NAME_MAX_LENGTH, oneOf } from '@/utils/validationUtils';

const TYPE_OPTIONS = FOOD_TYPES.map((type) => ({
  value: type,
  label: type === 'VEG' ? 'Veg only' : FOOD_TYPE_LABELS[type],
}));
const SORT_OPTIONS = MENU_SORTS.map((sort) => ({ value: sort, label: MENU_SORT_LABELS[sort] }));

interface OwnerMenuFilterBarProps {
  filters: OwnerMenuFilters;
  sort: MenuSort;
  /** The categories the restaurant's dishes are in */
  categoryOptions: readonly SelectOption[];
  /** The address parameters to change: a filter's own name, and its new text ('' or undefined = all) */
  onChange: (changes: { search?: string; category?: string; type?: string; sort?: string }) => void;
  onClear: () => void;
}

/**
 * The menu page's toolbar: search by name, category, food type, and the order of the list. Where a dish stands
 * (on the menu, sold out, hidden) is chosen with the status chips above it.
 */
export function OwnerMenuFilterBar({
  filters,
  sort,
  categoryOptions,
  onChange,
  onClear,
}: OwnerMenuFilterBarProps) {
  return (
    // Two to a row from 360 px to 1280 px (the name gets a row of its own), so the dishes stay close on a phone
    <div className="grid grid-cols-1 items-start gap-x-3 gap-y-4 min-[360px]:grid-cols-2 sm:gap-x-4 xl:grid-cols-[2fr_1fr_1fr_1fr_auto]">
      <div className="min-[360px]:col-span-2 xl:col-span-1">
        <SearchInput
          label="Name"
          placeholder="Search by name"
          maxLength={FOOD_NAME_MAX_LENGTH}
          value={filters.search ?? ''}
          onSearch={(text) => onChange({ search: text })}
        />
      </div>
      <Select
        label="Category"
        placeholder="All categories"
        options={categoryOptions}
        value={filters.categoryId?.toString() ?? ''}
        onChange={(event) => onChange({ category: event.target.value })}
      />
      <Select
        label="Food type"
        placeholder="All types"
        options={TYPE_OPTIONS}
        value={filters.foodType ?? ''}
        onChange={(event) => onChange({ type: oneOf(event.target.value, FOOD_TYPES) })}
      />
      <Select
        label="Sort by"
        options={SORT_OPTIONS}
        value={sort}
        // Name order is the default: it is left out of the address
        onChange={(event) => {
          const chosen = oneOf(event.target.value, MENU_SORTS);
          onChange({ sort: chosen === 'name' ? undefined : chosen });
        }}
      />
      <div className="flex items-end min-[360px]:pt-7">
        <ClearFiltersButton disabled={!hasMenuFilters(filters)} onClick={onClear} />
      </div>
    </div>
  );
}
