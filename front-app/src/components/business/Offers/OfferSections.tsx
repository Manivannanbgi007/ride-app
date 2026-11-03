import {
  ArrowRight,
  BadgePercent,
  CircleCheck,
  Info,
  ShoppingBag,
  Sparkles,
  TicketPercent,
  type LucideIcon,
} from 'lucide-react';
import { useState, type ReactNode } from 'react';
import { Link } from 'react-router';

import { CopyCode } from '@/components/business/Offers/CopyCode';
import { CouponTicket, OfferDeadline } from '@/components/business/Offers/OfferCards';
import { UseOffer } from '@/components/business/Offers/UseOffer';
import { buttonStyles } from '@/components/common/Button';
import { EmptyState } from '@/components/common/EmptyState';
import { useNow } from '@/hooks/useNow';
import type { Offer } from '@/types/shopping';
import { cn } from '@/utils/classNames';
import { CUSTOMER_ROUTES, customerRouteTo } from '@/utils/constants';
import { categoryImageOf } from '@/utils/foodImages';
import { endsWithinWeek, offerMinimumLabel, offerScopeLabel } from '@/utils/offerUtils';

export interface OfferSectionLink {
  id: string;
  label: string;
}

const STEPS: readonly { icon: LucideIcon; title: string; text: string }[] = [
  { icon: TicketPercent, title: 'Pick a coupon', text: 'Copy its code or apply it' },
  { icon: ShoppingBag, title: 'Fill your cart', text: 'From any open restaurant' },
  { icon: BadgePercent, title: 'Save at checkout', text: 'The cart checks it fits' },
];

/**
 * The top of the offers page: how many coupons there are today, the three steps to use one, chips to jump to the
 * sections below, and the coupon that ends soonest as a ticket to copy or apply right away.
 */
export function OffersHero({
  couponCount,
  spotlight,
  cartHasItems,
  sections,
}: {
  couponCount: number | undefined;
  /** The coupon shown large at the right: the one that ends soonest */
  spotlight?: Offer;
  cartHasItems: boolean;
  sections: readonly OfferSectionLink[];
}) {
  return (
    <section
      aria-labelledby="offers-title"
      className="relative isolate overflow-hidden rounded-3xl bg-linear-to-br from-brand-600 via-brand-700 to-brand-900 p-6 text-white shadow-brand-lg sm:p-10"
    >
      <span
        aria-hidden="true"
        className="absolute -top-24 -left-20 -z-10 size-72 rounded-full bg-white/10"
      />
      <span
        aria-hidden="true"
        className="absolute -right-16 -bottom-28 -z-10 size-80 rounded-full bg-lime-500/25 blur-3xl"
      />
      <span
        aria-hidden="true"
        className="absolute top-10 right-1/3 -z-10 size-24 rounded-full bg-sunset-500/30 blur-2xl"
      />

      <div className="grid items-center gap-8 lg:grid-cols-[minmax(0,1.2fr)_minmax(0,1fr)]">
        <div className="flex flex-col gap-5">
          <p className="inline-flex w-fit items-center gap-2 rounded-full bg-white/15 px-4 py-1.5 text-sm font-semibold ring-1 ring-white/25">
            <Sparkles aria-hidden="true" className="size-4" />
            {couponCount
              ? `${couponCount} ${couponCount === 1 ? 'coupon' : 'coupons'} you can use today`
              : 'Coupons you can use today'}
          </p>
          <div>
            <h1
              id="offers-title"
              className="text-4xl leading-[1.05] font-extrabold tracking-tight sm:text-6xl"
            >
              Offers and deals
            </h1>
            <p className="mt-3 max-w-xl text-base text-white/90 sm:text-lg">
              Copy a code, or apply it to your cart in one tap. The cart checks that your order
              qualifies.
            </p>
          </div>
          <ol aria-label="How to use a coupon" className="grid gap-3 sm:grid-cols-3">
            {STEPS.map((step, index) => (
              <li
                key={step.title}
                className="flex items-center gap-3 rounded-2xl bg-white/10 p-3 ring-1 ring-white/20 backdrop-blur-sm"
              >
                <span
                  aria-hidden="true"
                  className="relative flex size-10 shrink-0 items-center justify-center rounded-xl bg-white text-brand-text-strong"
                >
                  <step.icon className="size-5" />
                  <span className="absolute -top-1.5 -right-1.5 flex size-5 items-center justify-center rounded-full bg-sunset-500 text-[11px] font-extrabold text-white">
                    {index + 1}
                  </span>
                </span>
                <span className="flex min-w-0 flex-col">
                  <span className="text-sm font-bold">{step.title}</span>
                  <span className="text-xs text-white/85">{step.text}</span>
                </span>
              </li>
            ))}
          </ol>
          {sections.length > 0 && (
            <nav aria-label="Sections of this page">
              <ul className="flex flex-wrap gap-2">
                {sections.map((section) => (
                  <li key={section.id}>
                    <a
                      href={`#${section.id}`}
                      className="inline-flex h-9 items-center rounded-full bg-white/15 px-4 text-sm font-semibold ring-1 ring-white/30 transition-colors hover:bg-white hover:text-brand-text-strong focus-visible:ring-2 focus-visible:ring-white focus-visible:outline-none"
                    >
                      {section.label}
                    </a>
                  </li>
                ))}
              </ul>
            </nav>
          )}
        </div>

        {spotlight ? (
          <SpotlightTicket offer={spotlight} cartHasItems={cartHasItems} />
        ) : (
          <div className="hidden flex-col items-center justify-center gap-3 rounded-3xl border-2 border-dashed border-white/40 p-10 text-center lg:flex">
            <TicketPercent aria-hidden="true" className="size-12 text-white/80" />
            <p className="text-lg font-bold">New coupons appear here</p>
            <p className="text-sm text-white/85">As soon as an offer starts, it shows up first.</p>
          </div>
        )}
      </div>
    </section>
  );
}

