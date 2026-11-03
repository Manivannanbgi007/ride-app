import { History } from 'lucide-react';

import { EmptyState } from '@/components/common/EmptyState';
import type { AuditLogEntry } from '@/types/api';
import { formatDateTime } from '@/utils/dateUtils';
import { humanizeEnum } from '@/utils/formatUtils';

/**
 * What admins changed on a record, newest first: the action, who and when, the old and new value, and the reason
 * they gave. (Every service keeps these records; they cannot be changed or deleted.)
 */
export function AuditHistory({ entries }: { entries: readonly AuditLogEntry[] }) {
  if (entries.length === 0) {
    return <EmptyState icon={History} title="No admin changes yet" />;
  }
  return (
    <ol className="flex flex-col gap-4">
      {entries.map((entry) => (
        <li key={entry.auditId} className="rounded-xl bg-page px-4 py-3 text-sm">
          <div className="flex flex-wrap items-baseline justify-between gap-2">
            <p className="font-semibold">{humanizeEnum(entry.action)}</p>
            <p className="text-xs text-ink-soft">
              {formatDateTime(entry.timestamp)} by admin #{entry.adminUserId}
            </p>
          </div>
          {(entry.oldValue || entry.newValue) && (
            <p className="mt-1 text-xs">
              {entry.oldValue ?? '—'} → {entry.newValue ?? '—'}
            </p>
          )}
          {entry.reason && <p className="mt-1 text-ink-soft">"{entry.reason}"</p>}
        </li>
      ))}
    </ol>
  );
}
