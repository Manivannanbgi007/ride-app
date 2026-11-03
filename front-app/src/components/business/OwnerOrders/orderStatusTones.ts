import type { OrderStatus } from '@/types/order';

/**
 * The colours of one order status (tokens --color-st-* in index.css), as whole class names so Tailwind finds them:
 *   fill   the status's gradient, for white text (the open tab, the icon tiles, the order's stripe)
 *   glow   a coloured shadow under the filled tab
 *   soft   the light tint (the status pill, the details); text the status's own text colour on it or on a surface
 *   ring   a fine line of the status's colour (an open row)
 */
export interface StatusTone {
  fill: string;
  stripe: string;
  glow: string;
  soft: string;
  text: string;
  ring: string;
  hover: string;
}

export const ORDER_STATUS_TONES: Record<OrderStatus, StatusTone> = {
  // Not shown to the owner (the payment is not done yet); slate, like any order that is not going anywhere
  PAYMENT_PENDING: {
    fill: 'bg-linear-to-br from-st-cancelled-from to-st-cancelled-to',
    stripe: 'bg-linear-to-b from-st-cancelled-from to-st-cancelled-to',
    glow: 'shadow-lg shadow-st-cancelled-to/35',
    soft: 'bg-st-cancelled-soft',
    text: 'text-st-cancelled-text',
    ring: 'ring-st-cancelled-from/40',
    hover: 'hover:bg-st-cancelled-soft',
  },
  PLACED: {
    fill: 'bg-linear-to-br from-st-placed-from to-st-placed-to',
    stripe: 'bg-linear-to-b from-st-placed-from to-st-placed-to',
    glow: 'shadow-lg shadow-st-placed-to/35',
    soft: 'bg-st-placed-soft',
    text: 'text-st-placed-text',
    ring: 'ring-st-placed-from/40',
    hover: 'hover:bg-st-placed-soft',
  },
  ACCEPTED: {
    fill: 'bg-linear-to-br from-st-accepted-from to-st-accepted-to',
    stripe: 'bg-linear-to-b from-st-accepted-from to-st-accepted-to',
    glow: 'shadow-lg shadow-st-accepted-to/35',
    soft: 'bg-st-accepted-soft',
    text: 'text-st-accepted-text',
    ring: 'ring-st-accepted-from/40',
    hover: 'hover:bg-st-accepted-soft',
  },
  PREPARING: {
    fill: 'bg-linear-to-br from-st-preparing-from to-st-preparing-to',
    stripe: 'bg-linear-to-b from-st-preparing-from to-st-preparing-to',
    glow: 'shadow-lg shadow-st-preparing-from/35',
    soft: 'bg-st-preparing-soft',
    text: 'text-st-preparing-text',
    ring: 'ring-st-preparing-from/40',
    hover: 'hover:bg-st-preparing-soft',
  },
  READY_FOR_PICKUP: {
    fill: 'bg-linear-to-br from-st-ready-from to-st-ready-to',
    stripe: 'bg-linear-to-b from-st-ready-from to-st-ready-to',
    glow: 'shadow-lg shadow-st-ready-to/35',
    soft: 'bg-st-ready-soft',
    text: 'text-st-ready-text',
    ring: 'ring-st-ready-from/40',
    hover: 'hover:bg-st-ready-soft',
  },
  OUT_FOR_DELIVERY: {
    fill: 'bg-linear-to-br from-st-out-from to-st-out-to',
    stripe: 'bg-linear-to-b from-st-out-from to-st-out-to',
    glow: 'shadow-lg shadow-st-out-to/35',
    soft: 'bg-st-out-soft',
    text: 'text-st-out-text',
    ring: 'ring-st-out-from/40',
    hover: 'hover:bg-st-out-soft',
  },
  DELIVERED: {
    fill: 'bg-linear-to-br from-st-delivered-from to-st-delivered-to',
    stripe: 'bg-linear-to-b from-st-delivered-from to-st-delivered-to',
    glow: 'shadow-lg shadow-st-delivered-from/35',
    soft: 'bg-st-delivered-soft',
    text: 'text-st-delivered-text',
    ring: 'ring-st-delivered-from/40',
    hover: 'hover:bg-st-delivered-soft',
  },
  REJECTED: {
    fill: 'bg-linear-to-br from-st-rejected-from to-st-rejected-to',
    stripe: 'bg-linear-to-b from-st-rejected-from to-st-rejected-to',
    glow: 'shadow-lg shadow-st-rejected-to/35',
    soft: 'bg-st-rejected-soft',
    text: 'text-st-rejected-text',
    ring: 'ring-st-rejected-from/40',
    hover: 'hover:bg-st-rejected-soft',
  },
  CANCELLED: {
    fill: 'bg-linear-to-br from-st-cancelled-from to-st-cancelled-to',
    stripe: 'bg-linear-to-b from-st-cancelled-from to-st-cancelled-to',
    glow: 'shadow-lg shadow-st-cancelled-to/35',
    soft: 'bg-st-cancelled-soft',
    text: 'text-st-cancelled-text',
    ring: 'ring-st-cancelled-from/40',
    hover: 'hover:bg-st-cancelled-soft',
  },
};

/**
 * The customers' avatars: gradients made of the status colours' ends, each with white initials (4.5:1 or more).
 * A customer keeps the same one (chosen by their number), so a regular is recognised down the list.
 */
const AVATARS = [
  'bg-linear-to-br from-st-accepted-from to-st-placed-to',
  'bg-linear-to-br from-st-ready-from to-st-out-to',
  'bg-linear-to-br from-st-placed-from to-st-preparing-to',
  'bg-linear-to-br from-st-out-from to-st-accepted-to',
  'bg-linear-to-br from-st-rejected-to to-st-accepted-from',
  'bg-linear-to-br from-st-delivered-from to-st-ready-from',
] as const;

export function avatarToneOf(customerId: number): string {
  return AVATARS[Math.abs(customerId) % AVATARS.length];
}
