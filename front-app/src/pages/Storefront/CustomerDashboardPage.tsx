import {
  ActiveOrders,
  DashboardOffers,
  DashboardQuickActions,
  DashboardWelcome,
  FrequentPicks,
  OrderActivity,
  RecentOrders,
  SummaryCards,
  YouMayLike,
} from '@/components/business/CustomerDashboard';
import { AsyncContent } from '@/components/common/AsyncContent';
import { Skeleton } from '@/components/common/Loader';
import { useMyOrderSummary } from '@/hooks/api/customerDashboardHooks';
import { useAppSelector } from '@/store/hooks';
import { selectCustomer } from '@/store/slices/sessionSlice';

/**
 * "My dashboard": the signed-in customer's place to start, most useful first. The banner (where the food goes, a
 * search), the quick actions right under it, an order still going (it matters most while it lasts), the dishes
 * they order again and again, their orders all time, the latest finished orders, today's coupons, top rated
 * dishes, and their order activity over a chosen period. Every figure is added up by order-service for this
 * customer only.
 */
export default function CustomerDashboardPage() {
  const customer = useAppSelector(selectCustomer);
  const summary = useMyOrderSummary();
  const firstName = customer?.fullName.trim().split(/\s+/)[0];

  return (
    <div className="flex flex-col gap-6">
      <DashboardWelcome firstName={firstName} />
      <DashboardQuickActions />
      <ActiveOrders />
      <FrequentPicks />
      <AsyncContent
        isLoading={summary.isPending}
        error={summary.error}
        errorTitle="Unable to load your order totals"
        onRetry={() => void summary.refetch()}
        isRetrying={summary.isFetching}
        loading={
          <div className="grid grid-cols-2 gap-2 sm:gap-3 lg:grid-cols-4">
            {Array.from({ length: 4 }, (_, index) => (
              <Skeleton key={index} className="h-[4.5rem] rounded-2xl bg-surface" />
            ))}
          </div>
        }
      >
        {summary.data && <SummaryCards summary={summary.data} />}
      </AsyncContent>
      <RecentOrders />
      <DashboardOffers />
      <YouMayLike />
      <OrderActivity />
    </div>
  );
}
