import {
  CurrentDeliveryCard,
  HowItWorks,
  LookingUpPanel,
  OfflinePanel,
  PartnerStatusHero,
  QuickLinks,
  SetupCard,
  SuspendedPanel,
  WaitingPanel,
} from '@/components/business/PartnerArea';
import { AsyncContent } from '@/components/common/AsyncContent';
import { Skeleton } from '@/components/common/Loader';
import { useCurrentDelivery, usePartnerProfile } from '@/hooks/api/partnerHooks';
import { useAppSelector } from '@/store/hooks';
import { selectPartner } from '@/store/slices/sessionSlice';
import type { Delivery, Partner } from '@/types/delivery';

/**
 * "Today": the partner's whole day on one page. A hero with the online switch, then the delivery offered to them or
 * being worked on (with its next step), or what happens while they wait. New offers appear by themselves (live
 * updates, and the page asks again every few seconds while the partner is online).
 */
export default function PartnerHomePage() {
  const signedIn = useAppSelector(selectPartner);
  const profile = usePartnerProfile();
  const online = profile.data?.status === 'AVAILABLE' || profile.data?.status === 'BUSY';
  const current = useCurrentDelivery(online);
  const firstName = signedIn?.fullName.split(' ')[0] ?? '';

  return (
    <AsyncContent
      isLoading={profile.isPending}
      error={profile.data ? null : profile.error}
      errorTitle="Your partner profile could not be loaded"
      onRetry={() => void profile.refetch()}
      isRetrying={profile.isFetching}
      loading={<HomeLoader />}
    >
      {profile.data && (
        <div className="flex flex-col gap-6">
          <PartnerStatusHero
            partner={profile.data}
            currentDelivery={current.data ?? null}
            firstName={firstName}
          />
          <div className="grid items-start gap-6 lg:grid-cols-5">
            <div className="flex flex-col gap-6 lg:col-span-3">
              <TodayPanel
                partner={profile.data}
                currentDelivery={current.data ?? null}
                isLookingUp={online && current.isPending}
              />
            </div>
            <div className="flex flex-col gap-6 lg:col-span-2">
              <QuickLinks />
              <HowItWorks />
            </div>
          </div>
        </div>
      )}
    </AsyncContent>
  );
}

interface TodayPanelProps {
  partner: Partner;
  currentDelivery: Delivery | null;
  isLookingUp: boolean;
}

/** The main panel: the current delivery, or what the partner can expect without one. */
function TodayPanel({ partner, currentDelivery, isLookingUp }: TodayPanelProps) {
  if (currentDelivery) {
    return <CurrentDeliveryCard delivery={currentDelivery} />;
  }
  if (partner.status === 'SUSPENDED') {
    return <SuspendedPanel />;
  }
  if (isLookingUp) {
    return <LookingUpPanel />;
  }
  if (partner.status === 'AVAILABLE' || partner.status === 'BUSY') {
    return <WaitingPanel partner={partner} />;
  }
  return partner.readyToWork ? <OfflinePanel partner={partner} /> : <SetupCard />;
}

/** The page's shape while the profile loads. */
function HomeLoader() {
  return (
    <div role="status" aria-label="Loading" className="flex flex-col gap-6">
      <Skeleton className="h-60 rounded-3xl" />
      <div className="grid gap-6 lg:grid-cols-5">
        <Skeleton className="h-72 rounded-3xl lg:col-span-3" />
        <Skeleton className="h-72 rounded-3xl lg:col-span-2" />
      </div>
    </div>
  );
}
