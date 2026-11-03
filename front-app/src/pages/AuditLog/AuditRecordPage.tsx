import { AuditLogTable, AuditRecordCard } from '@/components/business/AuditLog';
import { AsyncContent } from '@/components/common/AsyncContent';
import { BackLink } from '@/components/common/BackLink';
import { Card } from '@/components/common/Card';
import { EmptyState } from '@/components/common/EmptyState';
import { PageHeader } from '@/components/common/PageHeader';
import { useAuditRecord } from '@/hooks/api/auditLogHooks';
import { useIdParam } from '@/hooks/useIdParam';
import { NotFoundPage } from '@/pages/NotFound/NotFoundPage';
import { serviceLabel } from '@/utils/auditUtils';
import { ROUTES } from '@/utils/constants';
import { ERROR_MESSAGES } from '@/utils/errorMessages';
import { humanizeEnum } from '@/utils/formatUtils';

/** One audit record, with the other records of the same admin request (an order cancelled and refunded, ...). */
export default function AuditRecordPage() {
  const auditId = useIdParam('auditId');
  return auditId === null ? <NotFoundPage /> : <AuditRecordDetails auditId={auditId} />;
}

function AuditRecordDetails({ auditId }: { auditId: number }) {
  const details = useAuditRecord(auditId);

  return (
    <div className="flex flex-col gap-6">
      <BackLink to={ROUTES.auditLogs}>Back to the audit log</BackLink>
      <AsyncContent
        isLoading={details.isPending}
        error={details.error}
        errorTitle={ERROR_MESSAGES.AUDIT_RECORD_LOAD_FAILED}
        onRetry={() => void details.refetch()}
        isRetrying={details.isFetching}
      >
        {details.data && (
          <>
            <PageHeader
              title={humanizeEnum(details.data.record.action)}
              subtitle={`Audit record #${details.data.record.auditId} from ${serviceLabel(details.data.record.service)}`}
            />
            <AuditRecordCard details={details.data} />
            <Card
              title="Same request"
              subtitle="Other changes made by the same admin action (same request id)"
            >
              {details.data.sameRequest.length === 0 ? (
                <EmptyState title="No other changes in this request" />
              ) : (
                <AuditLogTable
                  records={details.data.sameRequest}
                  caption="Other records of the same request"
                />
              )}
            </Card>
          </>
        )}
      </AsyncContent>
    </div>
  );
}
