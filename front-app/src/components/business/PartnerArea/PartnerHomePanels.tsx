import {
  ArrowRight,
  BellRing,
  Bike,
  Check,
  CheckCircle2,
  ListChecks,
  MapPin,
  Power,
  Radio,
  Timer,
  UserRound,
  Wallet,
  type LucideIcon,
} from 'lucide-react';
import { useId, type ReactNode } from 'react';
import { Link } from 'react-router';

import {
  NightStreet,
  RadarMap,
  SetupScene,
  ShieldScene,
} from '@/components/business/PartnerArea/PartnerIllustrations';
import { useAvailabilitySwitch } from '@/components/business/PartnerArea/useAvailabilitySwitch';
import { VEHICLE_ICONS } from '@/components/business/PartnerArea/vehicleIcons';
import { Alert } from '@/components/common/Alert';
import { Button, buttonStyles } from '@/components/common/Button';
import type { Partner } from '@/types/delivery';
import { cn } from '@/utils/classNames';
import { PARTNER_ROUTES } from '@/utils/constants';
import { OFFER_ACCEPT_SECONDS } from '@/utils/partnerAreaUtils';

/** A panel of the Today page: the illustration (a banner across the top) above the words. */
const PANEL = 'overflow-hidden rounded-3xl bg-surface shadow-card';
const BANNER = 'relative h-40 sm:h-48';
const BODY = 'p-6 sm:p-8';

function Tip({ icon: Icon, children }: { icon: LucideIcon; children: ReactNode }) {
  return (
    <li className="inline-flex items-center gap-1.5 rounded-full bg-brand-50 px-3 py-1.5 text-xs font-semibold text-brand-text-strong ring-1 ring-brand-100">
      <Icon aria-hidden="true" className="size-3.5" />
      {children}
    </li>
  );
}

/**
 * Online with no delivery yet: a map of the partner's city with a radar sweeping out from them. New offers appear by
 * themselves (live updates, and the page asks again every few seconds while the partner is online).
 */
export function WaitingPanel({ partner }: { partner: Partner }) {
  const VehicleIcon = partner.vehicleType ? VEHICLE_ICONS[partner.vehicleType] : Bike;
  return (
    <section aria-label="Your delivery" className={PANEL}>
      <div aria-hidden="true" className={BANNER}>
        <RadarMap className="absolute inset-0 size-full" />
        <span className="absolute top-3 left-3 inline-flex items-center gap-1.5 rounded-full bg-surface/90 px-3 py-1 text-xs font-bold text-brand-text-strong shadow-card backdrop-blur-sm">
          <span className="relative flex size-2">
            <span className="absolute inline-flex size-full rounded-full bg-brand-500 motion-safe:animate-ping" />
            <span className="relative inline-flex size-2 rounded-full bg-brand-500" />
          </span>
          Live
        </span>
        <span className="absolute top-1/2 left-1/2 flex size-14 -translate-x-1/2 -translate-y-1/2 items-center justify-center rounded-full bg-brand-500 text-on-brand shadow-brand-lg ring-4 ring-surface">
          <VehicleIcon className="size-6" />
        </span>
      </div>
      <div role="status" className={cn(BODY, 'flex flex-col items-center gap-4 text-center')}>
        <p className="text-lg font-bold">Waiting for a delivery in {partner.city}…</p>
        <p className="max-w-sm text-sm text-ink-soft">
          When a restaurant has food ready, the delivery is offered to the partner who has waited
          longest. Keep this page open: the offer appears here.
        </p>
        <ul className="flex flex-wrap justify-center gap-2">
          <Tip icon={BellRing}>Offers appear by themselves</Tip>
          <Tip icon={Timer}>{OFFER_ACCEPT_SECONDS / 60} minutes to accept</Tip>
        </ul>
      </div>
    </section>
  );
}

/** Offline (with a vehicle and a city): a quiet night street, a big "Go online", and why the platform set them offline. */
export function OfflinePanel({ partner }: { partner: Partner }) {
  const { switchTo, isPending } = useAvailabilitySwitch();
  const titleId = useId();
  return (
    <section aria-labelledby={titleId} className={PANEL}>
      <div aria-hidden="true" className={BANNER}>
        <NightStreet className="absolute inset-0" />
      </div>
      <div className={cn(BODY, 'flex flex-col items-center gap-4 text-center')}>
        <h2 id={titleId} className="text-lg font-bold">
          No delivery right now
        </h2>
        <p className="max-w-sm text-sm text-ink-soft">
          You are offline. Go online to receive delivery offers.
        </p>
        {partner.statusReason && (
          <div className="w-full max-w-md text-left">
            <Alert tone="info">{partner.statusReason}</Alert>
          </div>
        )}
        <Button
          size="lg"
          className="w-full sm:w-auto sm:min-w-56"
          leftIcon={<Power aria-hidden="true" className="size-5" />}
          isLoading={isPending}
          onClick={() => switchTo(true)}
        >
          Go online
        </Button>
      </div>
    </section>
  );
}

