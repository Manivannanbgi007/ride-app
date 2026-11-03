import { useNavigate } from 'react-router';

import { AuditHistory } from '@/components/business/AuditHistory';
import {
  CustomerLikedFoods,
  CustomerOrderSummaryCard,
  CustomerProfileCard,
  CustomerSpendCard,
  CustomerTopFoods,
} from '@/components/business/Customer';
import { OrderTable } from '@/components/business/Order';
import { AsyncContent } from '@/components/common/AsyncContent';
import { BackLink } from '@/components/common/BackLink';
import { Button } from '@/components/common/Button';
import { Card } from '@/components/common/Card';
import { EmptyState } from '@/components/common/EmptyState';
import { PageHeader } from '@/components/common/PageHeader';
import { useCustomerOrderSummary, useOrders } from '@/hooks/api/orderHooks';
import { useUser, useUserAuditLogs } from '@/hooks/api/userHooks';
import { useIdParam } from '@/hooks/useIdParam';
import { NotFoundPage } from '@/pages/NotFound/NotFoundPage';
import { useAppDispatch } from '@/store/hooks';
import { orderFiltersReplaced } from '@/store/slices/orderListSlice';
import type { AdminUserDetails } from '@/types/user';
import { ROUTES } from '@/utils/constants';
import { ERROR_MESSAGES } from '@/utils/errorMessages';

const RECENT_ORDERS = 5;

/** "Customer Detail": one account, what the customer orders and likes, their orders, and the admin actions. */
export default function CustomerDetailsPage() {
  const userId = useIdParam('userId');
  return userId === null ? <NotFoundPage /> : <CustomerDetails userId={userId} />;
}

function CustomerDetails({ userId }: { userId: number }) {
  const user = useUser(userId);

  return (
    <div className="flex flex-col gap-6">
      <BackLink to={ROUTES.customers}>Back to customers</BackLink>
      <AsyncContent
        isLoading={user.isPending}
        error={user.error}
        errorTitle={ERROR_MESSAGES.CUSTOMER_LOAD_FAILED}
        onRetry={() => void user.refetch()}
        isRetrying={user.isFetching}
      >
        {user.data && (
          <>
            <PageHeader title="Customer Detail" subtitle="Here is your customer's detail profile" />
            <CustomerSections user={user.data} />
          </>
        )}
      </AsyncContent>
    </div>
  );
}

/** Only customers have orders and ratings: the other roles show their profile and change history. */
function CustomerSections({ user }: { user: AdminUserDetails }) {
  const isCustomer = user.role === 'CUSTOMER';
  return (
    <>
      <div className="grid gap-6 xl:grid-cols-3">
        <div className={isCustomer ? 'xl:col-span-2' : 'xl:col-span-3'}>
          <CustomerProfileCard user={user} />
        </div>
        {isCustomer && (
          <CustomerSpendCard
            customerId={user.id}
            customerName={user.fullName}
            memberSince={user.createdAt}
          />
        )}
      </div>
      {isCustomer && (
        <div className="grid gap-6 xl:grid-cols-2">
          <CustomerTopFoods customerId={user.id} />
          <CustomerLikedFoods customerId={user.id} />
        </div>
      )}
      {isCustomer && <CustomerOrderSummary customerId={user.id} />}
      <div className="grid gap-6 xl:grid-cols-3">
        {isCustomer && (
          <div className="xl:col-span-2">
            <LatestOrders customerId={user.id} />
          </div>
        )}
        <div className={isCustomer ? undefined : 'xl:col-span-3'}>
          <ChangeHistory userId={user.id} />
        </div>
      </div>
    </>
  );
}

function ChangeHistory({ userId }: { userId: number }) {
  const auditLogs = useUserAuditLogs(userId);
  return (
    <Card title="Change history" subtitle="Admin actions on this account">
      <AsyncContent
        isLoading={auditLogs.isPending}
        error={auditLogs.error}
        errorTitle={ERROR_MESSAGES.AUDIT_LOAD_FAILED}
        onRetry={() => void auditLogs.refetch()}
      >
        {auditLogs.data && <AuditHistory entries={auditLogs.data.content} />}
      </AsyncContent>
    </Card>
  );
}

/** A customer's order numbers. */
function CustomerOrderSummary({ customerId }: { customerId: number }) {
  const summary = useCustomerOrderSummary(customerId);
  return (
    <AsyncContent
      isLoading={summary.isPending}
      error={summary.error}
      errorTitle={ERROR_MESSAGES.CUSTOMER_ORDERS_LOAD_FAILED}
      onRetry={() => void summary.refetch()}
    >
      {summary.data && <CustomerOrderSummaryCard summary={summary.data} />}
    </AsyncContent>
  );
}

/** The latest orders, with a way to see all of them in the order list. */
function LatestOrders({ customerId }: { customerId: number }) {
  const dispatch = useAppDispatch();
  const navigate = useNavigate();
  const recentOrders = useOrders({ customerId }, 0, RECENT_ORDERS);

  const showAllOrders = () => {
    dispatch(orderFiltersReplaced({ customerId }));
    void navigate(ROUTES.orders);
  };

  return (
    <Card
      title="Latest orders"
      actions={
        <Button variant="outline" size="sm" onClick={showAllOrders}>
          All orders of this customer
        </Button>
      }
    >
      <AsyncContent
        isLoading={recentOrders.isPending}
        error={recentOrders.error}
        isEmpty={recentOrders.data?.content.length === 0}
        errorTitle={ERROR_MESSAGES.CUSTOMER_ORDERS_LOAD_FAILED}
        onRetry={() => void recentOrders.refetch()}
        empty={<EmptyState title="No orders yet" />}
      >
        {recentOrders.data && <OrderTable orders={recentOrders.data.content} />}
      </AsyncContent>
    </Card>
  );
}
