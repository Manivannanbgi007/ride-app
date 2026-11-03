import type { DeliveryStatus } from '@/types/delivery';
import { cn } from '@/utils/classNames';

import { PARTNER_DELIVERY_LOOKS } from './partnerDeliveryLooks';

/** A delivery's status in the partner's words, as a small pill of its colour with its icon. */
export function PartnerDeliveryStatusPill({ status }: { status: DeliveryStatus }) {
  const { label, icon: Icon, pill } = PARTNER_DELIVERY_LOOKS[status];
  return (
    <span
      className={cn(
        'inline-flex shrink-0 items-center gap-1 rounded-full px-2 py-0.5 text-xs font-semibold whitespace-nowrap',
        pill,
      )}
    >
      <Icon aria-hidden="true" className="size-3.5" />
      {label}
    </span>
  );
}
