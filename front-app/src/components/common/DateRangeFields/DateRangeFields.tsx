import { DatePicker } from '@/components/common/DatePicker';
import { dateRangeError } from '@/utils/validationUtils';

interface DateRangeFieldsProps {
  /** 'YYYY-MM-DD' */
  fromDate?: string;
  toDate?: string;
  onChange: (changes: { fromDate?: string; toDate?: string }) => void;
  fromLabel?: string;
  toLabel?: string;
}

/**
 * Two date pickers for a filter (calendar days, both included). Each calendar shows the chosen range, and a day
 * that would make it impossible ("until" before "from") cannot be picked; a range that is impossible all the same
 * (an old link) is explained under the second field, and the page does not search with it (see dateRangeError).
 */
export function DateRangeFields({
  fromDate,
  toDate,
  onChange,
  fromLabel = 'From',
  toLabel = 'Until',
}: DateRangeFieldsProps) {
  return (
    <>
      <DatePicker
        label={fromLabel}
        value={fromDate ?? ''}
        max={toDate}
        rangeFrom={fromDate}
        rangeTo={toDate}
        onChange={(date) => onChange({ fromDate: date })}
      />
      <DatePicker
        label={toLabel}
        value={toDate ?? ''}
        min={fromDate}
        rangeFrom={fromDate}
        rangeTo={toDate}
        error={dateRangeError(fromDate, toDate)}
        onChange={(date) => onChange({ toDate: date })}
      />
    </>
  );
}
