import { MapPinOff, Navigation } from 'lucide-react';
import type { ReactNode } from 'react';

import { LiveLocationContext, useLiveLocationSharing } from '@/context/LiveLocationContext';
import { useCurrentDelivery } from '@/hooks/api/partnerHooks';
import { useShareLiveLocation } from '@/hooks/useShareLiveLocation';
import { cn } from '@/utils/classNames';

/**
 * Shares the partner's live position while their delivery is on its way, on every page of the partner area (they
 * may open their history on the road), and tells the pages how it goes (useLiveLocationSharing). The current
 * delivery is the home page's: it is not asked for more often here.
 */
export function PartnerLiveLocation({ children }: { children: ReactNode }) {
  const current = useCurrentDelivery(false);
  const sharing = useShareLiveLocation(current.data);
  return <LiveLocationContext value={sharing}>{children}</LiveLocationContext>;
}

/**
 * In the header while a delivery is on its way: the partner always sees that their position is shared, or that it
 * should be and cannot (blocked, no signal). Nothing otherwise.
 */
export function LiveLocationChip() {
  const { state } = useLiveLocationSharing();
  if (state === 'off') {
    return null;
  }
  const working = state === 'sharing' || state === 'starting';
  const label = working ? 'Sharing location' : 'Location not shared';
  return (
    <span
      className={cn(
        'inline-flex items-center gap-1.5 rounded-full px-2.5 py-1.5 text-xs font-bold',
        working ? 'bg-brand-100 text-brand-text-deep' : 'bg-warning-100 text-warning-ink',
      )}
    >
      {working ? (
        <Navigation aria-hidden="true" className="size-3.5" />
      ) : (
        <MapPinOff aria-hidden="true" className="size-3.5" />
      )}
      <span className="sr-only sm:not-sr-only">{label}</span>
    </span>
  );
}
