import { ClipboardList } from 'lucide-react';
import { Link } from 'react-router';

import { MyOrderCard } from '@/components/business/MyOrders';
import { AsyncContent } from '@/components/common/AsyncContent';
import { Button } from '@/components/common/Button';
import { EmptyState } from '@/components/common/EmptyState';
import { Skeleton } from '@/components/common/Loader';
import { PageHeader } from '@/components/common/PageHeader';
import { Pagination } from '@/components/common/Pagination';
import { Select } from '@/components/common/Select';
import { buttonStyles } from '@/components/common/Button';
import { useMyOrders } from '@/hooks/api/myOrderHooks';
import { pageFromParam, pageToParam, useQueryParams } from '@/hooks/useQueryParams';
import { ORDER_STATUSES } from '@/types/order';
import { cn } from '@/utils/classNames';
import { CUSTOMER_ORDER_STATUS_LABELS, CUSTOMER_ROUTES } from '@/utils/constants';
import { ERROR_MESSAGES } from '@/utils/errorMessages';
import { oneOf } from '@/utils/validationUtils';

const PAGE_SIZE = 10;
const STATUS_OPTIONS = ORDER_STATUSES.map((status) => ({
  value: status,
  label: CUSTOMER_ORDER_STATUS_LABELS[status],
}));

/** "My orders": newest first, with a status filter; each opens its page (progress, delivery, cancel). */
export default function MyOrdersPage() {
  const [params, setParams] = useQueryParams(['status', 'page'] as const);
  const status = oneOf(params.status ?? '', ORDER_STATUSES);
  const orders = useMyOrders(status, pageFromParam(params.page), PAGE_SIZE);

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title="My orders"
        subtitle="Newest first"
        actions={
          <div className="w-64">
            <Select
              aria-label="Show"
              placeholder="All orders"
              options={STATUS_OPTIONS}
              value={status ?? ''}
              onChange={(event) => setParams({ status: event.target.value })}
            />
          </div>
        }
      />
      <AsyncContent
        isLoading={orders.isPending}
        error={orders.error}
        isEmpty={orders.data?.content.length === 0}
        errorTitle={ERROR_MESSAGES.MY_ORDERS_LOAD_FAILED}
        onRetry={() => void orders.refetch()}
        isRetrying={orders.isFetching}
        loading={
          <div className="flex flex-col gap-4">
            {Array.from({ length: 3 }, (_, index) => (
              <Skeleton key={index} className="h-24 rounded-2xl bg-surface" />
            ))}
          </div>
        }
        empty={
          <EmptyState
            icon={ClipboardList}
            title={status ? 'No orders with this status' : 'No orders yet'}
            action={
              status ? (
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => setParams({ status: undefined })}
                >
                  Show all orders
                </Button>
              ) : (
                <Link to={CUSTOMER_ROUTES.restaurants} className={buttonStyles()}>
                  Find food
                </Link>
              )
            }
          />
        }
      >
        {orders.data && (
          <div className="flex flex-col gap-4">
            <div className={cn('flex flex-col gap-4', orders.isPlaceholderData && 'opacity-60')}>
              {orders.data.content.map((order) => (
                <MyOrderCard key={order.id} order={order} />
              ))}
            </div>
            <Pagination
              page={orders.data.page}
              size={orders.data.size}
              totalPages={orders.data.totalPages}
              totalElements={orders.data.totalElements}
              onPageChange={(page) => setParams({ page: pageToParam(page) })}
            />
          </div>
        )}
      </AsyncContent>
    </div>
  );
}
