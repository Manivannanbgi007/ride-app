import { DatabaseBackup } from 'lucide-react';
import { useState } from 'react';

import { AuditLogFilterBar, AuditLogTable } from '@/components/business/AuditLog';
import { Button } from '@/components/common/Button';
import { Card } from '@/components/common/Card';
import { PageHeader } from '@/components/common/PageHeader';
import { PagedResults } from '@/components/common/PagedResults';
import { ReasonDialog } from '@/components/common/ReasonDialog';
import { useToast } from '@/context/ToastContext';
import { useAuditActions, useAuditBackfill, useAuditLogs } from '@/hooks/api/auditLogHooks';
import { useAppDispatch, useAppSelector } from '@/store/hooks';
import {
  auditLogFiltersChanged,
  auditLogFiltersCleared,
  auditLogPageChanged,
  selectAuditLogFilters,
  selectAuditLogPage,
} from '@/store/slices/auditLogListSlice';
import type { AuditBackfillResult } from '@/types/audit';
import { serviceLabel } from '@/utils/auditUtils';
import { DEFAULT_PAGE_SIZE } from '@/utils/constants';
import { ERROR_MESSAGES } from '@/utils/errorMessages';
import { formatNumber } from '@/utils/formatUtils';
import { dateRangeError } from '@/utils/validationUtils';

/** "Audit Logs": every audited admin action of every service, newest first. Read-only. */
export default function AuditLogListPage() {
  const dispatch = useAppDispatch();
  const filters = useAppSelector(selectAuditLogFilters);
  const page = useAppSelector(selectAuditLogPage);
  const isRangeValid = !dateRangeError(filters.fromDate, filters.toDate);
  const records = useAuditLogs(filters, page, DEFAULT_PAGE_SIZE, isRangeValid);
  const actions = useAuditActions();
  const clearFilters = () => dispatch(auditLogFiltersCleared());
  // The order is not a filter: "Clear filters" is offered only when the search itself is narrowed
  const hasFilters = Object.keys(filters).some((key) => key !== 'sort');

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title="Audit Logs"
        subtitle="Every admin action on the platform: who did what, when, and why"
        actions={<BackfillAction />}
      />
      <Card>
        <AuditLogFilterBar
          filters={filters}
          actions={actions.data ?? []}
          onChange={(changes) => dispatch(auditLogFiltersChanged(changes))}
          onClear={clearFilters}
        />
      </Card>
      <Card>
        <PagedResults
          query={records}
          errorTitle={ERROR_MESSAGES.AUDIT_LOG_LOAD_FAILED}
          emptyTitle="No admin actions yet"
          filteredEmptyTitle="No records match these filters"
          hasFilters={hasFilters}
          onClearFilters={clearFilters}
          onPageChange={(newPage) => dispatch(auditLogPageChanged(newPage))}
          isDisabled={!isRangeValid}
          columns={7}
        >
          {(rows, isUpdating) => <AuditLogTable records={rows} isUpdating={isUpdating} />}
        </PagedResults>
      </Card>
    </div>
  );
}

function backfillMessage(result: AuditBackfillResult): string {
  const sent = Object.values(result.republished).reduce((sum, count) => sum + count, 0);
  const services = Object.keys(result.republished).length;
  const missing = result.unavailable.map(serviceLabel).join(', ');
  return (
    `${formatNumber(sent)} records sent again by ${services} services; new ones appear within seconds.` +
    (missing ? ` Not reached: ${missing}. Run it again later.` : '')
  );
}

/**
 * Fills the log with the records the services made before it existed. Safe to repeat (copies already in the
 * log are skipped), and audited itself.
 */
function BackfillAction() {
  const [open, setOpen] = useState(false);
  const backfill = useAuditBackfill();
  const { showToast } = useToast();

  const submit = async (reason: string) => {
    const result = await backfill.mutateAsync(reason);
    showToast(backfillMessage(result), result.unavailable.length > 0 ? 'info' : 'success');
  };

  return (
    <>
      <Button
        variant="outline"
        onClick={() => setOpen(true)}
        leftIcon={<DatabaseBackup aria-hidden="true" className="size-4" />}
      >
        Load older records
      </Button>
      <ReasonDialog
        open={open}
        title="Load older records?"
        description="Every service sends its audit records again, so records made before this log existed are added. Records already here are not added twice. This is recorded in the log too."
        confirmLabel="Load records"
        onSubmit={submit}
        onClose={() => setOpen(false)}
      />
    </>
  );
}
