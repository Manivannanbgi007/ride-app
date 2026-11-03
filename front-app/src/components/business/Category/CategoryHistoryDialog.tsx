import { AuditHistory } from '@/components/business/AuditHistory';
import { AsyncContent } from '@/components/common/AsyncContent';
import { Modal } from '@/components/common/Modal';
import { useCategoryAuditLogs } from '@/hooks/api/catalogHooks';
import type { Category } from '@/types/catalog';
import { ERROR_MESSAGES } from '@/utils/errorMessages';

/** The admin changes of one category (the history is kept even after the category is deleted). */
export function CategoryHistoryDialog({
  category,
  onClose,
}: {
  category: Category;
  onClose: () => void;
}) {
  const auditLogs = useCategoryAuditLogs(category.id);
  return (
    <Modal open title={`History of "${category.name}"`} onClose={onClose} size="lg">
      <AsyncContent
        isLoading={auditLogs.isPending}
        error={auditLogs.error}
        errorTitle={ERROR_MESSAGES.AUDIT_LOAD_FAILED}
        onRetry={() => void auditLogs.refetch()}
      >
        {auditLogs.data && <AuditHistory entries={auditLogs.data.content} />}
      </AsyncContent>
    </Modal>
  );
}
