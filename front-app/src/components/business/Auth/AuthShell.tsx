import {
  BarChart3,
  BellRing,
  Bike,
  ChefHat,
  ClipboardList,
  CreditCard,
  MapPin,
  Power,
  ShieldCheck,
  Star,
  Store,
  Timer,
  Truck,
  UtensilsCrossed,
  Wallet,
  type LucideIcon,
} from 'lucide-react';
import type { ReactNode } from 'react';
import { Link } from 'react-router';

import { buttonStyles } from '@/components/common/Button';
import { ThemeToggle } from '@/components/common/ThemeToggle';
import { cn } from '@/utils/classNames';
import { env } from '@/utils/env';

export type AuthAudience = 'customer' | 'owner' | 'partner' | 'admin';

/** The small word next to the brand name on the staff areas' sign-in. */
const AREA_LABELS: Record<Exclude<AuthAudience, 'customer'>, string> = {
  owner: 'Restaurant',
  partner: 'Partner',
  admin: 'Admin',
};

/** A little card floating on the picture: what the area is about, as the application shows it. */
interface FloatingNote {
  icon: LucideIcon;
  title: string;
  text: string;
}

interface Pitch {
  /** The big words under the picture */
  headline: string;
  text: string;
  photo: string;
  /** On a phone, the small round pictures beside the headline (default: three dishes) */
  strip?: readonly string[];
  /** What the account is good for: only things the application really does */
  points: readonly { icon: LucideIcon; text: string }[];
  /** Two cards on the picture (decoration: the same things the application does) */
  notes: readonly [FloatingNote, FloatingNote];
}

/** The dishes around the main photo (and the strip of the compact banner on a phone). */
const DISHES = [
  '/images/foods/chicken-biryani.jpg',
  '/images/categories/pizza.jpg',
  '/images/categories/desserts.jpg',
];

/** A delivery rider on a scooter with a delivery box: the partner (rider) area's picture. */
const RIDER_PHOTO = '/images/delivery/rider.jpg';

const PITCHES: Record<AuthAudience, Pitch> = {
  customer: {
    headline: 'Hungry? Good food from your city’s restaurants.',
    text: 'Order in a few taps and follow it to your door.',
    photo: '/images/categories/biryani.jpg',
    points: [
      { icon: UtensilsCrossed, text: 'Dishes, restaurants and offers near you' },
      { icon: Truck, text: 'Follow your order until it is delivered' },
      { icon: CreditCard, text: 'Pay online or in cash on delivery' },
      { icon: Star, text: 'Rate what you ate, and read what others say' },
    ],
    notes: [
      { icon: Bike, title: 'Order on the way', text: 'Follow it live to your door' },
      { icon: Star, title: 'Ratings and reviews', text: 'From customers like you' },
    ],
  },
  owner: {
    headline: 'Run your restaurant from one place.',
    text: 'Take orders, keep your menu fresh and hear from your customers.',
    photo: '/images/categories/north-indian.jpg',
    points: [
      { icon: BellRing, text: 'New orders appear as they arrive' },
      { icon: ClipboardList, text: 'Accept, prepare and hand over in a few clicks' },
      { icon: ChefHat, text: 'Change prices and mark dishes sold out any time' },
      { icon: Star, text: 'See your ratings and every review' },
    ],
    notes: [
      { icon: BellRing, title: 'New order', text: 'Accept it in one click' },
      { icon: ChefHat, title: 'Menu updated', text: 'Prices and stock, any time' },
    ],
  },
  partner: {
    headline: 'Deliver good food, on your own time.',
    text: 'Go online when you want to work: orders from your city come to you.',
    // A rider, not a dish: at a glance, this is the riders' sign-in (on a phone too, first in the strip)
    photo: RIDER_PHOTO,
    strip: [RIDER_PHOTO, DISHES[0], DISHES[1]],
    points: [
      { icon: Power, text: 'Go online and offline whenever you like' },
      { icon: BellRing, text: 'Delivery offers from restaurants in your city' },
      { icon: MapPin, text: 'Pickup and drop-off addresses, step by step' },
      { icon: Wallet, text: 'Cash on delivery: the exact amount to collect' },
      { icon: ClipboardList, text: 'Every delivery you made, in one list' },
    ],
    notes: [
      { icon: Power, title: 'You are online', text: 'Offers from your city' },
      { icon: Timer, title: 'Delivery offer', text: 'Accept within 2:00' },
    ],
  },
  admin: {
    headline: 'Keep the platform running smoothly.',
    text: 'Everything the team needs to look after orders, restaurants and customers.',
    photo: '/images/categories/chinese.jpg',
    points: [
      { icon: ClipboardList, text: 'Follow every order from placed to delivered' },
      { icon: Store, text: 'Approve restaurants and look after their menus' },
      { icon: CreditCard, text: 'Payments, refunds and coupons in one place' },
      { icon: BarChart3, text: 'Reports, reviews and a full audit log' },
      { icon: ShieldCheck, text: 'Every admin action is recorded' },
    ],
    notes: [
      { icon: ShieldCheck, title: 'Restaurant approved', text: 'Recorded in the audit log' },
      { icon: BarChart3, title: 'Reports ready', text: 'Orders, revenue, deliveries' },
    ],
  },
};

