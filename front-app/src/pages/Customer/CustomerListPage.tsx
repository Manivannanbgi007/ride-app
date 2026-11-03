import { CustomerFilterBar, CustomerTable } from '@/components/business/Customer';
import { Card } from '@/components/common/Card';
import { PageHeader } from '@/components/common/PageHeader';
import { PagedResults } from '@/components/common/PagedResults';
import { useUsers } from '@/hooks/api/userHooks';
import { useAppDispatch, useAppSelector } from '@/store/hooks';
import {
  customerFiltersChanged,
  customerFiltersCleared,
  customerPageChanged,
  selectCustomerFilters,
  selectCustomerPage,
} from '@/store/slices/customerListSlice';
import { DEFAULT_PAGE_SIZE } from '@/utils/constants';
import { ERROR_MESSAGES } from '@/utils/errorMessages';

/** "Customer": every account (customers, restaurant owners, delivery partners, admins), with search and filters. */
export default function CustomerListPage() {
  const dispatch = useAppDispatch();
  const filters = useAppSelector(selectCustomerFilters);
  const page = useAppSelector(selectCustomerPage);
  const users = useUsers(filters, page, DEFAULT_PAGE_SIZE);
  const clearFilters = () => dispatch(customerFiltersCleared());

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title="Customers"
        subtitle="Every account: customers, restaurant owners, delivery partners and admins"
      />

      <Card>
        <CustomerFilterBar
          filters={filters}
          onChange={(changes) => dispatch(customerFiltersChanged(changes))}
          onClear={clearFilters}
        />
      </Card>

      <Card>
        <PagedResults
          query={users}
          errorTitle={ERROR_MESSAGES.CUSTOMERS_LOAD_FAILED}
          emptyTitle="No users yet"
          filteredEmptyTitle="Nobody matches these filters"
          hasFilters={Boolean(filters.search || filters.role || filters.status)}
          onClearFilters={clearFilters}
          onPageChange={(newPage) => dispatch(customerPageChanged(newPage))}
        >
          {(rows, isUpdating) => <CustomerTable users={rows} isUpdating={isUpdating} />}
        </PagedResults>
      </Card>
    </div>
  );
}
