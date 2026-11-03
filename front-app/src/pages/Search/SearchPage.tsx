import { Search } from 'lucide-react';
import { useNavigate, useSearchParams } from 'react-router';

import { CustomerTable } from '@/components/business/Customer';
import { FoodTable } from '@/components/business/Food';
import { OrderTable } from '@/components/business/Order';
import { AsyncContent } from '@/components/common/AsyncContent';
import { Button } from '@/components/common/Button';
import { Card } from '@/components/common/Card';
import { EmptyState } from '@/components/common/EmptyState';
import { TableLoader } from '@/components/common/Loader';
import { PageHeader } from '@/components/common/PageHeader';
import { useMenuItems } from '@/hooks/api/catalogHooks';
import { useOrders } from '@/hooks/api/orderHooks';
import { useUsers } from '@/hooks/api/userHooks';
import { useAppDispatch } from '@/store/hooks';
import { customerFiltersChanged } from '@/store/slices/customerListSlice';
import { foodFiltersChanged } from '@/store/slices/foodListSlice';
import { CUSTOMER_SORT_OPTIONS, FOOD_SORT_OPTIONS, ROUTES } from '@/utils/constants';
import { ERROR_MESSAGES } from '@/utils/errorMessages';
import { isWholeNumber } from '@/utils/validationUtils';

const RESULTS_PER_SECTION = 5;

/**
 * The header's "Search here". A number is looked for as an order number (and in phone numbers); text in
 * customers (name, email, phone) and in menu items (name). There is no single search API for admins, so each
 * section asks its own service; each one loads and fails on its own.
 */
export default function SearchPage() {
  const [searchParams] = useSearchParams();
  const text = (searchParams.get('q') ?? '').trim();
  const isNumber = isWholeNumber(text);

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title="Search"
        subtitle={text ? `Results for "${text}"` : 'Type in the search box above'}
      />
      {text ? (
        <>
          {isNumber && <OrderResults orderId={Number(text)} />}
          <CustomerResults text={text} />
          {!isNumber && <FoodResults text={text} />}
        </>
      ) : (
        <Card>
          <EmptyState
            icon={Search}
            title="Search orders, customers and dishes"
            message="An order number, a customer's name, email or phone, or a dish name."
          />
        </Card>
      )}
    </div>
  );
}

function OrderResults({ orderId }: { orderId: number }) {
  const orders = useOrders({ orderId }, 0, RESULTS_PER_SECTION);
  return (
    <Card title={`Order #${orderId}`}>
      <AsyncContent
        isLoading={orders.isPending}
        error={orders.error}
        isEmpty={orders.data?.content.length === 0}
        errorTitle={ERROR_MESSAGES.SEARCH_FAILED}
        onRetry={() => void orders.refetch()}
        loading={<TableLoader rows={1} columns={8} />}
        empty={<EmptyState title="No order with this number" />}
      >
        {orders.data && <OrderTable orders={orders.data.content} />}
      </AsyncContent>
    </Card>
  );
}

function CustomerResults({ text }: { text: string }) {
  const dispatch = useAppDispatch();
  const navigate = useNavigate();
  // user-service accepts at most 100 characters
  const search = text.slice(0, 100);
  const users = useUsers({ search, sort: CUSTOMER_SORT_OPTIONS[0].value }, 0, RESULTS_PER_SECTION);
  const total = users.data?.totalElements ?? 0;

  const showAll = () => {
    dispatch(customerFiltersChanged({ search }));
    void navigate(ROUTES.customers);
  };

  return (
    <Card
      title="Customers and users"
      subtitle={users.data ? `${total.toLocaleString('en-IN')} found` : undefined}
      actions={
        total > RESULTS_PER_SECTION && (
          <Button variant="outline" size="sm" onClick={showAll}>
            Show all {total}
          </Button>
        )
      }
    >
      <AsyncContent
        isLoading={users.isPending}
        error={users.error}
        isEmpty={total === 0}
        errorTitle={ERROR_MESSAGES.SEARCH_FAILED}
        onRetry={() => void users.refetch()}
        loading={<TableLoader rows={3} columns={6} />}
        empty={<EmptyState title="Nobody found" />}
      >
        {users.data && <CustomerTable users={users.data.content} />}
      </AsyncContent>
    </Card>
  );
}

function FoodResults({ text }: { text: string }) {
  const dispatch = useAppDispatch();
  const navigate = useNavigate();
  // catalog-service accepts at most 120 characters
  const search = text.slice(0, 120);
  const items = useMenuItems({ search, sort: FOOD_SORT_OPTIONS[0].value }, 0, RESULTS_PER_SECTION);
  const total = items.data?.totalElements ?? 0;

  const showAll = () => {
    dispatch(foodFiltersChanged({ search }));
    void navigate(ROUTES.foods);
  };

  return (
    <Card
      title="Menu items"
      subtitle={items.data ? `${total.toLocaleString('en-IN')} found` : undefined}
      actions={
        total > RESULTS_PER_SECTION && (
          <Button variant="outline" size="sm" onClick={showAll}>
            Show all {total}
          </Button>
        )
      }
    >
      <AsyncContent
        isLoading={items.isPending}
        error={items.error}
        isEmpty={total === 0}
        errorTitle={ERROR_MESSAGES.SEARCH_FAILED}
        onRetry={() => void items.refetch()}
        loading={<TableLoader rows={3} columns={7} />}
        empty={<EmptyState title="No dish found" />}
      >
        {items.data && <FoodTable items={items.data.content} />}
      </AsyncContent>
    </Card>
  );
}