/** The fields of the auth forms: a little taller and rounder than elsewhere (the shared Input stays as it is). */
const FORM_FIELDS = cn(
  '[&_input:not([type=checkbox]):not([type=radio])]:h-12',
  '[&_input:not([type=checkbox]):not([type=radio])]:rounded-2xl',
);

function Brand({ audience }: { audience: AuthAudience }) {
  return (
    <p className="flex items-baseline text-2xl font-extrabold tracking-tight">
      {env.appName}
      <span className="text-brand-500">.</span>
      {audience !== 'customer' && (
        <span className="ml-2 rounded-full bg-brand-500/10 px-2.5 py-0.5 text-xs font-bold text-brand-text-strong">
          {AREA_LABELS[audience]}
        </span>
      )}
    </p>
  );
}

function Note({ note, className }: { note: FloatingNote; className: string }) {
  const Icon = note.icon;
  return (
    <div
      className={cn(
        'absolute flex items-center gap-3 rounded-2xl bg-surface/95 py-2.5 pr-4 pl-2.5 shadow-card ring-1 ring-line backdrop-blur-sm',
        className,
      )}
    >
      <span className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-brand-500 text-on-brand shadow-brand">
        <Icon className="size-5" />
      </span>
      <span className="flex flex-col leading-tight whitespace-nowrap">
        <span className="text-sm font-bold">{note.title}</span>
        <span className="text-xs text-ink-soft">{note.text}</span>
      </span>
    </div>
  );
}

/** The picture of the area: a dish on a plate of brand green, more dishes around it and two floating cards. */
function Stage({ pitch }: { pitch: Pitch }) {
  return (
    // Smaller on a short screen (a laptop), so that the picture, the headline and the points fit without scrolling
    <div
      aria-hidden="true"
      className="relative mx-auto aspect-square w-full max-w-[min(26rem,40vh)]"
    >
      <span className="absolute inset-0 rounded-full border-2 border-dashed border-brand-300/70 motion-safe:animate-[spin_90s_linear_infinite]" />
      <span className="absolute inset-[9%] rounded-full bg-linear-to-br from-brand-200 to-brand-100" />
      <img
        src={pitch.photo}
        alt=""
        decoding="async"
        className="absolute inset-[17%] size-[66%] rounded-full object-cover shadow-[0_30px_60px_-24px_rgb(16_40_28/0.55)] ring-8 ring-surface"
      />
      <img
        src={DISHES[0]}
        alt=""
        loading="lazy"
        decoding="async"
        className="absolute top-[4%] right-[6%] size-[22%] rounded-full object-cover shadow-lg ring-4 ring-surface"
      />
      <img
        src={DISHES[2]}
        alt=""
        loading="lazy"
        decoding="async"
        className="absolute bottom-[6%] left-[4%] size-[18%] rounded-full object-cover shadow-lg ring-4 ring-surface"
      />
      {/* Further out on a wide screen (there is room beside the picture), so they hide less of the dish */}
      <Note note={pitch.notes[0]} className="top-[20%] -left-[6%] xl:-left-[16%]" />
      <Note note={pitch.notes[1]} className="-right-[6%] bottom-[16%] xl:-right-[16%]" />
    </div>
  );
}

/** "New here? [Create an account]": the other page of the pair, as a second button under the form. */
export interface AuthAlternative {
  prompt: string;
  label: string;
  to: string;
}

interface AuthShellProps {
  audience: AuthAudience;
  /** Small words above the title ("Welcome back") */
  eyebrow?: string;
  /** The page's heading (the only h1) */
  title: string;
  subtitle?: string;
  /** The form */
  children: ReactNode;
  /** The other page of the pair (sign in / create an account), as a second button under the form */
  alternative?: AuthAlternative;
  /** Under it, in small print: more links (another area's sign-in, the storefront) */
  footer?: ReactNode;
}

/**
 * The frame of the sign-in and registration pages: on a wide screen the picture of what the account is for (a dish on
 * brand green, two cards of what the application does, the selling points) next to an open form column; on a phone
 * a short banner above the form. The staff areas' pages (owner, partner, admin) fill the screen and carry their brand
 * and the theme switch; the customer's sit inside the storefront (its header has both). Light and dark themes follow
 * the colour tokens.
 */
