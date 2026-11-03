import {
  Bike,
  CalendarDays,
  Clock3,
  MapPin,
  PackageCheck,
  ShieldAlert,
  type LucideIcon,
} from 'lucide-react';
import type { ReactNode } from 'react';

import { OnlineSwitch } from '@/components/business/PartnerArea/OnlineSwitch';
import { CityScene, type SceneMood } from '@/components/business/PartnerArea/PartnerIllustrations';
import { useAvailabilitySwitch } from '@/components/business/PartnerArea/useAvailabilitySwitch';
import { VEHICLE_ICONS } from '@/components/business/PartnerArea/vehicleIcons';
import { useNow } from '@/hooks/useNow';
import type { Delivery, Partner, PartnerStatus } from '@/types/delivery';
import { cn } from '@/utils/classNames';
import { VEHICLE_TYPE_LABELS } from '@/utils/constants';
import { formatNumber } from '@/utils/formatUtils';
import { greetingOf, onlineForLabel } from '@/utils/partnerAreaUtils';

/** The hero's colour tells the status at a glance: green online, deep green working, night slate offline, red suspended. */
const TONES: Record<PartnerStatus, string> = {
  AVAILABLE: 'bg-linear-to-br from-brand-500 via-brand-600 to-brand-800 text-white',
  BUSY: 'bg-linear-to-br from-brand-700 via-brand-800 to-brand-900 text-white',
  OFFLINE: 'bg-linear-to-br from-strip to-[#1c2c20] text-on-strip',
  SUSPENDED: 'bg-linear-to-br from-danger-600 via-ruby-600 to-berry-800 text-white',
};

/** What the illustration shows for each status: a day ride, a ride to a pin, a night street, a warning sign. */
const MOODS: Record<PartnerStatus, SceneMood> = {
  AVAILABLE: 'day',
  BUSY: 'busy',
  OFFLINE: 'night',
  SUSPENDED: 'alert',
};

const HEADLINES: Record<PartnerStatus, string> = {
  AVAILABLE: 'You are online',
  BUSY: 'You are on a delivery',
  OFFLINE: 'You are offline',
  SUSPENDED: 'Your account is suspended',
};

/** Under the switch: what tapping it does. */
const SWITCH_HINTS: Record<PartnerStatus, string> = {
  AVAILABLE: 'Tap to go offline',
  BUSY: 'Busy until delivered',
  OFFLINE: 'Tap to go online',
  SUSPENDED: '',
};

/** The little light next to the headline. */
const DOTS: Record<PartnerStatus, string> = {
  AVAILABLE: 'bg-lime-500',
  BUSY: 'bg-lime-500',
  OFFLINE: 'bg-white/60',
  SUSPENDED: 'bg-white',
};

/** What the partner is doing: the current delivery is the freshest fact (the profile's status can lag a moment). */
function statusOf(partner: Partner, currentDelivery: Delivery | null): PartnerStatus {
  if (partner.status === 'SUSPENDED') {
    return 'SUSPENDED';
  }
  return currentDelivery ? 'BUSY' : partner.status;
}

function describe(status: PartnerStatus, partner: Partner): ReactNode {
  switch (status) {
    case 'AVAILABLE':
      return (
        <>
          Deliveries from restaurants in <strong>{partner.city}</strong> are offered to you.
        </>
      );
    case 'BUSY':
      return 'Finish your delivery before going offline.';
    case 'OFFLINE':
      return partner.readyToWork
        ? 'Switch on to start receiving delivery offers.'
        : 'Add your vehicle and city, then switch on.';
    case 'SUSPENDED':
      return [partner.statusReason, 'Please contact support.'].filter(Boolean).join('. ');
  }
}

function Chip({ icon: Icon, children }: { icon: LucideIcon; children: ReactNode }) {
  return (
    <span className="inline-flex items-center gap-1.5 rounded-full bg-white/15 px-3 py-1.5 text-xs font-semibold ring-1 ring-white/20 backdrop-blur-sm">
      <Icon aria-hidden="true" className="size-3.5" />
      {children}
    </span>
  );
}

interface PartnerStatusHeroProps {
  partner: Partner;
  /** The delivery offered to the partner or being worked on (it makes them busy whatever the profile says) */
  currentDelivery: Delivery | null;
  firstName: string;
}

/**
 * The top of "Today": who is working, whether they are online (the big switch), and their day in a few chips, over
 * an illustrated street that changes with the status (a day ride, a night street, a warning sign). While online,
 * deliveries from restaurants in their city are offered to them; on a delivery they stay busy until it is
 * delivered; a suspended partner cannot go online.
 */
