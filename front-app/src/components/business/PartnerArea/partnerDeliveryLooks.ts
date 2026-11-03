import { Ban, Bike, Clock, Hand, PackageCheck, Store, type LucideIcon } from 'lucide-react';

import type { DeliveryStatus } from '@/types/delivery';

/**
 * A delivery's status as the partner sees it (the admin labels speak of "a partner"), with its colours from the
 * order-status palette of index.css (--color-st-*, readable in both themes), as whole class names for Tailwind:
 *   pill    the light tint and the status's text on it
 *   stripe  the coloured edge of a row or panel
 *   ring    a fine line of the colour round the panel of the delivery being worked on
 */
export interface PartnerDeliveryLook {
  label: string;
  icon: LucideIcon;
  pill: string;
  stripe: string;
  ring: string;
}

export const PARTNER_DELIVERY_LOOKS: Record<DeliveryStatus, PartnerDeliveryLook> = {
  PENDING: {
    label: 'Waiting',
    icon: Clock,
    pill: 'bg-st-cancelled-soft text-st-cancelled-text',
    stripe: 'bg-linear-to-b from-st-cancelled-from to-st-cancelled-to',
    ring: 'ring-st-cancelled-from/40',
  },
  ASSIGNED: {
    label: 'New offer',
    icon: Hand,
    pill: 'bg-st-accepted-soft text-st-accepted-text',
    stripe: 'bg-linear-to-b from-st-accepted-from to-st-accepted-to',
    ring: 'ring-st-accepted-from/40',
  },
  ACCEPTED: {
    label: 'Go to pickup',
    icon: Store,
    pill: 'bg-st-preparing-soft text-st-preparing-text',
    stripe: 'bg-linear-to-b from-st-preparing-from to-st-preparing-to',
    ring: 'ring-st-preparing-from/40',
  },
  PICKED_UP: {
    label: 'On the way',
    icon: Bike,
    pill: 'bg-st-out-soft text-st-out-text',
    stripe: 'bg-linear-to-b from-st-out-from to-st-out-to',
    ring: 'ring-st-out-from/40',
  },
  DELIVERED: {
    label: 'Delivered',
    icon: PackageCheck,
    pill: 'bg-st-delivered-soft text-st-delivered-text',
    stripe: 'bg-linear-to-b from-st-delivered-from to-st-delivered-to',
    ring: 'ring-st-delivered-from/40',
  },
  CANCELLED: {
    label: 'Cancelled',
    icon: Ban,
    pill: 'bg-st-rejected-soft text-st-rejected-text',
    stripe: 'bg-linear-to-b from-st-rejected-from to-st-rejected-to',
    ring: 'ring-st-rejected-from/40',
  },
};

/** Still to do: offered, accepted or on the way (the "Active" tab). */
export function isActiveDelivery(status: DeliveryStatus): boolean {
  return (
    status === 'PENDING' || status === 'ASSIGNED' || status === 'ACCEPTED' || status === 'PICKED_UP'
  );
}
