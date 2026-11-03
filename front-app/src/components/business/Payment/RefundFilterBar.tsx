import { ClearFiltersButton } from '@/components/common/ClearFiltersButton';
import { DateRangeFields } from '@/components/common/DateRangeFields';
import { SearchInput } from '@/components/common/SearchInput';
import { Select } from '@/components/common/Select';
import { REFUND_STATUSES, type RefundFilters } from '@/types/payment';
import { REFUND_STATUS_LABELS } from '@/utils/constants';
import { idFilterError, oneOf, toIdFilter } from '@/utils/validationUtils';

const STATUS_OPTIONS = REFUND_STATUSES.map((status) => ({
  value: status,
  label: REFUND_STATUS_LABELS[status],
}));

interface RefundFilterBarProps {
  filters: RefundFilters;
  onChange: (changes: Partial<RefundFilters>) => void;
  onClear: () => void;
}

/** The refund list's filters; "Failed" finds the refunds to follow up. */
export function RefundFilterBar({ filters, onChange, onClear }: RefundFilterBarProps) {
  return (
    <div className="grid items-start gap-4 sm:grid-cols-2 xl:grid-cols-4">
      <SearchInput
        label="Payment #"
        inputMode="numeric"
        value={filters.paymentId?.toString() ?? ''}
        validate={idFilterError}
        onSearch={(text) => onChange({ paymentId: toIdFilter(text) })}
      />
      <SearchInput
        label="Order #"
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
        onChange={(event) => onChange({ status: oneOf(event.target.value, REFUND_STATUSES) })}
      />
      <div className="hidden xl:block" />
      <DateRangeFields
        fromDate={filters.fromDate}
        toDate={filters.toDate}
        fromLabel="Requested from"
        toLabel="Requested until"
        onChange={onChange}
      />
      <div className="flex items-end sm:pt-7">
        <ClearFiltersButton onClick={onClear} />
      </div>
    </div>
  );
}
