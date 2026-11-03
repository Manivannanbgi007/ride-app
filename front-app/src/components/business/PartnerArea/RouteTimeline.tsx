import { Check, House, Navigation, Phone, Store, type LucideIcon } from 'lucide-react';

import type { Delivery, DeliveryAddress } from '@/types/delivery';
import { cn } from '@/utils/classNames';
import { formatDeliveryAddress, mapsLinkOf } from '@/utils/partnerAreaUtils';

export type RouteLeg = 'pickup' | 'dropoff';

interface StopProps {
  icon: LucideIcon;
  kicker: string;
  name: string | null;
  address: DeliveryAddress | null;
  phone?: string | null;
  isActive: boolean;
  isDone: boolean;
  isLast?: boolean;
}

/** One stop of the route: what and where, with "Navigate" (and "Call" for the customer). */
function Stop({ icon: Icon, kicker, name, address, phone, isActive, isDone, isLast }: StopProps) {
  const text = formatDeliveryAddress(address);
  const maps = mapsLinkOf(address);
  return (
    <li className="relative flex gap-4 pb-6 last:pb-0">
      {!isLast && (
        <span
          aria-hidden="true"
          className="absolute top-12 bottom-1 left-5.5 border-l-2 border-dashed border-line"
        />
      )}
      <span
        aria-hidden="true"
        className={cn(
          'relative flex size-11 shrink-0 items-center justify-center rounded-2xl transition-colors',
          isActive && 'bg-brand-500 text-on-brand shadow-brand ring-4 ring-brand-500/20',
          isDone && 'bg-brand-100 text-brand-text-strong',
          !isActive && !isDone && 'bg-page text-ink-soft',
        )}
      >
        {isDone ? <Check className="size-5" strokeWidth={2.5} /> : <Icon className="size-5" />}
      </span>
      <div className="flex min-w-0 flex-1 flex-col gap-1 pt-0.5">
        <p className="flex flex-wrap items-center gap-2 text-xs font-bold tracking-wider text-ink-soft uppercase">
          {kicker}
          {isActive && (
            <span className="rounded-full bg-brand-50 px-2 py-0.5 text-[10px] tracking-wide text-brand-text-strong">
              Next stop
            </span>
          )}
        </p>
        {name && <p className="text-base font-bold">{name}</p>}
        <p className="text-sm text-ink-soft">{text ?? 'Address on its way'}</p>
        {(maps ?? phone) && (
          <div className="mt-2 flex flex-wrap gap-2">
            {maps && (
              <a
                href={maps}
                target="_blank"
                rel="noreferrer"
                aria-label={`Navigate to ${name ?? kicker.toLowerCase()} (opens in a new tab)`}
                className="inline-flex h-9 items-center gap-1.5 rounded-full bg-brand-50 px-3.5 text-sm font-semibold text-brand-text-strong transition-colors hover:bg-brand-100 focus-visible:ring-4 focus-visible:ring-brand-500/30 focus-visible:outline-none"
              >
                <Navigation aria-hidden="true" className="size-3.5" />
                Navigate
              </a>
            )}
            {phone && (
              <a
                href={`tel:${phone}`}
                className="inline-flex h-9 items-center gap-1.5 rounded-full bg-info-100 px-3.5 text-sm font-semibold text-info-ink transition-opacity hover:opacity-85 focus-visible:ring-4 focus-visible:ring-info-500/30 focus-visible:outline-none"
              >
                <Phone aria-hidden="true" className="size-3.5" />
                {phone}
              </a>
            )}
          </div>
        )}
      </div>
    </li>
  );
}

interface RouteTimelineProps {
  delivery: Delivery;
  /** The stop the partner is heading to now (none: an offer still to decide, or a finished delivery) */
  activeLeg: RouteLeg | null;
}

/**
 * The route of a delivery: the restaurant, then the customer, joined by a dashed line, each with a "Navigate" link
 * to the phone's maps app; the customer's stop has their phone number to call. Details can be missing for a moment
 * while the order's events are on their way.
 */
export function RouteTimeline({ delivery, activeLeg }: RouteTimelineProps) {
  const pickedUp = delivery.pickedUpAt !== null;
  return (
    <ol aria-label="Route" className="flex flex-col">
      <Stop
        icon={Store}
        kicker="Pick up"
        name={delivery.restaurantName}
        address={delivery.pickupAddress}
        isActive={activeLeg === 'pickup'}
        isDone={pickedUp}
      />
      <Stop
        icon={House}
        kicker="Deliver to"
        name={delivery.customerName}
        address={delivery.dropoffAddress}
        phone={delivery.customerPhone}
        isActive={activeLeg === 'dropoff'}
        isDone={delivery.deliveredAt !== null}
        isLast
      />
    </ol>
  );
}
