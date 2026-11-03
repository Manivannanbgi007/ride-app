import { usePartnerProfile } from '@/hooks/api/partnerHooks';
import type { PartnerStatus } from '@/types/delivery';
import { cn } from '@/utils/classNames';
import { PARTNER_STATUS_WORDS } from '@/utils/partnerAreaUtils';

const TONES: Record<PartnerStatus, { chip: string; dot: string }> = {
  AVAILABLE: { chip: 'bg-brand-100 text-brand-text-deep', dot: 'bg-brand-500' },
  BUSY: { chip: 'bg-info-100 text-info-ink', dot: 'bg-info-500' },
  OFFLINE: { chip: 'bg-page text-ink-soft ring-1 ring-line', dot: 'bg-ink-soft/60' },
  SUSPENDED: { chip: 'bg-danger-100 text-danger-ink', dot: 'bg-danger-500' },
};

/** The partner's status in the header, on every page: online (with a pulse), on a delivery, offline, suspended. */
export function PartnerStatusChip() {
  const profile = usePartnerProfile();
  if (!profile.data) {
    return null;
  }
  const { status } = profile.data;
  return (
    <span
      className={cn(
        'hidden items-center gap-2 rounded-full px-3 py-1.5 text-xs font-bold sm:inline-flex',
        TONES[status].chip,
      )}
    >
      <span aria-hidden="true" className="relative flex size-2">
        {status === 'AVAILABLE' && (
          <span className="absolute inline-flex size-full rounded-full bg-brand-500/60 motion-safe:animate-ping" />
        )}
        <span className={cn('relative inline-flex size-2 rounded-full', TONES[status].dot)} />
      </span>
      <span className="sr-only">Status: </span>
      {PARTNER_STATUS_WORDS[status]}
    </span>
  );
}