const SETUP_STEPS = [
  { label: 'Partner account created', done: true },
  { label: 'Your vehicle and its number plate', done: false },
  { label: 'The city you deliver in', done: false },
  { label: 'Go online and take your first delivery', done: false },
] as const;

/** A new partner: what is left before the first delivery (the vehicle and the city), as a checklist. */
export function SetupCard() {
  const titleId = useId();
  const done = SETUP_STEPS.filter((step) => step.done).length;
  return (
    <section aria-labelledby={titleId} className={PANEL}>
      <div aria-hidden="true" className={cn(BANNER, 'bg-warning-100')}>
        <SetupScene className="absolute inset-0 size-full" />
      </div>
      <div className={cn(BODY, 'flex flex-col gap-5')}>
        <div className="flex flex-col gap-1">
          <h2 id={titleId} className="text-lg font-bold">
            One step before your first delivery
          </h2>
          <p className="text-sm text-ink-soft">
            Add your vehicle and the city you deliver in: deliveries are offered from restaurants in
            that city.
          </p>
        </div>

        <div className="flex items-center gap-3">
          <span aria-hidden="true" className="h-2.5 flex-1 overflow-hidden rounded-full bg-line">
            <span
              className="block h-full rounded-full bg-linear-to-r from-brand-400 to-brand-600"
              style={{ width: `${(done / SETUP_STEPS.length) * 100}%` }}
            />
          </span>
          <span className="text-xs font-semibold text-ink-soft">
            {done} of {SETUP_STEPS.length} done
          </span>
        </div>

        <ol className="flex flex-col gap-3">
          {SETUP_STEPS.map((step, index) => (
            <li
              key={step.label}
              className={cn(
                'flex items-center gap-3 rounded-2xl px-3 py-2.5 text-sm',
                step.done ? 'bg-brand-50' : 'bg-page',
              )}
            >
              <span
                aria-hidden="true"
                className={cn(
                  'flex size-7 shrink-0 items-center justify-center rounded-full text-xs font-bold',
                  step.done
                    ? 'bg-brand-500 text-on-brand'
                    : 'border-2 border-line bg-surface text-ink-soft',
                )}
              >
                {step.done ? <Check className="size-4" strokeWidth={3} /> : index + 1}
              </span>
              <span className={cn(step.done ? 'text-ink-soft line-through' : 'font-semibold')}>
                {step.label}
                <span className="sr-only">{step.done ? ' (done)' : ' (to do)'}</span>
              </span>
            </li>
          ))}
        </ol>

        <Link
          to={PARTNER_ROUTES.profile}
          className={buttonStyles('primary', 'lg', 'sm:self-start')}
        >
          Add them now
          <ArrowRight aria-hidden="true" className="size-5" />
        </Link>
      </div>
    </section>
  );
}

/** Suspended: no deliveries until the platform reactivates the account (the reason is in the hero above). */
export function SuspendedPanel() {
  const titleId = useId();
  return (
    <section aria-labelledby={titleId} className={PANEL}>
      <div aria-hidden="true" className={cn(BANNER, 'bg-danger-50')}>
        <ShieldScene className="absolute inset-0 size-full" />
      </div>
      <div className={cn(BODY, 'flex flex-col items-center gap-3 text-center')}>
        <h2 id={titleId} className="text-lg font-bold">
          No deliveries while suspended
        </h2>
        <p className="max-w-sm text-sm text-ink-soft">
          Deliveries are not offered to you until the platform reactivates your account.
        </p>
      </div>
    </section>
  );
}

/** While the current delivery is being looked up. */
export function LookingUpPanel() {
  return (
    <section aria-label="Your delivery" className={cn(PANEL, BODY)}>
      <p
        role="status"
        className="flex items-center justify-center gap-3 py-6 text-sm text-ink-soft"
      >
        <span
          aria-hidden="true"
          className="size-5 rounded-full border-2 border-brand-500 border-t-transparent motion-safe:animate-spin"
        />
        <span>Looking for your current delivery…</span>
      </p>
    </section>
  );
}

const QUICK_LINKS = [
  {
    to: PARTNER_ROUTES.deliveries,
    icon: ListChecks,
    title: 'My deliveries',
    text: 'History, totals and cash',
    tone: 'from-brand-400 to-brand-600',
  },
  {
    to: PARTNER_ROUTES.profile,
    icon: UserRound,
    title: 'Profile',
    text: 'Vehicle, city and account',
    tone: 'from-sunset-500 to-sunset-600',
  },
] as const;