export function PartnerStatusHero({ partner, currentDelivery, firstName }: PartnerStatusHeroProps) {
  const { switchTo, isPending } = useAvailabilitySwitch();
  const now = useNow(30_000);
  const status = statusOf(partner, currentDelivery);
  const online = status === 'AVAILABLE' || status === 'BUSY';
  const onlineFor = status === 'AVAILABLE' ? onlineForLabel(partner.availableSince, now) : null;
  const VehicleIcon = partner.vehicleType ? VEHICLE_ICONS[partner.vehicleType] : Bike;
  const today = new Date(now).toLocaleDateString('en-IN', {
    weekday: 'long',
    day: 'numeric',
    month: 'long',
  });

  return (
    <section
      aria-labelledby="partner-greeting"
      className={cn(
        'relative isolate overflow-clip rounded-3xl shadow-card transition-colors duration-500',
        TONES[status],
      )}
    >
      {/* Soft light, and a fine dot grid like the page behind */}
      <span
        aria-hidden="true"
        className="absolute -top-28 -right-20 -z-10 size-96 rounded-full bg-white/10 blur-3xl"
      />
      <span
        aria-hidden="true"
        className="absolute -bottom-40 -left-24 -z-10 size-96 rounded-full bg-lime-500/20 blur-3xl"
      />
      <span
        aria-hidden="true"
        className="absolute inset-0 -z-10 [background-image:radial-gradient(rgb(255_255_255/0.28)_1px,transparent_1px)] [mask-image:linear-gradient(to_bottom,black,transparent_70%)] [background-size:22px_22px] opacity-40"
      />

      {/* The street at the bottom */}
      <CityScene
        mood={MOODS[status]}
        className="absolute inset-x-0 bottom-0 -z-10 h-36 w-full sm:h-44"
      />

      <div className="relative flex flex-col gap-6 p-6 pb-40 sm:p-8 sm:pb-48">
        <div className="flex flex-wrap items-start justify-between gap-5">
          <div className="flex min-w-0 flex-col gap-3">
            <p className="inline-flex w-fit items-center gap-2 rounded-full bg-white/15 px-3 py-1 text-xs font-semibold ring-1 ring-white/20 backdrop-blur-sm">
              <CalendarDays aria-hidden="true" className="size-3.5" />
              {today}
            </p>
            <h1
              id="partner-greeting"
              className="text-3xl leading-tight font-extrabold tracking-tight sm:text-5xl"
            >
              {greetingOf(new Date(now).getHours())}, {firstName}
            </h1>
          </div>
          {status !== 'SUSPENDED' && (
            <div className="flex flex-col items-center gap-2 rounded-3xl bg-white/10 p-3 ring-1 ring-white/20 backdrop-blur-md">
              <OnlineSwitch
                checked={online}
                disabled={status === 'BUSY' || (!online && !partner.readyToWork)}
                isLoading={isPending}
                onChange={switchTo}
              />
              <span className="text-xs font-semibold opacity-90">{SWITCH_HINTS[status]}</span>
            </div>
          )}
        </div>

        <div className="flex max-w-xl items-start gap-3 rounded-2xl bg-black/10 p-4 ring-1 ring-white/15 backdrop-blur-sm">
          <span aria-hidden="true" className="relative mt-2 flex size-3 shrink-0">
            {online && (
              <span className="absolute inline-flex size-full rounded-full bg-white/70 motion-safe:animate-ping" />
            )}
            <span className={cn('relative inline-flex size-3 rounded-full', DOTS[status])} />
          </span>
          <div className="flex flex-col gap-1">
            <p className="flex items-center gap-2 text-xl font-bold">
              {status === 'SUSPENDED' && <ShieldAlert aria-hidden="true" className="size-5" />}
              {HEADLINES[status]}
            </p>
            <p className="text-sm opacity-90">{describe(status, partner)}</p>
          </div>
        </div>

        <div className="flex flex-wrap gap-2">
          {partner.readyToWork && partner.vehicleType && (
            <Chip icon={VehicleIcon}>
              {VEHICLE_TYPE_LABELS[partner.vehicleType]} · {partner.vehicleNumber}
            </Chip>
          )}
          {partner.city && <Chip icon={MapPin}>{partner.city}</Chip>}
          {onlineFor && <Chip icon={Clock3}>Online for {onlineFor}</Chip>}
          <Chip icon={PackageCheck}>{formatNumber(partner.completedDeliveries)} completed</Chip>
        </div>
      </div>
    </section>
  );
}
