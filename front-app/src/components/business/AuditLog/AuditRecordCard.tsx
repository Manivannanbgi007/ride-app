import { Link } from 'react-router';

import { AuditRecordLink } from '@/components/business/AuditLog/AuditLogTable';
import { Card } from '@/components/common/Card';
import { DescriptionList } from '@/components/common/DescriptionList';
import type { AuditRecordDetails } from '@/types/audit';
import { serviceLabel } from '@/utils/auditUtils';
import { routeTo } from '@/utils/constants';
import { formatDateTime } from '@/utils/dateUtils';

function Value({ text }: { text: string }) {
  return <span className="font-mono text-xs break-all whitespace-pre-wrap">{text}</span>;
}

/** One audit record in full: who, what, the old and new value, why, and from where. */
export function AuditRecordCard({ details }: { details: AuditRecordDetails }) {
  const { record } = details;
  return (
    <Card title="Record" subtitle="Audit records can never be changed or deleted">
      <DescriptionList
        items={[
          { label: 'Record', value: <AuditRecordLink record={record} /> },
          {
            label: 'Admin',
            value: (
              <Link
                to={routeTo.customerDetails(record.adminUserId)}
                className="text-brand-text hover:underline"
              >
                User #{record.adminUserId}
              </Link>
            ),
          },
          { label: 'When', value: formatDateTime(record.timestamp) },
          { label: 'Old value', value: record.oldValue && <Value text={record.oldValue} /> },
          { label: 'New value', value: record.newValue && <Value text={record.newValue} /> },
          { label: 'Reason', value: record.reason },
          {
            label: 'Saved by',
            value: `${serviceLabel(record.service)} (${record.service}${record.sourceAuditId ? ` #${record.sourceAuditId}` : ''})`,
          },
          { label: 'Reached this log', value: formatDateTime(details.receivedAt) },
          { label: 'IP address', value: record.ipAddress },
          { label: 'Request id', value: record.requestId && <Value text={record.requestId} /> },
        ]}
      />
    </Card>
  );
}
