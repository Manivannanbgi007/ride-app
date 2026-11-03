import { RefundFilterBar, RefundTable } from '@/components/business/Payment';
import { Card } from '@/components/common/Card';
import { PageHeader } from '@/components/common/PageHeader';
import { PagedResults } from '@/components/common/PagedResults';
import { useRefunds } from '@/hooks/api/paymentHooks';
import { useAppDispatch, useAppSelector } from '@/store/hooks';
import {
  refundFiltersChanged,
  refundFiltersCleared,
  refundPageChanged,
  selectRefundFilters,
  selectRefundPage,
} from '@/store/slices/refundListSlice';
import { DEFAULT_PAGE_SIZE } from '@/utils/constants';
import { ERROR_MESSAGES } from '@/utils/errorMessages';
import { dateRangeError } from '@/utils/validationUtils';

/** "Refunds": every refund, by admins and automatic ones; failed refunds are found with the status filter. */
export default function RefundListPage() {
  const dispatch = useAppDispatch();
  const filters = useAppSelector(selectRefundFilters);
  const page = useAppSelector(selectRefundPage);
  const isRangeValid = !dateRangeError(filters.fromDate, filters.toDate);
  const refunds = useRefunds(filters, page, DEFAULT_PAGE_SIZE, isRangeValid);
  const clearFilters = () => dispatch(refundFiltersCleared());

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title="Refunds"
        subtitle="Refunds are started from a payment; automatic ones follow cancelled orders"
      />

      <Card>
        <RefundFilterBar
          filters={filters}
          onChange={(changes) => dispatch(refundFiltersChanged(changes))}
          onClear={clearFilters}
        />
      </Card>

      <Card>
        <PagedResults
          query={refunds}
          errorTitle={ERROR_MESSAGES.REFUNDS_LOAD_FAILED}
          emptyTitle="No refunds yet"
          filteredEmptyTitle="No refunds match these filters"
          hasFilters={Object.keys(filters).length > 0}
          onClearFilters={clearFilters}
          onPageChange={(newPage) => dispatch(refundPageChanged(newPage))}
          isDisabled={!isRangeValid}
          columns={7}
        >
          {(rows, isUpdating) => <RefundTable refunds={rows} isUpdating={isUpdating} />}
        </PagedResults>
      </Card>
    </div>
  );
}
