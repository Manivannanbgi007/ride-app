import { useState } from 'react';

import {
  DeliveryFilterBar,
  DeliverySummaryCards,
  DeliveryTable,
  PartnerAvailabilityCards,
  PartnerFilterBar,
  PartnerTable,
} from '@/components/business/Delivery';
import { AsyncContent } from '@/components/common/AsyncContent';
import { Card } from '@/components/common/Card';
import { Skeleton } from '@/components/common/Loader';
import { PageHeader } from '@/components/common/PageHeader';
import { PagedResults } from '@/components/common/PagedResults';
import { TabPanel, Tabs } from '@/components/common/Tabs';
import {
  useDelayedDeliveries,
  useDeliveries,
  useDeliverySummary,
  usePartnerAvailability,
  usePartners,
} from '@/hooks/api/deliveryHooks';
import { useTabParam } from '@/hooks/useTabParam';
import { useAppDispatch, useAppSelector } from '@/store/hooks';
import {
  deliveryFiltersChanged,
  deliveryFiltersCleared,
  deliveryPageChanged,
  selectDeliveryFilters,
  selectDeliveryPage,
} from '@/store/slices/deliveryListSlice';
import {
  partnerFiltersChanged,
  partnerFiltersCleared,
  partnerPageChanged,
  selectPartnerFilters,
  selectPartnerPage,
} from '@/store/slices/partnerListSlice';
import { DEFAULT_PAGE_SIZE } from '@/utils/constants';
import { ERROR_MESSAGES } from '@/utils/errorMessages';
import { dateRangeError } from '@/utils/validationUtils';

const TAB_IDS = ['deliveries', 'delayed', 'partners'] as const;

/**
 * "Deliveries": the period's delivery figures and every delivery; the late ones right now; and the delivery
 * partners. The tab is in the address (?tab=delayed), so a link can open the late deliveries directly.
 */
export default function DeliveriesPage() {
  const dispatch = useAppDispatch();
  const filters = useAppSelector(selectDeliveryFilters);
  const [tab, setTab] = useTabParam(TAB_IDS);
  const isRangeValid = !dateRangeError(filters.fromDate, filters.toDate);
  // The figures follow the list's dates; "delayed now" is the same whatever the period (it feeds the tab badge).
  const summary = useDeliverySummary(filters.fromDate, filters.toDate, isRangeValid);

  return (
    <div className="flex flex-col gap-5">
      <PageHeader title="Deliveries" subtitle="Deliveries, late deliveries and delivery partners" />

      <AsyncContent
        isLoading={summary.isPending && isRangeValid}
        error={summary.error}
        errorTitle={ERROR_MESSAGES.DELIVERY_SUMMARY_LOAD_FAILED}
        onRetry={() => void summary.refetch()}
        loading={
          <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-5">
            {Array.from({ length: 5 }, (_, index) => (
              <Skeleton key={index} className="h-[4.5rem] rounded-2xl bg-surface" />
            ))}
          </div>
        }
      >
        {summary.data && <DeliverySummaryCards report={summary.data} />}
      </AsyncContent>

      <Tabs
        label="Deliveries"
        activeId={tab}
        onChange={setTab}
        tabs={[
          { id: 'deliveries', label: 'All deliveries' },
          { id: 'delayed', label: 'Delayed now', count: summary.data?.delayedNow },
          { id: 'partners', label: 'Delivery partners' },
        ]}
      />

      {tab === 'deliveries' && (
        <TabPanel id="deliveries" className="flex flex-col gap-4">
          <Card>
            <DeliveryFilterBar
              filters={filters}
              onChange={(changes) => dispatch(deliveryFiltersChanged(changes))}
              onClear={() => dispatch(deliveryFiltersCleared())}
            />
          </Card>
          <DeliveryList isRangeValid={isRangeValid} />
        </TabPanel>
      )}
      {tab === 'delayed' && (
        <TabPanel id="delayed" className="flex flex-col gap-4">
          <DelayedDeliveries />
        </TabPanel>
      )}
      {tab === 'partners' && (
        <TabPanel id="partners" className="flex flex-col gap-4">
          <Partners />
        </TabPanel>
      )}
    </div>
  );
}

