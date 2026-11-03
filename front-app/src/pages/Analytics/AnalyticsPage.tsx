import { useState } from 'react';

import {
  CustomerReportView,
  DeliveryReportView,
  OrderReportView,
  ReportPanel,
  RestaurantReportView,
  RevenueReportView,
} from '@/components/business/Analytics';
import { PeriodFilter } from '@/components/business/Dashboard';
import { PageHeader } from '@/components/common/PageHeader';
import { Select } from '@/components/common/Select';
import { TabPanel, Tabs } from '@/components/common/Tabs';
import {
  useCustomerReport,
  useDeliveryReport,
  useOrderReport,
  useRestaurantReport,
  useRevenueReport,
} from '@/hooks/api/reportHooks';
import { useTabParam } from '@/hooks/useTabParam';
import { useAppDispatch, useAppSelector } from '@/store/hooks';
import { periodDaysChanged, selectPeriodDays } from '@/store/slices/reportPeriodSlice';
import type { ReportParams, RevenueGroupBy } from '@/types/report';
import { REVENUE_GROUP_BY_LABELS } from '@/utils/constants';
import { periodOf } from '@/utils/dateUtils';
import {
  customerReportCsv,
  deliveryReportCsv,
  orderReportCsv,
  reportFileName,
  restaurantReportCsv,
  revenueReportCsv,
} from '@/utils/reportCsv';

const TABS = [
  { id: 'orders', label: 'Orders' },
  { id: 'revenue', label: 'Revenue' },
  { id: 'restaurants', label: 'Restaurants' },
  { id: 'customers', label: 'Customers' },
  { id: 'deliveries', label: 'Deliveries' },
] as const;
const TAB_IDS = TABS.map((tab) => tab.id);

/** Rows of each ranking (top restaurants, top customers, ...). */
const REPORT_ROWS = 10;

/**
 * "Analytics": admin-service's reports for the period chosen with "Filter Period" (the same choice as the
 * dashboard's). One report per tab; only the open tab is loaded. Each can be saved as a spreadsheet (CSV).
 */
export default function AnalyticsPage() {
  const dispatch = useAppDispatch();
  const days = useAppSelector(selectPeriodDays);
  const [tab, setTab] = useTabParam(TAB_IDS);
  const params: ReportParams = { ...periodOf(days), limit: REPORT_ROWS };

  return (
    <div className="flex flex-col gap-5">
      <PageHeader
        title="Analytics"
        subtitle="Orders, revenue, restaurants, customers and deliveries of the period"
        actions={
          <PeriodFilter
            days={days}
            onChange={(newDays) => dispatch(periodDaysChanged(newDays))}
            fromDate={params.fromDate}
            toDate={params.toDate}
          />
        }
      />
      <Tabs label="Reports" tabs={TABS} activeId={tab} onChange={setTab} />
      <TabPanel id={tab}>
        {tab === 'orders' && <OrdersTab params={params} />}
        {tab === 'revenue' && <RevenueTab params={params} />}
        {tab === 'restaurants' && <RestaurantsTab params={params} />}
        {tab === 'customers' && <CustomersTab params={params} />}
        {tab === 'deliveries' && <DeliveriesTab params={params} />}
      </TabPanel>
    </div>
  );
}

function OrdersTab({ params }: { params: ReportParams }) {
  return (
    <ReportPanel
      query={useOrderReport(params)}
      fileName={(report) => reportFileName('orders', report)}
      csvRows={orderReportCsv}
    >
      {(report) => <OrderReportView report={report} />}
    </ReportPanel>
  );
}

const GROUP_BY_OPTIONS = (['DAY', 'WEEK', 'MONTH'] as const).map((value) => ({
  value,
  label: REVENUE_GROUP_BY_LABELS[value],
}));

function RevenueTab({ params }: { params: ReportParams }) {
  const [groupBy, setGroupBy] = useState<RevenueGroupBy>('DAY');
  return (
    <ReportPanel
      query={useRevenueReport(params, groupBy)}
      fileName={(report) => reportFileName('revenue', report)}
      csvRows={revenueReportCsv}
      actions={
        <Select
          aria-label="Revenue per"
          options={GROUP_BY_OPTIONS}
          value={groupBy}
          onChange={(event) =>
            setGroupBy(
              GROUP_BY_OPTIONS.find((option) => option.value === event.target.value)?.value ??
                'DAY',
            )
          }
          className="h-9"
        />
      }
    >
      {(report) => <RevenueReportView report={report} />}
    </ReportPanel>
  );
}

function RestaurantsTab({ params }: { params: ReportParams }) {
  return (
    <ReportPanel
      query={useRestaurantReport(params)}
      fileName={(report) => reportFileName('restaurants', report)}
      csvRows={restaurantReportCsv}
    >
      {(report) => <RestaurantReportView report={report} />}
    </ReportPanel>
  );
}

function CustomersTab({ params }: { params: ReportParams }) {
  return (
    <ReportPanel
      query={useCustomerReport(params)}
      fileName={(report) => reportFileName('customers', report)}
      csvRows={customerReportCsv}
    >
      {(report) => <CustomerReportView report={report} />}
    </ReportPanel>
  );
}

function DeliveriesTab({ params }: { params: ReportParams }) {
  return (
    <ReportPanel
      query={useDeliveryReport(params)}
      fileName={(report) => reportFileName('deliveries', report)}
      csvRows={deliveryReportCsv}
    >
      {(report) => <DeliveryReportView report={report} />}
    </ReportPanel>
  );
}