/** The hero's ticket: the coupon ending soonest, with its countdown, its code and the action. */
function SpotlightTicket({ offer, cartHasItems }: { offer: Offer; cartHasItems: boolean }) {
  return (
    <section
      aria-label="Ending soonest"
      className="relative rounded-3xl bg-surface p-6 text-ink shadow-2xl ring-1 ring-white/30 motion-safe:lg:rotate-1"
    >
      <p className="flex flex-wrap items-center justify-between gap-2 text-xs">
        <span className="inline-flex items-center gap-1.5 font-bold text-sunset-text uppercase">
          <Sparkles aria-hidden="true" className="size-3.5" />
          Ending soonest
        </span>
        <OfferDeadline offer={offer} />
      </p>
      <p className="mt-4 text-4xl leading-tight font-extrabold tracking-tight text-brand-text-strong">
        {offer.summary}
      </p>
      {offer.description && <p className="mt-2 font-semibold text-ink">{offer.description}</p>}
      <p className="mt-2 text-sm text-ink-soft">
        {offerMinimumLabel(offer)} · {offerScopeLabel(offer)}
      </p>
      <div className="mt-5 flex items-center gap-2 border-t-2 border-dashed border-line pt-5">
        <CopyCode code={offer.code} look="solid" className="flex-1" />
        <UseOffer offer={offer} cartHasItems={cartHasItems} variant="outline" />
      </div>
    </section>
  );
}

/** A section's heading, with an optional link at the right ("See all restaurants"). */
export function OfferSectionHeading({
  id,
  title,
  subtitle,
  link,
}: {
  id: string;
  title: string;
  subtitle?: string;
  link?: { to: string; label: string };
}) {
  return (
    <div className="flex flex-wrap items-end justify-between gap-x-4 gap-y-1">
      <div>
        <h2 id={id} className="text-2xl font-extrabold tracking-tight sm:text-3xl">
          {title}
        </h2>
        {subtitle && <p className="mt-1 text-sm text-ink-soft">{subtitle}</p>}
      </div>
      {link && (
        <Link
          to={link.to}
          className="inline-flex items-center gap-1 text-sm font-semibold text-brand-text hover:underline"
        >
          {link.label}
          <ArrowRight aria-hidden="true" className="size-4" />
        </Link>
      )}
    </div>
  );
}

