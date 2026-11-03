import { ClearFiltersButton } from '@/components/common/ClearFiltersButton';
import { DatePicker } from '@/components/common/DatePicker';
import { SearchInput } from '@/components/common/SearchInput';
import { Select } from '@/components/common/Select';
import { COUPON_STATUSES, DISCOUNT_TYPES, type CouponFilters } from '@/types/coupon';
import { COUPON_SORT_OPTIONS, COUPON_STATUS_LABELS, DISCOUNT_TYPE_LABELS } from '@/utils/constants';
import { idFilterError, oneOf, toIdFilter } from '@/utils/validationUtils';

const STATUS_OPTIONS = COUPON_STATUSES.map((status) => ({
  value: status,
  label: COUPON_STATUS_LABELS[status],
}));
const TYPE_OPTIONS = DISCOUNT_TYPES.map((type) => ({
  value: type,
  label: DISCOUNT_TYPE_LABELS[type],
}));

interface CouponFilterBarProps {
  filters: CouponFilters;
  onChange: (changes: Partial<CouponFilters>) => void;
  onClear: () => void;
}

/** The coupon list's search, filters and order. */
export function CouponFilterBar({ filters, onChange, onClear }: CouponFilterBarProps) {
  return (
    <div className="grid items-start gap-4 sm:grid-cols-2 xl:grid-cols-4">
      <SearchInput
        label="Code"
        placeholder="Part of the code"
        maxLength={30}
        value={filters.code ?? ''}
        onSearch={(text) => onChange({ code: text })}
      />
      <Select
        label="Status"
        placeholder="Active and paused"
        options={STATUS_OPTIONS}
        value={filters.status ?? ''}
        onChange={(event) => onChange({ status: oneOf(event.target.value, COUPON_STATUSES) })}
      />
      <Select
        label="Discount"
        placeholder="Any discount"
        options={TYPE_OPTIONS}
        value={filters.discountType ?? ''}
        onChange={(event) => onChange({ discountType: oneOf(event.target.value, DISCOUNT_TYPES) })}
      />
      <SearchInput
        label="Restaurant #"
        placeholder="Coupons of one restaurant"
        inputMode="numeric"
        value={filters.restaurantId?.toString() ?? ''}
        validate={idFilterError}
        onSearch={(text) => onChange({ restaurantId: toIdFilter(text) })}
      />
      <DatePicker
        label="Valid on"
        value={filters.validOn ?? ''}
        onChange={(date) => onChange({ validOn: date })}
      />
      <Select
        label="Sort by"
        options={COUPON_SORT_OPTIONS}
        value={filters.sort}
        onChange={(event) => onChange({ sort: event.target.value })}
      />
      <div className="flex items-end sm:pt-7">
        <ClearFiltersButton onClick={onClear} />
      </div>
    </div>
  );
}
