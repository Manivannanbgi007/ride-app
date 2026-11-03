import { Download } from 'lucide-react';
import type { ReactNode } from 'react';

import { PartialDataAlert } from '@/components/business/PartialDataAlert';
import { AsyncContent } from '@/components/common/AsyncContent';
import { Button } from '@/components/common/Button';
import { SkeletonLoader } from '@/components/common/Loader';
import { useToast } from '@/context/ToastContext';
import { downloadCsv } from '@/utils/csvUtils';
import { formatDate } from '@/utils/dateUtils';
import { ERROR_MESSAGES } from '@/utils/errorMessages';

interface Report {
  fromDate: string;
  toDate: string;
  unavailable: string[];
}

/** The parts of a React Query result this component reads. */
interface ReportQuery<T> {
  data?: T;
  isPending: boolean;
  isFetching: boolean;
  error: unknown;
  refetch: () => Promise<unknown>;
}

interface ReportPanelProps<T extends Report> {
  query: ReportQuery<T>;
  /** The CSV file's name and rows ("Save Report") */
  fileName: (report: T) => string;
  csvRows: (report: T) => ReadonlyArray<ReadonlyArray<string | number | null>>;
  /** More controls next to "Save Report" (revenue: per day / week / month) */
  actions?: ReactNode;
  children: (report: T) => ReactNode;
}

/**
 * One analytics report: loading and error states, the period it covers, "Save Report" (CSV), the warning when a
 * service did not answer, and the report itself.
 */
export function ReportPanel<T extends Report>({
  query,
  fileName,
  csvRows,
  actions,
  children,
}: ReportPanelProps<T>) {
  const { showToast } = useToast();
  const report = query.data;

  const save = () => {
    if (report) {
      downloadCsv(fileName(report), csvRows(report));
      showToast('Report saved to your downloads.');
    }
  };

  return (
    <AsyncContent
      isLoading={query.isPending}
      error={query.error}
      errorTitle={ERROR_MESSAGES.REPORT_LOAD_FAILED}
      onRetry={() => void query.refetch()}
      isRetrying={query.isFetching}
      loading={<SkeletonLoader lines={6} className="rounded-2xl bg-surface p-6" />}
    >
      {report && (
        <div className="flex flex-col gap-4">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <p className="text-sm text-ink-soft">
              {formatDate(report.fromDate)} - {formatDate(report.toDate)}
            </p>
            <div className="flex flex-wrap items-center gap-3">
              {actions}
              <Button
                variant="outline"
                size="sm"
                onClick={save}
                leftIcon={<Download aria-hidden="true" className="size-4" />}
              >
                Save Report
              </Button>
            </div>
          </div>
          <PartialDataAlert unavailable={report.unavailable} />
          {children(report)}
        </div>
      )}
    </AsyncContent>
  );
}
