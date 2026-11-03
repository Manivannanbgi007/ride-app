import type { ReactNode } from 'react';

import { AsyncContent } from '@/components/common/AsyncContent';
import { ClearFiltersButton } from '@/components/common/ClearFiltersButton';
import { EmptyState } from '@/components/common/EmptyState';
import { TableLoader } from '@/components/common/Loader';
import { Pagination } from '@/components/common/Pagination';
import type { PageResponse } from '@/types/api';

/** The parts of a React Query result this component reads (what useOrders(), useUsers(), ... return). */
interface PagedQuery<T> {
  data?: PageResponse<T>;
  isPending: boolean;
  isFetching: boolean;
  isPlaceholderData: boolean;
  error: unknown;
  refetch: () => Promise<unknown>;
}

interface PagedResultsProps<T> {
  query: PagedQuery<T>;
  errorTitle: string;
  /** "No restaurants yet" */
  emptyTitle: string;
  /** "No restaurants match these filters": shown, with a "Clear filters" button, when filters are set */
  filteredEmptyTitle: string;
  hasFilters: boolean;
  onClearFilters: () => void;
  onPageChange: (page: number) => void;
  /** The query is held back (an invalid filter): nothing is shown meanwhile */
  isDisabled?: boolean;
  /** Columns of the loading placeholder */
  columns?: number;
  /** Replaces the table placeholder while loading (e.g. cards) */
  loading?: ReactNode;
  /** The text of the footer, from what is on this page and in all (default: "Showing 1-20 of 303") */
  summary?: (shown: number, total: number) => string;
  /** The rows, e.g. as a table; isUpdating = another page is loading (dim the rows) */
  children: (rows: readonly T[], isUpdating: boolean) => ReactNode;
}

/**
 * The result part of every admin list: loading, error with "Try again", empty (with "Clear filters" when
 * filters are set), or the rows and the page buttons under them.
 */
export function PagedResults<T>({
  query,
  errorTitle,
  emptyTitle,
  filteredEmptyTitle,
  hasFilters,
  onClearFilters,
  onPageChange,
  isDisabled = false,
  columns = 6,
  loading,
  summary,
  children,
}: PagedResultsProps<T>) {
  return (
    <AsyncContent
      isLoading={query.isPending && !isDisabled}
      error={query.error}
      isEmpty={query.data?.content.length === 0}
      errorTitle={errorTitle}
      onRetry={() => void query.refetch()}
      isRetrying={query.isFetching}
      loading={loading ?? <TableLoader columns={columns} />}
      empty={
        <EmptyState
          title={hasFilters ? filteredEmptyTitle : emptyTitle}
          action={hasFilters && <ClearFiltersButton size="sm" onClick={onClearFilters} />}
        />
      }
    >
      {query.data && (
        <div className="flex flex-col gap-4">
          {children(query.data.content, query.isPlaceholderData)}
          <Pagination
            page={query.data.page}
            size={query.data.size}
            totalPages={query.data.totalPages}
            totalElements={query.data.totalElements}
            onPageChange={onPageChange}
            summary={summary?.(query.data.content.length, query.data.totalElements)}
          />
        </div>
      )}
    </AsyncContent>
  );
}