const FILTERS = [
  { id: 'all', label: 'All', keep: () => true },
  {
    id: 'everywhere',
    label: 'All restaurants',
    keep: (offer: Offer) => offer.restaurantId === null,
  },
  {
    id: 'restaurant',
    label: 'Restaurant specials',
    keep: (offer: Offer) => offer.restaurantId !== null,
  },
  {
    id: 'soon',
    label: 'Ending soon',
    keep: (offer: Offer, now: number) => endsWithinWeek(offer, now),
  },
] as const;
type FilterId = (typeof FILTERS)[number]['id'];

/**
 * Every coupon usable today as tickets, with tabs to narrow them down (where they can be used, or ending within a
 * week; a tab without coupons is left out), and a line on how "Apply" works for this customer.
 */
export function CouponCenter({
  offers,
  cartHasItems,
  signedIn,
}: {
  offers: readonly Offer[];
  cartHasItems: boolean;
  signedIn: boolean;
}) {
  const now = useNow(60_000);
  const [filter, setFilter] = useState<FilterId>('all');
  const counts = Object.fromEntries(
    FILTERS.map((entry) => [entry.id, offers.filter((offer) => entry.keep(offer, now)).length]),
  ) as Record<FilterId, number>;
  const tabs = FILTERS.filter((entry) => entry.id === 'all' || counts[entry.id] > 0);
  const chosen = counts[filter] > 0 ? filter : 'all';
  const keep = FILTERS.find((entry) => entry.id === chosen)?.keep ?? (() => true);
  const shown = offers.filter((offer) => keep(offer, now));

  return (
    <div className="flex flex-col gap-5">
      <div className="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
        {tabs.length > 1 && (
          <div role="group" aria-label="Show coupons" className="flex flex-wrap gap-2">
            {tabs.map((tab) => {
              const pressed = tab.id === chosen;
              return (
                <button
                  key={tab.id}
                  type="button"
                  aria-pressed={pressed}
                  onClick={() => setFilter(tab.id)}
                  className={cn(
                    'inline-flex h-10 cursor-pointer items-center gap-2 rounded-full pr-2 pl-4 text-sm font-bold transition duration-200',
                    'focus-visible:ring-4 focus-visible:ring-brand-500/30 focus-visible:outline-none',
                    pressed
                      ? 'bg-brand-500 text-on-brand shadow-brand'
                      : 'bg-surface text-ink shadow-sm ring-1 ring-line hover:bg-brand-50 hover:ring-brand-300',
                  )}
                >
                  {tab.label}
                  <span
                    className={cn(
                      'min-w-6 rounded-full px-2 py-0.5 text-center text-xs tabular-nums',
                      pressed ? 'bg-on-brand/15' : 'bg-brand-50 text-brand-text-strong',
                    )}
                  >
                    {counts[tab.id]}
                  </span>
                </button>
              );
            })}
          </div>
        )}
        <ApplyHint signedIn={signedIn} cartHasItems={cartHasItems} />
      </div>
      {shown.length === 0 ? (
        <EmptyState title="No coupons here" />
      ) : (
        <ul aria-label="Coupons" className="grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
          {shown.map((offer, index) => (
            <li key={offer.code} className="flex">
              <CouponTicket offer={offer} index={index} cartHasItems={cartHasItems} />
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

/** One line on what "Apply" does for this customer right now. */
function ApplyHint({ signedIn, cartHasItems }: { signedIn: boolean; cartHasItems: boolean }) {
  const [Icon, text]: [LucideIcon, ReactNode] = !signedIn
    ? [Info, 'Sign in to apply a coupon to your cart in one tap.']
    : cartHasItems
      ? [
          CircleCheck,
          'Your cart is ready: "Apply to cart" checks a coupon against your order straight away.',
        ]
      : [Info, 'Add dishes to your cart, then apply a coupon here in one tap.'];
  return (
    <p className="flex items-center gap-2 rounded-2xl bg-surface px-4 py-2.5 text-sm text-ink shadow-sm ring-1 ring-line lg:max-w-md">
      <Icon aria-hidden="true" className="size-4 shrink-0 text-brand-text" />
      {text}
    </p>
  );
}

/**
 * Seasonal picks: editorial themes (no backend yet), each leading to the dishes it is about. They promise no
 * discount of their own: the coupons above are what customers apply.
 */
const SEASONAL_PICKS = [
  // Overlays use fixed colours (not theme tokens): the text on them stays white in light and dark mode
  {
    tag: 'This season',
    title: 'Summer coolers',
    text: 'Cold drinks, shakes and desserts for the hottest afternoons.',
    image: categoryImageOf('beverages'),
    search: 'beverages',
    tone: 'from-sky-950/90 via-sky-900/45',
  },
  {
    tag: 'Weekends',
    title: 'Weekend feast',
    text: 'Biryanis and family-size meals to share on Saturday and Sunday.',
    image: categoryImageOf('biryani'),
    search: 'biryani',
    tone: 'from-orange-950/90 via-orange-900/45',
  },
  {
    tag: 'After 10 pm',
    title: 'Late-night cravings',
    text: 'Pizzas, burgers and sweets when the kitchen at home is closed.',
    image: categoryImageOf('pizza'),
    search: 'pizza',
    tone: 'from-rose-950/90 via-rose-900/45',
  },
] as const;

export function SeasonalPicks() {
  return (
    <ul className="grid gap-6 md:grid-cols-3">
      {SEASONAL_PICKS.map((pick) => (
        <li key={pick.title}>
          <Link
            to={customerRouteTo.search(pick.search)}
            className="group relative flex h-56 flex-col justify-end overflow-hidden rounded-3xl p-6 text-white shadow-card focus-visible:ring-2 focus-visible:ring-brand-500 focus-visible:outline-none"
          >
            {pick.image && (
              <img
                src={pick.image}
                alt=""
                loading="lazy"
                className="absolute inset-0 size-full object-cover transition-transform duration-500 group-hover:scale-105 motion-reduce:transition-none"
              />
            )}
            <span
              aria-hidden="true"
              className={cn('absolute inset-0 bg-linear-to-t to-transparent', pick.tone)}
            />
            <span className="relative w-fit rounded-full bg-white/20 px-2.5 py-0.5 text-xs font-bold ring-1 ring-white/30 backdrop-blur-sm">
              {pick.tag}
            </span>
            <span className="relative mt-2 text-xl font-extrabold">{pick.title}</span>
            <span className="relative mt-1 text-sm text-white/90">{pick.text}</span>
            <span className="relative mt-3 inline-flex w-fit items-center gap-1 text-sm font-bold">
              See dishes
              <ArrowRight
                aria-hidden="true"
                className="size-4 transition-transform group-hover:translate-x-1"
              />
            </span>
          </Link>
        </li>
      ))}
    </ul>
  );
}

/** The closing band: go and order; visitors are invited to create an account, customers to their cart. */
export function SavingsBanner({ signedIn }: { signedIn: boolean }) {
  return (
    <section className="relative overflow-hidden rounded-3xl bg-linear-to-br from-brand-600 to-brand-800 px-6 py-12 text-center text-white shadow-brand-lg sm:py-14">
      <span
        aria-hidden="true"
        className="absolute -right-16 -bottom-20 size-64 rounded-full bg-lime-500/20 blur-2xl"
      />
      <span className="relative mx-auto flex size-14 items-center justify-center rounded-2xl bg-white/15 ring-1 ring-white/25">
        <ShoppingBag aria-hidden="true" className="size-7" />
      </span>
      <h2 className="relative mt-4 text-3xl font-extrabold tracking-tight">Start saving today</h2>
      <p className="relative mx-auto mt-2 max-w-md text-white/85">
        Pick your food, then apply a code at checkout. The best deal for your order is one tap away.
      </p>
      <div className="relative mt-6 flex flex-wrap justify-center gap-3">
        <Link
          to={CUSTOMER_ROUTES.restaurants}
          className={buttonStyles('secondary', 'lg', 'bg-surface text-brand-text-strong')}
        >
          Order now
          <ArrowRight aria-hidden="true" className="size-4" />
        </Link>
        <Link
          to={signedIn ? CUSTOMER_ROUTES.cart : CUSTOMER_ROUTES.register}
          className="inline-flex h-12 items-center rounded-xl px-6 text-base font-semibold ring-2 ring-white/70 transition-colors hover:bg-white/10 focus-visible:ring-white focus-visible:outline-none"
        >
          {signedIn ? 'Go to my cart' : 'Create an account'}
        </Link>
      </div>
    </section>
  );
}