/** Two big shortcuts to the other pages of the partner area. */
export function QuickLinks() {
  return (
    <nav
      aria-label="Shortcuts"
      className="grid gap-3 sm:grid-cols-2 lg:grid-cols-1 2xl:grid-cols-2"
    >
      {QUICK_LINKS.map(({ to, icon: Icon, title, text, tone }) => (
        <Link
          key={to}
          to={to}
          className="group flex items-center gap-3 rounded-2xl bg-surface p-4 shadow-card ring-1 ring-transparent transition duration-200 hover:ring-brand-300 focus-visible:ring-4 focus-visible:ring-brand-500/30 focus-visible:outline-none motion-safe:hover:-translate-y-0.5"
        >
          <span
            aria-hidden="true"
            className={cn(
              'flex size-11 shrink-0 items-center justify-center rounded-xl bg-linear-to-br text-white shadow-card',
              tone,
            )}
          >
            <Icon className="size-5" />
          </span>
          <span className="min-w-0 flex-1">
            <span className="block text-sm font-bold">{title}</span>
            <span className="block truncate text-xs text-ink-soft">{text}</span>
          </span>
          <ArrowRight
            aria-hidden="true"
            className="size-4 shrink-0 text-ink-soft transition-transform group-hover:translate-x-0.5"
          />
        </Link>
      ))}
    </nav>
  );
}

const HOW_IT_WORKS = [
  { icon: Power, text: 'Go online when you are ready to work.' },
  {
    icon: BellRing,
    text: `A delivery from a restaurant in your city is offered to you: accept it within ${OFFER_ACCEPT_SECONDS / 60} minutes.`,
  },
  { icon: MapPin, text: 'Go to the restaurant and tap "picked up" once the food is with you.' },
  {
    icon: Wallet,
    text: 'Hand it to the customer; for cash on delivery, collect the exact amount.',
  },
  {
    icon: CheckCircle2,
    text: 'Mark it delivered: you are available for the next one straight away.',
  },
] as const;

/** The partner's day in five steps, joined by a line, under a green header with a dotted route. */
export function HowItWorks() {
  const titleId = useId();
  return (
    <section
      aria-labelledby={titleId}
      className="overflow-hidden rounded-3xl bg-surface shadow-card"
    >
      <header className="relative isolate overflow-hidden bg-linear-to-br from-brand-500 to-brand-700 px-6 py-5 text-white">
        <span
          aria-hidden="true"
          className="absolute -top-10 -right-8 -z-10 size-36 rounded-full bg-white/15"
        />
        <svg
          aria-hidden="true"
          viewBox="0 0 120 40"
          className="absolute right-4 bottom-2 -z-10 h-9 w-28 text-white/60"
        >
          <path
            d="M4 32 C30 32 24 10 52 10 S84 34 112 8"
            fill="none"
            stroke="currentColor"
            strokeWidth="2.5"
            strokeDasharray="1 7"
            strokeLinecap="round"
          />
          <circle cx="112" cy="8" r="4" fill="currentColor" />
        </svg>
        <p className="flex items-center gap-1.5 text-xs font-semibold opacity-90">
          <Radio aria-hidden="true" className="size-3.5" />
          Your day in five steps
        </p>
        <h2 id={titleId} className="mt-0.5 text-lg font-extrabold tracking-tight">
          How it works
        </h2>
      </header>
      <ol className="flex flex-col p-6">
        {HOW_IT_WORKS.map(({ icon: Icon, text }, index) => (
          <li key={text} className="relative flex gap-4 pb-5 text-sm last:pb-0">
            {index < HOW_IT_WORKS.length - 1 && (
              <span
                aria-hidden="true"
                className="absolute top-10 bottom-0 left-4.5 w-0.5 rounded-full bg-brand-100"
              />
            )}
            <span
              aria-hidden="true"
              className="relative flex size-9 shrink-0 items-center justify-center rounded-xl bg-linear-to-br from-brand-400 to-brand-600 text-white shadow-brand"
            >
              <Icon className="size-4.5" />
              <span className="absolute -top-1.5 -right-1.5 flex size-4.5 items-center justify-center rounded-full bg-surface text-[10px] font-extrabold text-brand-text-strong ring-1 ring-brand-200">
                {index + 1}
              </span>
            </span>
            <span className="pt-2">
              <span className="sr-only">Step {index + 1}: </span>
              {text}
            </span>
          </li>
        ))}
      </ol>
    </section>
  );
}
