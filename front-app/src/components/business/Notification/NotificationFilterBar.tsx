import { ClearFiltersButton } from '@/components/common/ClearFiltersButton';
import { DateRangeFields } from '@/components/common/DateRangeFields';
import { SearchInput } from '@/components/common/SearchInput';
import { Select } from '@/components/common/Select';
import {
  NOTIFICATION_CHANNELS,
  NOTIFICATION_STATUSES,
  NOTIFICATION_TYPES,
  type NotificationFilters,
} from '@/types/notification';
import {
  NOTIFICATION_CHANNEL_LABELS,
  NOTIFICATION_STATUS_LABELS,
  NOTIFICATION_TYPE_LABELS,
} from '@/utils/constants';
import { idFilterError, oneOf, toIdFilter } from '@/utils/validationUtils';

const optionsOf = <T extends string>(values: readonly T[], labels: Record<T, string>) =>
  values.map((value) => ({ value, label: labels[value] }));

const STATUS_OPTIONS = optionsOf(NOTIFICATION_STATUSES, NOTIFICATION_STATUS_LABELS);
const CHANNEL_OPTIONS = optionsOf(NOTIFICATION_CHANNELS, NOTIFICATION_CHANNEL_LABELS);
const TYPE_OPTIONS = optionsOf(NOTIFICATION_TYPES, NOTIFICATION_TYPE_LABELS);

type IdFilter = 'recipientUserId' | 'orderId' | 'restaurantId';

interface NotificationFilterBarProps {
  filters: NotificationFilters;
  onChange: (changes: Partial<NotificationFilters>) => void;
  onClear: () => void;
}

/** The admin notification list's filters (failed ones: status "Failed"). */
export function NotificationFilterBar({ filters, onChange, onClear }: NotificationFilterBarProps) {
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
    <div className="grid items-start gap-4 sm:grid-cols-2 xl:grid-cols-4">
      <Select
        label="Status"
        placeholder="All statuses"
        options={STATUS_OPTIONS}
        value={filters.status ?? ''}
        onChange={(event) => onChange({ status: oneOf(event.target.value, NOTIFICATION_STATUSES) })}
      />
      <Select
        label="Channel"
        placeholder="All channels"
        options={CHANNEL_OPTIONS}
        value={filters.channel ?? ''}
        onChange={(event) =>
          onChange({ channel: oneOf(event.target.value, NOTIFICATION_CHANNELS) })
        }
      />
      <Select
        label="Type"
        placeholder="All types"
        options={TYPE_OPTIONS}
        value={filters.type ?? ''}
        onChange={(event) => onChange({ type: oneOf(event.target.value, NOTIFICATION_TYPES) })}
      />
      {idBox('User #', 'recipientUserId')}
      {idBox('Order #', 'orderId')}
      {idBox('Restaurant #', 'restaurantId')}
      <DateRangeFields
        fromDate={filters.fromDate}
        toDate={filters.toDate}
        fromLabel="Created from"
        toLabel="Created until"
        onChange={onChange}
      />
      <div className="flex items-end sm:pt-7">
        <ClearFiltersButton onClick={onClear} />
      </div>
    </div>
  );
}
