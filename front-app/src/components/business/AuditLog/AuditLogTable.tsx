import { Link } from 'react-router';

import { Badge } from '@/components/common/Badge';
import { Table, type TableColumn } from '@/components/common/Table';
import type { AuditRecord } from '@/types/audit';
import { entityTypeLabel, recordLinkOf, serviceLabel } from '@/utils/auditUtils';
import { routeTo } from '@/utils/constants';
import { formatDateTime } from '@/utils/dateUtils';
import { humanizeEnum } from '@/utils/formatUtils';

/** The record an entry is about, linked to its page when it has one. */
export function AuditRecordLink({ record }: { record: AuditRecord }) {
  const text = `${entityTypeLabel(record.entityType)} #${record.entityId}`;
  const link = recordLinkOf(record.entityType, record.entityId);
  return link ? (
    <Link to={link} className="hover:underline">
      {text}
    </Link>
  ) : (
    <span>{text}</span>
  );
}

/** "old → new", for a table cell (long values are cut; the record's page shows them whole). */
export function AuditChange({ record }: { record: AuditRecord }) {
  if (!record.oldValue && !record.newValue) {
    return <span className="text-ink-soft">—</span>;
  }
  return (
    <span className="line-clamp-2 max-w-xs text-xs break-words">
      {record.oldValue ?? '—'} → {record.newValue ?? '—'}
    </span>
  );
}

const COLUMNS: readonly TableColumn<AuditRecord>[] = [
  {
    header: 'When',
    render: (record) => (
      <Link
        to={routeTo.auditLogDetails(record.auditId)}
        className="font-semibold whitespace-nowrap text-brand-text hover:underline"
      >
        {formatDateTime(record.timestamp)}
      </Link>
    ),
  },
  { header: 'Service', render: (record) => <Badge>{serviceLabel(record.service)}</Badge> },
  { header: 'Action', render: (record) => humanizeEnum(record.action) },
  { header: 'Record', render: (record) => <AuditRecordLink record={record} /> },
  { header: 'Change', render: (record) => <AuditChange record={record} /> },
  {
    header: 'Reason',
    render: (record) => (
      <span className="line-clamp-2 max-w-xs text-xs">{record.reason ?? '—'}</span>
    ),
  },
  {
    header: 'Admin',
    render: (record) => (
      <Link to={routeTo.customerDetails(record.adminUserId)} className="hover:underline">
        #{record.adminUserId}
      </Link>
    ),
  },
];

interface AuditLogTableProps {
  records: readonly AuditRecord[];
  isUpdating?: boolean;
  caption?: string;
}

/** Audit records; the time opens the record with the others of the same request. */
export function AuditLogTable({ records, isUpdating, caption = 'Audit log' }: AuditLogTableProps) {
  return (
    <Table
      caption={caption}
      columns={COLUMNS}
      rows={records}
      rowKey={(record) => record.auditId}
      isUpdating={isUpdating}
    />
  );
}
