import { Bike, SearchX } from 'lucide-react';

import {
  ActiveDeliveryPanel,
  DeliveryHistoryList,
  DeliveryHistoryLoader,
  DeliverySummaryLoader,
  DeliverySummaryStrip,
  isActiveDelivery,
} from '@/components/business/PartnerArea';
import { AsyncContent } from '@/components/common/AsyncContent';
import { ClearFiltersButton } from '@/components/common/ClearFiltersButton';
import { DateRangeFields } from '@/components/common/DateRangeFields';
import { EmptyState } from '@/components/common/EmptyState';
import { PageHeader } from '@/components/common/PageHeader';
import { Pagination } from '@/components/common/Pagination';
import { SearchInput } from '@/components/common/SearchInput';
import { SegmentedControl } from '@/components/common/SegmentedControl';
import { Select } from '@/components/common/Select';
import {
  useCurrentDelivery,
  usePartnerDeliveries,
  usePartnerProfile,
} from '@/hooks/api/partnerHooks';
import { pageFromParam, pageToParam, useQueryParams } from '@/hooks/useQueryParams';
import type { DeliverySummary } from '@/types/delivery';
import { formatNumber } from '@/utils/formatUtils';
import { filterHistory, summarizeHistory } from '@/utils/partnerHistoryUtils';

/** The partner's latest deliveries are read at once (the API's largest page): the filters then answer instantly. */
const HISTORY_SIZE = 100;
/** Deliveries on one page of the list (compact rows: ten fit on a screen). */
const PAGE_SIZE = 10;
/** A space that does not break the line: "All (5)" stays in one piece in the narrow tab bar. */
const NBSP = ' ';

const TAB_IDS = ['all', 'active', 'delivered', 'cancelled'] as const;
type TabId = (typeof TAB_IDS)[number];

const SORT_OPTIONS = [
  { value: 'newest', label: 'Newest first' },
  { value: 'oldest', label: 'Oldest first' },
] as const;

// The tab and the order are kept in the address with the filters, so a link or "back" shows the same list
const PARAM_KEYS = ['search', 'from', 'to', 'tab', 'sort', 'page'] as const;
const FILTER_KEYS = ['search', 'from', 'to'] as const;

/** Which rows a tab shows: "Active" is what is still to do (an offer, or a delivery on its way). */
const TAB_MATCHES: Record<TabId, (row: DeliverySummary) => boolean> = {
  all: () => true,
  active: (row) => isActiveDelivery(row.status),
  delivered: (row) => row.status === 'DELIVERED',
  cancelled: (row) => row.status === 'CANCELLED',
};

const TAB_LABELS: Record<TabId, string> = {
  all: 'All',
  active: 'Active',
  delivered: 'Delivered',
  cancelled: 'Cancelled',
};

/**
 * "My deliveries", compact: the delivery being worked on (with its next step's buttons) on top while there is one,
 * the partner's totals, one bar of filters (what to show, a search, dates and the order), and every delivery as a
 * compact row, grouped by day. The list is worked out from the latest deliveries the API returns.
 */
