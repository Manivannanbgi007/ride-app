import { Bike, Phone, Search } from 'lucide-react';

import { buttonStyles } from '@/components/common/Button';
import type { LiveTracking, LiveTrackingPhase } from '@/types/liveTracking';
import type { OrderStatus } from '@/types/order';
import { VEHICLE_TYPE_LABELS } from '@/utils/constants';
import { initialsOf } from '@/utils/formatUtils';

/** What the partner is doing, as a short status under their name. */
const PHASE_STATUS: Record<LiveTrackingPhase, string> = {
  TO_RESTAURANT: 'Heading to the restaurant',
  AT_RESTAURANT: 'At the restaurant, collecting your food',
  TO_CUSTOMER: 'Picked up · on the way to you',
  DELIVERED: 'Delivered',
};

/**
 * The partner bringing the order: their initials (the platform keeps no photo), name, vehicle, what they are doing
 * and a button to call them (delivery-service shares the phone only while they are on the way). Not drawn once the
 * food is delivered.
 */
export function DeliveryPartnerCard({ tracking }: { tracking: LiveTracking }) {
  const { partner, phase } = tracking;
  if (phase === 'DELIVERED') {
    return null;
  }
  const onTheWay = phase === 'TO_CUSTOMER';
  return (
    <section
      aria-label="Your delivery partner"
      className="rounded-3xl bg-surface p-5 shadow-card motion-safe:animate-page-in"
    >
      <div className="flex items-center gap-4">
        <span className="relative shrink-0">
          <span
            aria-hidden="true"
            className="flex size-14 items-center justify-center rounded-2xl bg-brand-500 text-lg font-bold text-on-brand shadow-brand"
          >
            {initialsOf(partner.name)}
          </span>
          <span
            aria-hidden="true"
            className="absolute -right-1.5 -bottom-1.5 flex size-7 items-center justify-center rounded-full bg-surface text-brand-text shadow-card"
          >
            <Bike className="size-4" />
          </span>
        </span>
        <div className="min-w-0 flex-1">
          <p className="text-xs font-medium text-ink-soft">Your delivery partner</p>
          <p className="truncate text-base font-bold">{partner.name}</p>
          {partner.vehicleType && (
            <p className="text-sm text-ink-soft">{VEHICLE_TYPE_LABELS[partner.vehicleType]}</p>
          )}
        </div>
        {partner.phone && (
          <a
            href={`tel:${partner.phone}`}
            aria-label={`Call ${partner.name}`}
            className={buttonStyles('primary', 'sm', 'rounded-full')}
          >
            <Phone aria-hidden="true" className="size-4" />
            Call
          </a>
        )}
      </div>
      <p
        className={
          onTheWay
            ? 'mt-4 flex items-center gap-2 rounded-2xl bg-brand-50 px-3.5 py-2.5 text-sm font-semibold text-brand-text-strong'
            : 'mt-4 flex items-center gap-2 rounded-2xl bg-page px-3.5 py-2.5 text-sm font-medium text-ink'
        }
      >
        <span aria-hidden="true" className="relative flex size-2.5">
          <span className="absolute inline-flex size-full rounded-full bg-brand-500 opacity-60 motion-safe:animate-ping" />
          <span className="relative inline-flex size-2.5 rounded-full bg-brand-500" />
        </span>
        {PHASE_STATUS[phase]}
      </p>
    </section>
  );
}

const PENDING_TEXTS: Partial<Record<OrderStatus, { title: string; text: string }>> = {
  READY_FOR_PICKUP: {
    title: 'Finding a delivery partner',
    text: 'Your food is ready. Their name and a call button appear as soon as a partner accepts.',
  },
  OUT_FOR_DELIVERY: {
    title: 'Your partner is on the way',
    text: 'Their live position and contact are not available right now. This page keeps checking.',
  },
};

const BEFORE_DELIVERY = {
  title: 'Delivery partner',
  text: 'A partner is assigned once your food is ready. Their name and a call button appear here.',
};

/**
 * While the live delivery is not available: what happens next, from the order's own status (the delivery starts
 * when the food is ready, and a partner is then looked for). No name, no phone, no time: none is known here.
 * Nothing is drawn for a delivered or ended order.
 */
export function DeliveryPartnerPending({ status }: { status: OrderStatus }) {
  if (status === 'DELIVERED' || status === 'CANCELLED' || status === 'REJECTED') {
    return null;
  }
  const searching = status === 'READY_FOR_PICKUP';
  const { title, text } = PENDING_TEXTS[status] ?? BEFORE_DELIVERY;
  return (
    <section
      aria-label="Your delivery partner"
      className="flex items-center gap-4 rounded-3xl bg-surface p-5 shadow-card motion-safe:animate-page-in"
    >
      <span
        aria-hidden="true"
        className="relative flex size-14 shrink-0 items-center justify-center rounded-2xl border-2 border-dashed border-brand-500/50 bg-brand-50 text-brand-text"
      >
        {searching && (
          <span className="absolute inset-0 rounded-2xl bg-brand-500/20 motion-safe:animate-ping" />
        )}
        {searching ? <Search className="relative size-6" /> : <Bike className="size-6" />}
      </span>
      <div className="min-w-0">
        <p className="text-base font-bold">{title}</p>
        <p className="text-sm text-ink-soft">{text}</p>
      </div>
    </section>
  );
}
