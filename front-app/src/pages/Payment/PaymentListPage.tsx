import { Undo2 } from 'lucide-react';
import { Link } from 'react-router';

import { PaymentFilterBar, PaymentSummaryCards, PaymentTable } from '@/components/business/Payment';
import { AsyncContent } from '@/components/common/AsyncContent';
import { buttonStyles } from '@/components/common/Button';
import { Card } from '@/components/common/Card';
import { Skeleton } from '@/components/common/Loader';
import { PageHeader } from '@/components/common/PageHeader';
import { PagedResults } from '@/components/common/PagedResults';
import { usePayments, usePaymentSummary } from '@/hooks/api/paymentHooks';
import { useAppDispatch, useAppSelector } from '@/store/hooks';
import {
  paymentFiltersChanged,
  paymentFiltersCleared,
  paymentPageChanged,
  selectPaymentFilters,
  selectPaymentPage,
} from '@/store/slices/paymentListSlice';
import { DEFAULT_PAGE_SIZE, ROUTES } from '@/utils/constants';
import { ERROR_MESSAGES } from '@/utils/errorMessages';
import { dateRangeError } from '@/utils/validationUtils';

/**
 * "Payments" (the design's Wallet): the period's money at the top (today, or the chosen dates), and every payment
 * below; failed payments are found with the status filter.
 */
export default function PaymentListPage() {
  const dispatch = useAppDispatch();
  const filters = useAppSelector(selectPaymentFilters);
  const page = useAppSelector(selectPaymentPage);
  const isRangeValid = !dateRangeError(filters.fromDate, filters.toDate);
  const payments = usePayments(filters, page, DEFAULT_PAGE_SIZE, isRangeValid);
  const summary = usePaymentSummary(filters.fromDate, filters.toDate, isRangeValid);
  const clearFilters = () => dispatch(paymentFiltersCleared());

  return (
    <div className="flex flex-col gap-5">
      <PageHeader
        title="Payments"
        subtitle="Online payments and their refunds"
        actions={
          <Link to={ROUTES.refunds} className={buttonStyles('outline', 'md')}>
            <Undo2 aria-hidden="true" className="size-4" />
            All refunds
          </Link>
        }
      />

      <AsyncContent
        isLoading={summary.isPending && isRangeValid}
        error={summary.error}
        errorTitle={ERROR_MESSAGES.PAYMENT_SUMMARY_LOAD_FAILED}
        onRetry={() => void summary.refetch()}
        loading={
          <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
            {Array.from({ length: 4 }, (_, index) => (
              <Skeleton key={index} className="h-[4.5rem] rounded-2xl bg-surface" />
            ))}
          </div>
        }
      >
        {summary.data && <PaymentSummaryCards summary={summary.data} />}
      </AsyncContent>

      <Card>
        <PaymentFilterBar
          filters={filters}
          onChange={(changes) => dispatch(paymentFiltersChanged(changes))}
          onClear={clearFilters}
        />
      </Card>

      <Card>
        <PagedResults
          query={payments}
          errorTitle={ERROR_MESSAGES.PAYMENTS_LOAD_FAILED}
          emptyTitle="No payments yet"
          filteredEmptyTitle="No payments match these filters"
          hasFilters={Object.keys(filters).length > 0}
          onClearFilters={clearFilters}
          onPageChange={(newPage) => dispatch(paymentPageChanged(newPage))}
          isDisabled={!isRangeValid}
          columns={7}
        >
          {(rows, isUpdating) => <PaymentTable payments={rows} isUpdating={isUpdating} />}
        </PagedResults>
      </Card>
    </div>
  );
}
