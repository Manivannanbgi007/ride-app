import {
  Bike,
  ChefHat,
  ClipboardCheck,
  HandPlatter,
  House,
  PackageCheck,
  type LucideIcon,
} from 'lucide-react';

import type { OrderStatus } from '@/types/order';

/** How each step of an order is drawn: its name, icon and colour (the green stays for what is done). */
export interface OrderStepLook {
  label: string;
  icon: LucideIcon;
  /** The round marker while this step is the current one (background and text) */
  color: string;
  /** What is happening, in a sentence, while this step is the current one */
  hint: string;
}

export const ORDER_STEP_LOOKS: Partial<Record<OrderStatus, OrderStepLook>> = {
  PLACED: {
    label: 'Placed',
    icon: ClipboardCheck,
    color: 'bg-info-500 text-white',
    hint: 'The restaurant will confirm your order in a moment.',
  },
  ACCEPTED: {
    label: 'Accepted',
    icon: HandPlatter,
    color: 'bg-lime-500 text-white',
    hint: 'The restaurant has your order and is getting started.',
  },
  PREPARING: {
    label: 'Preparing',
    icon: ChefHat,
    color: 'bg-sunset-500 text-white',
    hint: 'Your food is being prepared.',
  },
  READY_FOR_PICKUP: {
    label: 'Ready',
    icon: PackageCheck,
    color: 'bg-warning-500 text-on-accent',
    hint: 'Your food is ready: a delivery partner is on the way to collect it.',
  },
  OUT_FOR_DELIVERY: {
    label: 'On the way',
    icon: Bike,
    color: 'bg-berry-700 text-white',
    hint: 'Your food has been picked up and is on its way to you.',
  },
  DELIVERED: {
    label: 'Delivered',
    icon: House,
    color: 'bg-brand-500 text-on-brand',
    hint: 'Delivered. Enjoy your meal!',
  },
};