export default function PartnerDeliveriesPage() {
  const [params, setParams] = useQueryParams(PARAM_KEYS);
  const tab = TAB_IDS.find((id) => id === params.tab) ?? TAB_IDS[0];
  const sort = params.sort === 'oldest' ? 'oldest' : 'newest';
  const profile = usePartnerProfile();
  // While online the delivery being worked on is followed, as on "Today" (a new offer appears by itself)
  const online = profile.data?.status === 'AVAILABLE' || profile.data?.status === 'BUSY';
  const current = useCurrentDelivery(online);
  const deliveries = usePartnerDeliveries('ALL', 0, HISTORY_SIZE);

  const hasFilters = tab !== 'all' || FILTER_KEYS.some((key) => params[key]);

  const changeFilters = (changes: Partial<Record<(typeof FILTER_KEYS)[number], string>>) => {
    setParams(changes);
  };
  const clearFilters = () => {
    setParams({ search: undefined, from: undefined, to: undefined, tab: undefined });
  };
  const changePage = (next: number) => {
    setParams({ page: pageToParam(next) });
    window.scrollTo({ top: 0, behavior: 'smooth' });
  };

  const all = deliveries.data?.content ?? [];
  // Search and dates narrow every number; the tab only narrows the list
  const matching = filterHistory(all, {
    search: params.search,
    fromDate: params.from,
    toDate: params.to,
  });
  // The API lists the newest first
  const inTab = matching.filter(TAB_MATCHES[tab]);
  const rows = sort === 'oldest' ? [...inTab].reverse() : inTab;
  const totalPages = Math.max(1, Math.ceil(rows.length / PAGE_SIZE));
  // An old link may ask for a page that no longer exists
  const page = Math.min(pageFromParam(params.page), totalPages - 1);
  const shown = rows.slice(page * PAGE_SIZE, (page + 1) * PAGE_SIZE);
  const total = deliveries.data?.totalElements ?? all.length;

  const tabOptions = TAB_IDS.map((id) => ({
    id,
    label: `${TAB_LABELS[id]}${NBSP}(${matching.filter(TAB_MATCHES[id]).length})`,
  }));

  return (
    <div className="flex flex-col gap-4">
      <PageHeader
        title="My deliveries"
        subtitle="Every delivery offered to you and taken by you."
      />

      {current.data && <ActiveDeliveryPanel delivery={current.data} />}

      <AsyncContent
        isLoading={deliveries.isPending}
        error={deliveries.data ? null : deliveries.error}
        isEmpty={all.length === 0}
        errorTitle="Your deliveries could not be loaded"
        onRetry={() => void deliveries.refetch()}
        isRetrying={deliveries.isFetching}
        loading={
          <div className="flex flex-col gap-4">
            <DeliverySummaryLoader />
            <DeliveryHistoryLoader />
          </div>
        }
        empty={
          <div className="rounded-2xl bg-surface shadow-card ring-1 ring-line/60">
            <EmptyState
              icon={Bike}
              title="No deliveries yet: go online to receive your first offer"
            />
          </div>
        }
      >
        <div className="flex flex-col gap-4">
          <DeliverySummaryStrip summary={summarizeHistory(matching)} />

          <section
            aria-label="Filters"
            className="flex flex-col gap-3 rounded-2xl bg-surface p-3 shadow-card ring-1 ring-line/60 sm:p-4"
          >
            <div className="flex flex-wrap items-center justify-between gap-2">
              <div className="max-w-full overflow-x-auto">
                <SegmentedControl
                  label="Show"
                  options={tabOptions}
                  value={tab}
                  onChange={(next) => setParams({ tab: next })}
                />
              </div>
              <div className="flex items-center gap-2 text-sm text-ink-soft">
                <p aria-live="polite">
                  {rows.length === 1 ? '1 delivery' : `${formatNumber(rows.length)} deliveries`}
                </p>
                <span aria-hidden="true">·</span>
                <Select
                  variant="inline"
                  aria-label="Order"
                  options={SORT_OPTIONS}
                  value={sort}
                  onChange={(event) =>
                    setParams({ sort: event.target.value === 'oldest' ? 'oldest' : undefined })
                  }
                  className="text-sm font-semibold"
                />
              </div>
            </div>
            <div className="grid items-start gap-3 sm:grid-cols-2 lg:grid-cols-[minmax(0,2fr)_minmax(0,1fr)_minmax(0,1fr)_auto]">
              <SearchInput
                className="sm:col-span-2 lg:col-span-1"
                label="Search"
                placeholder="Order number, restaurant or city"
                maxLength={60}
                value={params.search ?? ''}
                onSearch={(search) => changeFilters({ search })}
              />
              {/* Side by side even on a phone; on a wide screen the two fields join the bar's columns */}
              <div className="grid grid-cols-2 items-start gap-3 sm:col-span-2 lg:contents">
                <DateRangeFields
                  fromDate={params.from}
                  toDate={params.to}
                  onChange={({ fromDate, toDate }) =>
                    changeFilters({
                      ...(fromDate !== undefined && { from: fromDate }),
                      ...(toDate !== undefined && { to: toDate }),
                    })
                  }
                />
              </div>
              {hasFilters && (
                <div className="flex sm:items-end lg:h-full lg:pt-7">
                  <ClearFiltersButton onClick={clearFilters} />
                </div>
              )}
            </div>
          </section>

          {rows.length === 0 ? (
            <div className="rounded-2xl bg-surface shadow-card ring-1 ring-line/60">
              <EmptyState
                icon={SearchX}
                title="No deliveries match these filters"
                message="Try another search or a wider date range."
                action={<ClearFiltersButton size="sm" onClick={clearFilters} />}
              />
            </div>
          ) : (
            <div className="flex flex-col gap-3">
              <DeliveryHistoryList rows={shown} isUpdating={deliveries.isFetching} />
              <Pagination
                page={page}
                size={PAGE_SIZE}
                totalPages={totalPages}
                totalElements={rows.length}
                onPageChange={changePage}
              />
            </div>
          )}

          {total > all.length && (
            <p className="text-center text-xs text-ink-soft">
              Showing your latest {formatNumber(all.length)} of {formatNumber(total)} deliveries.
            </p>
          )}
        </div>
      </AsyncContent>
    </div>
  );
}