export function AuthShell({
  audience,
  eyebrow,
  title,
  subtitle,
  children,
  alternative,
  footer,
}: AuthShellProps) {
  const pitch = PITCHES[audience];
  const standalone = audience !== 'customer';

  return (
    <div
      className={cn(
        'relative grid w-full gap-6 lg:grid-cols-[minmax(0,1.1fr)_minmax(0,1fr)] lg:gap-8',
        standalone && 'min-h-screen p-3 sm:p-4 lg:p-5',
      )}
    >
      {standalone && (
        <div className="absolute top-6 right-6 z-10 sm:top-7 sm:right-7 lg:top-8 lg:right-8">
          <ThemeToggle />
        </div>
      )}

      <aside className="relative isolate flex flex-col gap-5 overflow-hidden rounded-3xl bg-linear-to-br from-brand-50 via-surface to-brand-100 p-5 ring-1 ring-line sm:p-6 lg:justify-between lg:gap-8 lg:rounded-[2rem] lg:p-10">
        {/* A faint dot grid and a soft glow behind the picture */}
        <span
          aria-hidden="true"
          className="absolute inset-0 -z-10 bg-[radial-gradient(rgb(29_134_76/0.14)_1px,transparent_1.5px)] mask-[radial-gradient(ellipse_at_center,#000_20%,transparent_75%)] bg-size-[20px_20px]"
        />
        <span
          aria-hidden="true"
          className="absolute -top-32 -right-24 -z-10 size-96 rounded-full bg-lime-500/15 blur-3xl"
        />

        {standalone && <Brand audience={audience} />}

        <div className="hidden lg:block">
          <Stage pitch={pitch} />
        </div>

        <div className="flex items-center justify-between gap-4 lg:block">
          <div className="flex flex-col gap-2">
            <p className="text-lg leading-snug font-extrabold tracking-tight sm:text-xl lg:text-3xl lg:leading-tight">
              {pitch.headline}
            </p>
            <p className="hidden text-sm text-ink-soft sm:block">{pitch.text}</p>
          </div>
          {/* On a phone: three small pictures beside the headline instead of the big one */}
          <div aria-hidden="true" className="flex shrink-0 items-center lg:hidden">
            {(pitch.strip ?? DISHES).map((photo, index) => (
              <img
                key={photo}
                src={photo}
                alt=""
                loading="lazy"
                decoding="async"
                className={cn(
                  'size-11 rounded-full object-cover shadow-md ring-2 ring-surface sm:size-12',
                  index > 0 && '-ml-3',
                )}
              />
            ))}
          </div>
          <ul className="mt-6 hidden grid-cols-2 gap-x-6 gap-y-3 lg:grid">
            {pitch.points.map(({ icon: Icon, text }) => (
              <li key={text} className="flex items-center gap-3 text-sm font-medium">
                <span className="flex size-9 shrink-0 items-center justify-center rounded-xl bg-surface text-brand-text-strong shadow-card ring-1 ring-line">
                  <Icon aria-hidden="true" className="size-4.5" />
                </span>
                {text}
              </li>
            ))}
          </ul>
        </div>
      </aside>

      <section className="flex flex-col justify-center px-1 pb-6 sm:px-6 lg:px-10 lg:py-10">
        <div className={cn('mx-auto flex w-full max-w-md flex-col gap-7', FORM_FIELDS)}>
          <div className="flex flex-col items-start gap-3">
            {eyebrow && (
              <span className="inline-flex items-center gap-2 rounded-full bg-brand-500/10 px-3 py-1 text-xs font-bold text-brand-text-strong">
                <span aria-hidden="true" className="size-1.5 rounded-full bg-brand-500" />
                {eyebrow}
              </span>
            )}
            <h1 className="text-3xl font-extrabold tracking-tight sm:text-4xl">{title}</h1>
            {subtitle && <p className="text-sm text-ink-soft sm:text-base">{subtitle}</p>}
          </div>

          {children}

          {alternative && (
            <div className="flex flex-col gap-4">
              <p className="flex items-center gap-3 text-sm text-ink-soft">
                <span aria-hidden="true" className="h-px flex-1 bg-line" />
                {alternative.prompt}
                <span aria-hidden="true" className="h-px flex-1 bg-line" />
              </p>
              <Link to={alternative.to} className={buttonStyles('outline', 'lg', 'w-full')}>
                {alternative.label}
              </Link>
            </div>
          )}

          {footer && <div className="text-center text-sm text-ink-soft">{footer}</div>}
        </div>
      </section>
    </div>
  );
}
