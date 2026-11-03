import { CalendarDays, MapPin } from 'lucide-react';
import { Link } from 'react-router';

import { SearchBox } from '@/components/business/Storefront';
import { useAddresses } from '@/hooks/api/addressHooks';
import { ADDRESS_TYPE_LABELS, CUSTOMER_ROUTES, DISPLAY_LOCALE } from '@/utils/constants';

/** 'Sunday, 18 October' */
function formatToday(today: Date): string {
  return today.toLocaleDateString(DISPLAY_LOCALE, {
    weekday: 'long',
    day: 'numeric',
    month: 'long',
  });
}

/** "Good morning" before noon, "Good afternoon" until five, then "Good evening" (the customer's own clock). */
function greetingOf(hour: number): string {
  if (hour >= 5 && hour < 12) {
    return 'Good morning';
  }
  return hour >= 12 && hour < 17 ? 'Good afternoon' : 'Good evening';
}

/**
 * The top of the dashboard, kept low: today's date and a greeting by name, where the food goes (the default
 * address, with a way to change it) and a search for dishes and restaurants. The cart is one of the quick actions
 * right below; the site's header keeps the notifications and the account menu.
 */
export function DashboardWelcome({ firstName }: { firstName: string | undefined }) {
  const addresses = useAddresses();
  const address = addresses.data?.find((entry) => entry.defaultAddress) ?? addresses.data?.[0];

  return (
    <section
      aria-labelledby="dashboard-title"
      className="relative isolate overflow-hidden rounded-3xl bg-linear-to-br from-hero-orders-from via-hero-orders-via to-hero-orders-to px-5 py-5 text-white shadow-lg shadow-hero-orders-to/20 sm:px-6"
    >
      {/* Soft coloured light behind the content: rose at the top right, amber below */}
      <span
        aria-hidden="true"
        className="absolute -top-24 -right-12 -z-10 size-64 rounded-full bg-st-placed-to/40 blur-3xl"
      />
      <span
        aria-hidden="true"
        className="absolute -bottom-28 left-1/3 -z-10 size-64 rounded-full bg-warning-500/25 blur-3xl"
      />
      <div className="flex flex-col gap-4 lg:flex-row lg:items-end lg:justify-between lg:gap-8">
        <div className="flex min-w-0 flex-col gap-2.5">
          <p className="inline-flex w-fit items-center gap-2 rounded-full bg-white/15 px-2.5 py-0.5 text-xs font-semibold ring-1 ring-white/25">
            <CalendarDays aria-hidden="true" className="size-3.5" />
            {formatToday(new Date())}
          </p>
          <h1
            id="dashboard-title"
            className="text-2xl leading-tight font-extrabold tracking-tight sm:text-3xl"
          >
            {greetingOf(new Date().getHours())}
            {firstName ? `, ${firstName}` : ''}
          </h1>
          <p className="flex w-fit max-w-full flex-wrap items-center gap-x-2 gap-y-0.5 rounded-xl bg-white/15 px-3 py-1.5 text-sm ring-1 ring-white/25">
            <MapPin aria-hidden="true" className="size-4 shrink-0" />
            {address ? (
              <>
                <span className="font-semibold">
                  Deliver to {ADDRESS_TYPE_LABELS[address.type]}:
                </span>
                <span className="min-w-0 truncate">
                  {address.line1}, {address.city}
                </span>
              </>
            ) : (
              <span>
                {addresses.isPending ? 'Loading your address…' : 'No delivery address yet'}
              </span>
            )}
            <Link
              to={CUSTOMER_ROUTES.addresses}
              className="rounded-md font-bold underline-offset-2 hover:underline focus-visible:ring-2 focus-visible:ring-white focus-visible:outline-none"
            >
              {address ? 'Change' : 'Add one'}
            </Link>
          </p>
        </div>
        <SearchBox className="w-full text-ink lg:max-w-md" />
      </div>
    </section>
  );
}
