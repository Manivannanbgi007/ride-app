import { SlidersHorizontal } from 'lucide-react';
import { useState } from 'react';

import { Button } from '@/components/common/Button';
import { ClearFiltersButton } from '@/components/common/ClearFiltersButton';
import { DateRangeFields } from '@/components/common/DateRangeFields';
import { SearchInput } from '@/components/common/SearchInput';
import { Select } from '@/components/common/Select';
import { PAYMENT_METHODS } from '@/types/order';
import { PAYMENT_RECORD_STATUSES, type PaymentFilters } from '@/types/payment';
import { PAYMENT_METHOD_LABELS, PAYMENT_RECORD_STATUS_LABELS } from '@/utils/constants';
import { idFilterError, oneOf, toIdFilter } from '@/utils/validationUtils';

const STATUS_OPTIONS = PAYMENT_RECORD_STATUSES.map((status) => ({
  value: status,
  label: PAYMENT_RECORD_STATUS_LABELS[status],
}));
const METHOD_OPTIONS = PAYMENT_METHODS.map((method) => ({
  value: method,
  label: PAYMENT_METHOD_LABELS[method],
}));

type IdFilter = 'paymentId' | 'orderId' | 'customerId';
const ID_FILTERS: readonly IdFilter[] = ['paymentId', 'orderId', 'customerId'];

interface PaymentFilterBarProps {
  filters: PaymentFilters;
  onChange: (changes: Partial<PaymentFilters>) => void;
  onClear: () => void;
}

/**
 * The payment list's filters, compact: status ("Failed" finds the payments to look after), method and the days paid
 * in one row; the payment, order and customer numbers under "More" (open by itself when one of them is set).
 */
export function PaymentFilterBar({ filters, onChange, onClear }: PaymentFilterBarProps) {
  const idsSet = ID_FILTERS.filter((key) => filters[key] !== undefined).length;
  const [moreOpen, setMoreOpen] = useState(idsSet > 0);
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
      <div className="grid items-start gap-3 sm:grid-cols-2 xl:grid-cols-[repeat(4,minmax(0,1fr))_auto]">
        <Select
          label="Status"
          placeholder="All statuses"
          options={STATUS_OPTIONS}
          value={filters.status ?? ''}
          onChange={(event) =>
            onChange({ status: oneOf(event.target.value, PAYMENT_RECORD_STATUSES) })
          }
        />
        <Select
          label="Method"
          placeholder="All methods"
          options={METHOD_OPTIONS}
          value={filters.method ?? ''}
          onChange={(event) => onChange({ method: oneOf(event.target.value, PAYMENT_METHODS) })}
        />
        <DateRangeFields
          fromDate={filters.fromDate}
          toDate={filters.toDate}
          fromLabel="Paid from"
          toLabel="Paid until"
          onChange={onChange}
        />
        <div className="flex flex-wrap items-end gap-2 sm:pt-7">
          <Button
            variant={moreOpen || idsSet > 0 ? 'secondary' : 'outline'}
            size="md"
            aria-expanded={moreOpen}
            aria-controls="payment-more-filters"
            leftIcon={<SlidersHorizontal aria-hidden="true" className="size-4" />}
            onClick={() => setMoreOpen((open) => !open)}
          >
            More{idsSet > 0 && ` (${idsSet})`}
          </Button>
          <ClearFiltersButton onClick={onClear} />
        </div>
      </div>
      {moreOpen && (
        <div
          id="payment-more-filters"
          className="grid items-start gap-3 border-t border-line pt-3 sm:grid-cols-3 xl:grid-cols-[repeat(4,minmax(0,1fr))_auto]"
        >
          {idBox('Payment #', 'paymentId')}
          {idBox('Order #', 'orderId')}
          {idBox('Customer #', 'customerId')}
        </div>
      )}
    </div>
  );
}