function DeliveryList({ isRangeValid }: { isRangeValid: boolean }) {
  const dispatch = useAppDispatch();
  const filters = useAppSelector(selectDeliveryFilters);
  const page = useAppSelector(selectDeliveryPage);
  const deliveries = useDeliveries(filters, page, DEFAULT_PAGE_SIZE, isRangeValid);

  return (
    <Card>
      <PagedResults
        query={deliveries}
        errorTitle={ERROR_MESSAGES.DELIVERIES_LOAD_FAILED}
        emptyTitle="No deliveries yet"
        filteredEmptyTitle="No deliveries match these filters"
        hasFilters={Object.keys(filters).length > 0}
        onClearFilters={() => dispatch(deliveryFiltersCleared())}
        onPageChange={(newPage) => dispatch(deliveryPageChanged(newPage))}
        isDisabled={!isRangeValid}
        columns={8}
      >
        {(rows, isUpdating) => <DeliveryTable deliveries={rows} isUpdating={isUpdating} />}
      </PagedResults>
    </Card>
  );
}

/** Refreshed every minute while open; the longest waiting first. */
function DelayedDeliveries() {
  const [page, setPage] = useState(0);
  const delayed = useDelayedDeliveries(page, DEFAULT_PAGE_SIZE);

  return (
    <Card
      title="Late right now"
      subtitle="No partner 10 minutes after the food was ready, or not delivered 45 minutes after it. Refreshed every minute."
    >
      <PagedResults
        query={delayed}
        errorTitle={ERROR_MESSAGES.DELIVERIES_LOAD_FAILED}
        emptyTitle="No delivery is late right now"
        filteredEmptyTitle="No delivery is late right now"
        hasFilters={false}
        onClearFilters={() => setPage(0)}
        onPageChange={setPage}
        columns={8}
      >
        {(rows, isUpdating) => <DeliveryTable deliveries={rows} isUpdating={isUpdating} />}
      </PagedResults>
    </Card>
  );
}

function Partners() {
  const dispatch = useAppDispatch();
  const filters = useAppSelector(selectPartnerFilters);
  const page = useAppSelector(selectPartnerPage);
  const availability = usePartnerAvailability();
  const partners = usePartners(filters, page, DEFAULT_PAGE_SIZE);
  const clearFilters = () => dispatch(partnerFiltersCleared());

  return (
    <>
      <AsyncContent
        isLoading={availability.isPending}
        error={availability.error}
        errorTitle={ERROR_MESSAGES.PARTNERS_LOAD_FAILED}
        onRetry={() => void availability.refetch()}
        loading={<Skeleton className="h-20 rounded-2xl bg-surface" />}
      >
        {availability.data && <PartnerAvailabilityCards availability={availability.data} />}
      </AsyncContent>
      <Card>
        <PartnerFilterBar
          filters={filters}
          onChange={(changes) => dispatch(partnerFiltersChanged(changes))}
          onClear={clearFilters}
        />
      </Card>
      <Card>
        <PagedResults
          query={partners}
          errorTitle={ERROR_MESSAGES.PARTNERS_LOAD_FAILED}
          emptyTitle="No delivery partners yet"
          filteredEmptyTitle="No partners match these filters"
          hasFilters={Object.keys(filters).length > 0}
          onClearFilters={clearFilters}
          onPageChange={(newPage) => dispatch(partnerPageChanged(newPage))}
          columns={6}
        >
          {(rows, isUpdating) => <PartnerTable partners={rows} isUpdating={isUpdating} />}
        </PagedResults>
      </Card>
    </>
  );
}
