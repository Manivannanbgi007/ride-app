import { CalendarCheck, Clock, Plus, Store, UtensilsCrossed } from 'lucide-react';
import { Link } from 'react-router';

import { RestaurantStatusNotice } from '@/components/business/OwnerDashboard/RestaurantStatusNotice';
import { RestaurantEditor, RestaurantStatusPanel } from '@/components/business/OwnerRestaurant';
import { AsyncContent } from '@/components/common/AsyncContent';
import { buttonStyles } from '@/components/common/Button';
import { Skeleton } from '@/components/common/Loader';
import { PageHero, type HeroStat } from '@/components/common/PageHero';
import { useToast } from '@/context/ToastContext';
import { useOwnerRestaurantDetails, useUpdateRestaurant } from '@/hooks/api/ownerHooks';
import { useOwnerRestaurant } from '@/layouts/owner/ownerRestaurantContext';
import type { RestaurantDetails } from '@/types/restaurant';
import { OWNER_ROUTES } from '@/utils/constants';
import { formatClockTime } from '@/utils/dateUtils';
import { formValuesOf, hoursRowsOf } from '@/utils/ownerRestaurantUtils';
import { DAYS_OF_WEEK } from '@/utils/storefrontUtils';

/** The form on the left and the restaurant at a glance on the right; one column (the glance first) below 1280 px. */
const LAYOUT = 'grid items-start gap-6 xl:grid-cols-[minmax(0,1fr)_20rem]';

/** The restaurant at a glance in the hero: today's hours, the days it opens, its cuisines (as they are saved). */
function restaurantStatsOf(details?: RestaurantDetails): HeroStat[] {
  if (!details) {
    return [];
  }
  const today = DAYS_OF_WEEK[(new Date().getDay() + 6) % 7];
  const todays = hoursRowsOf(details.operatingHours).find((row) => row.day === today);
  const openDays = new Set(details.operatingHours.map((hours) => hours.dayOfWeek)).size;
  return [
    {
      label: 'Open today',
      value: todays?.open
        ? `${formatClockTime(todays.opensAt)} – ${formatClockTime(todays.closesAt)}`
        : 'Closed',
      icon: Clock,
    },
    { label: 'Days open a week', value: String(openDays), icon: CalendarCheck },
    {
      label: 'Cuisines',
      value: details.cuisines.length > 0 ? details.cuisines.join(', ') : '—',
      icon: UtensilsCrossed,
    },
  ];
}

/**
 * The owner's restaurant as the platform has it: its status (read only), and the details and opening hours to
 * change, saved from a bar that stays in sight. A change is sent with the version that was read, so one made on
 * another screen in the meantime is refused, not overwritten; leaving with unsaved changes asks first.
 */
export default function OwnerRestaurantPage() {
  const { restaurant, restaurantId } = useOwnerRestaurant();
  const details = useOwnerRestaurantDetails(restaurantId);
  const update = useUpdateRestaurant(restaurantId);
  const { showToast } = useToast();

  return (
    <div className="flex flex-col gap-6">
      <PageHero
        eyebrow={restaurant.name}
        title="Restaurant"
        subtitle={`The details and opening hours of ${restaurant.name}`}
        icon={Store}
        colour="teal"
        actions={
          <Link to={OWNER_ROUTES.newRestaurant} className={buttonStyles('outline', 'sm')}>
            <Plus aria-hidden="true" className="size-4" />
            Register another
          </Link>
        }
        stats={restaurantStatsOf(details.data)}
      />

      <RestaurantStatusNotice restaurant={restaurant} />

      <AsyncContent
        isLoading={details.isPending}
        error={details.error}
        errorTitle="Unable to load your restaurant"
        onRetry={() => void details.refetch()}
        isRetrying={details.isFetching}
        loading={<RestaurantPageLoader />}
      >
        {details.data && (
          <div className={LAYOUT}>
            <div className="xl:sticky xl:top-24 xl:order-last">
              <RestaurantStatusPanel details={details.data} />
            </div>
            {/* Keyed by the version: after a save (or a change elsewhere) the form starts again from what is stored */}
            <RestaurantEditor
              key={details.data.version}
              editing
              initialValues={formValuesOf(details.data)}
              initialHours={hoursRowsOf(details.data.operatingHours)}
              otherDocuments={details.data.documents}
              submitLabel="Save changes"
              onSave={async (request) => {
                await update.mutateAsync({ ...request, version: details.data.version });
                showToast('Your restaurant was updated.');
              }}
            />
          </div>
        )}
      </AsyncContent>
    </div>
  );
}

/** The panel and the form's sections while they load: the page keeps its shape. */
function RestaurantPageLoader() {
  return (
    <div role="status" aria-label="Loading your restaurant" className={LAYOUT}>
      <Skeleton className="h-64 w-full rounded-3xl xl:order-last" />
      <div className="flex flex-col gap-6">
        <Skeleton className="h-72 w-full rounded-3xl" />
        <Skeleton className="h-72 w-full rounded-3xl" />
      </div>
    </div>
  );
}
