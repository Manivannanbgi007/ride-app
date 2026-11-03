import { CalendarDays } from 'lucide-react';

import { DateRangeFields } from '@/components/common/DateRangeFields';
import { Select } from '@/components/common/Select';
import {
  REVIEW_PERIODS,
  REVIEW_PERIOD_LABELS,
  describePeriod,
  periodScope,
  type ReviewPeriod,
  type ReviewPeriodKind,
} from '@/utils/reviewUtils';

interface ReviewPeriodFilterProps {
  period: ReviewPeriod;
  onChange: (period: ReviewPeriod) => void;
}

const OPTIONS = REVIEW_PERIODS.map((kind) => ({ value: kind, label: REVIEW_PERIOD_LABELS[kind] }));

/**
 * "Filter Period": the same tile as the dashboard's, with the last 7, 30 or 90 days, all time, or two days of
 * the admin's choice (the date fields appear under the tile).
 */
export function ReviewPeriodFilter({ period, onChange }: ReviewPeriodFilterProps) {
  return (
    <div className="flex flex-col items-stretch gap-3 sm:items-end">
      <div className="flex items-center gap-4 rounded-2xl bg-surface px-5 py-3 shadow-card">
        <span className="flex size-11 items-center justify-center rounded-xl bg-info-50 text-info-500">
          <CalendarDays aria-hidden="true" className="size-5" />
        </span>
        <div>
          <Select
            variant="inline"
            label="Filter Period"
            options={OPTIONS}
            value={period.kind}
            onChange={(event) => onChange({ kind: event.target.value as ReviewPeriodKind })}
            className="-ml-1 px-1"
          />
          <p className="text-xs text-ink-soft">{describePeriod(periodScope(period))}</p>
        </div>
      </div>
      {period.kind === 'custom' && (
        <div className="grid gap-3 rounded-2xl bg-surface p-4 shadow-card sm:grid-cols-2">
          <DateRangeFields
            fromDate={period.from}
            toDate={period.to}
            onChange={(changes) =>
              onChange({
                kind: 'custom',
                from: 'fromDate' in changes ? changes.fromDate : period.from,
                to: 'toDate' in changes ? changes.toDate : period.to,
              })
            }
          />
        </div>
      )}
    </div>
  );
}
