import { ClearFiltersButton } from '@/components/common/ClearFiltersButton';
import { DateRangeFields } from '@/components/common/DateRangeFields';
import { SearchInput } from '@/components/common/SearchInput';
import { Select } from '@/components/common/Select';
import type { AuditActionCount, AuditLogFilters } from '@/types/audit';
import { AUDIT_ENTITY_TYPE_LABELS, serviceLabel } from '@/utils/auditUtils';
import { AUDIT_LOG_SORT_OPTIONS } from '@/utils/constants';
import { formatNumber, humanizeEnum } from '@/utils/formatUtils';
import { idFilterError, toIdFilter } from '@/utils/validationUtils';

const ENTITY_TYPE_OPTIONS = Object.entries(AUDIT_ENTITY_TYPE_LABELS).map(([value, label]) => ({
  value,
  label,
}));

interface AuditLogFilterBarProps {
  filters: AuditLogFilters;
  /** What the log has (GET /audit-logs/actions): the service and action lists are built from it */
  actions: readonly AuditActionCount[];
  onChange: (changes: Partial<AuditLogFilters>) => void;
  onClear: () => void;
}

/**
 * The audit log's search: service and action (the actions of the chosen service, with how many records each
 * has), record, admin, request, text in the reason or values, dates and order.
 */
export function AuditLogFilterBar({ filters, actions, onChange, onClear }: AuditLogFilterBarProps) {
  const services = [...new Set(actions.map((entry) => entry.service))];
  const actionOptions = actions
    .filter((entry) => !filters.service || entry.service === filters.service)
    .map((entry) => ({
      value: entry.action,
      label: `${humanizeEnum(entry.action)} (${formatNumber(entry.records)})`,
    }))
    // The same action name in two services is one choice
    .filter(
      (option, index, all) => all.findIndex((other) => other.value === option.value) === index,
    );

  return (
    <div className="grid items-start gap-4 sm:grid-cols-2 xl:grid-cols-4">
      <SearchInput
        label="Search"
        placeholder="Text in the reason or the values"
        maxLength={100}
        value={filters.q ?? ''}
        onSearch={(text) => onChange({ q: text })}
      />
      <Select
        label="Service"
        placeholder="All services"
        options={services.map((service) => ({ value: service, label: serviceLabel(service) }))}
        value={filters.service ?? ''}
        // Another service has other actions: the chosen action no longer applies
        onChange={(event) => onChange({ service: event.target.value, action: undefined })}
      />
      <Select
        label="Action"
        placeholder="All actions"
        options={actionOptions}
        value={filters.action ?? ''}
        onChange={(event) => onChange({ action: event.target.value })}
      />
      <Select
        label="Order"
        options={AUDIT_LOG_SORT_OPTIONS}
        value={filters.sort}
        onChange={(event) => onChange({ sort: event.target.value })}
      />
      <Select
        label="Record type"
        placeholder="All records"
        options={ENTITY_TYPE_OPTIONS}
        value={filters.entityType ?? ''}
        onChange={(event) => onChange({ entityType: event.target.value })}
      />
      <SearchInput
        label="Record #"
        maxLength={64}
        value={filters.entityId ?? ''}
        onSearch={(text) => onChange({ entityId: text })}
      />
      <SearchInput
        label="Admin #"
        inputMode="numeric"
        value={filters.adminUserId?.toString() ?? ''}
        validate={idFilterError}
        onSearch={(text) => onChange({ adminUserId: toIdFilter(text) })}
      />
      <SearchInput
        label="Request id"
        placeholder="X-Request-Id"
        maxLength={64}
        value={filters.requestId ?? ''}
        onSearch={(text) => onChange({ requestId: text })}
      />
      <DateRangeFields fromDate={filters.fromDate} toDate={filters.toDate} onChange={onChange} />
      <div className="flex items-end sm:pt-7">
        <ClearFiltersButton onClick={onClear} />
      </div>
    </div>
  );
}
