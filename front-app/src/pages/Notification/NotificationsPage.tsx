import {
  AdminNotificationTable,
  InboxPanel,
  NotificationFilterBar,
  NotificationSummaryTiles,
} from '@/components/business/Notification';
import { AsyncContent } from '@/components/common/AsyncContent';
import { Card } from '@/components/common/Card';
import { Skeleton } from '@/components/common/Loader';
import { PageHeader } from '@/components/common/PageHeader';
import { PagedResults } from '@/components/common/PagedResults';
import { TabPanel, Tabs } from '@/components/common/Tabs';
import {
  useAdminNotifications,
  useNotificationSummary,
  useUnreadNotificationCount,
} from '@/hooks/api/notificationHooks';
import { useTabParam } from '@/hooks/useTabParam';
import { useAppDispatch, useAppSelector } from '@/store/hooks';
import {
  notificationFiltersChanged,
  notificationFiltersCleared,
  notificationPageChanged,
  selectNotificationFilters,
  selectNotificationPage,
} from '@/store/slices/notificationListSlice';
import { DEFAULT_PAGE_SIZE } from '@/utils/constants';
import { ERROR_MESSAGES } from '@/utils/errorMessages';
import { dateRangeError } from '@/utils/validationUtils';

const TAB_IDS = ['inbox', 'all'] as const;

/**
 * "Notifications" (the bell): the acting user's own in-app notifications, and every notification the platform
 * sent, to find and resend failed ones.
 */
export default function NotificationsPage() {
  const [tab, setTab] = useTabParam(TAB_IDS);
  const { data: unread } = useUnreadNotificationCount();

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title="Notifications"
        subtitle="Your notifications, and everything the platform sent"
      />
      <Tabs
        label="Notifications"
        activeId={tab}
        onChange={setTab}
        tabs={[
          { id: 'inbox', label: 'My notifications', count: unread },
          { id: 'all', label: 'All notifications' },
        ]}
      />
      <TabPanel id={tab}>
        {tab === 'inbox' ? <InboxPanel unread={unread ?? 0} /> : <AllNotifications />}
      </TabPanel>
    </div>
  );
}

function AllNotifications() {
  const dispatch = useAppDispatch();
  const filters = useAppSelector(selectNotificationFilters);
  const page = useAppSelector(selectNotificationPage);
  const isRangeValid = !dateRangeError(filters.fromDate, filters.toDate);
  const notifications = useAdminNotifications(filters, page, DEFAULT_PAGE_SIZE, isRangeValid);
  // The totals follow the dates once both are chosen; otherwise they are today's
  const bothDates = Boolean(filters.fromDate && filters.toDate);
  const summary = useNotificationSummary(
    bothDates ? filters.fromDate : undefined,
    bothDates ? filters.toDate : undefined,
    isRangeValid,
  );
  const clearFilters = () => dispatch(notificationFiltersCleared());

  return (
    <>
      <AsyncContent
        isLoading={summary.isPending && isRangeValid}
        error={summary.error}
        errorTitle={ERROR_MESSAGES.NOTIFICATION_SUMMARY_LOAD_FAILED}
        onRetry={() => void summary.refetch()}
        loading={<Skeleton className="h-24 rounded-2xl bg-surface" />}
      >
        {summary.data && <NotificationSummaryTiles summary={summary.data} />}
      </AsyncContent>
      <Card>
        <NotificationFilterBar
          filters={filters}
          onChange={(changes) => dispatch(notificationFiltersChanged(changes))}
          onClear={clearFilters}
        />
      </Card>
      <Card>
        <PagedResults
          query={notifications}
          errorTitle={ERROR_MESSAGES.NOTIFICATIONS_LOAD_FAILED}
          emptyTitle="No notifications yet"
          filteredEmptyTitle="No notifications match these filters"
          hasFilters={Object.keys(filters).length > 0}
          onClearFilters={clearFilters}
          onPageChange={(newPage) => dispatch(notificationPageChanged(newPage))}
          isDisabled={!isRangeValid}
          columns={6}
        >
          {(rows, isUpdating) => (
            <AdminNotificationTable notifications={rows} isUpdating={isUpdating} />
          )}
        </PagedResults>
      </Card>
    </>
  );
}
